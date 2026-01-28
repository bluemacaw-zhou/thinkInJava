package io.bluemacaw.mongodb.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;

/**
 * Channel实体 - 对应设计文档中的Channel
 * 群聊场景: channel_id = group_id
 * 私聊场景: channel_id 由 from_id 和 to_id 推导生成 (小id_大id)
 */
@Document("channel")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Channel {
    /**
     * 频道ID
     * 私聊: userId1_userId2 (小ID在前)
     * 群聊: group_groupId
     */
    @Id
    private String id;

    /**
     * 频道类型
     * PRIVATE(0) - 私聊
     * GROUP(1) - 群聊
     */
    @Field("channel_type")
    private Integer channelType;

    /**
     * 消息版本号
     * 每条新消息时 +1
     * 用于增量拉取消息和消息排序
     */
    @Field("message_version")
    private Long messageVersion;

    /**
     * 创建时间
     */
    @Field("create_time")
    private LocalDateTime createTime;

    /**
     * 更新时间 - 最新消息时间,用于频道排序
     */
    @Field("update_time")
    private LocalDateTime updateTime;

    /**
     * 静态方法：根据两个ID生成 sessionId
     */
    public static String generateChannelId(long id1, long id2) {
        if (id1 < id2) {
            return id1 + "_" + id2;
        } else {
            return id2 + "_" + id1;
        }
    }
}
