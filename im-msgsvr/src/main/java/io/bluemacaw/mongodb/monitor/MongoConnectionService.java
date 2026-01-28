package io.bluemacaw.mongodb.monitor;

import io.bluemacaw.mongodb.config.MongoConnectionPoolListener;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * MongoDB连接状态检查服务
 */
@Slf4j
@Service
public class MongoConnectionService {

    @Resource
    private MongoTemplate mongoTemplate;

    @Resource
    private MongoConnectionPoolListener connectionPoolListener;

    @Value("${mongodb.database}")
    private String database;

    /**
     * 检查MongoDB连接状态
     */
    public boolean isConnected() {
        try {
            // 方法1：通过MongoTemplate执行ping命令
            mongoTemplate.getCollection(database).estimatedDocumentCount();
            log.info("MongoDB连接正常");
            return true;
        } catch (Exception e) {
            log.error("MongoDB连接失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 获取详细的连接信息
     */
    public ConnectionStatus getConnectionStatus() {
        ConnectionStatus status = new ConnectionStatus();
        
        try {
            // 检查连接
            mongoTemplate.getCollection("test").estimatedDocumentCount();
            status.setConnected(true);
            status.setMessage("连接正常");
            
            // 获取数据库名称
            status.setDatabaseName(mongoTemplate.getDb().getName());
            
            // 获取集合列表
            status.setCollectionCount(mongoTemplate.getDb().listCollectionNames().into(new java.util.ArrayList<>()).size());
            
            log.info("MongoDB连接状态检查成功");
            
        } catch (Exception e) {
            status.setConnected(false);
            status.setMessage("连接失败: " + e.getMessage());
            status.setError(e.getClass().getSimpleName());
            log.error("MongoDB连接状态检查失败: {}", e.getMessage());
        }
        
        return status;
    }

    /**
     * 获取连接池使用情况
     */
    public ConnectionPoolStatus getConnectionPoolStatus() {
        ConnectionPoolStatus poolStatus = new ConnectionPoolStatus();
        
        try {
            // 获取连接池汇总信息
            MongoConnectionPoolListener.ConnectionPoolSummary summary = 
                connectionPoolListener.getPoolSummary();
            
            poolStatus.setPoolCount(summary.poolCount);
            poolStatus.setCurrentConnections(summary.totalCurrentConnections);
            poolStatus.setActiveConnections(summary.totalActiveConnections);
            poolStatus.setIdleConnections(summary.totalCurrentConnections - summary.totalActiveConnections);
            poolStatus.setTotalCreated(summary.totalCreatedConnections);
            poolStatus.setTotalClosed(summary.totalClosedConnections);
            poolStatus.setCheckOutRequests(summary.totalCheckOutRequests);
            poolStatus.setSuccessfulCheckOuts(summary.totalSuccessfulCheckOuts);
            poolStatus.setFailedCheckOuts(summary.totalFailedCheckOuts);
            poolStatus.setUtilizationRate(summary.getConnectionUtilizationRate());
            poolStatus.setCheckOutSuccessRate(summary.getCheckOutSuccessRate());
            poolStatus.setTimestamp(System.currentTimeMillis());
            
            // 获取详细的连接池指标
            Map<String, MongoConnectionPoolListener.ConnectionPoolMetrics> detailedMetrics = 
                connectionPoolListener.getPoolMetrics();
            poolStatus.setDetailedMetrics(detailedMetrics);
            
            log.info("连接池状态获取成功: {}", summary);
            
        } catch (Exception e) {
            log.error("获取连接池状态失败: {}", e.getMessage());
            poolStatus.setError("获取连接池状态失败: " + e.getMessage());
        }
        
        return poolStatus;
    }

    /**
     * 获取连接池健康状态
     */
    public PoolHealthStatus getPoolHealthStatus() {
        PoolHealthStatus healthStatus = new PoolHealthStatus();
        
        try {
            MongoConnectionPoolListener.ConnectionPoolSummary summary = 
                connectionPoolListener.getPoolSummary();
            
            // 评估连接池健康状态
            boolean isHealthy = true;
            String healthMessage = "连接池运行正常";
            
            // 检查连接池利用率
            double utilizationRate = summary.getConnectionUtilizationRate();
            if (utilizationRate > 90) {
                isHealthy = false;
                healthMessage = "连接池利用率过高: " + String.format("%.2f%%", utilizationRate);
            } else if (utilizationRate > 80) {
                healthMessage = "连接池利用率较高: " + String.format("%.2f%%", utilizationRate);
            }
            
            // 检查失败率
            double checkOutSuccessRate = summary.getCheckOutSuccessRate();
            if (checkOutSuccessRate < 95) {
                isHealthy = false;
                healthMessage = "连接检出成功率过低: " + String.format("%.2f%%", checkOutSuccessRate);
            }
            
            // 检查连接数
            if (summary.totalCurrentConnections == 0) {
                isHealthy = false;
                healthMessage = "无可用连接";
            }
            
            healthStatus.setHealthy(isHealthy);
            healthStatus.setMessage(healthMessage);
            healthStatus.setUtilizationRate(utilizationRate);
            healthStatus.setCheckOutSuccessRate(checkOutSuccessRate);
            healthStatus.setCurrentConnections(summary.totalCurrentConnections);
            healthStatus.setActiveConnections(summary.totalActiveConnections);
            healthStatus.setTimestamp(System.currentTimeMillis());
            
        } catch (Exception e) {
            healthStatus.setHealthy(false);
            healthStatus.setMessage("连接池健康检查失败: " + e.getMessage());
            log.error("连接池健康检查失败: {}", e.getMessage());
        }
        
        return healthStatus;
    }

    /**
     * 测试数据库操作
     */
    public boolean testDatabaseOperation() {
        try {
            // 尝试获取数据库统计信息
            String dbName = mongoTemplate.getDb().getName();
            boolean collectionExists = mongoTemplate.collectionExists("books");
            
            log.info("数据库操作测试成功 - 数据库: {}, books集合存在: {}", dbName, collectionExists);
            return true;
            
        } catch (Exception e) {
            log.error("数据库操作测试失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 连接状态数据类
     */
    public static class ConnectionStatus {
        private boolean connected;
        private String message;
        private String databaseName;
        private int collectionCount;
        private String error;
        private long timestamp;

        public ConnectionStatus() {
            this.timestamp = System.currentTimeMillis();
        }

        // Getters and Setters
        public boolean isConnected() {
            return connected;
        }

        public void setConnected(boolean connected) {
            this.connected = connected;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getDatabaseName() {
            return databaseName;
        }

        public void setDatabaseName(String databaseName) {
            this.databaseName = databaseName;
        }

        public int getCollectionCount() {
            return collectionCount;
        }

        public void setCollectionCount(int collectionCount) {
            this.collectionCount = collectionCount;
        }

        public String getError() {
            return error;
        }

        public void setError(String error) {
            this.error = error;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(long timestamp) {
            this.timestamp = timestamp;
        }

        @Override
        public String toString() {
            return "ConnectionStatus{" +
                    "connected=" + connected +
                    ", message='" + message + '\'' +
                    ", databaseName='" + databaseName + '\'' +
                    ", collectionCount=" + collectionCount +
                    ", error='" + error + '\'' +
                    ", timestamp=" + timestamp +
                    '}';
        }
    }

    /**
     * 连接池状态数据类
     */
    public static class ConnectionPoolStatus {
        private int poolCount;
        private int currentConnections;
        private int activeConnections;
        private int idleConnections;
        private long totalCreated;
        private long totalClosed;
        private long checkOutRequests;
        private long successfulCheckOuts;
        private long failedCheckOuts;
        private double utilizationRate;
        private double checkOutSuccessRate;
        private long timestamp;
        private String error;
        private Map<String, MongoConnectionPoolListener.ConnectionPoolMetrics> detailedMetrics;

        // Getters and Setters
        public int getPoolCount() { return poolCount; }
        public void setPoolCount(int poolCount) { this.poolCount = poolCount; }

        public int getCurrentConnections() { return currentConnections; }
        public void setCurrentConnections(int currentConnections) { this.currentConnections = currentConnections; }

        public int getActiveConnections() { return activeConnections; }
        public void setActiveConnections(int activeConnections) { this.activeConnections = activeConnections; }

        public int getIdleConnections() { return idleConnections; }
        public void setIdleConnections(int idleConnections) { this.idleConnections = idleConnections; }

        public long getTotalCreated() { return totalCreated; }
        public void setTotalCreated(long totalCreated) { this.totalCreated = totalCreated; }

        public long getTotalClosed() { return totalClosed; }
        public void setTotalClosed(long totalClosed) { this.totalClosed = totalClosed; }

        public long getCheckOutRequests() { return checkOutRequests; }
        public void setCheckOutRequests(long checkOutRequests) { this.checkOutRequests = checkOutRequests; }

        public long getSuccessfulCheckOuts() { return successfulCheckOuts; }
        public void setSuccessfulCheckOuts(long successfulCheckOuts) { this.successfulCheckOuts = successfulCheckOuts; }

        public long getFailedCheckOuts() { return failedCheckOuts; }
        public void setFailedCheckOuts(long failedCheckOuts) { this.failedCheckOuts = failedCheckOuts; }

        public double getUtilizationRate() { return utilizationRate; }
        public void setUtilizationRate(double utilizationRate) { this.utilizationRate = utilizationRate; }

        public double getCheckOutSuccessRate() { return checkOutSuccessRate; }
        public void setCheckOutSuccessRate(double checkOutSuccessRate) { this.checkOutSuccessRate = checkOutSuccessRate; }

        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

        public String getError() { return error; }
        public void setError(String error) { this.error = error; }

        public Map<String, MongoConnectionPoolListener.ConnectionPoolMetrics> getDetailedMetrics() { 
            return detailedMetrics; 
        }
        public void setDetailedMetrics(Map<String, MongoConnectionPoolListener.ConnectionPoolMetrics> detailedMetrics) { 
            this.detailedMetrics = detailedMetrics; 
        }

        @Override
        public String toString() {
            return String.format("ConnectionPoolStatus{" +
                    "pools=%d, current=%d, active=%d, idle=%d, utilization=%.2f%%, " +
                    "created=%d, closed=%d, checkOutRequests=%d, successRate=%.2f%%, error='%s'}",
                    poolCount, currentConnections, activeConnections, idleConnections,
                    utilizationRate, totalCreated, totalClosed, checkOutRequests,
                    checkOutSuccessRate, error);
        }
    }

    /**
     * 连接池健康状态数据类
     */
    public static class PoolHealthStatus {
        private boolean healthy;
        private String message;
        private double utilizationRate;
        private double checkOutSuccessRate;
        private int currentConnections;
        private int activeConnections;
        private long timestamp;

        // Getters and Setters
        public boolean isHealthy() { return healthy; }
        public void setHealthy(boolean healthy) { this.healthy = healthy; }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }

        public double getUtilizationRate() { return utilizationRate; }
        public void setUtilizationRate(double utilizationRate) { this.utilizationRate = utilizationRate; }

        public double getCheckOutSuccessRate() { return checkOutSuccessRate; }
        public void setCheckOutSuccessRate(double checkOutSuccessRate) { this.checkOutSuccessRate = checkOutSuccessRate; }

        public int getCurrentConnections() { return currentConnections; }
        public void setCurrentConnections(int currentConnections) { this.currentConnections = currentConnections; }

        public int getActiveConnections() { return activeConnections; }
        public void setActiveConnections(int activeConnections) { this.activeConnections = activeConnections; }

        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

        @Override
        public String toString() {
            return String.format("PoolHealthStatus{" +
                    "healthy=%s, message='%s', utilization=%.2f%%, successRate=%.2f%%, " +
                    "current=%d, active=%d}",
                    healthy, message, utilizationRate, checkOutSuccessRate,
                    currentConnections, activeConnections);
        }
    }
}
