package io.bluemacaw.mongodb.entity.mq;

import io.bluemacaw.mongodb.entity.UserSubscription;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Change Stream UserSubscription 集合变更事件（用于 MQ 传输）
 *
 * @author shzhou.michael
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChangeStreamUserSubscriptionEvent {

    /**
     * 操作类型：insert/update/delete/replace
     */
    private String operationType;

    /**
     * 变更的 UserSubscription 列表
     */
    private List<UserSubscription> userSubscriptions;

    /**
     * 事件发生时间戳
     */
    private Long timestamp;
}
