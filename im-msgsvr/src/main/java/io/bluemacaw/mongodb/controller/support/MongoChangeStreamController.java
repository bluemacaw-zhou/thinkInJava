package io.bluemacaw.mongodb.controller.support;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * MongoDB Change Stream 管理控制器
 *
 * 支持 Database 级别的 Change Stream 监听
 * 自动监听所有 message_ 开头的 collection
 *
 * @author shzhou.michael
 */
@Slf4j
@RestController
@RequestMapping("/api/mongo/changestream")
@ConditionalOnProperty(prefix = "mongodb.change-stream", name = "enabled", havingValue = "true")
public class MongoChangeStreamController {

    @Resource
    private MongoTemplate mongoTemplate;

    /**
     * 获取 Change Stream 状态
     * GET /api/mongo/changestream/status
     */
    @GetMapping("/status")
    public Map<String, Object> getStatus() {
        Map<String, Object> response = new HashMap<>();

        try {
            // 检查 MongoDB 副本集状态
            org.bson.Document isMaster = mongoTemplate.executeCommand(new org.bson.Document("isMaster", 1));

            boolean isReplicaSet = isMaster.containsKey("setName");
            String setName = isReplicaSet ? isMaster.getString("setName") : null;

            response.put("success", true);
            response.put("enabled", true);
            response.put("mode", "database-level");
            response.put("watchPattern", "message_*");
            response.put("isReplicaSet", isReplicaSet);
            response.put("replicaSetName", setName);

            if (!isReplicaSet) {
                response.put("warning", "MongoDB is not running as a replica set. Change Streams require a replica set.");
            }

        } catch (Exception e) {
            log.error("Error getting Change Stream status", e);
            response.put("success", false);
            response.put("error", e.getMessage());
        }

        return response;
    }

    /**
     * 获取 Resume Token 信息
     * GET /api/mongo/changestream/resume-token
     *
     * Database 级别的 Change Stream 使用固定的 token key: "database_changestream"
     */
    @GetMapping("/resume-token")
    public Map<String, Object> getResumeToken() {
        Map<String, Object> response = new HashMap<>();

        try {
            String tokenKey = "resume_token_database_changestream";
            org.bson.Document tokenDoc = mongoTemplate.findById(
                    tokenKey,
                    org.bson.Document.class,
                    "change_stream_resume_tokens"
            );

            if (tokenDoc != null) {
                response.put("success", true);
                response.put("tokenKey", tokenKey);
                response.put("hasToken", true);
                response.put("updateTime", tokenDoc.get("updateTime"));
                response.put("mode", "database-level");
                response.put("watchPattern", "message_*");
                // 不返回实际 token，只返回是否存在
            } else {
                response.put("success", true);
                response.put("tokenKey", tokenKey);
                response.put("hasToken", false);
                response.put("mode", "database-level");
                response.put("watchPattern", "message_*");
            }

        } catch (Exception e) {
            log.error("Error getting resume token", e);
            response.put("success", false);
            response.put("error", e.getMessage());
        }

        return response;
    }

    /**
     * 清除 Resume Token（强制从头开始监听）
     * DELETE /api/mongo/changestream/resume-token
     *
     * Database 级别的 Change Stream 使用固定的 token key: "database_changestream"
     */
    @DeleteMapping("/resume-token")
    public Map<String, Object> clearResumeToken() {
        Map<String, Object> response = new HashMap<>();

        try {
            String tokenKey = "resume_token_database_changestream";
            mongoTemplate.remove(
                    new org.springframework.data.mongodb.core.query.Query(
                            org.springframework.data.mongodb.core.query.Criteria.where("_id")
                                    .is(tokenKey)
                    ),
                    "change_stream_resume_tokens"
            );

            response.put("success", true);
            response.put("message", "Resume token cleared for database-level Change Stream");
            response.put("tokenKey", tokenKey);
            response.put("mode", "database-level");
            response.put("watchPattern", "message_*");

            log.info("Resume token cleared for database-level Change Stream");

        } catch (Exception e) {
            log.error("Error clearing resume token", e);
            response.put("success", false);
            response.put("error", e.getMessage());
        }

        return response;
    }
}
