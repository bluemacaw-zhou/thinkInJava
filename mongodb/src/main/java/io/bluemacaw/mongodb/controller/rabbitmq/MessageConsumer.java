package io.bluemacaw.mongodb.controller.rabbitmq;

import com.alibaba.fastjson.JSON;
import com.rabbitmq.client.Channel;
import io.bluemacaw.mongodb.entity.MqMsgItem;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 单条消息消费者
 * 当批量消费未启用时使用此消费者
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "rabbitmq.consumer", name = "batch-enabled", havingValue = "false", matchIfMissing = true)
public class MessageConsumer {
    @Resource
    private MongoTemplate mongoTemplate;

    @RabbitListener(queues = "${spring.rabbitmq.queueMessage}")
    public void onMessage(Message message, Channel channel) throws Exception {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();

        byte[] body = message.getBody();
        String content = new String(body);
        MqMsgItem msg = JSON.parseObject(content, MqMsgItem.class);

        log.info("onMessage receive: " +
                        "oldMsgId: {}, " +
                        "msgId: {}, " +
                        "contactType: {}, " +
                        "fromId: {}, " +
                        "contractId: {}, " +
                        "clientMsgId: {}, " +
                        "content size: {}",
                msg.getMsgData().getOldMsgId(), msg.getMsgData().getMsgId(), msg.getMsgData().getContactType(),
                msg.getMsgData().getFromId(), msg.getMsgData().getContactId(), msg.getMsgData().getClientMsgId(),
                msg.getMsgData().getContent().getBytes(StandardCharsets.UTF_8).length);

        // 插入msgData
        mongoTemplate.insert(msg.getMsgData());

        // 手动确认消息
        channel.basicAck(deliveryTag, false);
    }
}
