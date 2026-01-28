package io.bluemacaw.mongodb.service;

import io.bluemacaw.mongodb.entity.UserSubscription;
import io.bluemacaw.mongodb.repository.UserSubscriptionRepository;
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
 * UserSubscription 业务逻辑层
 *
 * 数据访问策略（与 ChannelService 保持一致）：
 * - 原子操作（upsert, findAndModify）：使用 MongoTemplate（保证并发安全）
 * - 普通查询：使用 UserSubscriptionRepository（简洁的 API）
 *
 * @author shzhou.michael
 */
@Slf4j
@Service
public class UserSubscriptionService {

    /**
     * MongoTemplate - 用于需要原子性的操作
     * 如：findAndModify、upsert 等并发安全操作
     */
    @Resource
    private MongoTemplate mongoTemplate;

    /**
     * UserSubscriptionRepository - 用于普通的 CRUD 操作
     * 如：findById、findByUserIdAndChannelId 等简单查询
     */
    @Resource
    private UserSubscriptionRepository userSubscriptionRepository;

    /**
     * GroupService - 获取群成员信息
     */
    @Resource
    private GroupService groupService;

    @Resource
    @Qualifier("clickHouseDataSource")
    private DataSource clickHouseDataSource;

    /**
     * 私聊场景 - 确保双方的UserSubscription存在
     *
     * @param channelId 频道ID
     * @param userId1 用户1的ID
     * @param userId2 用户2的ID
     * @param currentVersion 当前频道版本号
     * @param now 当前时间
     */
    public void ensureUserSubscriptionsForPrivateChat(String channelId,
                                                      Long userId1, Long userId2,
                                                      Long currentVersion,
                                                      LocalDateTime now) {
        // 为用户1创建/更新订阅（使用原子操作）
        ensureUserSubscription(userId1, channelId, 0, currentVersion, now);

        // 为用户2创建/更新订阅（使用原子操作）
        ensureUserSubscription(userId2, channelId, 0, currentVersion, now);
    }

    /**
     * 群聊场景 - 确保所有群成员的UserSubscription存在
     *
     * @param channelId 频道ID（群聊场景下等于 groupId）
     * @param currentVersion 当前频道版本号
     * @param now 当前时间
     */
    public void ensureUserSubscriptionsForGroupChat(String channelId,
                                                    Long currentVersion,
                                                    LocalDateTime now) {
        try {
            // channelId 就是群 ID
            Long groupId = Long.parseLong(channelId);

            // 从 GroupService 获取群成员列表
            List<Long> memberUserIds = groupService.getGroupMemberUserIds(groupId);

            if (memberUserIds == null || memberUserIds.isEmpty()) {
                log.warn("群 {} 没有成员，跳过创建 UserSubscription", groupId);
                return;
            }

            log.debug("为群 {} 的 {} 个成员创建 UserSubscription", groupId, memberUserIds.size());

            // 为每个群成员创建订阅（使用原子操作保证并发安全）
            for (Long userId : memberUserIds) {
                ensureUserSubscription(userId, channelId, 1, currentVersion, now);
            }

            log.info("群 {} 的所有成员 UserSubscription 已确保存在，成员数: {}", groupId, memberUserIds.size());

        } catch (NumberFormatException e) {
            log.error("无效的群ID: {}", channelId, e);
        } catch (Exception e) {
            log.error("为群 {} 创建 UserSubscription 失败", channelId, e);
        }
    }

    /**
     * 确保用户订阅存在 - 高并发安全方式
     *
     * 使用 MongoDB 的 upsert 特性，利用 (user_id, channel_id) 的唯一索引保证：
     * 1. 多个线程同时检测到订阅不存在
     * 2. 多个线程同时尝试创建订阅
     * 3. 只有一个线程能成功创建，其他线程的 upsert 会变成 update（但不修改任何字段）
     * 4. 所有线程最终都能确保订阅存在
     *
     * 注意：此方法必须使用 MongoTemplate.findAndModify 保证原子性
     * 前提：user_subscription 表需要有 (user_id, channel_id) 的唯一索引
     *
     * @param userId 用户ID
     * @param channelId 频道ID
     * @param channelType 频道类型（0-私聊，1-群聊）
     * @param joinVersion 加入时的版本号
     * @param now 当前时间
     */
    private void ensureUserSubscription(Long userId, String channelId,
                                        int channelType, Long joinVersion,
                                        LocalDateTime now) {
        try {
            Query query = Query.query(
                    Criteria.where("user_id").is(userId)
                            .and("channel_id").is(channelId)
            );

            Update update = new Update()
                    // 只在 insert 时设置
                    .setOnInsert("user_id", userId)
                    .setOnInsert("channel_id", channelId)
                    .setOnInsert("channel_type", channelType)
                    .setOnInsert("last_read_version", 0L)
                    .setOnInsert("join_version", joinVersion)
                    .setOnInsert("join_time", now)
                    .setOnInsert("create_time", now)
                    .setOnInsert("update_time", now);

            FindAndModifyOptions options = new FindAndModifyOptions()
                    // 如果不存在则插入
                    .upsert(true)
                    // 返回更新后的文档
                    .returnNew(true);

            // 使用 MongoTemplate 保证原子性
            UserSubscription subscription = mongoTemplate.findAndModify(
                    query, update, options, UserSubscription.class
            );

            log.debug("UserSubscription 已确保存在: userId={}, channelId={}, subscriptionId={}",
                    userId, channelId, subscription.getId());

        } catch (Exception e) {
            log.error("确保 UserSubscription 存在失败: userId={}, channelId={}",
                    userId, channelId, e);

            // 发生异常时，使用 Repository 降级查询
            userSubscriptionRepository.findByUserIdAndChannelId(userId, channelId)
                    .ifPresent(sub -> log.debug("降级查询成功: subscriptionId={}", sub.getId()));
        }
    }

    /**
     * 更新UserSubscription的joinVersion和joinTime（用于历史数据导入）
     *
     * 注意：只更新未离开的订阅（leave_version为null）
     *
     * @param channelId 频道ID
     * @param userId 用户ID
     * @param joinVersion 加入版本号（最小seq）
     * @param joinTime 加入时间（消息日期）
     */
    public void updateUserSubscriptionJoinInfo(String channelId, Long userId, Long joinVersion, LocalDateTime joinTime) {
        try {
            Query query = Query.query(
                    Criteria.where("user_id").is(userId)
                            .and("channel_id").is(channelId)
                            .and("leave_version").isNull()
            );

            Update update = new Update()
                    .set("join_version", joinVersion)
                    .set("join_time", joinTime)
                    .set("update_time", LocalDateTime.now());

            mongoTemplate.updateFirst(query, update, UserSubscription.class);

        } catch (Exception e) {
            log.error("更新UserSubscription joinInfo失败: channelId={}, userId={}", channelId, userId, e);
        }
    }

    /**
     * 更新群聊所有成员的UserSubscription的joinVersion和joinTime（用于历史数据导入）
     *
     * 注意：只更新未离开的订阅（leave_version为null）
     *
     * @param channelId 频道ID（群ID）
     * @param joinVersion 加入版本号（最小seq）
     * @param joinTime 加入时间（消息日期）
     */
    public void updateGroupSubscriptionJoinInfo(String channelId, Long joinVersion, LocalDateTime joinTime) {
        try {
            Query query = Query.query(
                    Criteria.where("channel_id").is(channelId)
                            .and("leave_version").isNull()
            );

            Update update = new Update()
                    .set("join_version", joinVersion)
                    .set("join_time", joinTime)
                    .set("update_time", LocalDateTime.now());

            mongoTemplate.updateMulti(query, update, UserSubscription.class);

        } catch (Exception e) {
            log.error("更新群聊UserSubscription joinInfo失败: channelId={}", channelId, e);
        }
    }

    /**
     * 批量插入 UserSubscription 到 ClickHouse
     */
    public void batchInsertUserSubscriptionToClickHouse(List<UserSubscription> dataList) {
        String sql = "INSERT INTO im_message.user_subscription " +
                     "(id, userId, channelId, channelType, " +
                     "lastReadVersion, lastReadTime, " +
                     "joinVersion, joinTime, " +
                     "leaveVersion, leaveTime) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = clickHouseDataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            int batchCount = 0;
            for (UserSubscription data : dataList) {
                try {
                    PreparedStatementConverter.setUserSubscriptionStatementParameters(pstmt, data);
                    pstmt.addBatch();
                    batchCount++;

                    // 每 1000 条执行一次批量插入
                    if (batchCount >= 1000) {
                        pstmt.executeBatch();
                        log.debug("Executed batch insert: {} user subscriptions", batchCount);
                        batchCount = 0;
                    }
                } catch (Exception e) {
                    log.error("Failed to add user subscription to batch, subscriptionId={}", data.getId(), e);
                }
            }

            // 执行剩余的批量插入
            if (batchCount > 0) {
                pstmt.executeBatch();
                log.debug("Executed final batch insert: {} user subscriptions", batchCount);
            }

//            log.info("Successfully batch inserted {} user subscriptions to ClickHouse", dataList.size());

        } catch (Exception e) {
            log.error("Failed to batch insert user subscriptions to ClickHouse, size={}", dataList.size(), e);
            throw new RuntimeException("ClickHouse batch insert failed", e);
        }
    }
}
