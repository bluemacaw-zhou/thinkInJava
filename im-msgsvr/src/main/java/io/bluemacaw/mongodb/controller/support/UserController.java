package io.bluemacaw.mongodb.controller.support;

import io.bluemacaw.mongodb.service.UserService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 用户管理Controller
 * 用于初始化和管理Mock用户数据
 *
 * @author shzhou.michael
 */
@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private UserService userService;

    /**
     * 初始化用户数据
     * POST /user/initialize
     *
     * @return 初始化结果
     */
    @PostMapping("/initialize")
    public Map<String, Object> initializeUsers() {
        log.info("Received request to initialize users");
        return userService.initializeUsers();
    }

    /**
     * 获取用户ID范围信息
     * GET /user/range
     *
     * @return 用户ID范围信息
     */
    @GetMapping("/range")
    public Map<String, Object> getUserIdRange() {
        return userService.getUserIdRange();
    }

    /**
     * 清空所有用户数据
     * DELETE /user/clear
     *
     * @return 清空结果
     */
    @DeleteMapping("/clear")
    public Map<String, Object> clearAllUsers() {
        log.warn("Received request to clear all users");
        return userService.clearAllUsers();
    }
}
