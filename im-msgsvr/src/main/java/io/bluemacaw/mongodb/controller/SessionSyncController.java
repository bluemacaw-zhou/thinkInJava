package io.bluemacaw.mongodb.controller;

import io.bluemacaw.mongodb.entity.DeviceSyncState;
import io.bluemacaw.mongodb.entity.Session;
import io.bluemacaw.mongodb.entity.UserSessionState;
import jakarta.annotation.Resource;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 会话同步压测接口控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/session")
public class SessionSyncController {

    @Resource
    private MongoTemplate mongoTemplate;

    /**
     * 获取用户未读数>0的会话列表
     * 查询逻辑: Session.version - UserSessionState.lastReadVersion > 0
     *
     * @param userId 用户ID
     * @return 未读会话列表
     */
    @GetMapping("/unread")
    public ResponseEntity<UnreadResponse> getUnreadSessions(@RequestParam Long userId) {
        long startTime = System.currentTimeMillis();

        try {
            // 1. 查询该用户的所有UserSessionState
            Query userStateQuery = new Query(Criteria.where("userId").is(userId));
            List<UserSessionState> userStates = mongoTemplate.find(userStateQuery, UserSessionState.class);

            if (userStates.isEmpty()) {
                return ResponseEntity.ok(new UnreadResponse(userId, new ArrayList<>(), 0L, 0L));
            }

            // 2. 提取所有sessionId
            List<String> sessionIds = userStates.stream()
                    .map(UserSessionState::getSessionId)
                    .collect(Collectors.toList());

            // 3. 查询所有相关的Session
            Query sessionQuery = new Query(Criteria.where("id").in(sessionIds));
            List<Session> sessions = mongoTemplate.find(sessionQuery, Session.class);

            // 4. 构建sessionId -> Session的映射
            var sessionMap = sessions.stream()
                    .collect(Collectors.toMap(Session::getId, s -> s));

            // 5. 计算未读数并筛选未读会话
            List<UnreadSessionInfo> unreadSessions = new ArrayList<>();
            for (UserSessionState userState : userStates) {
                Session session = sessionMap.get(userState.getSessionId());
                if (session != null) {
                    long unreadCount = session.getVersion() - userState.getLastReadVersion();
                    if (unreadCount < 0) {
                        UnreadSessionInfo info = new UnreadSessionInfo();
                        info.setSessionId(session.getId());
                        info.setSessionType(session.getSessionType());
                        info.setSessionVersion(session.getVersion());
                        info.setLastReadVersion(userState.getLastReadVersion());
                        info.setUnreadCount(unreadCount);
                        unreadSessions.add(info);
                    }
                }
            }

            long queryTime = System.currentTimeMillis() - startTime;
            log.info("查询用户{}的未读会话完成, 总会话数: {}, 未读会话数: {}, 耗时: {}ms",
                    userId, userStates.size(), unreadSessions.size(), queryTime);

            return ResponseEntity.ok(new UnreadResponse(userId, unreadSessions,
                    (long)unreadSessions.size(), queryTime));

        } catch (Exception e) {
            log.error("查询用户{}的未读会话失败", userId, e);
            return ResponseEntity.status(500).body(new UnreadResponse(userId, new ArrayList<>(), 0L,
                    System.currentTimeMillis() - startTime));
        }
    }

    /**
     * 获取设备未同步数>0的会话列表
     * 查询逻辑: Session.version - DeviceSyncState.lastSyncVersion > 0
     *
     * @param userId   用户ID
     * @param deviceId 设备ID (PC 或 MOBILE)
     * @return 未同步会话列表
     */
    @GetMapping("/unsync")
    public ResponseEntity<UnsyncResponse> getUnsyncSessions(
            @RequestParam Long userId,
            @RequestParam String deviceId) {
        long startTime = System.currentTimeMillis();

        try {
            // 1. 查询该设备的所有DeviceSyncState
            // 需要通过sessionId模糊匹配找到属于该用户的会话
            Query deviceStateQuery = new Query(
                    Criteria.where("deviceId").is(deviceId)
                            .and("sessionId").regex("^" + userId + "_")
            );
            List<DeviceSyncState> deviceStates = mongoTemplate.find(deviceStateQuery, DeviceSyncState.class);

            if (deviceStates.isEmpty()) {
                return ResponseEntity.ok(new UnsyncResponse(userId, deviceId, new ArrayList<>(), 0L, 0L));
            }

            // 2. 提取所有sessionId
            List<String> sessionIds = deviceStates.stream()
                    .map(DeviceSyncState::getSessionId)
                    .collect(Collectors.toList());

            // 3. 查询所有相关的Session
            Query sessionQuery = new Query(Criteria.where("id").in(sessionIds));
            List<Session> sessions = mongoTemplate.find(sessionQuery, Session.class);

            // 4. 构建sessionId -> Session的映射
            var sessionMap = sessions.stream()
                    .collect(Collectors.toMap(Session::getId, s -> s));

            // 5. 计算未同步数并筛选未同步会话
            List<UnsyncSessionInfo> unsyncSessions = new ArrayList<>();
            for (DeviceSyncState deviceState : deviceStates) {
                Session session = sessionMap.get(deviceState.getSessionId());
                if (session != null) {
                    long unsyncCount = session.getVersion() - deviceState.getLastSyncVersion();
                    if (unsyncCount < 0) {
                        UnsyncSessionInfo info = new UnsyncSessionInfo();
                        info.setSessionId(session.getId());
                        info.setSessionType(session.getSessionType());
                        info.setSessionVersion(session.getVersion());
                        info.setLastSyncVersion(deviceState.getLastSyncVersion());
                        info.setUnsyncCount(unsyncCount);
                        unsyncSessions.add(info);
                    }
                }
            }

            long queryTime = System.currentTimeMillis() - startTime;
            log.info("查询用户{}设备{}的未同步会话完成, 总会话数: {}, 未同步会话数: {}, 耗时: {}ms",
                    userId, deviceId, deviceStates.size(), unsyncSessions.size(), queryTime);

            return ResponseEntity.ok(new UnsyncResponse(userId, deviceId, unsyncSessions,
                    (long)unsyncSessions.size(), queryTime));

        } catch (Exception e) {
            log.error("查询用户{}设备{}的未同步会话失败", userId, deviceId, e);
            return ResponseEntity.status(500).body(new UnsyncResponse(userId, deviceId, new ArrayList<>(), 0L,
                    System.currentTimeMillis() - startTime));
        }
    }

    // ==================== 响应数据结构 ====================

    @Data
    public static class UnreadResponse {
        private Long userId;
        private List<UnreadSessionInfo> unreadSessions;
        private Long totalUnreadCount;
        private Long queryTimeMs;

        public UnreadResponse(Long userId, List<UnreadSessionInfo> unreadSessions,
                            Long totalUnreadCount, Long queryTimeMs) {
            this.userId = userId;
            this.unreadSessions = unreadSessions;
            this.totalUnreadCount = totalUnreadCount;
            this.queryTimeMs = queryTimeMs;
        }
    }

    @Data
    public static class UnreadSessionInfo {
        private String sessionId;
        private Integer sessionType;
        private Long sessionVersion;
        private Long lastReadVersion;
        private Long unreadCount;
    }

    @Data
    public static class UnsyncResponse {
        private Long userId;
        private String deviceId;
        private List<UnsyncSessionInfo> unsyncSessions;
        private Long totalUnsyncCount;
        private Long queryTimeMs;

        public UnsyncResponse(Long userId, String deviceId, List<UnsyncSessionInfo> unsyncSessions,
                            Long totalUnsyncCount, Long queryTimeMs) {
            this.userId = userId;
            this.deviceId = deviceId;
            this.unsyncSessions = unsyncSessions;
            this.totalUnsyncCount = totalUnsyncCount;
            this.queryTimeMs = queryTimeMs;
        }
    }

    @Data
    public static class UnsyncSessionInfo {
        private String sessionId;
        private Integer sessionType;
        private Long sessionVersion;
        private Long lastSyncVersion;
        private Long unsyncCount;
    }
}
