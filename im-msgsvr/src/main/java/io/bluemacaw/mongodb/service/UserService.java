package io.bluemacaw.mongodb.service;

import io.bluemacaw.mongodb.entity.User;
import io.bluemacaw.mongodb.repository.UserRepository;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.Optional;

/**
 * 用户服务
 * Mock用户数据，用于测试
 *
 * @author shzhou.michael
 */
@Slf4j
@Service
public class UserService {

    @Resource
    private UserRepository userRepository;

    @Value("${mongodb.mock-data.total-users:30000}")
    private int totalUsers;

    @Value("${mongodb.mock-data.wind-user-ratio:0.7}")
    private double windUserRatio;

    @Value("${mongodb.mock-data.user-id-start:100001}")
    private long userIdStart;

    @Value("${mongodb.mock-data.user-id-end:130000}")
    private long userIdEnd;

    // 其他公司列表
    private static final String[] OTHER_COMPANIES = {
        "复旦大学", "交通大学", "同济大学", "华东师范大学",
        "上海财经大学", "华东理工大学", "东华大学", "上海大学"
    };

    /**
     * 初始化Mock用户数据
     * 通过HTTP接口调用
     */
    public Map<String, Object> initializeUsers() {
        Map<String, Object> result = new HashMap<>();

        // 检查是否已经初始化过
        long existingUserCount = userRepository.count();
        if (existingUserCount > 0) {
            log.info("User data already exists: {} users, skipping initialization", existingUserCount);
            result.put("status", "skipped");
            result.put("message", "User data already exists");
            result.put("existingCount", existingUserCount);
            return result;
        }

        log.info("Starting user data initialization: {} users", totalUsers);

        int windUserCount = (int) (totalUsers * windUserRatio);
        int otherUserCount = totalUsers - windUserCount;

        log.info("User distribution: total={}, wind={}, other={}", totalUsers, windUserCount, otherUserCount);

        // 生成用户数据
        List<User> users = new ArrayList<>();
        Random random = new Random(12345); // 固定种子

        for (int i = 0; i < totalUsers; i++) {
            User user = new User();
            user.setId(userIdStart + i);

            if (i < windUserCount) {
                user.setCompany("Wind");
            } else {
                user.setCompany(OTHER_COMPANIES[random.nextInt(OTHER_COMPANIES.length)]);
            }

            users.add(user);
        }

        // 批量插入MongoDB
        userRepository.saveAll(users);

        log.info("User data initialization completed: {} users inserted", users.size());

        result.put("status", "success");
        result.put("message", "Users initialized successfully");
        result.put("totalUsers", totalUsers);
        result.put("windUsers", windUserCount);
        result.put("otherUsers", otherUserCount);
        result.put("userIdStart", userIdStart);
        result.put("userIdEnd", userIdEnd);

        return result;
    }

    /**
     * 获取用户ID范围信息
     */
    public Map<String, Object> getUserIdRange() {
        Map<String, Object> result = new HashMap<>();
        long count = userRepository.count();

        result.put("userIdStart", userIdStart);
        result.put("userIdEnd", userIdEnd);
        result.put("totalUsers", totalUsers);
        result.put("currentCount", count);
        result.put("initialized", count > 0);

        return result;
    }

    /**
     * 清空所有用户数据
     */
    public Map<String, Object> clearAllUsers() {
        long count = userRepository.count();
        userRepository.deleteAll();

        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "All users cleared");
        result.put("deletedCount", count);

        log.info("Cleared {} users", count);
        return result;
    }

    /**
     * 随机获取一个用户（通过ID范围随机，主键查询）
     */
    public User getRandomUser() {
        Random random = new Random();
        long randomId = userIdStart + random.nextInt(totalUsers);
        return userRepository.findById(randomId)
                .orElseThrow(() -> new IllegalStateException("User not found: " + randomId));
    }

    /**
     * 随机获取两个用户（用于私聊，可能相同）
     */
    public User[] getRandomUserPair() {
        Random random = new Random();
        long userId1 = userIdStart + random.nextInt(totalUsers);
        long userId2 = userIdStart + random.nextInt(totalUsers);

        User user1 = userRepository.findById(userId1)
                .orElseThrow(() -> new IllegalStateException("User not found: " + userId1));
        User user2 = userRepository.findById(userId2)
                .orElseThrow(() -> new IllegalStateException("User not found: " + userId2));

        return new User[]{user1, user2};
    }

    /**
     * 随机获取指定数量的不同用户（用于群成员）
     *
     * @param count 需要的用户数量
     * @return 用户列表
     */
    public List<User> getRandomUsers(int count) {
        if (count <= 0) {
            throw new IllegalArgumentException("Count must be positive: " + count);
        }
        if (count > totalUsers) {
            throw new IllegalArgumentException("Count exceeds total users: " + count + " > " + totalUsers);
        }

        Random random = new Random();
        Set<Long> selectedIds = new HashSet<>();
        List<User> users = new ArrayList<>();

        // 生成不同的随机ID
        while (selectedIds.size() < count) {
            long randomId = userIdStart + random.nextInt(totalUsers);
            if (selectedIds.add(randomId)) {
                User user = userRepository.findById(randomId)
                        .orElseThrow(() -> new IllegalStateException("User not found: " + randomId));
                users.add(user);
            }
        }

        return users;
    }

    /**
     * 根据ID获取用户
     */
    public Optional<User> getUserById(Long userId) {
        return userRepository.findById(userId);
    }

    /**
     * 获取用户总数
     */
    public int getTotalUserCount() {
        return (int) userRepository.count();
    }
}
