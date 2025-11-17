package cn.com.wind.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * ClickHouse数据源配置 - 使用HikariCP连接池
 */
@Slf4j
@Configuration
public class ClickHouseConfig {

    @Value("${clickhouse.url}")
    private String url;

    @Value("${clickhouse.username}")
    private String username;

    @Value("${clickhouse.password}")
    private String password;

    @Value("${clickhouse.hikari.pool-name:ClickHousePool}")
    private String poolName;

    @Value("${clickhouse.hikari.maximum-pool-size:20}")
    private int maximumPoolSize;

    @Value("${clickhouse.hikari.minimum-idle:5}")
    private int minimumIdle;

    @Value("${clickhouse.hikari.connection-timeout:30000}")
    private long connectionTimeout;

    @Value("${clickhouse.hikari.idle-timeout:600000}")
    private long idleTimeout;

    @Value("${clickhouse.hikari.max-lifetime:1800000}")
    private long maxLifetime;

    @Value("${clickhouse.hikari.auto-commit:true}")
    private boolean autoCommit;

    @Value("${clickhouse.hikari.connection-test-query:SELECT 1}")
    private String connectionTestQuery;

    @Bean(name = "clickHouseDataSource", destroyMethod = "close")
    public DataSource clickHouseDataSource() {
        log.info("Initializing ClickHouse HikariCP DataSource: {}", url);

        HikariConfig config = new HikariConfig();

        // 基本连接信息
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName("com.clickhouse.jdbc.ClickHouseDriver");

        // 连接池配置
        config.setPoolName(poolName);
        config.setMaximumPoolSize(maximumPoolSize);
        config.setMinimumIdle(minimumIdle);
        config.setConnectionTimeout(connectionTimeout);
        config.setIdleTimeout(idleTimeout);
        config.setMaxLifetime(maxLifetime);
        config.setAutoCommit(autoCommit);
        config.setConnectionTestQuery(connectionTestQuery);

        // ClickHouse特定的连接参数
        config.addDataSourceProperty("socket_timeout", "30000");
        config.addDataSourceProperty("connection_timeout", "10000");
        config.addDataSourceProperty("max_execution_time", "60");
        config.addDataSourceProperty("compress", "1");

        // 性能优化配置
        config.setRegisterMbeans(false);  // 禁用JMX监控，避免注册冲突（使用自定义监控接口）
        config.setLeakDetectionThreshold(60000);  // 连接泄漏检测阈值(60秒)

        HikariDataSource dataSource = new HikariDataSource(config);

        log.info("ClickHouse HikariCP DataSource initialized successfully - Pool: {}, MaxSize: {}, MinIdle: {}",
                poolName, maximumPoolSize, minimumIdle);

        return dataSource;
    }
}
