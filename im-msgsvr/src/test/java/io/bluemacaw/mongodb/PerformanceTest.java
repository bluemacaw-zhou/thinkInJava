package io.bluemacaw.mongodb;

import io.bluemacaw.mongodb.entity.DeviceSyncState;
import io.bluemacaw.mongodb.entity.Session;
import io.bluemacaw.mongodb.entity.UserSessionState;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

@Slf4j
@SpringBootTest
public class PerformanceTest {
    @Resource
    MongoTemplate mongoTemplate;

    // 测试配置常量
    private static final int TOTAL_USERS = 100000;           // 10万用户
    private static final int SESSIONS_PER_USER = 100;        // 每个用户100个会话
    private static final int DEVICES_PER_USER = 2;           // 每个用户2台设备
    private static final int UNREAD_SESSIONS_PER_USER = 10;  // 每个用户约10个会话有未读
    private static final int UNSYNC_SESSIONS_PER_USER = 10;  // 每个用户约10个会话有未同步
    private static final int BATCH_SIZE = 1000;              // 批量插入大小

    @Test
    public void testInsert(){
        Session session = new Session();
        session.setId("1");
        session.setSessionType(0);
        session.setVersion(100000);
        mongoTemplate.save(session);

        UserSessionState userSessionState = new UserSessionState();
        userSessionState.setId("1");
        userSessionState.setSessionId("123_456");
        userSessionState.setUserId(123);
        userSessionState.setSessionType(0);
        userSessionState.setLastReadVersion(100001);
        mongoTemplate.save(userSessionState);

        DeviceSyncState deviceSyncState = new DeviceSyncState();
        deviceSyncState.setId("1");
        deviceSyncState.setSessionId("123_456");
        deviceSyncState.setDeviceId(UUID.randomUUID().toString());
        deviceSyncState.setSessionType(0);
        deviceSyncState.setLastSyncVersion(0);
        mongoTemplate.save(deviceSyncState);
    }

    /**
     * 模拟线上场景的性能测试
     * - 10万用户
     * - 每个用户100个会话
     * - 每个用户2台设备(PC + Mobile)
     * - 每个用户约10个会话有未读数/未同步数
     */
    @Test
    public void testBatchInsertProductionScenario() {
        long startTime = System.currentTimeMillis();
        Random random = new Random();

        log.info("开始插入测试数据...");
        log.info("总用户数: {}, 每用户会话数: {}, 每用户设备数: {}",
                 TOTAL_USERS, SESSIONS_PER_USER, DEVICES_PER_USER);

        // 第一步: 批量插入Session数据
//        insertSessions(random);

        // 第二步: 批量插入UserSessionState数据
//        insertUserSessionStates(random);

        // 第三步: 批量插入DeviceSyncState数据
        insertDeviceSyncStates(random);

        long endTime = System.currentTimeMillis();
        log.info("数据插入完成! 总耗时: {} 秒", (endTime - startTime) / 1000.0);
    }

    /**
     * 批量插入Session数据
     * 总数: TOTAL_USERS * SESSIONS_PER_USER = 1000万条
     */
    private void insertSessions(Random random) {
        log.info("开始插入Session数据...");
        long startTime = System.currentTimeMillis();
        long totalSessions = (long) TOTAL_USERS * SESSIONS_PER_USER;
        long insertedCount = 0;

        List<Session> sessionBatch = new ArrayList<>(BATCH_SIZE);

        for (int userId = 1; userId <= TOTAL_USERS; userId++) {
            for (int sessionIndex = 1; sessionIndex <= SESSIONS_PER_USER; sessionIndex++) {
                String sessionId = userId + "_" + sessionIndex;
                Session session = new Session();
                session.setId(sessionId);
                session.setSessionType(0); // 0: 单聊, 1: 群聊
                // 版本号设置为随机值,模拟真实场景
                session.setVersion(100000);

                sessionBatch.add(session);

                if (sessionBatch.size() >= BATCH_SIZE) {
                    mongoTemplate.insertAll(sessionBatch);
                    insertedCount += sessionBatch.size();
                    sessionBatch.clear();

                    if (insertedCount % 100000 == 0) {
                        log.info("Session插入进度: {}/{} ({} %)",
                                 insertedCount, totalSessions,
                                 String.format("%.2f", insertedCount * 100.0 / totalSessions));
                    }
                }
            }
        }

        // 插入剩余数据
        if (!sessionBatch.isEmpty()) {
            mongoTemplate.insertAll(sessionBatch);
            insertedCount += sessionBatch.size();
        }

        long endTime = System.currentTimeMillis();
        log.info("Session数据插入完成! 总数: {}, 耗时: {} 秒",
                 insertedCount, (endTime - startTime) / 1000.0);
    }

    /**
     * 批量插入UserSessionState数据
     * 总数: TOTAL_USERS * SESSIONS_PER_USER = 1000万条
     * 每个用户约10个会话有未读数
     */
    private void insertUserSessionStates(Random random) {
        log.info("开始插入UserSessionState数据...");
        long startTime = System.currentTimeMillis();
        long totalStates = (long) TOTAL_USERS * SESSIONS_PER_USER;
        long insertedCount = 0;

        List<UserSessionState> stateBatch = new ArrayList<>(BATCH_SIZE);

        // 随机选择10个会话作为有未读数的会话
        List<Integer> unreadSessionIndices = new ArrayList<>();
        while (unreadSessionIndices.size() < 10) {
            int sessionIndex = random.nextInt(SESSIONS_PER_USER) + 1;
            if (!unreadSessionIndices.contains(sessionIndex)) {
                unreadSessionIndices.add(sessionIndex);
            }
        }

        for (int userId = 1; userId <= TOTAL_USERS; userId++) {
            for (int sessionIndex = 1; sessionIndex <= SESSIONS_PER_USER; sessionIndex++) {
                String sessionId = userId + "_" + sessionIndex;
                UserSessionState userSessionState = new UserSessionState();
                userSessionState.setId(userId + "_state_" + sessionIndex);
                userSessionState.setSessionId(sessionId);
                userSessionState.setUserId(userId);
                userSessionState.setSessionType(0);

                // 如果是未读会话,lastReadVersion > Session.version
                if (unreadSessionIndices.contains(sessionIndex)) {
                    userSessionState.setLastReadVersion(100001);
                } else {
                    userSessionState.setLastReadVersion(100000);
                }

                stateBatch.add(userSessionState);

                if (stateBatch.size() >= BATCH_SIZE) {
                    mongoTemplate.insertAll(stateBatch);
                    insertedCount += stateBatch.size();
                    stateBatch.clear();

                    if (insertedCount % 100000 == 0) {
                        log.info("UserSessionState插入进度: {}/{} ({} %)",
                                 insertedCount, totalStates,
                                 String.format("%.2f", insertedCount * 100.0 / totalStates));
                    }
                }
            }
        }

        // 插入剩余数据
        if (!stateBatch.isEmpty()) {
            mongoTemplate.insertAll(stateBatch);
            insertedCount += stateBatch.size();
        }

        long endTime = System.currentTimeMillis();
        log.info("UserSessionState数据插入完成! 总数: {}, 耗时: {} 秒",
                 insertedCount, (endTime - startTime) / 1000.0);
    }

    /**
     * 批量插入DeviceSyncState数据
     * 总数: TOTAL_USERS * DEVICES_PER_USER * SESSIONS_PER_USER = 2000万条
     * 每个用户2台设备,每台设备需要记录所有会话的同步状态
     */
    private void insertDeviceSyncStates(Random random) {
        log.info("开始插入DeviceSyncState数据...");
        long startTime = System.currentTimeMillis();
        long totalStates = (long) TOTAL_USERS * DEVICES_PER_USER * SESSIONS_PER_USER;
        long insertedCount = 0;

        List<DeviceSyncState> stateBatch = new ArrayList<>(BATCH_SIZE);
        String[] deviceTypes = {"PC", "MOBILE"};

        for (int userId = 1; userId <= TOTAL_USERS; userId++) {
            // 为每个用户生成2台设备
            String pcDeviceId = "PC";
            String mobileDeviceId = "MOBILE";
            String[] deviceIds = {pcDeviceId, mobileDeviceId};

            for (int deviceIdx = 0; deviceIdx < DEVICES_PER_USER; deviceIdx++) {
                // 随机选择10个会话作为有未读数的会话
                List<Integer> unsyncSessionIndices = new ArrayList<>();
                while (unsyncSessionIndices.size() < 10) {
                    int sessionIndex = random.nextInt(SESSIONS_PER_USER) + 1;
                    if (!unsyncSessionIndices.contains(sessionIndex)) {
                        unsyncSessionIndices.add(sessionIndex);
                    }
                }

                for (int sessionIndex = 1; sessionIndex <= SESSIONS_PER_USER; sessionIndex++) {
                    String sessionId = userId + "_" + sessionIndex;
                    DeviceSyncState deviceSyncState = new DeviceSyncState();
                    deviceSyncState.setId(userId + "_" + deviceTypes[deviceIdx] + "_" + sessionIndex);
                    deviceSyncState.setSessionId(sessionId);
                    deviceSyncState.setDeviceId(deviceIds[deviceIdx]);
                    deviceSyncState.setSessionType(random.nextInt(3));

                    // 如果是未读会话,lastSyncVersion > Session.version
                    if (unsyncSessionIndices.contains(sessionIndex)) {
                        deviceSyncState.setLastSyncVersion(100001);
                    } else {
                        deviceSyncState.setLastSyncVersion(100000);
                    }

                    stateBatch.add(deviceSyncState);

                    if (stateBatch.size() >= BATCH_SIZE) {
                        mongoTemplate.insertAll(stateBatch);
                        insertedCount += stateBatch.size();
                        stateBatch.clear();

                        if (insertedCount % 100000 == 0) {
                            log.info("DeviceSyncState插入进度: {}/{} ({} %)",
                                     insertedCount, totalStates,
                                     String.format("%.2f", insertedCount * 100.0 / totalStates));
                        }
                    }
                }
            }
        }

        // 插入剩余数据
        if (!stateBatch.isEmpty()) {
            mongoTemplate.insertAll(stateBatch);
            insertedCount += stateBatch.size();
        }

        long endTime = System.currentTimeMillis();
        log.info("DeviceSyncState数据插入完成! 总数: {}, 耗时: {} 秒",
                 insertedCount, (endTime - startTime) / 1000.0);
    }
}
