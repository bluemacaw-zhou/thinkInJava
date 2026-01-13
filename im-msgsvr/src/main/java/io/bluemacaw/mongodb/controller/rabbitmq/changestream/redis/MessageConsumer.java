package io.bluemacaw.mongodb.controller.rabbitmq.changeStream.redis;

import com.alibaba.fastjson.JSON;
import com.rabbitmq.client.Channel;
import io.bluemacaw.mongodb.entity.mq.ChangeStreamMessageEvent;
import io.bluemacaw.mongodb.enums.ChangeStreamOperationType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Change Stream Message 事件消费者 - Redis
 *
 * 消费 Message 集合的变更事件，同步到 Redis 缓存
 * 主要用于：
 * - 缓存最新消息
 * - 维护频道最后消息
 * - 更新消息状态等
 *
 * @author shzhou.michael
 */
@Slf4j
@Component("redisMessageConsumer")
public class MessageConsumer {

    @RabbitListener(
        queues = "${spring.rabbitmq.queueChangeStreamMessageRedis}",
        concurrency = "1"
    )
    public void consumeMessageEvent(Message message, Channel channel) throws Exception {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        byte[] body = message.getBody();
        String content = new String(body, StandardCharsets.UTF_8);
        boolean success = false;

        try {
            ChangeStreamMessageEvent event = JSON.parseObject(content, ChangeStreamMessageEvent.class);

            if (event == null || event.getMessages() == null || event.getMessages().isEmpty()) {
                log.warn("Redis - Received empty ChangeStreamMessageEvent");
                success = true;
                return;
            }

//            log.info("Redis - ChangeStream Message event: operation={}, count={}",
//                    event.getOperationType(), event.getMessages().size());

            // 根据操作类型处理
            ChangeStreamOperationType operationType = ChangeStreamOperationType.fromValue(event.getOperationType());
            if (operationType == ChangeStreamOperationType.INSERT || operationType == ChangeStreamOperationType.REPLACE) {
                // TODO: 实现 Redis 缓存逻辑
                // 例如：
                // 1. 缓存最新消息到 Redis
                // 2. 更新频道最后消息
                // 3. 维护消息索引
//                log.info("Redis - TODO: Cache {} messages", event.getMessages().size());
            }
            
            success = true;

        } catch (Exception e) {
            log.error("Redis - Failed to consume ChangeStreamMessageEvent, deliveryTag: {}", deliveryTag, e);
        } finally {
            // 确保消息只被确认一次
            try {
                if (success) {
                    channel.basicAck(deliveryTag, false);
                } else {
                    channel.basicNack(deliveryTag, false, true);
                }
            } catch (Exception ackException) {
                log.error("Redis - Failed to ack/nack message, deliveryTag: {}", deliveryTag, ackException);
            }
        }
    }
}
