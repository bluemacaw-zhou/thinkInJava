package io.bluemacaw.mongodb.controller.rabbitmq.changeStream.clickhouse;

import com.alibaba.fastjson.JSON;
import com.rabbitmq.client.Channel;
import io.bluemacaw.mongodb.entity.mq.ChangeStreamChannelEvent;
import io.bluemacaw.mongodb.enums.ChangeStreamOperationType;
import io.bluemacaw.mongodb.service.ChannelService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Change Stream Channel 事件消费者 - ClickHouse
 *
 * 消费 Channel 集合的变更事件，同步到 ClickHouse
 *
 * @author shzhou.michael
 */
@Slf4j
@Component
public class ChannelConsumer {

    @Resource
    private ChannelService channelService;

    @RabbitListener(
        queues = "${spring.rabbitmq.queueChangeStreamChannelClickHouse}",
        concurrency = "1"
    )
    public void consumeChannelEvent(Message message, Channel channel) throws Exception {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        byte[] body = message.getBody();
        String content = new String(body, StandardCharsets.UTF_8);
        boolean success = false;

        try {
            ChangeStreamChannelEvent event = JSON.parseObject(content, ChangeStreamChannelEvent.class);

            if (event == null || event.getChannels() == null || event.getChannels().isEmpty()) {
                log.warn("ClickHouse - Received empty ChangeStreamChannelEvent");
                success = true;
                return;
            }

//            log.info("ClickHouse - ChangeStream Channel event: operation={}, count={}",
//                    event.getOperationType(), event.getChannels().size());

            // 根据操作类型处理
            ChangeStreamOperationType operationType = ChangeStreamOperationType.fromValue(event.getOperationType());
            if (operationType == ChangeStreamOperationType.INSERT) {
                channelService.batchInsertChannelToClickHouse(event.getChannels());
//                log.info("ClickHouse - Synced {} channels", event.getChannels().size());
            }
            
            success = true;

        } catch (Exception e) {
            log.error("ClickHouse - Failed to consume ChangeStreamChannelEvent, deliveryTag: {}", deliveryTag, e);
        } finally {
            // 确保消息只被确认一次
            try {
                if (success) {
                    channel.basicAck(deliveryTag, false);
                } else {
                    channel.basicNack(deliveryTag, false, true);
                }
            } catch (Exception ackException) {
                log.error("ClickHouse - Failed to ack/nack Channel message, deliveryTag: {}", deliveryTag, ackException);
            }
        }
    }
}
