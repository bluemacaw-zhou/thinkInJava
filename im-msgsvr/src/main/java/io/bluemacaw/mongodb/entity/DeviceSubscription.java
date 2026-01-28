package io.bluemacaw.mongodb.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;

/**
 * DeviceSubscription实体 - 设备订阅
 * 记录设备级别的同步进度和已读进度
 */
@Document("device_subscription")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DeviceSubscription {
    @Id
    private String id;

    /**
     * 设备唯一标识
     */
    @Field("device_id")
    private String deviceId;

    /**
     * 用户订阅ID(外键)
     */
    @Field("user_subscription_id")
    private String userSubscriptionId;

    /**
     * 用户ID(冗余,避免JOIN)
     */
    @Field("user_id")
    private Long userId;

    /**
     * 频道ID(冗余,避免JOIN)
     */
    @Field("channel_id")
    private String channelId;

    /**
     * 设备级已读版本号
     * 设备上报的已读进度
     */
    @Field("last_read_version")
    private Long lastReadVersion;

    /**
     * 设备级同步版本号
     * 设备已拉取并存储的消息版本
     */
    @Field("last_sync_version")
    private Long lastSyncVersion;

    /**
     * 用户离开时的消息版本号(冗余)
     * 继承自UserSubscription
     * null表示仍在频道中
     */
    @Field("leave_version")
    private Long leaveVersion;

    /**
     * 用户离开时间(冗余)
     * 继承自UserSubscription
     * null表示仍在频道中
     */
    @Field("leave_time")
    private LocalDateTime leaveTime;

    /**
     * 创建时间
     */
    @Field("create_time")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @Field("update_time")
    private LocalDateTime updateTime;
}
