package io.bluemacaw.mongodb.controller;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * MongoDB Change Stream管理控制器（单节点模式）
 */
@Slf4j
@RestController
@RequestMapping("/api/mongo/changestream")
@ConditionalOnProperty(prefix = "mongodb.change-stream", name = "enabled", havingValue = "true")
public class MongoChangeStreamController {

    @Resource
    private MongoTemplate mongoTemplate;

    /**
     * 获取Change Stream状态
     * GET /api/mongo/changestream/status
     */
    @GetMapping("/status")
    public Map<String, Object> getStatus() {
        Map<String, Object> response = new HashMap<>();

        try {
            // 检查MongoDB副本集状态
            org.bson.Document isMaster = mongoTemplate.executeCommand(new org.bson.Document("isMaster", 1));

            boolean isReplicaSet = isMaster.containsKey("setName");
            String setName = isReplicaSet ? isMaster.getString("setName") : null;

            response.put("success", true);
            response.put("enabled", true);
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
     * 获取Resume Token信息
     * GET /api/mongo/changestream/resume-token?collection=message
     */
    @GetMapping("/resume-token")
    public Map<String, Object> getResumeToken(
            @RequestParam(defaultValue = "message") String collection) {

        Map<String, Object> response = new HashMap<>();

        try {
            org.bson.Document tokenDoc = mongoTemplate.findById(
                "resume_token_" + collection,
                org.bson.Document.class,
                "change_stream_resume_tokens"
            );

            if (tokenDoc != null) {
                response.put("success", true);
                response.put("collection", collection);
                response.put("hasToken", true);
                response.put("updateTime", tokenDoc.get("updateTime"));
                // 不返回实际token，只返回是否存在
            } else {
                response.put("success", true);
                response.put("collection", collection);
                response.put("hasToken", false);
            }

        } catch (Exception e) {
            log.error("Error getting resume token", e);
            response.put("success", false);
            response.put("error", e.getMessage());
        }

        return response;
    }

    /**
     * 清除Resume Token（强制从头开始监听）
     * DELETE /api/mongo/changestream/resume-token?collection=message
     */
    @DeleteMapping("/resume-token")
    public Map<String, Object> clearResumeToken(
            @RequestParam(defaultValue = "message") String collection) {

        Map<String, Object> response = new HashMap<>();

        try {
            mongoTemplate.remove(
                new org.springframework.data.mongodb.core.query.Query(
                    org.springframework.data.mongodb.core.query.Criteria.where("_id")
                        .is("resume_token_" + collection)
                ),
                "change_stream_resume_tokens"
            );

            response.put("success", true);
            response.put("message", "Resume token cleared for collection: " + collection);
            response.put("collection", collection);

            log.info("Resume token cleared for collection: {}", collection);

        } catch (Exception e) {
            log.error("Error clearing resume token", e);
            response.put("success", false);
            response.put("error", e.getMessage());
        }

        return response;
    }

    /**
     * 测试插入数据（用于测试Change Stream）
     * POST /api/mongo/changestream/test-insert
     */
    @PostMapping("/test-insert")
    public Map<String, Object> testInsert() {
        Map<String, Object> response = new HashMap<>();

        try {
            long timestamp = System.currentTimeMillis();
            org.bson.Document testDoc = new org.bson.Document();
            testDoc.put("msgId", "test_" + timestamp);
            testDoc.put("fromId", 999999L);
            testDoc.put("contactId", 888888L);
            testDoc.put("contactType", 0);
            testDoc.put("fromCompanyId", "test_company");
            testDoc.put("fromCompany", "Test Company");
            testDoc.put("contactCompanyId", "");
            testDoc.put("contactCompany", "");
            testDoc.put("oldMsgId", "");
            testDoc.put("msgType", 0);
            testDoc.put("msgTime", String.valueOf(timestamp));
            testDoc.put("deleted", 0);
            testDoc.put("status", 0);
            testDoc.put("content", "This is a test message for Change Stream - " + timestamp);
            testDoc.put("contentVersion", 1);
            testDoc.put("clientMsgId", "test_client_" + timestamp);
            testDoc.put("clientInfo", "Test Client");
            testDoc.put("createTime", new java.util.Date());
            testDoc.put("updateTime", new java.util.Date());

            mongoTemplate.insert(testDoc, "message");

            response.put("success", true);
            response.put("message", "Test document inserted successfully. Change Stream should capture this insert if it's currently running.");
            response.put("msgId", testDoc.getString("msgId"));
            response.put("tip", "Check the application logs for 'Received Change Stream event' message");

            log.info("=== TEST INSERT === Document inserted: {} at {}",
                testDoc.getString("msgId"),
                new java.text.SimpleDateFormat("HH:mm:ss.SSS").format(new java.util.Date()));

        } catch (Exception e) {
            log.error("Error inserting test document", e);
            response.put("success", false);
            response.put("error", e.getMessage());
        }

        return response;
    }
}
