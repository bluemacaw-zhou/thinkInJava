package io.bluemacaw.mongodb.controller.rabbitmq.changeStream.clickhouse;

import com.alibaba.fastjson.JSON;
import com.rabbitmq.client.Channel;
import io.bluemacaw.mongodb.entity.mq.ChangeStreamMessageEvent;
import io.bluemacaw.mongodb.enums.ChangeStreamOperationType;
import io.bluemacaw.mongodb.service.MessageService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Change Stream Message 事件消费者 - ClickHouse
 *
 * 消费 Message 集合的变更事件，批量同步到 ClickHouse
 *
 * @author shzhou.michael
 */
@Slf4j
@Component("clickHouseMessageConsumer")
public class MessageConsumer {

    @Resource
    private MessageService messageService;

    @RabbitListener(
        queues = "${spring.rabbitmq.queueChangeStreamMessageClickHouse}",
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
                log.warn("ClickHouse - Received empty ChangeStreamMessageEvent");
                success = true;
                return;
            }

//            log.info("ClickHouse - ChangeStream Message event: operation={}, count={}",
//                    event.getOperationType(), event.getMessages().size());

            // 根据操作类型处理
            ChangeStreamOperationType operationType = ChangeStreamOperationType.fromValue(event.getOperationType());
            if (operationType == ChangeStreamOperationType.INSERT) {
                messageService.batchInsertMessageToClickHouse(event.getMessages());
//                log.info("ClickHouse - Synced {} messages", event.getMessages().size());
            }
            
            success = true;

        } catch (Exception e) {
            log.error("ClickHouse - Failed to consume ChangeStreamMessageEvent, deliveryTag: {}", deliveryTag, e);
        } finally {
            // 确保消息只被确认一次
            try {
                if (success) {
                    channel.basicAck(deliveryTag, false);
                } else {
                    channel.basicNack(deliveryTag, false, true);
                }
            } catch (Exception ackException) {
                log.error("ClickHouse - Failed to ack/nack message, deliveryTag: {}", deliveryTag, ackException);
            }
        }
    }
}
