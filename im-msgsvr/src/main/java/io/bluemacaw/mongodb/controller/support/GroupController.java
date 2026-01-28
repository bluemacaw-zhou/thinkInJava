package io.bluemacaw.mongodb.controller.support;

import io.bluemacaw.mongodb.service.GroupService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 群组管理Controller
 * 用于初始化和管理Mock群组数据
 *
 * @author shzhou.michael
 */
@Slf4j
@RestController
@RequestMapping("/group")
public class GroupController {

    @Resource
    private GroupService groupService;

    /**
     * 初始化群组数据
     * POST /group/initialize
     *
     * @return 初始化结果
     */
    @PostMapping("/initialize")
    public Map<String, Object> initializeGroups() {
        log.info("Received request to initialize groups");
        return groupService.initializeGroups();
    }

    /**
     * 获取群ID范围信息
     * GET /group/range
     *
     * @return 群ID范围信息
     */
    @GetMapping("/range")
    public Map<String, Object> getGroupIdRange() {
        return groupService.getGroupIdRange();
    }

    /**
     * 清空所有群组数据
     * DELETE /group/clear
     *
     * @return 清空结果
     */
    @DeleteMapping("/clear")
    public Map<String, Object> clearAllGroups() {
        log.warn("Received request to clear all groups");
        return groupService.clearAllGroups();
    }
}
