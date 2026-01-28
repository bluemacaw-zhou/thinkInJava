package io.bluemacaw.mongodb.entity.mq;

import lombok.Data;
import java.util.List;

/**
 * 聚合消息MQ数据结构
 * 用于按channelId聚合后的消息批量发送
 */
@Data
public class MqAggregatedMessageData {
    /**
     * 频道ID
     */
    private String channelId;

    /**
     * 频道类型
     */
    private int channelType;

    /**
     * 消息日期（yyyy-MM-dd）
     */
    private String messageDate;

    /**
     * 该频道当天的消息列表
     */
    private List<MqMessage> messages;

    /**
     * 消息总数
     */
    private int messageCount;
}
