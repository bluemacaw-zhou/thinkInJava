package io.bluemacaw.mongodb.controller.rabbitmq;

import com.alibaba.fastjson.JSON;
import com.rabbitmq.client.Channel;
import io.bluemacaw.mongodb.entity.MqMsgItem;
import io.bluemacaw.mongodb.entity.MsgAnalysisData;
import io.bluemacaw.mongodb.entity.MsgData;
import io.bluemacaw.mongodb.service.ClickHouseMessageProducer;
import io.bluemacaw.mongodb.service.MsgAnalysisDataConverter;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 批量消息消费者
 * 支持批量从 RabbitMQ 消费、批量插入 MongoDB、批量发送到 ClickHouse
 */
@Slf4j
@Component
public class BatchMessageConsumer {

    @Resource
    private BatchMessageScheduler batchMessageScheduler;

    @Value("${rabbitmq.consumer.max-batch-size:1000}")
    private int maxBatchSize;

    /**
     * 接收消息并添加到批次缓存
     * 支持背压机制：当缓存超过阈值时，暂停消费并休眠
     */
    @RabbitListener(queues = "${spring.rabbitmq.queueMessageBatch}",
                    ackMode = "MANUAL",
                    concurrency = "${rabbitmq.consumer.concurrency:16}")
    public void onMessage(Message message, Channel channel) throws Exception {
        try {
            // 检查当前批次大小，如果超过阈值则等待
            int currentBatchSize = batchMessageScheduler.getBatchSize();
            if (currentBatchSize >= maxBatchSize) {
                log.warn("Batch size {} exceeds max threshold {}, pausing consumption for 1s",
                         currentBatchSize, maxBatchSize);
                Thread.sleep(1000);

                // 拒绝消息并重新入队，让其他消费者或稍后处理
                channel.basicNack(message.getMessageProperties().getDeliveryTag(), false, true);
                return;
            }

            long deliveryTag = message.getMessageProperties().getDeliveryTag();

            // 解析消息
            String content = new String(message.getBody());
            MqMsgItem msg = JSON.parseObject(content, MqMsgItem.class);

            // 添加到批次缓存
            batchMessageScheduler.addMessage(msg.getMsgData(), deliveryTag, channel);

            // 短暂休眠，降低消费速度
            Thread.sleep(10);

        } catch (InterruptedException e) {
            log.warn("Consumer interrupted", e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("Error processing message", e);
            // 拒绝消息并重新入队
            channel.basicNack(message.getMessageProperties().getDeliveryTag(), false, true);
        }
    }
}
