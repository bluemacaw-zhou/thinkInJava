package io.bluemacaw.mongodb.service;

import io.bluemacaw.mongodb.entity.MsgAnalysisData;
import io.bluemacaw.mongodb.entity.MsgData;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * MongoDB Change Stream消息处理服务
 */
@Slf4j
@Service
public class MongoChangeStreamService {

    @Resource
    private ClickHouseMessageProducer clickHouseProducer;

    /**
     * 处理MongoDB Insert事件
     *
     * @param document 插入的文档
     */
    public void handleInsert(Document document) {
        try {
            log.debug("Insert event received, id: {}", document.get("_id"));

            // 将Document转换为MsgData
            MsgData msgData = documentToMsgData(document);

            if (msgData == null) {
                log.warn("Document conversion failed, skipping");
                return;
            }

            // 转换为MsgAnalysisData
            MsgAnalysisData analysisData = MsgAnalysisDataConverter.convert(msgData);

            if (analysisData != null) {
                // 发送到ClickHouse队列
                boolean success = clickHouseProducer.sendMessage(analysisData);

                if (!success) {
                    log.error("Failed to send to ClickHouse queue, msgId: {}", msgData.getMsgId());
                }
            } else {
                log.warn("Analysis data conversion failed, msgId: {}", msgData.getMsgId());
            }

        } catch (Exception e) {
            log.error("Insert event handling error", e);
        }
    }

    /**
     * 处理MongoDB Update事件
     *
     * @param documentKey 文档键
     * @param updateDescription 更新描述
     */
    public void handleUpdate(Document documentKey, Document updateDescription) {
        try {
            log.debug("Update event received, id: {}", documentKey.get("_id"));

            // 可以根据业务需求处理更新事件
            // 例如：记录审计日志、触发其他业务逻辑等

        } catch (Exception e) {
            log.error("Update event handling error", e);
        }
    }

    /**
     * 处理MongoDB Delete事件
     *
     * @param documentKey 文档键
     */
    public void handleDelete(Document documentKey) {
        try {
            log.debug("Delete event received, id: {}", documentKey.get("_id"));

            // 可以根据业务需求处理删除事件
            // 例如：记录删除日志、同步删除ClickHouse数据等

        } catch (Exception e) {
            log.error("Delete event handling error", e);
        }
    }

    /**
     * 处理MongoDB Replace事件
     *
     * @param document 替换后的文档
     */
    public void handleReplace(Document document) {
        try {
            log.debug("Replace event received, id: {}", document.get("_id"));

            // 可以根据业务需求处理替换事件

        } catch (Exception e) {
            log.error("Replace event handling error", e);
        }
    }

    /**
     * 将MongoDB Document转换为MsgData
     */
    private MsgData documentToMsgData(Document doc) {
        try {
            MsgData msgData = new MsgData();

            msgData.setMsgId(doc.getString("msgId"));
            msgData.setFromId(doc.getLong("fromId"));
            msgData.setContactId(doc.getLong("contactId"));
            msgData.setContactType(doc.getInteger("contactType"));
            msgData.setFromCompanyId(doc.getString("fromCompanyId"));
            msgData.setFromCompany(doc.getString("fromCompany"));
            msgData.setContactCompanyId(doc.getString("contactCompanyId"));
            msgData.setContactCompany(doc.getString("contactCompany"));
            msgData.setOldMsgId(doc.getString("oldMsgId"));
            msgData.setMsgType(doc.getInteger("msgType"));
            msgData.setMsgTime(doc.getString("msgTime"));
            msgData.setDeleted(doc.getInteger("deleted"));
            msgData.setStatus(doc.getInteger("status"));
            msgData.setContent(doc.getString("content"));
            msgData.setContentVersion(doc.getInteger("contentVersion"));
            msgData.setClientMsgId(doc.getString("clientMsgId"));
            msgData.setClientInfo(doc.getString("clientInfo"));

            // 处理时间字段
            Object createTimeObj = doc.get("createTime");
            if (createTimeObj instanceof java.util.Date) {
                java.util.Date createDate = (java.util.Date) createTimeObj;
                msgData.setCreateTime(LocalDateTime.ofInstant(createDate.toInstant(), java.time.ZoneId.systemDefault()));
            }

            Object updateTimeObj = doc.get("updateTime");
            if (updateTimeObj instanceof java.util.Date) {
                java.util.Date updateDate = (java.util.Date) updateTimeObj;
                msgData.setUpdateTime(LocalDateTime.ofInstant(updateDate.toInstant(), java.time.ZoneId.systemDefault()));
            }

            return msgData;

        } catch (Exception e) {
            log.error("Error converting Document to MsgData", e);
            return null;
        }
    }

    /**
     * 保存Resume Token
     */
    public void saveResumeToken(String resumeToken, MongoTemplate mongoTemplate, String collectionName) {
        try {
            Document tokenDoc = new Document();
            tokenDoc.put("_id", "resume_token_" + collectionName);
            tokenDoc.put("token", resumeToken);
            tokenDoc.put("updateTime", new java.util.Date());

            mongoTemplate.save(tokenDoc, "change_stream_resume_tokens");

            log.debug("Resume token saved: {}", collectionName);

        } catch (Exception e) {
            log.error("Resume token save error", e);
        }
    }

    /**
     * 获取Resume Token
     */
    public String getResumeToken(MongoTemplate mongoTemplate, String collectionName) {
        try {
            Document tokenDoc = mongoTemplate.findById(
                "resume_token_" + collectionName,
                Document.class,
                "change_stream_resume_tokens"
            );

            if (tokenDoc != null) {
                String token = tokenDoc.getString("token");
                log.debug("Resume token loaded: {}", collectionName);
                return token;
            }

        } catch (Exception e) {
            log.error("Resume token load error", e);
        }

        return null;
    }
}
