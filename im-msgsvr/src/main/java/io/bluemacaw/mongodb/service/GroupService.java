package io.bluemacaw.mongodb.service;

import io.bluemacaw.mongodb.entity.Group;
import io.bluemacaw.mongodb.repository.GroupRepository;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 群组服务
 * 提供群组相关的业务逻辑
 *
 * Mock实现：提供测试用的群组和成员数据
 * 群成员都来自UserService中的真实用户
 *
 * @author shzhou.michael
 */
@Slf4j
@Service
public class GroupService {

    @Resource
    private UserService userService;

    @Resource
    private GroupRepository groupRepository;

    @Value("${mongodb.mock-data.total-groups:1000}")
    private int totalGroups;

    @Value("${mongodb.mock-data.min-group-members:5}")
    private int minGroupMembers;

    @Value("${mongodb.mock-data.max-group-members:20}")
    private int maxGroupMembers;

    @Value("${mongodb.mock-data.group-id-start:500001}")
    private long groupIdStart;

    @Value("${mongodb.mock-data.group-id-end:501000}")
    private long groupIdEnd;

    /**
     * 初始化群组数据
     * 通过HTTP接口调用
     */
    public Map<String, Object> initializeGroups() {
        Map<String, Object> result = new HashMap<>();

        // 检查是否已经初始化过
        long existingGroupCount = groupRepository.count();
        if (existingGroupCount > 0) {
            log.info("Group data already exists: {} groups, skipping initialization", existingGroupCount);
            result.put("status", "skipped");
            result.put("message", "Group data already exists");
            result.put("existingCount", existingGroupCount);
            return result;
        }

        // 检查用户是否已初始化
        int userCount = userService.getTotalUserCount();
        if (userCount == 0) {
            log.warn("No users available, please initialize users first");
            result.put("status", "error");
            result.put("message", "No users available, please initialize users first");
            return result;
        }

        log.info("Starting group data initialization: {} groups", totalGroups);

        // 生成群组数据
        List<Group> groups = new ArrayList<>();
        Random random = new Random(12345); // 固定种子

        for (int i = 0; i < totalGroups; i++) {
            Group group = new Group();
            group.setId(groupIdStart + i);

            // 随机确定群成员数量（大多数群在5-20人之间）
            int memberCount = minGroupMembers + random.nextInt(maxGroupMembers - minGroupMembers + 1);

            // 通过UserService获取随机用户（ID范围随机+主键查询）
            List<io.bluemacaw.mongodb.entity.User> members = userService.getRandomUsers(memberCount);
            List<Long> memberIds = members.stream()
                    .map(io.bluemacaw.mongodb.entity.User::getId)
                    .collect(java.util.stream.Collectors.toList());

            group.setMemberUserIds(memberIds);
            groups.add(group);
        }

        // 批量插入MongoDB
        groupRepository.saveAll(groups);

        int totalMembers = groups.stream().mapToInt(g -> g.getMemberUserIds().size()).sum();

        log.info("Group data initialization completed: {} groups inserted, total {} member records",
                groups.size(), totalMembers);

        result.put("status", "success");
        result.put("message", "Groups initialized successfully");
        result.put("totalGroups", totalGroups);
        result.put("totalMembers", totalMembers);
        result.put("minGroupMembers", minGroupMembers);
        result.put("maxGroupMembers", maxGroupMembers);
        result.put("groupIdStart", groupIdStart);
        result.put("groupIdEnd", groupIdEnd);

        return result;
    }

    /**
     * 获取群ID范围信息
     */
    public Map<String, Object> getGroupIdRange() {
        Map<String, Object> result = new HashMap<>();
        long count = groupRepository.count();

        result.put("groupIdStart", groupIdStart);
        result.put("groupIdEnd", groupIdEnd);
        result.put("totalGroups", totalGroups);
        result.put("currentCount", count);
        result.put("initialized", count > 0);

        return result;
    }

    /**
     * 清空所有群组数据
     */
    public Map<String, Object> clearAllGroups() {
        long count = groupRepository.count();
        groupRepository.deleteAll();

        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "All groups cleared");
        result.put("deletedCount", count);

        log.info("Cleared {} groups", count);
        return result;
    }

    /**
     * 随机获取一个群（通过ID范围随机，主键查询）
     */
    public Group getRandomGroup() {
        Random random = new Random();
        long randomId = groupIdStart + random.nextInt(totalGroups);
        return groupRepository.findById(randomId)
                .orElseThrow(() -> new IllegalStateException("Group not found: " + randomId));
    }

    /**
     * 根据群ID获取群成员ID列表
     */
    public List<Long> getGroupMemberUserIds(Long groupId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalStateException("Group not found: " + groupId));
        return group.getMemberUserIds();
    }

    /**
     * 根据群ID获取群成员用户对象列表
     */
    public List<io.bluemacaw.mongodb.entity.User> getGroupMembers(Long groupId) {
        List<Long> memberIds = getGroupMemberUserIds(groupId);
        List<io.bluemacaw.mongodb.entity.User> members = new ArrayList<>();

        for (Long memberId : memberIds) {
            userService.getUserById(memberId).ifPresent(members::add);
        }

        return members;
    }

    /**
     * 获取群总数
     */
    public int getTotalGroupCount() {
        return (int) groupRepository.count();
    }
}
