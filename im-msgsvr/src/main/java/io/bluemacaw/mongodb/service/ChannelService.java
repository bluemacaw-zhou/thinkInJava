package io.bluemacaw.mongodb.service;

import io.bluemacaw.mongodb.entity.Channel;
import io.bluemacaw.mongodb.entity.mq.MqMessage;
import io.bluemacaw.mongodb.enums.ChannelType;
import io.bluemacaw.mongodb.repository.ChannelRepository;
import io.bluemacaw.mongodb.util.PreparedStatementConverter;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Channel 业务逻辑层
 * 提供高并发环境下安全的 Channel 创建和更新操作
 *
 * 数据访问策略：
 * - 原子操作（upsert, findAndModify）：使用 MongoTemplate（保证并发安全）
 * - 普通查询：使用 ChannelRepository（简洁的 API）
 *
 * @author shzhou.michael
 */
@Slf4j
@Service
public class ChannelService {

    /**
     * MongoTemplate - 用于需要原子性的操作
     * 如：findAndModify、upsert 等并发安全操作
     */
    @Resource
    private MongoTemplate mongoTemplate;

    /**
     * ChannelRepository - 用于普通的 CRUD 操作
     * 如：findById、save、delete 等简单查询
     */
    @Resource
    private ChannelRepository channelRepository;

    @Resource
    @Qualifier("clickHouseDataSource")
    private DataSource clickHouseDataSource;

    /**
     * 初始 channel message_version
     */
    private static final long INITIAL_VERSION = 100000L;

    /**
     * 确保 Channel 存在 - 高并发安全方式
     *
     * 使用 MongoDB 的 upsert 特性,利用 _id 的唯一索引保证:
     * 1. 多个实例同时检测到 channel 不存在
     * 2. 多个实例同时尝试创建 channel
     * 3. 只有一个实例能成功创建,其他实例的 upsert 会变成 update(但不修改任何字段)
     * 4. 所有实例最终都能获得同一个 channel
     *
     * 注意：此方法必须使用 MongoTemplate.findAndModify 保证原子性
     *
     * @param channelId   频道ID (私聊: "userId1_userId2", 群聊: "group_groupId")
     * @param channelType 频道类型 (PRIVATE/GROUP)
     * @return Session(Channel) 对象 (存在的或新创建的)
     */
    public Channel ensureChannelExists(String channelId, int channelType) {
        try {
            Query query = new Query(Criteria.where("_id").is(channelId));

            LocalDateTime now = LocalDateTime.now();
            Update update = new Update()
                    // 只在 insert 时设置
                    .setOnInsert("channel_type", channelType)
                    // 只在 insert 时设置
                    .setOnInsert("message_version", INITIAL_VERSION)
                    // 只在 insert 时设置
                    .setOnInsert("create_time", now)
                    // 只在 insert 时设置
                    .setOnInsert("update_time", now);

            FindAndModifyOptions options = new FindAndModifyOptions()
                    // 如果不存在则插入
                    .upsert(true)
                    // 返回更新后的文档
                    .returnNew(true);

            // 使用 MongoTemplate 保证原子性
            Channel channel = mongoTemplate.findAndModify(query, update, options, Channel.class);

            log.debug("Channel {} 已确保存在, message_version: {}", channelId, channel.getMessageVersion());
            return channel;

        } catch (Exception e) {
            log.error("确保 Channel 存在失败, channelId: {}, channelType: {}",
                    channelId, channelType, e);

            // 发生异常时,使用 Repository 降级查询
            return channelRepository.findById(channelId).orElse(null);
        }
    }

    /**
     * 增加 Channel 消息版本号并返回新版本号
     * 当有新消息到达时调用
     * 这个版本号将作为消息的seq使用
     *
     * 注意：此方法必须使用 MongoTemplate.findAndModify 保证原子性
     * 确保并发场景下 seq 唯一且单调递增
     *
     * @param channelId 频道ID
     * @return 更新后的版本号(即新消息的seq),失败返回 null
     */
    public Long incrementAndGetMessageVersion(String channelId) {
        return incrementAndGetMessageVersion(channelId, 1);
    }

    /**
     * 增加 Channel 消息版本号并返回起始版本号
     * 支持批量分配seq场景
     *
     * @param channelId 频道ID
     * @param incCount 递增数量
     * @return 如果incCount=1，返回更新后的版本号；如果incCount>1，返回更新前的版本号(作为起始seq)；失败返回 null
     */
    public Long incrementAndGetMessageVersion(String channelId, int incCount) {
        try {
            Query query = new Query(Criteria.where("_id").is(channelId));

            LocalDateTime now = LocalDateTime.now();
            Update update = new Update()
                    .inc("message_version", incCount)
                    .set("update_time", now);

            // 如果是单条消息，返回更新后的版本号；如果是批量，返回更新前的版本号
            FindAndModifyOptions options = new FindAndModifyOptions()
                    .returnNew(incCount == 1);

            Channel channel = mongoTemplate.findAndModify(query, update, options, Channel.class);

            if (channel == null) {
                log.error("Channel {} 不存在，无法更新版本号", channelId);
                return null;
            }

            Long version = channel.getMessageVersion();
            if (incCount == 1) {
                log.debug("Channel {} 消息版本号已更新: {}", channelId, version);
            } else {
                log.debug("Channel {} 批量递增版本号: startVersion={}, incCount={}, endVersion={}",
                        channelId, version, incCount, version + incCount);
            }
            return version;
        } catch (Exception e) {
            log.error("增加 Channel 消息版本号失败, channelId: {}, incCount: {}",
                    channelId, incCount, e);
            return null;
        }
    }

    /**
     * 递减并获取Channel的message_version (用于历史消息导入时的seq分配)
     *
     * 注意：
     * - 如果当前 message_version == 100000(初始值)，则不递减，直接返回 100000
     * - 保证 100000 这个seq能被分配出去
     * - 使用 findAndModify 保证原子性
     *
     * @param channelId 频道ID
     * @return 更新后的版本号(即新消息的seq),失败返回 null
     */
    public Long decrementAndGetMessageVersion(String channelId) {
        return decrementAndGetMessageVersion(channelId, 1);
    }

    /**
     * 递减并获取Channel的message_version (用于历史消息导入时的seq分配)
     * 支持批量分配seq场景
     *
     * @param channelId 频道ID
     * @param decCount 递减数量
     * @return 如果decCount=1，返回更新后的版本号；如果decCount>1，返回更新前的版本号(作为起始seq)；失败返回 null
     */
    public Long decrementAndGetMessageVersion(String channelId, int decCount) {
        try {
            Channel channel = getChannel(channelId);
            if (channel == null) {
                log.error("Channel {} 不存在，无法递减版本号", channelId);
                return null;
            }

            Long currentVersion = channel.getMessageVersion();

            // 如果当前版本号是100000(初始值)，需要特殊处理
            if (currentVersion == 100000L) {
                if (decCount == 1) {
                    // 单条消息：直接返回100000，不递减
                    log.debug("Channel {} 版本号为初始值100000，返回100000", channelId);
                    return 100000L;
                } else {
                    // 批量消息：递减 decCount-1，保证100000能被分配出去
                    decCount = decCount - 1;
                }
            }

            // 递减版本号
            Query query = new Query(Criteria.where("_id").is(channelId));
            LocalDateTime now = LocalDateTime.now();
            Update update = new Update()
                    .inc("message_version", -decCount)
                    .set("update_time", now);

            // 如果是单条消息，返回更新后的版本号；如果是批量，返回更新前的版本号
            FindAndModifyOptions options = new FindAndModifyOptions()
                    .returnNew(decCount == 1);

            Channel updatedChannel = mongoTemplate.findAndModify(query, update, options, Channel.class);

            if (updatedChannel == null) {
                log.error("Channel {} 递减版本号失败", channelId);
                return null;
            }

            Long version = updatedChannel.getMessageVersion();
            if (decCount == 1) {
                log.debug("Channel {} 消息版本号已递减: {}", channelId, version);
            } else {
                log.debug("Channel {} 批量递减版本号: startVersion={}, decCount={}, endVersion={}",
                        channelId, version, decCount, version - decCount);
            }
            return version;
        } catch (Exception e) {
            log.error("递减 Channel 消息版本号失败, channelId: {}, decCount: {}", channelId, decCount, e);
            return null;
        }
    }

    /**
     * 获取 Session
     *
     * @param channelId 会话ID
     * @return Channel 对象,不存在返回 null
     */
    public Channel getChannel(String channelId) {
        return channelRepository.findById(channelId).orElse(null);
    }


    public static String generateChannelId(MqMessage mqMessage) {
        int contactType = mqMessage.getMqMessageData().getContactType();
        long fromId = mqMessage.getMqMessageData().getFromId();
        long contactId = mqMessage.getMqMessageData().getContactId();

        if (contactType == ChannelType.PRIVATE.getCode()) {
            // 私聊：contactId是接收者ID，生成 fromId_contactId
            return ChannelService.generatePrivateChannelId(fromId, contactId);
        } else {
            // 群聊：contactId是群ID
            return ChannelService.generateGroupChannelId(contactId);
        }
    }

    /**
     * 生成私聊 Channel ID
     * 规则: 小ID_大ID
     *
     * @param userId1 用户1的ID
     * @param userId2 用户2的ID
     * @return channelId
     */
    public static String generatePrivateChannelId(Long userId1, Long userId2) {
        if (userId1 < userId2) {
            return userId1 + "_" + userId2;
        } else {
            return userId2 + "_" + userId1;
        }
    }

    /**
     * 生成群聊 Channel ID
     *
     * @param groupId 群ID
     * @return channelId
     */
    public static String generateGroupChannelId(Long groupId) {
        return groupId.toString();
    }

    /**
     * 批量插入 Channel 到 ClickHouse
     */
    public void batchInsertChannelToClickHouse(List<Channel> dataList) {
        String sql = "INSERT INTO im_message.channel " +
                     "(id, channelType, messageVersion) " +
                     "VALUES (?, ?, ?)";

        try (Connection conn = clickHouseDataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            int batchCount = 0;
            for (Channel data : dataList) {
                try {
                    PreparedStatementConverter.setChannelStatementParameters(pstmt, data);
                    pstmt.addBatch();
                    batchCount++;

                    // 每 1000 条执行一次批量插入
                    if (batchCount >= 1000) {
                        pstmt.executeBatch();
                        log.debug("Executed batch insert: {} channels", batchCount);
                        batchCount = 0;
                    }
                } catch (Exception e) {
                    log.error("Failed to add channel to batch, channelId={}", data.getId(), e);
                }
            }

            // 执行剩余的批量插入
            if (batchCount > 0) {
                pstmt.executeBatch();
                log.debug("Executed final batch insert: {} channels", batchCount);
            }

//            log.info("Successfully batch inserted {} channels to ClickHouse", dataList.size());

        } catch (Exception e) {
            log.error("Failed to batch insert channels to ClickHouse, size={}", dataList.size(), e);
            throw new RuntimeException("ClickHouse batch insert failed", e);
        }
    }
}
