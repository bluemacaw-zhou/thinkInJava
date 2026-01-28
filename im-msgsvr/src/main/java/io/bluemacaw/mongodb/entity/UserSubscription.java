package io.bluemacaw.mongodb.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;

/**
 * UserSubscription实体 - 用户订阅频道
 * 记录用户级别的已读进度(跨设备共享)和可见性范围
 * @author shzhou.michael
 */
@Document("user_subscription")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserSubscription {
    @Id
    private String id;

    /**
     * 用户ID
     */
    @Field("user_id")
    private Long userId;

    /**
     * 频道ID
     */
    @Field("channel_id")
    private String channelId;

    /**
     * 频道类型(冗余,避免JOIN)
     * direct/private/group
     */
    @Field("channel_type")
    private int channelType;

    /**
     * 用户级已读版本号(跨设备共享)
     * 客户端上报的已读进度
     */
    @Field("last_read_version")
    private Long lastReadVersion;

    /**
     * 最后已读时间(用于查询撤回消息)
     */
    @Field("last_read_time")
    private LocalDateTime lastReadTime;

    /**
     * 加入时的消息版本号(可见性起点)
     * 用户加入时 = Channel.message_version
     */
    @Field("join_version")
    private Long joinVersion;

    /**
     * 加入时间
     */
    @Field("join_time")
    private LocalDateTime joinTime;

    /**
     * 离开时的消息版本号(可见性终点)
     * null表示仍在频道中
     */
    @Field("leave_version")
    private Long leaveVersion;

    /**
     * 退出时间
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
