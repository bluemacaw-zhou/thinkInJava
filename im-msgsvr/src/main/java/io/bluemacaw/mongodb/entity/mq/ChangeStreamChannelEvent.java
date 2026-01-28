package io.bluemacaw.mongodb.entity.mq;

import io.bluemacaw.mongodb.entity.Channel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Change Stream Channel 集合变更事件（用于 MQ 传输）
 *
 * @author shzhou.michael
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChangeStreamChannelEvent {

    /**
     * 操作类型：insert/update/delete/replace
     */
    private String operationType;

    /**
     * 变更的 Channel 列表
     */
    private List<Channel> channels;

    /**
     * 事件发生时间戳
     */
    private Long timestamp;
}
