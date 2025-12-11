package io.bluemacaw.mongodb.service;

import com.mongodb.client.result.UpdateResult;
import io.bluemacaw.mongodb.entity.Session;
import io.bluemacaw.mongodb.repository.SessionRepository;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

/**
 * Session 业务逻辑层
 * 提供高并发环境下安全的 Session 创建和更新操作
 */
@Slf4j
@Service
public class SessionService {

    @Resource
    private MongoTemplate mongoTemplate;

    @Resource
    private SessionRepository sessionRepository;

    /**
     * 初始 session version
     */
    private static final long INITIAL_VERSION = 100000L;

    /**
     * 确保 Session 存在 - 高并发安全方式
     *
     * 使用 MongoDB 的 upsert 特性,利用 _id 的唯一索引保证:
     * 1. 多个实例同时检测到 session 不存在
     * 2. 多个实例同时尝试创建 session
     * 3. 只有一个实例能成功创建,其他实例的 upsert 会变成 update(但不修改任何字段)
     * 4. 所有实例最终都能获得同一个 session
     *
     * @param sessionId   会话ID (私聊: "userId1_userId2", 群聊: "group_groupId")
     * @param sessionType 会话类型 (0-私聊, 1-群聊)
     * @return Session 对象 (存在的或新创建的)
     */
    public Session ensureSessionExists(String sessionId, int sessionType) {
        try {
            // 方案一: 使用 findAndModify + upsert (推荐)
            // 这个方法是原子操作,在高并发场景下只有一个线程能创建成功
            Query query = new Query(Criteria.where("id").is(sessionId));

            Update update = new Update()
                    .setOnInsert("sessionType", sessionType)  // 只在 insert 时设置
                    .setOnInsert("version", INITIAL_VERSION); // 只在 insert 时设置

            FindAndModifyOptions options = new FindAndModifyOptions()
                    .upsert(true)  // 如果不存在则插入
                    .returnNew(true); // 返回更新后的文档

            Session session = mongoTemplate.findAndModify(query, update, options, Session.class);

            if (session != null) {
                log.debug("Session {} 已确保存在, version: {}", sessionId, session.getVersion());
                return session;
            } else {
                // 理论上不会走到这里,因为 upsert=true 且 returnNew=true
                log.warn("findAndModify 返回 null, sessionId: {}, 尝试直接查询", sessionId);
                return sessionRepository.findById(sessionId).orElse(null);
            }

        } catch (Exception e) {
            // 可能的异常:
            // 1. DuplicateKeyException: 理论上不会发生,因为 upsert 会处理
            // 2. 网络异常等
            log.error("确保 Session 存在失败, sessionId: {}, sessionType: {}",
                    sessionId, sessionType, e);

            // 发生异常时,尝试直接查询
            return sessionRepository.findById(sessionId).orElse(null);
        }
    }

    /**
     * 增加 Session 版本号
     * 当有新消息到达时调用
     *
     * @param sessionId 会话ID
     * @param increment 增量 (通常为 1)
     * @return 更新后的版本号,失败返回 null
     */
    public Long incrementVersion(String sessionId, long increment) {
        try {
            Query query = new Query(Criteria.where("id").is(sessionId));
            Update update = new Update().inc("version", increment);

            FindAndModifyOptions options = new FindAndModifyOptions()
                    .returnNew(true);

            Session session = mongoTemplate.findAndModify(query, update, options, Session.class);

            if (session != null) {
                log.debug("Session {} 版本号已更新: {}", sessionId, session.getVersion());
                return session.getVersion();
            } else {
                log.warn("Session {} 不存在,无法增加版本号", sessionId);
                return null;
            }
        } catch (Exception e) {
            log.error("增加 Session 版本号失败, sessionId: {}, increment: {}",
                    sessionId, increment, e);
            return null;
        }
    }

    /**
     * 获取 Session
     *
     * @param sessionId 会话ID
     * @return Session 对象,不存在返回 null
     */
    public Session getSession(String sessionId) {
        return sessionRepository.findById(sessionId).orElse(null);
    }

    /**
     * 生成私聊 Session ID
     * 规则: 小ID_大ID
     *
     * @param userId1 用户1的ID
     * @param userId2 用户2的ID
     * @return sessionId
     */
    public static String generatePrivateSessionId(Long userId1, Long userId2) {
        if (userId1 < userId2) {
            return userId1 + "_" + userId2;
        } else {
            return userId2 + "_" + userId1;
        }
    }

    /**
     * 生成群聊 Session ID
     * 规则: group_群ID
     *
     * @param groupId 群ID
     * @return sessionId
     */
    public static String generateGroupSessionId(Long groupId) {
        return "group_" + groupId;
    }
}
