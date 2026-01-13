package io.bluemacaw.mongodb.controller.rabbitmq;

import com.alibaba.fastjson.JSON;
import com.rabbitmq.client.Channel;
import io.bluemacaw.mongodb.entity.mq.MqAggregatedMessageData;
import io.bluemacaw.mongodb.service.MessageService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 批量消息消费者
 * 用于处理历史数据导入场景
 *
 * 处理流程：
 * 1. 从MQ接收聚合消息(MqAggregatedMessageData)
 * 2. 确保 Channel 和 UserSubscription 存在
 * 3. 根据消息日期分配seq（历史数据递减，新数据递增）
 * 4. 批量保存到MongoDB (按月分collection)
 * 5. MongoDB Change Stream 监听到插入事件
 * 6. 同步到 ClickHouse
 */
@Slf4j
@Component
public class BatchMessageConsumer {
    @Resource
    private MessageService messageService;

    @RabbitListener(
        queues = "${spring.rabbitmq.queueMessageBatch}",
        concurrency = "8"
    )
    public void onBatchMessage(Message message, Channel channel) throws Exception {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        byte[] body = message.getBody();
        String content = new String(body, StandardCharsets.UTF_8);

        try {
            MqAggregatedMessageData aggregatedData = JSON.parseObject(content, MqAggregatedMessageData.class);

            // 调用 MessageService 批量保存消息
            messageService.saveBatchMessages(aggregatedData);

            // 手动确认消息
            channel.basicAck(deliveryTag, false);

        } catch (DataIntegrityViolationException e) {
            // MongoDB 写冲突异常
            log.error("MongoDB WriteConflict detected, requeue batch message for retry. deliveryTag: {}", deliveryTag, e);
            // 负确认，消息重新入队等待重试
            channel.basicNack(deliveryTag, false, true);

        } catch (Exception e) {
            // 其他未预期的异常
            log.error("Unexpected error processing batch message, requeue for retry. deliveryTag: {}", deliveryTag, e);
            // 负确认，消息重新入队等待重试
            channel.basicNack(deliveryTag, false, true);
        }
    }
}
