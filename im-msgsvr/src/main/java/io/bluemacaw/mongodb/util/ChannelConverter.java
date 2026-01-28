package io.bluemacaw.mongodb.util;

import io.bluemacaw.mongodb.entity.Channel;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Slf4j
public class ChannelConverter {
    /**
     * 将 MongoDB Document 转换为 Channel 实体
     */
    public static Channel documentToChannel(Document doc) {
        try {
            Channel channel = new Channel();

            channel.setId(doc.getString("_id"));
            channel.setChannelType(doc.getInteger("channel_type"));
            channel.setMessageVersion(doc.getLong("message_version"));

            // 处理时间字段
            Object createTimeObj = doc.get("create_time");
            if (createTimeObj instanceof java.util.Date createDate) {
                channel.setCreateTime(LocalDateTime.ofInstant(createDate.toInstant(), ZoneId.systemDefault()));
            }

            // 处理 update_time
            Object updateTimeObj = doc.get("update_time");
            if (updateTimeObj instanceof java.util.Date updateDate) {
                channel.setUpdateTime(LocalDateTime.ofInstant(updateDate.toInstant(), ZoneId.systemDefault()));
            }

            return channel;
        } catch (Exception e) {
            log.error("Error converting Document to Channel", e);
            return null;
        }
    }
}
