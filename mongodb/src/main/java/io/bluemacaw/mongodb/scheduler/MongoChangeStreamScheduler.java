package io.bluemacaw.mongodb.scheduler;

import com.mongodb.client.ChangeStreamIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.changestream.ChangeStreamDocument;
import com.mongodb.client.model.changestream.FullDocument;
import io.bluemacaw.mongodb.service.MongoChangeStreamService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.bson.BsonDocument;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * MongoDB Change Stream定时任务调度器（单节点模式）
 * 定时执行Change Stream监听，适用于单节点部署
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "mongodb.change-stream", name = "enabled", havingValue = "true")
public class MongoChangeStreamScheduler {

    @Resource
    private MongoTemplate mongoTemplate;

    @Resource
    private MongoChangeStreamService changeStreamService;

    @Value("${mongodb.change-stream.collection:message}")
    private String collectionName;

    @Value("${mongodb.change-stream.batch-size:100}")
    private int batchSize;

    @Value("${mongodb.change-stream.process-duration:8000}")
    private long processDuration;

    /**
     * 定时执行Change Stream监听
     * cron表达式从配置文件读取，默认每10秒执行一次
     */
    @Scheduled(cron = "${mongodb.change-stream.schedule-cron:0/10 * * * * ?}")
    public void processChangeStream() {
        try {
            processChanges();
        } catch (Exception e) {
            log.error("Change Stream processing error", e);
        }
    }

    /**
     * 处理Change Stream事件
     */
    private void processChanges() {
        MongoCollection<Document> collection = mongoTemplate.getCollection(collectionName);

        // 检查MongoDB副本集状态
        try {
            Document isMaster = mongoTemplate.executeCommand(new Document("isMaster", 1));
            boolean isReplicaSet = isMaster.containsKey("setName");
            if (!isReplicaSet) {
                log.error("MongoDB is NOT a replica set, Change Stream disabled");
                return;
            }
        } catch (Exception e) {
            log.error("Failed to check replica set status", e);
            return;
        }

        // 尝试从上次中断的位置恢复
        String resumeToken = changeStreamService.getResumeToken(mongoTemplate, collectionName);

        ChangeStreamIterable<Document> changeStream;

        if (resumeToken != null) {
            log.debug("Resuming from saved token");
            try {
                BsonDocument resumeBsonToken = BsonDocument.parse(resumeToken);
                changeStream = collection.watch()
                    .fullDocument(FullDocument.UPDATE_LOOKUP)
                    .resumeAfter(resumeBsonToken)
                    .batchSize(batchSize)
                    .maxAwaitTime(1000, java.util.concurrent.TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                log.warn("Resume token invalid, starting new stream", e);
                changeStream = collection.watch()
                    .fullDocument(FullDocument.UPDATE_LOOKUP)
                    .batchSize(batchSize)
                    .maxAwaitTime(1000, java.util.concurrent.TimeUnit.MILLISECONDS);
            }
        } else {
            log.debug("Starting new Change Stream");
            changeStream = collection.watch()
                .fullDocument(FullDocument.UPDATE_LOOKUP)
                .batchSize(batchSize)
                .maxAwaitTime(1000, java.util.concurrent.TimeUnit.MILLISECONDS);
        }

        // 设置最大处理时间
        long startTime = System.currentTimeMillis();
        long endTime = startTime + processDuration;

        int eventCount = 0;
        BsonDocument lastResumeToken = null;

        try (MongoCursor<ChangeStreamDocument<Document>> cursor = changeStream.iterator()) {

            // 检查是否有待处理的事件
            boolean hasAnyEvent = false;
            while (System.currentTimeMillis() < endTime) {
                try {
                    // 使用 tryNext() 而不是 hasNext() + next()，避免无限阻塞
                    ChangeStreamDocument<Document> changeEvent = cursor.tryNext();

                    if (changeEvent == null) {
                        // 没有新事件，短暂休眠后继续检查
                        Thread.sleep(100);
                        continue;
                    }

                    hasAnyEvent = true;
                    eventCount++;

                    log.info("Change Stream event: type={}, id={}",
                        changeEvent.getOperationType().getValue(),
                        changeEvent.getDocumentKey());

                    try {
                        handleChangeEvent(changeEvent);
                        lastResumeToken = changeEvent.getResumeToken();

                    } catch (Exception e) {
                        log.error("Event handling error", e);
                        // 继续处理下一个事件
                    }
                } catch (InterruptedException e) {
                    log.warn("Change Stream interrupted", e);
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            // 刷新Service层的批量缓存
            changeStreamService.flushBatchCache();
            
            // 始终保存resume token（确保下次从当前位置继续）
            if (lastResumeToken != null) {
                changeStreamService.saveResumeToken(
                    lastResumeToken.toJson(),
                    mongoTemplate,
                    collectionName
                );
            }

            if (hasAnyEvent) {
                long actualDuration = System.currentTimeMillis() - startTime;
                log.info("Processed {} events in {} ms", eventCount, actualDuration);
            }

        } catch (Exception e) {
            log.error("Change Stream error", e);

            // 保存最后成功处理的token
            if (lastResumeToken != null) {
                changeStreamService.saveResumeToken(
                    lastResumeToken.toJson(),
                    mongoTemplate,
                    collectionName
                );
            }
        }
    }

    /**
     * 处理Change Stream事件
     */
    private void handleChangeEvent(ChangeStreamDocument<Document> changeEvent) {
        String operationType = changeEvent.getOperationType().getValue();

        switch (operationType) {
            case "insert":
                Document fullDocument = changeEvent.getFullDocument();
                if (fullDocument != null) {
                    changeStreamService.handleInsert(fullDocument);
                } else {
                    log.warn("Insert event missing document");
                }
                break;

            case "update":
                org.bson.BsonDocument bsonDocumentKey = changeEvent.getDocumentKey();
                Document documentKey = bsonDocumentKey != null ? Document.parse(bsonDocumentKey.toJson()) : null;
                Document updateDescription = changeEvent.getUpdateDescription() != null ?
                    new Document("updatedFields", changeEvent.getUpdateDescription().getUpdatedFields())
                        .append("removedFields", changeEvent.getUpdateDescription().getRemovedFields())
                    : new Document();

                changeStreamService.handleUpdate(documentKey, updateDescription);
                break;

            case "delete":
                org.bson.BsonDocument bsonDeleteKey = changeEvent.getDocumentKey();
                Document deleteKey = bsonDeleteKey != null ? Document.parse(bsonDeleteKey.toJson()) : null;
                if (deleteKey != null) {
                    changeStreamService.handleDelete(deleteKey);
                } else {
                    log.warn("Delete event missing document key");
                }
                break;

            case "replace":
                Document replaceDocument = changeEvent.getFullDocument();
                if (replaceDocument != null) {
                    changeStreamService.handleReplace(replaceDocument);
                } else {
                    log.warn("Replace event missing document");
                }
                break;

            default:
                log.debug("Unhandled operation: {}", operationType);
        }
    }
}
