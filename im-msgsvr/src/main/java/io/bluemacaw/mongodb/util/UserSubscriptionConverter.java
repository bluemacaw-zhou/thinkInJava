package io.bluemacaw.mongodb.util;

import io.bluemacaw.mongodb.entity.UserSubscription;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Slf4j
public class UserSubscriptionConverter {
    /**
     * 将 MongoDB Document 转换为 UserSubscription 实体
     */
    public static UserSubscription documentToUserSubscription(Document doc) {
        try {
            UserSubscription userSub = new UserSubscription();

            userSub.setId(doc.getObjectId("_id").toString());
            userSub.setUserId(doc.getLong("user_id"));
            userSub.setChannelId(doc.getString("channel_id"));
            userSub.setChannelType(doc.getInteger("channel_type"));
            
            // 必需字段 - 不能为null
            userSub.setJoinVersion(doc.getLong("join_version"));
            userSub.setJoinTime(doc.getDate("join_time").toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime());

            // 可选字段 - 可以为null
            if (doc.containsKey("last_read_version") && doc.get("last_read_version") != null) {
                userSub.setLastReadVersion(doc.getLong("last_read_version"));
            }
            
            if (doc.getDate("last_read_time") != null) {
                userSub.setLastReadTime(doc.getDate("last_read_time").toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime());
            }
            
            if (doc.containsKey("leave_version") && doc.get("leave_version") != null) {
                userSub.setLeaveVersion(doc.getLong("leave_version"));
            }
            
            if (doc.getDate("leave_time") != null) {
                userSub.setLeaveTime(doc.getDate("leave_time").toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime());
            }

            // 时间字段由ClickHouse自动管理，不需要从MongoDB同步
            Object createTimeObj = doc.get("create_time");
            if (createTimeObj instanceof java.util.Date createDate) {
                userSub.setCreateTime(LocalDateTime.ofInstant(createDate.toInstant(), ZoneId.systemDefault()));
            }

            // 处理 update_time
            Object updateTimeObj = doc.get("update_time");
            if (updateTimeObj instanceof java.util.Date updateDate) {
                userSub.setUpdateTime(LocalDateTime.ofInstant(updateDate.toInstant(), ZoneId.systemDefault()));
            }

            return userSub;
        } catch (Exception e) {
            log.error("Error converting Document to UserSubscription", e);
            return null;
        }
    }
}
