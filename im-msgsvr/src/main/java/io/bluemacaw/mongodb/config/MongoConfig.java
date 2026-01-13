package io.bluemacaw.mongodb.config;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.config.AbstractMongoClientConfiguration;
import org.springframework.data.mongodb.core.convert.*;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.beans.factory.annotation.Value;

import java.util.concurrent.TimeUnit;

@Slf4j
@Configuration
public class MongoConfig extends AbstractMongoClientConfiguration {
    @Value("${mongodb.host}")
    private String host;

    @Value("${mongodb.port}")
    private int port;

    @Value("${mongodb.database}")
    private String database;

    private MongoCredentials credentials;

    @Autowired
    private MongoConnectionPoolListener connectionPoolListener;

    /**
     * 从第三方 API 获取凭证
     */
    @PostConstruct
    public void initCredentials() {
        try {
            // 验证配置值是否正确映射
            log.info("验证配置映射 - Host: {}, Port: {}, Database: {}", host, port, database);
            
            if (host == null || host.trim().isEmpty()) {
                log.error("MongoDB host 配置未正确映射，当前值: {}", host);
                throw new IllegalStateException("MongoDB host 配置缺失");
            }
            
            if (port == 0) {
                log.error("MongoDB port 配置未正确映射，当前值: {}", port);
                throw new IllegalStateException("MongoDB port 配置缺失");
            }
            
            if (database == null || database.trim().isEmpty()) {
                log.error("MongoDB database 配置未正确映射，当前值: {}", database);
                throw new IllegalStateException("MongoDB database 配置缺失");
            }
            
            this.credentials = fetchCredentialsFromThirdParty();
            log.info("MongoDB 凭证获取成功 - 用户名: {}", credentials.getUsername());

        } catch (Exception e) {
            throw new RuntimeException("无法获取 MongoDB 凭证", e);
        }
    }

    @Bean
    @Override
    public MongoClient mongoClient() {
        if (credentials == null) {
            throw new IllegalStateException("MongoDB 凭证未初始化");
        }

        // 构建连接字符串（添加副本集参数以支持事务）
        String connectionString = String.format(
                "mongodb://%s:%s@%s:%d/%s?authSource=%s&replicaSet=%s",
                credentials.getUsername(),
                credentials.getPassword(),
                host,
                port,
                database,
                "admin",
                "rs0"  // 副本集名称
        );

        log.info("MongoDB 连接字符串: mongodb://{}:****@{}:{}/{}?authSource=admin&replicaSet=rs0",
                 credentials.getUsername(), host, port, database);

        // 使用MongoClientSettings来配置连接池监听器和参数
        MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(connectionString))
                .applyToConnectionPoolSettings(builder ->
                        builder.maxSize(100)                    // 最大连接数
                                .minSize(10)                    // 最小连接数
                                .maxWaitTime(120, TimeUnit.SECONDS)  // 最大等待时间
                                .maxConnectionLifeTime(300, TimeUnit.SECONDS)  // 连接最大生存时间
                                .maxConnectionIdleTime(240, TimeUnit.SECONDS)  // 连接最大空闲时间
                                .addConnectionPoolListener(connectionPoolListener)  // 添加连接池监听器
                )
                .applyToSocketSettings(builder ->
                        builder.connectTimeout(10, TimeUnit.SECONDS)     // 连接超时
                                .readTimeout(30, TimeUnit.SECONDS)       // 读取超时
                )
                .build();

        return MongoClients.create(settings);
    }

    /**
     * 调用第三方 API 获取凭证
     */
    private MongoCredentials fetchCredentialsFromThirdParty() {
        // 省去请求账号密码的逻辑
        String userName = "admin";
        String password = "admin";

        return new MongoCredentials(userName, password);
    }

    @Override
    protected String getDatabaseName() {
        return database;
    }

    /**
     * 凭证数据类
     */
    @Data
    @AllArgsConstructor
    public static class MongoCredentials {
        private String username;
        private String password;
    }

    /**
     * 第三方 API 响应类
     */
    @Data
    public static class CredentialResponse {
        private String username;
        private String password;
        private Long expiresAt;
    }

    @Bean
    @Override
    public MappingMongoConverter mappingMongoConverter(
            MongoDatabaseFactory databaseFactory,
            MongoCustomConversions customConversions,
            MongoMappingContext mappingContext) {
        DefaultDbRefResolver dbRefResolver = new DefaultDbRefResolver(databaseFactory);
        MappingMongoConverter mappingMongoConverter = new MappingMongoConverter(dbRefResolver, mappingContext);
        mappingMongoConverter.setCustomConversions(customConversions);

        // 去掉 _class 字段
        mappingMongoConverter.setTypeMapper(new DefaultMongoTypeMapper(null));

        return mappingMongoConverter;
    }

    /**
     * 配置 MongoDB 事务管理器
     * 这是让 @Transactional 注解生效的关键组件
     *
     * 前提条件：
     * 1. MongoDB 必须是副本集（Replica Set）或分片集群模式
     * 2. MongoDB 版本 >= 4.0（副本集）或 >= 4.2（分片）
     * 3. 连接字符串必须包含 replicaSet 参数
     *
     * @param dbFactory MongoDB 数据库工厂
     * @return MongoDB 事务管理器
     */
    @Bean
    public MongoTransactionManager transactionManager(MongoDatabaseFactory dbFactory) {
        log.info("初始化 MongoDB 事务管理器 - 支持 @Transactional 注解");
        return new MongoTransactionManager(dbFactory);
    }
}
