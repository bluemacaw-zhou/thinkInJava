package io.bluemacaw.mongodb.service;

import io.bluemacaw.mongodb.entity.Message;
import io.bluemacaw.mongodb.entity.Channel;
import io.bluemacaw.mongodb.entity.mq.MqAggregatedMessageData;
import io.bluemacaw.mongodb.entity.mq.MqMessage;
import io.bluemacaw.mongodb.entity.mq.MqMessageData;
import io.bluemacaw.mongodb.enums.ChannelType;
import io.bluemacaw.mongodb.util.CollectionNameUtil;
import io.bluemacaw.mongodb.util.PreparedStatementConverter;
import io.bluemacaw.mongodb.util.MessageConverter;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.bluemacaw.mongodb.service.ChannelService.generateChannelId;
import static io.bluemacaw.mongodb.util.MessageConverter.mqMsgItemToMessage;
import static org.springframework.data.domain.Sort.by;

/**
 * 消息服务
 * 实现消息的发送、查询等核心功能
 */
@Slf4j
@Service
public class MessageService {
    @Resource
    private MongoTemplate mongoTemplate;

    @Resource
    private ChannelService channelService;

    @Resource
    private UserSubscriptionService userSubscriptionService;

    @Resource
    @Qualifier("clickHouseDataSource")
    private DataSource clickHouseDataSource;

    @Value("${mongodb.seq-assignment.boundary-date}")
    private String seqBoundaryDate;

    /**
     * 发送消息 - MongoDB事务保证原子性
     *
     * @param mqMessage   mq消息
     * @return 保存后的消息对象(包含MongoDB生成的ID和seq)
     */
    @Transactional(rollbackFor = Exception.class)
    public Message saveMessage(MqMessage mqMessage) {
        MqMessageData mqMessageData = mqMessage.getMqMessageData();

        String channelId = generateChannelId(mqMessage);
        int channelType = mqMessageData.getContactType();
        long fromId = mqMessageData.getFromId();
        long toId = mqMessageData.getContactId();

        try {
            LocalDateTime now = LocalDateTime.now();

            // 步骤1: 确保Channel存在
            Channel channel = channelService.ensureChannelExists(channelId, channelType);
            if (channel == null) {
                throw new RuntimeException("创建Channel失败: " + channelId);
            }

            // 步骤2: 确保UserSubscription存在
            if (ChannelType.PRIVATE.getCode() == channelType) {
                // 私聊场景：为双方创建订阅
                userSubscriptionService.ensureUserSubscriptionsForPrivateChat(channelId, fromId, toId,
                        channel.getMessageVersion(), now);
            } else if (ChannelType.GROUP.getCode() == channelType) {
                // 群聊场景：为所有群成员创建订阅
                userSubscriptionService.ensureUserSubscriptionsForGroupChat(channelId,
                        channel.getMessageVersion(), now);
            }

            // 步骤3: 增加Channel的message_version并获取新版本号(作为消息seq)
            Long newSeq = channelService.incrementAndGetMessageVersion(channelId);
            if (newSeq == null) {
                throw new RuntimeException("更新Channel版本号失败: " + channelId);
            }

            // 步骤4: 构建mongodb中的消息
            Message mongodbMessage = mqMsgItemToMessage(mqMessage, channelId, newSeq);
            assert mongodbMessage != null;

            // 步骤5: 动态确定collection名称并保存消息
            String collectionName = CollectionNameUtil.getMessageCollection(now);
            
            log.info("准备保存消息到MongoDB: database={}, collection={}, channelId={}, seq={}, MongoTemplate={}", 
                    mongoTemplate.getDb().getName(), collectionName, channelId, newSeq, mongoTemplate.hashCode());
            
            Message savedMessage = mongoTemplate.insert(mongodbMessage, collectionName);

            log.info("消息发送成功: channelId={}, seq={}, collection={}, msgId={}",
                    channelId, newSeq, collectionName, savedMessage.getId());

            return savedMessage;

        } catch (Exception e) {
            log.error("发送消息失败: channelId={}, fromId={}, toId={}",
                    channelId, fromId, toId, e);
            throw new RuntimeException("发送消息失败", e);
        }
    }

    /**
     * 批量保存消息 - 用于历史数据导入场景
     *
     * 处理逻辑：
     * 1. 确保Channel存在（返回包含当前version的channel对象）
     * 2. 确保UserSubscription存在
     * 3. 按msgTime排序消息
     * 4. 根据消息日期与boundary-date比较，决定seq分配策略（递增/递减）
     * 5. 基于channel当前version和消息数量，计算seq区间并在内存中分配
     * 6. 批量插入MongoDB
     * 7. 根据seq区间边界更新数据库：
     *    - 如果最小seq ≤ 100000，更新userSubscription的joinVersion和joinTime
     *    - 如果最大seq > 100000，更新channel的messageVersion
     *
     * @param aggregatedData 聚合消息数据
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveBatchMessages(MqAggregatedMessageData aggregatedData) {
        String channelId = aggregatedData.getChannelId();
        int channelType = aggregatedData.getChannelType();
        String messageDate = aggregatedData.getMessageDate();
        List<MqMessage> mqMessages = aggregatedData.getMessages();

        if (mqMessages == null || mqMessages.isEmpty()) {
            return;
        }

        try {
            LocalDateTime now = LocalDateTime.now();

            // 步骤1: 确保Channel存在（获取当前version）
            Channel channel = channelService.ensureChannelExists(channelId, channelType);
            if (channel == null) {
                throw new RuntimeException("创建Channel失败: " + channelId);
            }
            Long currentVersion = channel.getMessageVersion();

            // 步骤2: 确保UserSubscription存在
            if (ChannelType.PRIVATE.getCode() == channelType) {
                MqMessageData firstMsgData = mqMessages.get(0).getMqMessageData();
                long fromId = firstMsgData.getFromId();
                long toId = firstMsgData.getContactId();
                userSubscriptionService.ensureUserSubscriptionsForPrivateChat(channelId, fromId, toId,
                        currentVersion, now);
            } else if (ChannelType.GROUP.getCode() == channelType) {
                userSubscriptionService.ensureUserSubscriptionsForGroupChat(channelId,
                        currentVersion, now);
            }

            // 步骤3: 按msgTime排序消息（确保seq按时间顺序分配）
            mqMessages.sort((m1, m2) -> {
                String msgTime1 = m1.getMqMessageData().getMsgTime();
                String msgTime2 = m2.getMqMessageData().getMsgTime();
                try {
                    long timestamp1 = Long.parseLong(msgTime1);
                    long timestamp2 = Long.parseLong(msgTime2);
                    return Long.compare(timestamp1, timestamp2);
                } catch (NumberFormatException e) {
                    return 0;
                }
            });

            // 步骤4: 判断seq分配策略
            LocalDate msgDate = LocalDate.parse(messageDate);
            LocalDate boundaryDate = LocalDate.parse(seqBoundaryDate);
            boolean useDecrement = !msgDate.isAfter(boundaryDate);

            int messageCount = mqMessages.size();

            // 步骤5: 基于当前version计算seq区间，并在内存中分配
            Long minSeq;
            Long maxSeq;

            if (useDecrement) {
                // 递减：从currentVersion开始往下分配
                maxSeq = currentVersion;
                minSeq = currentVersion - messageCount + 1;
            } else {
                // 递增：从currentVersion+1开始往上分配
                minSeq = currentVersion + 1;
                maxSeq = currentVersion + messageCount;
            }

            List<Message> mongoMessages = new ArrayList<>();
            for (int i = 0; i < mqMessages.size(); i++) {
                Long seq = useDecrement ? (maxSeq - i) : (minSeq + i);

                Message mongoMessage = mqMsgItemToMessage(mqMessages.get(i), channelId, seq);
                if (mongoMessage != null) {
                    mongoMessages.add(mongoMessage);
                }
            }

            // 步骤6: 批量插入MongoDB
            if (!mongoMessages.isEmpty()) {
                LocalDateTime firstMsgTime = mongoMessages.get(0).getMsgTime();
                String collectionName = CollectionNameUtil.getMessageCollection(firstMsgTime);
                mongoTemplate.insert(mongoMessages, collectionName);
            }

            // 步骤7: 根据seq区间边界更新数据库
            if (minSeq < 100000L) {
                // 最小seq ≤ 100000，更新userSubscription的joinVersion和joinTime
                LocalDateTime joinTime = msgDate.atStartOfDay();

                if (ChannelType.PRIVATE.getCode() == channelType) {
                    MqMessageData firstMsgData = mqMessages.get(0).getMqMessageData();
                    long fromId = firstMsgData.getFromId();
                    long toId = firstMsgData.getContactId();
                    userSubscriptionService.updateUserSubscriptionJoinInfo(channelId, fromId, minSeq, joinTime);
                    userSubscriptionService.updateUserSubscriptionJoinInfo(channelId, toId, minSeq, joinTime);
                } else if (ChannelType.GROUP.getCode() == channelType) {
                    userSubscriptionService.updateGroupSubscriptionJoinInfo(channelId, minSeq, joinTime);
                }
            }

            if (maxSeq > 100000L) {
                // 最大seq > 100000，更新channel的messageVersion
                channelService.incrementAndGetMessageVersion(channelId, messageCount);
            }

            log.info("批量保存消息成功: channelId={}, messageDate={}, count={}, seqRange=[{}, {}]",
                    channelId, messageDate, messageCount, minSeq, maxSeq);

        } catch (Exception e) {
            log.error("批量保存消息失败: channelId={}, messageDate={}, count={}",
                    channelId, messageDate, mqMessages.size(), e);
            throw new RuntimeException("批量保存消息失败", e);
        }
    }

    /**
     * 查询频道消息 - 根据 seq 区间查询缺失的消息
     *
     * 用途：客户端发现消息缺失时，根据 seq 区间查询补齐消息
     * 例如：本地有 seq=100, 105，发现缺失 101-104，则查询 sinceVersion=100, untilVersion=105
     *
     * 实现：从当前月开始往前查询，直到满足以下任一条件：
     * 1. 查到的最小 seq <= sinceVersion（说明已经覆盖了目标区间）
     * 2. collection 不存在（已经查到最早的月份）
     *
     * @param channelId     频道ID
     * @param sinceVersion  起始版本号(不含)
     * @param untilVersion  结束版本号(含)
     * @return 消息列表(按seq升序)，返回区间 (sinceVersion, untilVersion] 内的所有消息
     */
    public List<Message> queryMessages(String channelId,
                                       Long sinceVersion, Long untilVersion) {

        LocalDateTime now = LocalDateTime.now();
        List<Message> allMessages = new ArrayList<>();

        // 从当前月开始往前查询
        int monthOffset = 0;
        // 记录查到的最小 seq
        Long minSeqFound = Long.MAX_VALUE;

        while (minSeqFound > sinceVersion) {
            LocalDateTime monthDate = now.minusMonths(monthOffset);
            String collection = CollectionNameUtil.getMessageCollection(monthDate);

            // 检查 collection 是否存在
            if (!mongoTemplate.collectionExists(collection)) {
                log.debug("Collection {} 不存在，停止查询", collection);
                break;
            }

            Query query = Query.query(
                    Criteria.where("channel_id").is(channelId)
                            .and("seq").gt(sinceVersion).lte(untilVersion)
            );
            query.with(by("seq").ascending());

            // 从指定的collection查询
            List<Message> messages = mongoTemplate.find(query, Message.class, collection);

            if (!messages.isEmpty()) {
                allMessages.addAll(messages);
                // 更新找到的最小 seq
                Long currentMinSeq = messages.get(0).getSeq();
                if (currentMinSeq < minSeqFound) {
                    minSeqFound = currentMinSeq;
                }
                log.debug("从collection {} 查询到 {} 条消息，最小seq: {}", collection, messages.size(), currentMinSeq);
            } else {
                log.debug("从collection {} 查询到 0 条消息", collection);
            }

            monthOffset++;
        }

        // 按seq排序(防止跨collection时乱序)
        allMessages.sort((m1, m2) -> m1.getSeq().compareTo(m2.getSeq()));

        log.info("查询消息完成: channelId={}, sinceVersion={}, untilVersion={}, 总数={}, 查询了{}个月",
                channelId, sinceVersion, untilVersion, allMessages.size(), monthOffset);

        return allMessages;
    }

    /**
     * 分页查询历史消息(向上滑动加载)
     *
     * 从当前月开始往前查询，直到满足以下任一条件：
     * 1. 已经查到足够数量的消息 (limit)
     * 2. collection 不存在（说明已经查到最早的月份）
     *
     * @param channelId      频道ID
     * @param cursorVersion  游标版本号(查询小于此版本的消息)
     * @param limit          每次加载数量
     * @return 消息列表(按seq降序)
     */
    public List<Message> queryHistoryMessages(String channelId,
                                              Long cursorVersion,
                                              int limit) {

        LocalDateTime now = LocalDateTime.now();
        List<Message> allMessages = new java.util.ArrayList<>();

        // 从当前月开始往前查询，直到找不到 collection 或查够数量
        int monthOffset = 0;
        while (allMessages.size() < limit) {
            LocalDateTime monthDate = now.minusMonths(monthOffset);
            String collection = CollectionNameUtil.getMessageCollection(monthDate);

            // 检查 collection 是否存在
            if (!mongoTemplate.collectionExists(collection)) {
                log.debug("Collection {} 不存在，停止查询", collection);
                break;
            }

            Query query = Query.query(
                    Criteria.where("channel_id").is(channelId)
                            .and("seq").lt(cursorVersion)
            );
            query.with(by("seq").descending());
            query.limit(limit - allMessages.size());

            List<Message> messages = mongoTemplate.find(query, Message.class, collection);
            allMessages.addAll(messages);

            log.debug("从collection {} 查询到 {} 条历史消息", collection, messages.size());

            // 如果当前 collection 没有查到任何消息，继续查询下一个月
            // 但如果已经凑够了需要的数量，则停止查询
            if (allMessages.size() >= limit) {
                break;
            }

            monthOffset++;
        }

        log.info("查询历史消息完成: channelId={}, cursorVersion={}, limit={}, 实际返回={}, 查询了{}个月",
                channelId, cursorVersion, limit, allMessages.size(), monthOffset + 1);

        return allMessages;
    }

    /**
     * 批量插入 Message 到 ClickHouse
     * 使用 ClickHouse 推荐的 input() 函数进行批量插入，性能更优
     */
    public void batchInsertMessageToClickHouse(List<Message> dataList) {
        String sql = "INSERT INTO im_message.message " +
                     "SELECT id, channelId, seq, oldMsgId, " +
                     "fromId, fromCompanyId, fromCompany, " +
                     "toId, toCompanyId, toCompany, " +
                     "contactType, msgType, content, contentVersion, " +
                     "msgTime, createTime, " +
                     "clientMsgId, clientInfo, deleted, status " +
                     "FROM input('" +
                     "id String, channelId String, seq Int64, oldMsgId String, " +
                     "fromId Int64, fromCompanyId String, fromCompany String, " +
                     "toId Nullable(Int64), toCompanyId Nullable(String), toCompany Nullable(String), " +
                     "contactType Int32, msgType Int32, content String, contentVersion Int32, " +
                     "msgTime DateTime64(3), createTime DateTime64(3), " +
                     "clientMsgId String, clientInfo String, deleted Int32, status Int32" +
                     "')";

        try (Connection conn = clickHouseDataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            for (Message data : dataList) {
                PreparedStatementConverter.setMessageStatementParameters(pstmt, data);
                pstmt.addBatch();
            }

            pstmt.executeBatch();
//            log.info("Successfully batch inserted {} messages to ClickHouse using input() function", dataList.size());

        } catch (Exception e) {
            log.error("Failed to batch insert to ClickHouse, size={}", dataList.size(), e);
            throw new RuntimeException("ClickHouse batch insert failed", e);
        }
    }

    /**
     * 查询消息总数
     */
    public long getTotalCount() {
        String sql = "SELECT count(*) as total FROM im_message.message";

        try (Connection conn = clickHouseDataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            if (rs.next()) {
                return rs.getLong("total");
            }
        } catch (SQLException e) {
            log.error("Error querying total count from ClickHouse", e);
        }
        return 0;
    }

    /**
     * 查询最近的消息列表
     *
     * @param limit 限制数量
     * @return 消息列表
     */
    public List<Message> getRecentMessages(int limit) {
        String sql = "SELECT * FROM im_message.message ORDER BY createTime DESC LIMIT ?";
        List<Message> result = new ArrayList<>();

        try (Connection conn = clickHouseDataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, limit);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    result.add(MessageConverter.resultSetToMessage(rs));
                }
            }

            log.info("Query recent messages, limit: {}, result size: {}", limit, result.size());

        } catch (SQLException e) {
            log.error("Error querying recent messages from ClickHouse", e);
        }

        return result;
    }

    /**
     * 根据发送者ID查询消息
     *
     * @param fromId 发送者ID
     * @param limit  限制数量
     * @return 消息列表
     */
    public List<Message> getMessagesByFromId(long fromId, int limit) {
        String sql = "SELECT * FROM im_message.message WHERE fromId = ? ORDER BY createTime DESC LIMIT ?";
        List<Message> result = new ArrayList<>();

        try (Connection conn = clickHouseDataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, fromId);
            pstmt.setInt(2, limit);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    result.add(MessageConverter.resultSetToMessage(rs));
                }
            }

            log.info("Query messages by fromId: {}, limit: {}, result size: {}", fromId, limit, result.size());

        } catch (SQLException e) {
            log.error("Error querying messages by fromId from ClickHouse", e);
        }

        return result;
    }

    /**
     * 根据联系人ID查询消息
     *
     * @param contactId 联系人ID
     * @param limit     限制数量
     * @return 消息列表
     */
    public List<Message> getMessagesByContactId(long contactId, int limit) {
        String sql = "SELECT * FROM im_message.message WHERE contactId = ? ORDER BY createTime DESC LIMIT ?";
        List<Message> result = new ArrayList<>();

        try (Connection conn = clickHouseDataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, contactId);
            pstmt.setInt(2, limit);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    result.add(MessageConverter.resultSetToMessage(rs));
                }
            }

            log.info("Query messages by contactId: {}, limit: {}, result size: {}", contactId, limit, result.size());

        } catch (SQLException e) {
            log.error("Error querying messages by contactId from ClickHouse", e);
        }

        return result;
    }

    /**
     * 根据时间范围查询消息
     *
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @param limit     限制数量
     * @return 消息列表
     */
    public List<Message> getMessagesByTimeRange(LocalDateTime startTime, LocalDateTime endTime, int limit) {
        String sql = "SELECT * FROM im_message.message WHERE createTime BETWEEN ? AND ? ORDER BY createTime DESC LIMIT ?";
        List<Message> result = new ArrayList<>();

        try (Connection conn = clickHouseDataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setTimestamp(1, Timestamp.valueOf(startTime));
            pstmt.setTimestamp(2, Timestamp.valueOf(endTime));
            pstmt.setInt(3, limit);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    result.add(MessageConverter.resultSetToMessage(rs));
                }
            }

            log.info("Query messages by time range: {} to {}, limit: {}, result size: {}",
                    startTime, endTime, limit, result.size());

        } catch (SQLException e) {
            log.error("Error querying messages by time range from ClickHouse", e);
        }

        return result;
    }

    /**
     * 按日期统计消息数量
     *
     * @param days 最近天数
     * @return 日期统计数据
     */
    public List<Map<String, Object>> getMessageCountByDate(int days) {
        String sql = "SELECT toDate(createTime) as date, count(*) as count " +
                "FROM im_message.message " +
                "WHERE createTime >= now() - INTERVAL ? DAY " +
                "GROUP BY date " +
                "ORDER BY date DESC";

        List<Map<String, Object>> result = new ArrayList<>();

        try (Connection conn = clickHouseDataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, days);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("date", rs.getDate("date"));
                    row.put("count", rs.getLong("count"));
                    result.add(row);
                }
            }

            log.info("Query message count by date, days: {}, result size: {}", days, result.size());

        } catch (SQLException e) {
            log.error("Error querying message count by date from ClickHouse", e);
        }

        return result;
    }

    /**
     * 按用户统计消息数量
     *
     * @param limit 限制数量
     * @return 用户统计数据
     */
    public List<Map<String, Object>> getTopSenders(int limit) {
        String sql = "SELECT fromId, count(*) as msg_count " +
                "FROM im_message.message " +
                "GROUP BY fromId " +
                "ORDER BY msg_count DESC " +
                "LIMIT ?";

        List<Map<String, Object>> result = new ArrayList<>();

        try (Connection conn = clickHouseDataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, limit);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("fromId", rs.getLong("fromId"));
                    row.put("msgCount", rs.getLong("msg_count"));
                    result.add(row);
                }
            }

            log.info("Query top senders, limit: {}, result size: {}", limit, result.size());

        } catch (SQLException e) {
            log.error("Error querying top senders from ClickHouse", e);
        }

        return result;
    }

    /**
     * 执行自定义SQL查询
     *
     * @param sql 自定义SQL语句
     * @return 查询结果
     */
    public List<Map<String, Object>> executeCustomQuery(String sql) {
        List<Map<String, Object>> result = new ArrayList<>();

        try (Connection conn = clickHouseDataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();

            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                for (int i = 1; i <= columnCount; i++) {
                    row.put(metaData.getColumnName(i), rs.getObject(i));
                }
                result.add(row);
            }

            log.info("Execute custom query, result size: {}", result.size());

        } catch (SQLException e) {
            log.error("Error executing custom query from ClickHouse: {}", sql, e);
        }

        return result;
    }
}
