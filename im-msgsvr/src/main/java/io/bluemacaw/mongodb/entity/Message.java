package io.bluemacaw.mongodb.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;

/**
 * Message实体 - 消息
 * 按月分collection存储: messages_YYYYMM
 * 使用MongoDB自动生成的ObjectId作为主键
 *
 * 注意: 不使用固定的@Document collection名称
 * 而是在保存/查询时通过MongoTemplate动态指定collection
 * @author shzhou.michael
 */
@Document
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Message {
    /**
     * 消息ID - MongoDB自动生成的ObjectId
     */
    @Id
    private String id;

    /**
     * 频道ID
     * 群聊: group_id
     * 私聊: 由from_id和to_id推导
     */
    @Field("channel_id")
    private String channelId;

    /**
     * 消息序号 = Channel.message_version
     * 频道内单调递增,用于消息排序和增量拉取
     */
    @Field("seq")
    private Long seq;

    /**
     * 现行的消息唯一ID(兼容旧系统)
     */
    @Field("old_msg_id")
    private String oldMsgId;

    /**
     * 发送者ID
     */
    @Field("from_id")
    private Long fromId;

    /**
     * 接收者ID(私聊场景,群聊为null)
     */
    @Field("to_id")
    private Long toId;

    /**
     * 联系人类型
     * PRIVATE(0) - 私聊
     * GROUP(1) - 群聊
     */
    @Field("contact_type")
    private Integer contactType;

    /**
     * 发送者公司ID(快照)
     */
    @Field("from_company_id")
    private String fromCompanyId;

    /**
     * 发送者公司名称(快照)
     */
    @Field("from_company")
    private String fromCompany;

    /**
     * 接收者公司ID(私聊场景,群聊为null)
     */
    @Field("to_company_id")
    private String toCompanyId;

    /**
     * 接收者公司名称(私聊场景,群聊为null)
     */
    @Field("to_company")
    private String toCompany;

    /**
     * 消息类型
     */
    @Field("msg_type")
    private Integer msgType;

    /**
     * 消息内容
     */
    @Field("content")
    private String content;

    /**
     * 消息协议版本
     */
    @Field("content_version")
    private Integer contentVersion;

    /**
     * 消息时间(毫秒级)
     */
    @Field("msg_time")
    private LocalDateTime msgTime;

    /**
     * 客户端消息ID(用于去重)
     */
    @Field("client_msg_id")
    private String clientMsgId;

    /**
     * 客户端信息
     */
    @Field("client_info")
    private String clientInfo;

    /**
     * 删除标记
     */
    @Field("deleted")
    private Integer deleted;

    /**
     * 消息状态(包含撤回状态)
     */
    @Field("status")
    private Integer status;

    /**
     * 创建时间
     */
    @Field("create_time")
    private LocalDateTime createTime;

    /**
     * 更新时间(撤回时会更新)
     */
    @Field("update_time")
    private LocalDateTime updateTime;

    /**
     * 动态获取collection名称
     * 格式: messages_YYYYMM
     */
    public String getCollectionName() {
        if (createTime == null) {
            return "messages_" + java.time.YearMonth.now().toString().replace("-", "");
        }
        int year = createTime.getYear();
        int month = createTime.getMonthValue();
        return String.format("messages_%04d%02d", year, month);
    }
}
