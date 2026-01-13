package io.bluemacaw.mongodb.controller.rabbitmq;

import com.alibaba.fastjson.JSON;
import com.rabbitmq.client.Channel;
import io.bluemacaw.mongodb.entity.mq.MqMessage;
import io.bluemacaw.mongodb.entity.mq.MqMessageData;
import io.bluemacaw.mongodb.service.MessageService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

import static io.bluemacaw.mongodb.service.ChannelService.generateChannelId;

/**
 * 单条消息消费者
 * 当批量消费未启用时使用此消费者
 *
 * 处理流程：
 * 1. 从MQ接收消息(MqMsgItem)
 * 2. 根据消息生成/获取 Channel ID
 * 3. 确保 Channel 存在并增加 message_version
 * 4. 将MQ消息转换为Message实体，设置seq
 * 5. 保存到MongoDB (按月分collection)
 * 6. MongoDB Change Stream 监听到插入事件
 * 7. 同步到 ClickHouse
 */
@Slf4j
@Component
public class MessageConsumer {
    @Resource
    private MessageService messageService;

    @RabbitListener(
        queues = "${spring.rabbitmq.queueMessage}",
        concurrency = "1"
    )
    public void onMessage(Message message, Channel channel) throws Exception {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        byte[] body = message.getBody();
        String content = new String(body, StandardCharsets.UTF_8);
        MqMessage mqMessage = JSON.parseObject(content, MqMessage.class);
        MqMessageData mqMessageData = mqMessage.getMqMessageData();

        String channelId = generateChannelId(mqMessage);
        int channelType = mqMessageData.getContactType();
        long fromId = mqMessageData.getFromId();
        long toId = mqMessageData.getContactId();
        String clientMsgId = mqMessageData.getClientMsgId();
        String oldMsgId = mqMessageData.getOldMsgId();
        int contentLength = mqMessageData.getContent() != null ? mqMessageData.getContent().getBytes(StandardCharsets.UTF_8).length : 0;
        boolean success = false;

        try {
            log.info("onMessage receive: oldMsgId: {}, channelType: {}, fromId: {}, contactId: {}, clientMsgId: {}, content size: {}",
                    oldMsgId, channelType, fromId, toId, clientMsgId, contentLength);

            io.bluemacaw.mongodb.entity.Message mongodbMessage = messageService.saveMessage(mqMessage);

            log.info("Message saved successfully: channelId={}, seq={}, oldMsgId={}",
                    channelId, mongodbMessage.getSeq(), mqMessage.getMqMessageData().getOldMsgId());

            success = true;

        } catch (DataIntegrityViolationException e) {
            // MongoDB 写冲突异常
            log.warn("MongoDB WriteConflict detected, requeue message for retry. deliveryTag: {}, channelId: {}, oldMsgId: {}",
                    deliveryTag, channelId, oldMsgId, e);

        } catch (Exception e) {
            // 其他未预期的异常
            log.error("Unexpected error processing message, requeue for retry. deliveryTag: {}, channelId: {}, oldMsgId: {}",
                    deliveryTag, channelId, oldMsgId, e);
        } finally {
            // 确保消息只被确认一次
            try {
                if (success) {
                    // 成功处理，确认消息
                    channel.basicAck(deliveryTag, false);
                } else {
                    // 处理失败，拒绝消息并重新入队
                    channel.basicNack(deliveryTag, false, true);
                }
            } catch (Exception ackException) {
                log.error("Failed to ack/nack message, deliveryTag: {}", deliveryTag, ackException);
            }
        }
    }
}
