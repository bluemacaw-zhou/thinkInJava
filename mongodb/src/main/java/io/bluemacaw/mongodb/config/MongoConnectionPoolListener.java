package io.bluemacaw.mongodb.config;

import com.mongodb.event.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * MongoDB连接池事件监听器
 */
@Slf4j
@Component
public class MongoConnectionPoolListener implements ConnectionPoolListener {

    // 连接池统计数据
    private final ConcurrentHashMap<String, ConnectionPoolMetrics> poolMetrics = new ConcurrentHashMap<>();

    @Override
    public void connectionPoolCreated(ConnectionPoolCreatedEvent event) {
        String serverId = event.getServerId().toString();
        poolMetrics.put(serverId, new ConnectionPoolMetrics());
        log.info("连接池已创建: {} - 设置: {}", serverId, event.getSettings());
    }

    @Override
    public void connectionPoolCleared(ConnectionPoolClearedEvent event) {
        String serverId = event.getServerId().toString();
        log.warn("连接池已清空: {}", serverId);
        // 重置统计数据
        poolMetrics.computeIfPresent(serverId, (k, v) -> {
            v.reset();
            return v;
        });
    }

    @Override
    public void connectionPoolClosed(ConnectionPoolClosedEvent event) {
        String serverId = event.getServerId().toString();
        log.info("连接池已关闭: {}", serverId);
        poolMetrics.remove(serverId);
    }

    @Override
    public void connectionCreated(ConnectionCreatedEvent event) {
        String serverId = event.getConnectionId().getServerId().toString();
        ConnectionPoolMetrics metrics = poolMetrics.get(serverId);
        if (metrics != null) {
            metrics.incrementTotalCreated();
            metrics.incrementCurrentConnections();
        }
        log.debug("连接已创建: {}", event.getConnectionId());
    }

    @Override
    public void connectionClosed(ConnectionClosedEvent event) {
        String serverId = event.getConnectionId().getServerId().toString();
        ConnectionPoolMetrics metrics = poolMetrics.get(serverId);
        if (metrics != null) {
            metrics.decrementCurrentConnections();
            metrics.incrementTotalClosed();
        }
        log.debug("连接已关闭: {} - 原因: {}", event.getConnectionId(), 
                 event.getReason());
    }

    @Override
    public void connectionCheckOutStarted(ConnectionCheckOutStartedEvent event) {
        String serverId = event.getServerId().toString();
        ConnectionPoolMetrics metrics = poolMetrics.get(serverId);
        if (metrics != null) {
            metrics.incrementCheckOutRequests();
        }
        log.debug("开始检出连接: {}", serverId);
    }

    @Override
    public void connectionCheckedOut(ConnectionCheckedOutEvent event) {
        String serverId = event.getConnectionId().getServerId().toString();
        ConnectionPoolMetrics metrics = poolMetrics.get(serverId);
        if (metrics != null) {
            metrics.incrementActiveConnections();
            metrics.incrementSuccessfulCheckOuts();
        }
        log.debug("连接检出成功: {}", event.getConnectionId());
    }

    @Override
    public void connectionCheckOutFailed(ConnectionCheckOutFailedEvent event) {
        String serverId = event.getServerId().toString();
        ConnectionPoolMetrics metrics = poolMetrics.get(serverId);
        if (metrics != null) {
            metrics.incrementFailedCheckOuts();
        }
        log.warn("连接检出失败: {} - 原因: {}", serverId, event.getReason());
    }

    @Override
    public void connectionCheckedIn(ConnectionCheckedInEvent event) {
        String serverId = event.getConnectionId().getServerId().toString();
        ConnectionPoolMetrics metrics = poolMetrics.get(serverId);
        if (metrics != null) {
            metrics.decrementActiveConnections();
        }
        log.debug("连接已归还: {}", event.getConnectionId());
    }

    /**
     * 获取连接池指标
     */
    public ConcurrentHashMap<String, ConnectionPoolMetrics> getPoolMetrics() {
        return new ConcurrentHashMap<>(poolMetrics);
    }

    /**
     * 获取汇总的连接池指标
     */
    public ConnectionPoolSummary getPoolSummary() {
        ConnectionPoolSummary summary = new ConnectionPoolSummary();
        
        for (ConnectionPoolMetrics metrics : poolMetrics.values()) {
            summary.totalCurrentConnections += metrics.getCurrentConnections().get();
            summary.totalActiveConnections += metrics.getActiveConnections().get();
            summary.totalCreatedConnections += metrics.getTotalCreated().get();
            summary.totalClosedConnections += metrics.getTotalClosed().get();
            summary.totalCheckOutRequests += metrics.getCheckOutRequests().get();
            summary.totalSuccessfulCheckOuts += metrics.getSuccessfulCheckOuts().get();
            summary.totalFailedCheckOuts += metrics.getFailedCheckOuts().get();
        }
        
        summary.poolCount = poolMetrics.size();
        summary.timestamp = System.currentTimeMillis();
        
        return summary;
    }

    /**
     * 连接池指标数据类
     */
    public static class ConnectionPoolMetrics {
        private final AtomicInteger currentConnections = new AtomicInteger(0);
        private final AtomicInteger activeConnections = new AtomicInteger(0);
        private final AtomicLong totalCreated = new AtomicLong(0);
        private final AtomicLong totalClosed = new AtomicLong(0);
        private final AtomicLong checkOutRequests = new AtomicLong(0);
        private final AtomicLong successfulCheckOuts = new AtomicLong(0);
        private final AtomicLong failedCheckOuts = new AtomicLong(0);

        public void incrementCurrentConnections() { currentConnections.incrementAndGet(); }
        public void decrementCurrentConnections() { currentConnections.decrementAndGet(); }
        public void incrementActiveConnections() { activeConnections.incrementAndGet(); }
        public void decrementActiveConnections() { activeConnections.decrementAndGet(); }
        public void incrementTotalCreated() { totalCreated.incrementAndGet(); }
        public void incrementTotalClosed() { totalClosed.incrementAndGet(); }
        public void incrementCheckOutRequests() { checkOutRequests.incrementAndGet(); }
        public void incrementSuccessfulCheckOuts() { successfulCheckOuts.incrementAndGet(); }
        public void incrementFailedCheckOuts() { failedCheckOuts.incrementAndGet(); }

        public void reset() {
            currentConnections.set(0);
            activeConnections.set(0);
            // 不重置累计统计
        }

        // Getters
        public AtomicInteger getCurrentConnections() { return currentConnections; }
        public AtomicInteger getActiveConnections() { return activeConnections; }
        public AtomicLong getTotalCreated() { return totalCreated; }
        public AtomicLong getTotalClosed() { return totalClosed; }
        public AtomicLong getCheckOutRequests() { return checkOutRequests; }
        public AtomicLong getSuccessfulCheckOuts() { return successfulCheckOuts; }
        public AtomicLong getFailedCheckOuts() { return failedCheckOuts; }

        @Override
        public String toString() {
            return String.format("ConnectionPoolMetrics{" +
                    "current=%d, active=%d, created=%d, closed=%d, " +
                    "checkOutRequests=%d, successful=%d, failed=%d}",
                    currentConnections.get(), activeConnections.get(),
                    totalCreated.get(), totalClosed.get(),
                    checkOutRequests.get(), successfulCheckOuts.get(),
                    failedCheckOuts.get());
        }
    }

    /**
     * 连接池汇总信息
     */
    public static class ConnectionPoolSummary {
        public int poolCount;
        public int totalCurrentConnections;
        public int totalActiveConnections;
        public long totalCreatedConnections;
        public long totalClosedConnections;
        public long totalCheckOutRequests;
        public long totalSuccessfulCheckOuts;
        public long totalFailedCheckOuts;
        public long timestamp;

        public double getConnectionUtilizationRate() {
            return totalCurrentConnections > 0 ? 
                (double) totalActiveConnections / totalCurrentConnections * 100 : 0.0;
        }

        public double getCheckOutSuccessRate() {
            return totalCheckOutRequests > 0 ? 
                (double) totalSuccessfulCheckOuts / totalCheckOutRequests * 100 : 100.0;
        }

        @Override
        public String toString() {
            return String.format("ConnectionPoolSummary{" +
                    "pools=%d, current=%d, active=%d, utilization=%.2f%%, " +
                    "created=%d, closed=%d, checkOutRequests=%d, successRate=%.2f%%}",
                    poolCount, totalCurrentConnections, totalActiveConnections,
                    getConnectionUtilizationRate(), totalCreatedConnections,
                    totalClosedConnections, totalCheckOutRequests, getCheckOutSuccessRate());
        }
    }
}
