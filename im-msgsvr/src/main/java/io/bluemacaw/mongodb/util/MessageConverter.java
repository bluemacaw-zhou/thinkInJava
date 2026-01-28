package io.bluemacaw.mongodb.util;

import io.bluemacaw.mongodb.entity.Message;
import io.bluemacaw.mongodb.entity.mq.MqMessage;
import io.bluemacaw.mongodb.entity.mq.MqMessageData;
import io.bluemacaw.mongodb.enums.ChannelType;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 消息转换工具类
 * 提供各种数据源到 Message 实体的转换
 *
 * @author shzhou.michael
 */
@Slf4j
public class MessageConverter {

    /**
     * 将 MongoDB Document 转换为 Message 实体
     *
     * @param doc MongoDB Document
     * @return Message 实体
     */
    public static Message documentToMessage(Document doc) {
        try {
            Message message = new Message();

            // 处理 _id（ObjectId 转 String）
            Object idObj = doc.get("_id");
            if (idObj != null) {
                message.setId(idObj.toString());
            }

            message.setChannelId(doc.getString("channel_id"));
            message.setSeq(doc.getLong("seq"));
            message.setOldMsgId(doc.getString("old_msg_id"));
            message.setFromId(doc.getLong("from_id"));
            message.setToId(doc.getLong("to_id"));
            message.setContactType(doc.getInteger("contact_type"));
            message.setFromCompanyId(doc.getString("from_company_id"));
            message.setFromCompany(doc.getString("from_company"));
            message.setToCompanyId(doc.getString("to_company_id"));
            message.setToCompany(doc.getString("to_company"));
            message.setMsgType(doc.getInteger("msg_type"));
            message.setContent(doc.getString("content"));
            message.setContentVersion(doc.getInteger("content_version"));

            // 处理 msg_time
            Object msgTimeObj = doc.get("msg_time");
            if (msgTimeObj instanceof java.util.Date msgTime) {
                message.setMsgTime(LocalDateTime.ofInstant(msgTime.toInstant(), ZoneId.systemDefault()));
            }

            message.setClientMsgId(doc.getString("client_msg_id"));
            message.setClientInfo(doc.getString("client_info"));
            message.setDeleted(doc.getInteger("deleted"));
            message.setStatus(doc.getInteger("status"));

            // 处理 create_time
            Object createTimeObj = doc.get("create_time");
            if (createTimeObj instanceof java.util.Date createDate) {
                message.setCreateTime(LocalDateTime.ofInstant(createDate.toInstant(), ZoneId.systemDefault()));
            }

            // 处理 update_time
            Object updateTimeObj = doc.get("update_time");
            if (updateTimeObj instanceof java.util.Date updateDate) {
                message.setUpdateTime(LocalDateTime.ofInstant(updateDate.toInstant(), ZoneId.systemDefault()));
            }

            return message;

        } catch (Exception e) {
            log.error("Error converting Document to Message", e);
            return null;
        }
    }

    /**
     * 将 MqMsgItem 转换为 Message 实体
     * 用于从 RabbitMQ 接收的消息转换为 MongoDB 存储格式
     *
     * @param mqMessage MQ 消息项
     * @param channelId 频道ID
     * @param seq       消息序号
     * @return Message 实体
     */
    public static Message mqMsgItemToMessage(MqMessage mqMessage, String channelId, Long seq) {
        if (mqMessage == null || mqMessage.getMqMessageData() == null) {
            log.warn("MqMsgItem or message data is null");
            return null;
        }

        try {
            MqMessageData mqMessageData = mqMessage.getMqMessageData();
            Message message = new Message();

            // 设置主键和频道信息
            message.setChannelId(channelId);
            message.setSeq(seq);
            message.setOldMsgId(mqMessageData.getOldMsgId());

            // 设置发送方信息
            message.setFromId(mqMessageData.getFromId());
            message.setFromCompanyId(mqMessageData.getFromCompanyId());
            message.setFromCompany(mqMessageData.getFromCompany());

            // 设置接收方信息
            // contactType: PRIVATE=私聊, GROUP=群聊
            message.setContactType(mqMessageData.getContactType());
            if (mqMessageData.getContactType() == ChannelType.PRIVATE.getCode()) {
                // 私聊场景：contactId 是接收者ID
                message.setToId(mqMessageData.getContactId());
                message.setToCompanyId(mqMessageData.getContactCompanyId());
                message.setToCompany(mqMessageData.getContactCompany());
            } else {
                // 群聊场景：toId 为 null
                message.setToId(null);
                message.setToCompanyId(null);
                message.setToCompany(null);
            }

            // 设置消息内容
            message.setMsgType(mqMessageData.getMsgType());
            message.setContent(mqMessageData.getContent());
            message.setContentVersion(mqMessageData.getContentVersion());

            // 解析消息时间（msgTime 是时间戳字符串）
            if (mqMessageData.getMsgTime() != null && !mqMessageData.getMsgTime().isEmpty()) {
                try {
                    long timestamp = Long.parseLong(mqMessageData.getMsgTime());
                    message.setMsgTime(LocalDateTime.ofInstant(
                            Instant.ofEpochMilli(timestamp),
                            ZoneId.systemDefault()
                    ));
                } catch (NumberFormatException e) {
                    log.warn("Invalid msgTime format: {}", mqMessageData.getMsgTime());
                    message.setMsgTime(LocalDateTime.now());
                }
            } else {
                message.setMsgTime(LocalDateTime.now());
            }

            // 设置客户端信息
            message.setClientMsgId(mqMessageData.getClientMsgId());
            message.setClientInfo(mqMessageData.getClientInfo());

            // 设置状态字段
            message.setDeleted(mqMessageData.getDeleted() != null ? mqMessageData.getDeleted() : 0);
            message.setStatus(mqMessageData.getStatus() != null ? mqMessageData.getStatus() : 0);

            // 设置创建和更新时间
            LocalDateTime now = LocalDateTime.now();
            message.setCreateTime(now);
            message.setUpdateTime(now);

            log.debug("Successfully converted MqMsgItem to Message, oldMsgId: {}, channelId: {}, seq: {}",
                    mqMessageData.getOldMsgId(), channelId, seq);

            return message;

        } catch (Exception e) {
            log.error("Error converting MqMsgItem to Message, oldMsgId: {}",
                    mqMessage.getMqMessageData().getOldMsgId(), e);
            return null;
        }
    }

    /**
     * 将 ResultSet 转换为 Message 对象
     * 用于从 ClickHouse 查询结果转换为 Message 实体
     *
     * @param rs ResultSet
     * @return Message 实体
     * @throws SQLException SQL异常
     */
    public static Message resultSetToMessage(ResultSet rs) throws SQLException {
        Message message = new Message();
        message.setId(rs.getString("id"));
        message.setChannelId(rs.getString("channelId"));
        message.setSeq(rs.getLong("seq"));
        message.setOldMsgId(rs.getString("oldMsgId"));
        message.setFromId(rs.getLong("fromId"));
        message.setFromCompanyId(rs.getString("fromCompanyId"));
        message.setFromCompany(rs.getString("fromCompany"));
        message.setToId(rs.getLong("toId"));
        message.setToCompanyId(rs.getString("toCompanyId"));
        message.setToCompany(rs.getString("toCompany"));
        message.setContactType(rs.getInt("contactType"));
        message.setMsgType(rs.getInt("msgType"));
        message.setContent(rs.getString("content"));
        message.setContentVersion(rs.getInt("contentVersion"));
        message.setMsgTime(rs.getTimestamp("msgTime").toLocalDateTime());
        message.setClientMsgId(rs.getString("clientMsgId"));
        message.setClientInfo(rs.getString("clientInfo"));
        message.setDeleted(rs.getInt("deleted"));
        message.setStatus(rs.getInt("status"));
        return message;
    }
}
