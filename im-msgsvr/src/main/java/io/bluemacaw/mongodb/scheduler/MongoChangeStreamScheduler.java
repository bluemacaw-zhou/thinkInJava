package io.bluemacaw.mongodb.scheduler;

import com.mongodb.client.ChangeStreamIterable;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.changestream.ChangeStreamDocument;
import com.mongodb.client.model.changestream.FullDocument;
import io.bluemacaw.mongodb.changestream.ChangeStreamManager;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.bson.BsonDocument;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

import static io.bluemacaw.mongodb.util.ChangeStreamUtil.*;

/**
 * MongoDB Change Stream 定时任务调度器（框架入口）
 *
 * 架构设计：
 * 1. 此类作为框架入口，负责启动和管理 Database 级别的 Change Stream
 * 2. 通过 ChangeStreamRouter 将事件路由到不同的处理器
 * 3. 具体的业务逻辑由各个 ChangeStreamHandler 实现
 * 4. 支持动态注册多个处理器，每个处理器处理不同的 collection
 *
 * 监听策略：
 * - 使用 Database 级别 Change Stream
 * - 通过 pipeline 过滤需要监听的 collection（可配置前缀）
 * - 只需要一个 Resume Token，简化断点续传逻辑
 *
 * @author shzhou.michael
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "mongodb.change-stream", name = "enabled", havingValue = "true")
public class MongoChangeStreamScheduler {

    @Resource
    private MongoTemplate mongoTemplate;

    @Resource
    private ChangeStreamManager changeStreamManager;

    @Value("${mongodb.change-stream.batch-size:1000}")
    private int batchSize;

    @Value("${mongodb.change-stream.max-await-time:4000}")
    private long maxAwaitTime;

    /**
     * 启动时初始化 Resume Token 文档
     *
     * 说明：
     * - 检查 change_stream_resume_tokens 集合中是否存在 resume token 文档
     * - 如果不存在则创建初始文档（token=null）
     * - 使用 upsert 操作确保多实例并发启动时的安全性
     * - 如果 token 为 null，启动一次 Change Stream 获取初始 token
     */
    @PostConstruct
    public void init() {
        try {
            // 检查副本集状态
            Document isMaster = mongoTemplate.executeCommand(new Document("isMaster", 1));
            boolean isReplicaSet = isMaster.containsKey("setName");

            log.info("MongoDB isMaster result: {}", isMaster.toJson());

            if (!isReplicaSet) {
                log.warn("MongoDB is NOT a replica set, Change Stream initialization skipped");
                return;
            } else {
                log.info("MongoDB is a replica set, setName: {}", isMaster.getString("setName"));
            }

            // 初始化 Resume Token 文档
            boolean initialized = initializeResumeToken(mongoTemplate);
            if (!initialized) {
                log.error("Failed to initialize resume token, Change Stream may not work properly");
                return;
            } else {
                log.info("Resume token document initialized successfully");
            }

        } catch (Exception e) {
            log.error("Failed to initialize MongoChangeStreamScheduler", e);
        }
    }


    /**
     * 定时执行 Change Stream 监听
     * cron 表达式从配置文件读取，默认每 5 秒执行一次
     *
     * 说明：由 PowerJob 统一调度，避免分布式环境下的重复执行
     */
    @Scheduled(cron = "${mongodb.change-stream.schedule-cron:0/5 * * * * ?}")
    public void processChangeStream() {
        try {
            processChanges();
        } catch (Exception e) {
            log.error("Change Stream processing error", e);
        }
    }

    /**
     * 处理 Change Stream 事件
     *
     * 使用 Database 级别的 Change Stream 监听所有 collection
     * 通过 pipeline 过滤出 message_ 开头的 collection
     */
    private void processChanges() {
        MongoDatabase database = mongoTemplate.getDb();
        String resumeToken = getResumeToken(mongoTemplate);
        ChangeStreamIterable<Document> changeStream;
        boolean needHeartbeat = false;

        // 监听整个 database，不使用 pipeline 过滤
        // 具体的 collection 过滤由各个 Handler 的 supports() 方法决定
        if (resumeToken != null) {
            // 使用 resumeToken 恢复
            try {
                BsonDocument resumeBsonToken = BsonDocument.parse(resumeToken);
                changeStream = database.watch()
                        .fullDocument(FullDocument.UPDATE_LOOKUP)
                        .resumeAfter(resumeBsonToken)
                        .batchSize(batchSize)
                        .maxAwaitTime(maxAwaitTime, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                log.error("Resume token invalid, will insert heartbeat after starting Change Stream", e);
                // token 无效，标记需要插入心跳
                needHeartbeat = true;
                changeStream = database.watch()
                        .fullDocument(FullDocument.UPDATE_LOOKUP)
                        .batchSize(batchSize)
                        .maxAwaitTime(maxAwaitTime, TimeUnit.MILLISECONDS);
            }
        } else {
            // 没有 resumeToken，标记需要插入心跳
            needHeartbeat = true;
            changeStream = database.watch()
                    .fullDocument(FullDocument.UPDATE_LOOKUP)
                    .batchSize(batchSize)
                    .maxAwaitTime(maxAwaitTime, TimeUnit.MILLISECONDS);
        }

        int eventCount = 0;
        BsonDocument lastResumeToken = null;

        try (MongoCursor<ChangeStreamDocument<Document>> cursor = changeStream.iterator()) {

            // 如果需要心跳，在 Change Stream 启动后立即插入
            if (needHeartbeat) {
                insertHeartbeat(mongoTemplate);
                log.debug("Heartbeat inserted to initialize resume token");
            }

            while (true) {
                ChangeStreamDocument<Document> changeEvent = cursor.tryNext();

                if (changeEvent == null) {
                    log.debug("No more change events available");
                    break;
                }

                String collectionName = changeEvent.getNamespace().getCollectionName();

                // 过滤掉系统集合的事件，避免无限循环
                if ("changestream_heartbeat".equals(collectionName) ||
                    "change_stream_resume_tokens".equals(collectionName)) {
                    lastResumeToken = changeEvent.getResumeToken();
                    log.debug("Skipped system collection event: {}", collectionName);
                    continue;
                }

                eventCount++;

                log.debug("Change Stream event: collection={}, type={}, id={}",
                        collectionName,
                        changeEvent.getOperationType().getValue(),
                        changeEvent.getDocumentKey());

                changeStreamManager.route(collectionName, changeEvent);
                lastResumeToken = changeEvent.getResumeToken();
            }

            changeStreamManager.flushAllHandlers();

            if (lastResumeToken != null) {
                saveResumeToken(lastResumeToken.toJson(), mongoTemplate);
            }

            if (eventCount > 0) {
                log.info("Processed {} Change Stream events", eventCount);
            }

        } catch (Exception e) {
            log.error("Change Stream error", e);
        }
    }
}
