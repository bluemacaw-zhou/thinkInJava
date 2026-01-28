package io.bluemacaw.mongodb.util;

import com.mongodb.client.result.UpdateResult;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@Slf4j
public class ChangeStreamUtil {

    private static final String TOKEN_COLLECTION = "change_stream_resume_tokens";
    private static final String DATABASE_LEVEL_TOKEN_ID = "database_changestream";
    private static final String HEARTBEAT_COLLECTION = "changestream_heartbeat";

    /**
     * 获取 Database 级别 Change Stream 的 Resume Token
     *
     * 存储位置：im_message.change_stream_resume_tokens
     * 文档 _id：database_changestream
     *
     * @return Resume Token JSON 字符串,如果不存在返回 null
     */
    public static String getResumeToken(MongoTemplate mongoTemplate) {
        try {
            Document tokenDoc = mongoTemplate.findById(
                    DATABASE_LEVEL_TOKEN_ID,
                    Document.class,
                    TOKEN_COLLECTION
            );

            if (tokenDoc != null) {
                String token = tokenDoc.getString("token");
                log.debug("Resume token loaded for database change stream");
                return token;
            }

        } catch (Exception e) {
            log.error("Failed to load resume token", e);
        }

        return null;
    }

    /**
     * 插入心跳数据以触发 Change Stream 事件
     *
     * 当 resumeToken 为 null 或失效时,插入一条心跳数据
     * 用于主动获取 resumeToken,确保 Change Stream 能正常工作
     */
    public static void insertHeartbeat(MongoTemplate mongoTemplate) {
        try {
            Document heartbeat = new Document();
            heartbeat.put("type", "init");
            heartbeat.put("timestamp", new java.util.Date());
            heartbeat.put("description", "Heartbeat to initialize resume token");

            mongoTemplate.insert(heartbeat, HEARTBEAT_COLLECTION);

            log.info("Heartbeat inserted to initialize resume token");

        } catch (Exception e) {
            log.error("Failed to insert heartbeat", e);
        }
    }

    /**
     * 保存 Database 级别 Change Stream 的 Resume Token
     *
     * 存储位置：im_message.change_stream_resume_tokens
     * 文档 _id：database_changestream
     */
    public static void saveResumeToken(String resumeToken, MongoTemplate mongoTemplate) {
        try {
            Document tokenDoc = new Document();
            tokenDoc.put("_id", DATABASE_LEVEL_TOKEN_ID);
            tokenDoc.put("token", resumeToken);
            tokenDoc.put("updateTime", new java.util.Date());

            mongoTemplate.save(tokenDoc, TOKEN_COLLECTION);

            log.debug("Resume token saved for database change stream");

        } catch (Exception e) {
            log.error("Failed to save resume token", e);
        }
    }

    /**
     * 初始化 Resume Token 文档（多实例并发安全）
     *
     * 使用 upsert 操作确保在多实例启动时只有一个实例能创建文档
     * 如果文档已存在则不做任何操作
     *
     * @return true 表示成功初始化或文档已存在, false 表示初始化失败
     */
    public static boolean initializeResumeToken(MongoTemplate mongoTemplate) {
        try {
            Query query = new Query(Criteria.where("_id").is(DATABASE_LEVEL_TOKEN_ID));

            // 检查是否已存在
            boolean exists = mongoTemplate.exists(query, TOKEN_COLLECTION);
            if (exists) {
                log.debug("Resume token document already exists, skip initialization");
                return true;
            }

            // 使用 upsert 创建初始文档（并发安全）
            Update update = new Update()
                    .setOnInsert("_id", DATABASE_LEVEL_TOKEN_ID)
                    .setOnInsert("token", null)
                    .setOnInsert("createTime", new java.util.Date())
                    .setOnInsert("updateTime", new java.util.Date());

            UpdateResult result = mongoTemplate.upsert(query, update, TOKEN_COLLECTION);

            if (result.getUpsertedId() != null) {
                log.info("Resume token document initialized successfully");
            } else {
                log.debug("Resume token document already created by another instance");
            }

            return true;

        } catch (Exception e) {
            log.error("Failed to initialize resume token document", e);
            return false;
        }
    }
}
