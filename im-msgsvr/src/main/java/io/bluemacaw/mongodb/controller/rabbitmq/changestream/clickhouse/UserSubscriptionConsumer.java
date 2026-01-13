package io.bluemacaw.mongodb.controller.rabbitmq.changeStream.clickhouse;

import com.alibaba.fastjson.JSON;
import com.rabbitmq.client.Channel;
import io.bluemacaw.mongodb.entity.mq.ChangeStreamUserSubscriptionEvent;
import io.bluemacaw.mongodb.enums.ChangeStreamOperationType;
import io.bluemacaw.mongodb.service.UserSubscriptionService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Change Stream UserSubscription 事件消费者 - ClickHouse
 *
 * 消费 UserSubscription 集合的变更事件，同步到 ClickHouse
 *
 * @author shzhou.michael
 */
@Slf4j
@Component
public class UserSubscriptionConsumer {

    @Resource
    private UserSubscriptionService userSubscriptionService;

    /**
     * 消费 UserSubscription 变更事件
     */
    @RabbitListener(
        queues = "${spring.rabbitmq.queueChangeStreamUserSubClickHouse}",
        concurrency = "1"
    )
    public void consumeUserSubscriptionEvent(Message message, Channel channel) throws Exception {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        byte[] body = message.getBody();
        String content = new String(body, StandardCharsets.UTF_8);
        boolean success = false;

        try {
            ChangeStreamUserSubscriptionEvent event = JSON.parseObject(content, ChangeStreamUserSubscriptionEvent.class);

            if (event == null || event.getUserSubscriptions() == null || event.getUserSubscriptions().isEmpty()) {
                log.warn("ClickHouse - Received empty ChangeStreamUserSubscriptionEvent");
                success = true;
                return;
            }

//            log.info("ClickHouse - ChangeStream UserSubscription event: operation={}, count={}",
//                    event.getOperationType(), event.getUserSubscriptions().size());

            // 根据操作类型处理
            ChangeStreamOperationType operationType = ChangeStreamOperationType.fromValue(event.getOperationType());
            if (operationType == ChangeStreamOperationType.INSERT) {
                userSubscriptionService.batchInsertUserSubscriptionToClickHouse(event.getUserSubscriptions());
//                log.info("ClickHouse - Synced {} user subscriptions", event.getUserSubscriptions().size());
            }
            
            success = true;

        } catch (Exception e) {
            log.error("ClickHouse - Failed to consume ChangeStreamUserSubscriptionEvent, deliveryTag: {}", deliveryTag, e);
        } finally {
            // 确保消息只被确认一次
            try {
                if (success) {
                    channel.basicAck(deliveryTag, false);
                } else {
                    channel.basicNack(deliveryTag, false, true);
                }
            } catch (Exception ackException) {
                log.error("ClickHouse - Failed to ack/nack UserSubscription message, deliveryTag: {}", deliveryTag, ackException);
            }
        }
    }
}
