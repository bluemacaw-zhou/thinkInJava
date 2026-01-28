package io.bluemacaw.mongodb.controller.support;

import io.bluemacaw.mongodb.entity.Message;
import io.bluemacaw.mongodb.monitor.ClickHousePoolMonitorService;
import io.bluemacaw.mongodb.service.MessageService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ClickHouse查询控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/clickhouse")
public class ClickHouseQueryController {

    @Resource
    private MessageService messageService;

    @Resource
    private ClickHousePoolMonitorService poolMonitorService;

    /**
     * 获取消息总数
     * GET /api/clickhouse/count
     */
    @GetMapping("/count")
    public Map<String, Object> getTotalCount() {
        long count = messageService.getTotalCount();

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("total", count);

        return response;
    }

    /**
     * 查询最近的消息
     * GET /api/clickhouse/messages/recent?limit=10
     */
    @GetMapping("/messages/recent")
    public Map<String, Object> getRecentMessages(
            @RequestParam(defaultValue = "10") int limit) {

        if (limit <= 0 || limit > 1000) {
            limit = 10;
        }

        List<Message> messages = messageService.getRecentMessages(limit);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("count", messages.size());
        response.put("data", messages);

        return response;
    }

    /**
     * 根据发送者ID查询消息
     * GET /api/clickhouse/messages/from/{fromId}?limit=10
     */
    @GetMapping("/messages/from/{fromId}")
    public Map<String, Object> getMessagesByFromId(
            @PathVariable long fromId,
            @RequestParam(defaultValue = "10") int limit) {

        if (limit <= 0 || limit > 1000) {
            limit = 10;
        }

        List<Message> messages = messageService.getMessagesByFromId(fromId, limit);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("fromId", fromId);
        response.put("count", messages.size());
        response.put("data", messages);

        return response;
    }

    /**
     * 根据联系人ID查询消息
     * GET /api/clickhouse/messages/contact/{contactId}?limit=10
     */
    @GetMapping("/messages/contact/{contactId}")
    public Map<String, Object> getMessagesByContactId(
            @PathVariable long contactId,
            @RequestParam(defaultValue = "10") int limit) {

        if (limit <= 0 || limit > 1000) {
            limit = 10;
        }

        List<Message> messages = messageService.getMessagesByContactId(contactId, limit);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("contactId", contactId);
        response.put("count", messages.size());
        response.put("data", messages);

        return response;
    }

    /**
     * 根据时间范围查询消息
     * GET /api/clickhouse/messages/range?startTime=2025-01-01T00:00:00&endTime=2025-01-31T23:59:59&limit=100
     */
    @GetMapping("/messages/range")
    public Map<String, Object> getMessagesByTimeRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "100") int limit) {

        if (limit <= 0 || limit > 1000) {
            limit = 100;
        }

        List<Message> messages = messageService.getMessagesByTimeRange(startTime, endTime, limit);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("startTime", startTime);
        response.put("endTime", endTime);
        response.put("count", messages.size());
        response.put("data", messages);

        return response;
    }

    /**
     * 按日期统计消息数量
     * GET /api/clickhouse/stats/daily?days=7
     */
    @GetMapping("/stats/daily")
    public Map<String, Object> getMessageCountByDate(
            @RequestParam(defaultValue = "7") int days) {

        if (days <= 0 || days > 365) {
            days = 7;
        }

        List<Map<String, Object>> stats = messageService.getMessageCountByDate(days);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("days", days);
        response.put("count", stats.size());
        response.put("data", stats);

        return response;
    }

    /**
     * 获取消息发送最多的用户
     * GET /api/clickhouse/stats/top-senders?limit=10
     */
    @GetMapping("/stats/top-senders")
    public Map<String, Object> getTopSenders(
            @RequestParam(defaultValue = "10") int limit) {

        if (limit <= 0 || limit > 100) {
            limit = 10;
        }

        List<Map<String, Object>> topSenders = messageService.getTopSenders(limit);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("count", topSenders.size());
        response.put("data", topSenders);

        return response;
    }

    /**
     * 执行自定义SQL查询（需谨慎使用）
     * POST /api/clickhouse/query/custom
     * Body: { "sql": "SELECT * FROM im_message.message LIMIT 5" }
     */
    @PostMapping("/query/custom")
    public Map<String, Object> executeCustomQuery(@RequestBody Map<String, String> request) {
        String sql = request.get("sql");

        if (sql == null || sql.trim().isEmpty()) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", "SQL statement is required");
            return response;
        }

        // 安全检查：只允许SELECT查询
        String trimmedSql = sql.trim().toUpperCase();
        if (!trimmedSql.startsWith("SELECT")) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", "Only SELECT queries are allowed");
            return response;
        }

        try {
            List<Map<String, Object>> result = messageService.executeCustomQuery(sql);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("count", result.size());
            response.put("data", result);

            return response;

        } catch (Exception e) {
            log.error("Error executing custom query", e);

            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());

            return response;
        }
    }

    /**
     * 健康检查接口
     * GET /api/clickhouse/health
     */
    @GetMapping("/health")
    public Map<String, Object> healthCheck() {
        Map<String, Object> response = new HashMap<>();

        try {
            long count = messageService.getTotalCount();

            response.put("success", true);
            response.put("status", "healthy");
            response.put("totalMessages", count);

        } catch (Exception e) {
            log.error("ClickHouse health check failed", e);

            response.put("success", false);
            response.put("status", "unhealthy");
            response.put("error", e.getMessage());
        }

        return response;
    }

    // ========== 连接池监控接口 ==========

    /**
     * 获取连接池状态
     * GET /api/clickhouse/pool/status
     */
    @GetMapping("/pool/status")
    public Map<String, Object> getPoolStatus() {
        return poolMonitorService.getPoolStatus();
    }

    /**
     * 获取连接池健康状态
     * GET /api/clickhouse/pool/health
     */
    @GetMapping("/pool/health")
    public Map<String, Object> getPoolHealth() {
        return poolMonitorService.getHealthStatus();
    }

    /**
     * 软关闭连接池中的空闲连接
     * POST /api/clickhouse/pool/evict
     */
    @PostMapping("/pool/evict")
    public Map<String, Object> evictConnections() {
        log.info("Evicting idle connections from ClickHouse pool");
        return poolMonitorService.softEvictConnections();
    }

    /**
     * 暂停连接池
     * POST /api/clickhouse/pool/suspend
     */
    @PostMapping("/pool/suspend")
    public Map<String, Object> suspendPool() {
        log.warn("Suspending ClickHouse connection pool");
        return poolMonitorService.suspendPool();
    }

    /**
     * 恢复连接池
     * POST /api/clickhouse/pool/resume
     */
    @PostMapping("/pool/resume")
    public Map<String, Object> resumePool() {
        log.info("Resuming ClickHouse connection pool");
        return poolMonitorService.resumePool();
    }
}
