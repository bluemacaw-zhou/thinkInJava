package cn.com.wind.service;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * ClickHouse连接池监控服务
 */
@Slf4j
@Service
public class ClickHousePoolMonitorService {

    @Resource
    @Qualifier("clickHouseDataSource")
    private DataSource dataSource;

    /**
     * 获取连接池状态
     */
    public Map<String, Object> getPoolStatus() {
        Map<String, Object> status = new HashMap<>();

        if (!(dataSource instanceof HikariDataSource)) {
            status.put("error", "DataSource is not HikariDataSource");
            return status;
        }

        HikariDataSource hikariDataSource = (HikariDataSource) dataSource;
        HikariPoolMXBean poolMXBean = hikariDataSource.getHikariPoolMXBean();

        if (poolMXBean == null) {
            status.put("error", "HikariPoolMXBean is not available");
            return status;
        }

        // 基本信息
        status.put("poolName", hikariDataSource.getPoolName());
        status.put("jdbcUrl", hikariDataSource.getJdbcUrl());

        // 连接池状态
        status.put("activeConnections", poolMXBean.getActiveConnections());
        status.put("idleConnections", poolMXBean.getIdleConnections());
        status.put("totalConnections", poolMXBean.getTotalConnections());
        status.put("threadsAwaitingConnection", poolMXBean.getThreadsAwaitingConnection());

        // 配置信息
        status.put("maximumPoolSize", hikariDataSource.getMaximumPoolSize());
        status.put("minimumIdle", hikariDataSource.getMinimumIdle());
        status.put("connectionTimeout", hikariDataSource.getConnectionTimeout());
        status.put("idleTimeout", hikariDataSource.getIdleTimeout());
        status.put("maxLifetime", hikariDataSource.getMaxLifetime());

        // 计算利用率
        int totalConnections = poolMXBean.getTotalConnections();
        int maximumPoolSize = hikariDataSource.getMaximumPoolSize();
        double utilizationRate = totalConnections > 0 ?
            (double) poolMXBean.getActiveConnections() / totalConnections * 100 : 0;
        double poolUsageRate = maximumPoolSize > 0 ?
            (double) totalConnections / maximumPoolSize * 100 : 0;

        status.put("utilizationRate", String.format("%.2f%%", utilizationRate));
        status.put("poolUsageRate", String.format("%.2f%%", poolUsageRate));

        return status;
    }

    /**
     * 获取连接池健康状态
     */
    public Map<String, Object> getHealthStatus() {
        Map<String, Object> health = new HashMap<>();

        if (!(dataSource instanceof HikariDataSource)) {
            health.put("healthy", false);
            health.put("message", "DataSource is not HikariDataSource");
            return health;
        }

        HikariDataSource hikariDataSource = (HikariDataSource) dataSource;
        HikariPoolMXBean poolMXBean = hikariDataSource.getHikariPoolMXBean();

        if (poolMXBean == null) {
            health.put("healthy", false);
            health.put("message", "HikariPoolMXBean is not available");
            return health;
        }

        int totalConnections = poolMXBean.getTotalConnections();
        int activeConnections = poolMXBean.getActiveConnections();
        int threadsAwaiting = poolMXBean.getThreadsAwaitingConnection();
        int maximumPoolSize = hikariDataSource.getMaximumPoolSize();

        boolean healthy = true;
        StringBuilder message = new StringBuilder("Healthy");

        // 检查是否有线程等待连接
        if (threadsAwaiting > 0) {
            healthy = false;
            message.append("; ").append(threadsAwaiting).append(" threads awaiting connection");
        }

        // 检查连接池是否接近饱和
        double poolUsageRate = maximumPoolSize > 0 ?
            (double) totalConnections / maximumPoolSize * 100 : 0;
        if (poolUsageRate > 90) {
            healthy = false;
            message.append("; Pool usage rate is high: ").append(String.format("%.2f%%", poolUsageRate));
        }

        // 检查活跃连接比例
        double utilizationRate = totalConnections > 0 ?
            (double) activeConnections / totalConnections * 100 : 0;
        if (utilizationRate > 90) {
            message.append("; High utilization rate: ").append(String.format("%.2f%%", utilizationRate));
        }

        health.put("healthy", healthy);
        health.put("message", message.toString());
        health.put("totalConnections", totalConnections);
        health.put("activeConnections", activeConnections);
        health.put("threadsAwaitingConnection", threadsAwaiting);
        health.put("poolUsageRate", String.format("%.2f%%", poolUsageRate));

        return health;
    }

    /**
     * 软关闭连接池（优雅关闭）
     */
    public Map<String, Object> softEvictConnections() {
        Map<String, Object> result = new HashMap<>();

        if (!(dataSource instanceof HikariDataSource)) {
            result.put("success", false);
            result.put("message", "DataSource is not HikariDataSource");
            return result;
        }

        HikariDataSource hikariDataSource = (HikariDataSource) dataSource;
        HikariPoolMXBean poolMXBean = hikariDataSource.getHikariPoolMXBean();

        if (poolMXBean == null) {
            result.put("success", false);
            result.put("message", "HikariPoolMXBean is not available");
            return result;
        }

        try {
            int beforeTotal = poolMXBean.getTotalConnections();
            int beforeIdle = poolMXBean.getIdleConnections();

            poolMXBean.softEvictConnections();

            // 等待一小段时间让连接被回收
            Thread.sleep(1000);

            int afterTotal = poolMXBean.getTotalConnections();
            int afterIdle = poolMXBean.getIdleConnections();

            result.put("success", true);
            result.put("message", "Soft evict connections completed");
            result.put("beforeTotal", beforeTotal);
            result.put("beforeIdle", beforeIdle);
            result.put("afterTotal", afterTotal);
            result.put("afterIdle", afterIdle);
            result.put("evicted", beforeTotal - afterTotal);

            log.info("Soft evicted connections: {} -> {}", beforeTotal, afterTotal);

        } catch (Exception e) {
            log.error("Failed to soft evict connections", e);
            result.put("success", false);
            result.put("message", "Failed to soft evict connections: " + e.getMessage());
        }

        return result;
    }

    /**
     * 暂停连接池
     */
    public Map<String, Object> suspendPool() {
        Map<String, Object> result = new HashMap<>();

        if (!(dataSource instanceof HikariDataSource)) {
            result.put("success", false);
            result.put("message", "DataSource is not HikariDataSource");
            return result;
        }

        HikariDataSource hikariDataSource = (HikariDataSource) dataSource;
        HikariPoolMXBean poolMXBean = hikariDataSource.getHikariPoolMXBean();

        if (poolMXBean == null) {
            result.put("success", false);
            result.put("message", "HikariPoolMXBean is not available");
            return result;
        }

        try {
            poolMXBean.suspendPool();

            result.put("success", true);
            result.put("message", "Pool suspended successfully");

            log.info("ClickHouse connection pool suspended");

        } catch (Exception e) {
            log.error("Failed to suspend pool", e);
            result.put("success", false);
            result.put("message", "Failed to suspend pool: " + e.getMessage());
        }

        return result;
    }

    /**
     * 恢复连接池
     */
    public Map<String, Object> resumePool() {
        Map<String, Object> result = new HashMap<>();

        if (!(dataSource instanceof HikariDataSource)) {
            result.put("success", false);
            result.put("message", "DataSource is not HikariDataSource");
            return result;
        }

        HikariDataSource hikariDataSource = (HikariDataSource) dataSource;
        HikariPoolMXBean poolMXBean = hikariDataSource.getHikariPoolMXBean();

        if (poolMXBean == null) {
            result.put("success", false);
            result.put("message", "HikariPoolMXBean is not available");
            return result;
        }

        try {
            poolMXBean.resumePool();

            result.put("success", true);
            result.put("message", "Pool resumed successfully");

            log.info("ClickHouse connection pool resumed");

        } catch (Exception e) {
            log.error("Failed to resume pool", e);
            result.put("success", false);
            result.put("message", "Failed to resume pool: " + e.getMessage());
        }

        return result;
    }
}
