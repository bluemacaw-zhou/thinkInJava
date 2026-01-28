package io.bluemacaw.mongodb.entity.mq;

import io.bluemacaw.mongodb.entity.Message;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Change Stream Message 集合变更事件（用于 MQ 传输）
 *
 * 单条 MQ 消息最多包含 1000 条实际的 Message 记录
 *
 * @author shzhou.michael
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChangeStreamMessageEvent {

    /**
     * 操作类型：insert/update/delete/replace
     */
    private String operationType;

    /**
     * 批量消息列表（最多 1000 条）
     */
    private List<Message> messages;

    /**
     * 事件发生时间戳
     */
    private Long timestamp;

    /**
     * 来源 collection 名称（例如：message_202601）
     */
    private String collectionName;
}
