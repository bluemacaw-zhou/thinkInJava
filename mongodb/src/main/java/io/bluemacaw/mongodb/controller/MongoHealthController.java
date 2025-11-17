package io.bluemacaw.mongodb.controller;

import io.bluemacaw.mongodb.service.MongoConnectionService;
import jakarta.annotation.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * MongoDB健康检查控制器
 */
@RestController
@RequestMapping("/api/mongo")
public class MongoHealthController {

    @Resource
    private MongoConnectionService mongoConnectionService;

    /**
     * 获取连接池使用情况
     */
    @GetMapping("/pool/status")
    public ResponseEntity<MongoConnectionService.ConnectionPoolStatus> getConnectionPoolStatus() {
        MongoConnectionService.ConnectionPoolStatus poolStatus = mongoConnectionService.getConnectionPoolStatus();
        
        return poolStatus.getError() == null ? 
            ResponseEntity.ok(poolStatus) : 
            ResponseEntity.status(503).body(poolStatus);
    }

    /**
     * 获取连接池健康状态
     */
    @GetMapping("/pool/health")
    public ResponseEntity<MongoConnectionService.PoolHealthStatus> getPoolHealthStatus() {
        MongoConnectionService.PoolHealthStatus healthStatus = mongoConnectionService.getPoolHealthStatus();
        
        return healthStatus.isHealthy() ? 
            ResponseEntity.ok(healthStatus) : 
            ResponseEntity.status(503).body(healthStatus);
    }

    /**
     * 获取连接池简化指标
     */
    @GetMapping("/pool/metrics")
    public ResponseEntity<Map<String, Object>> getPoolMetrics() {
        MongoConnectionService.ConnectionPoolStatus poolStatus = mongoConnectionService.getConnectionPoolStatus();
        
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("poolCount", poolStatus.getPoolCount());
        metrics.put("currentConnections", poolStatus.getCurrentConnections());
        metrics.put("activeConnections", poolStatus.getActiveConnections());
        metrics.put("idleConnections", poolStatus.getIdleConnections());
        metrics.put("utilizationRate", String.format("%.2f%%", poolStatus.getUtilizationRate()));
        metrics.put("checkOutSuccessRate", String.format("%.2f%%", poolStatus.getCheckOutSuccessRate()));
        metrics.put("totalCreated", poolStatus.getTotalCreated());
        metrics.put("totalClosed", poolStatus.getTotalClosed());
        metrics.put("checkOutRequests", poolStatus.getCheckOutRequests());
        metrics.put("failedCheckOuts", poolStatus.getFailedCheckOuts());
        metrics.put("timestamp", poolStatus.getTimestamp());
        
        return poolStatus.getError() == null ? 
            ResponseEntity.ok(metrics) : 
            ResponseEntity.status(503).body(metrics);
    }
}
