package cn.com.wind.controller.rabbitmq;

import cn.com.wind.entity.MqMsgItem;
import cn.com.wind.entity.MsgAnalysisData;
import cn.com.wind.entity.MsgData;
import cn.com.wind.service.ClickHouseMessageProducer;
import cn.com.wind.service.MsgAnalysisDataConverter;
import com.alibaba.fastjson.JSON;
import com.rabbitmq.client.Channel;
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
@ConditionalOnProperty(prefix = "rabbitmq.consumer", name = "batch-enabled", havingValue = "true")
public class BatchMessageConsumer {

    @Resource
    private MongoTemplate mongoTemplate;

    @Resource
    private ClickHouseMessageProducer clickHouseProducer;

    @Value("${rabbitmq.consumer.batch-size:100}")
    private int batchSize;

    @Value("${rabbitmq.consumer.batch-timeout:5000}")
    private long batchTimeout;

    private final List<MessageBatch> messageBatch = new ArrayList<>();
    private long lastBatchTime = System.currentTimeMillis();

    /**
     * 批量消费消息
     * 注意：这里仍然是单条接收，但会在内存中累积后批量处理
     */
    @RabbitListener(queues = "${spring.rabbitmq.queueMessage}",
                    ackMode = "MANUAL",
                    concurrency = "${rabbitmq.consumer.concurrency:16}")
    public void onMessage(Message message, Channel channel) throws Exception {
        try {
            long deliveryTag = message.getMessageProperties().getDeliveryTag();

            // 解析消息
            String content = new String(message.getBody());
            MqMsgItem msg = JSON.parseObject(content, MqMsgItem.class);

            // 添加到批次
            synchronized (messageBatch) {
                messageBatch.add(new MessageBatch(msg, deliveryTag));

                // 检查是否需要批量处理
                boolean shouldProcess = messageBatch.size() >= batchSize ||
                                       (System.currentTimeMillis() - lastBatchTime) >= batchTimeout;

                if (shouldProcess) {
                    processBatch(channel);
                }
            }

        } catch (Exception e) {
            log.error("Error processing message", e);
            // 拒绝消息并重新入队
            channel.basicNack(message.getMessageProperties().getDeliveryTag(), false, true);
        }
    }

    /**
     * 批量处理消息
     */
    private void processBatch(Channel channel) throws Exception {
        if (messageBatch.isEmpty()) {
            return;
        }

        List<MessageBatch> currentBatch = new ArrayList<>(messageBatch);
        messageBatch.clear();
        lastBatchTime = System.currentTimeMillis();

        try {
            long startTime = System.currentTimeMillis();

            // 1. 批量插入 MongoDB
            List<MsgData> msgDataList = new ArrayList<>();
            for (MessageBatch batch : currentBatch) {
                msgDataList.add(batch.msg.getMsgData());
            }

            bulkInsertToMongoDB(msgDataList);

            // 2. 批量转换并发送到 ClickHouse
            List<MsgAnalysisData> analysisDataList = new ArrayList<>();
            for (MsgData msgData : msgDataList) {
                MsgAnalysisData analysisData = MsgAnalysisDataConverter.convert(msgData);
                if (analysisData != null) {
                    analysisDataList.add(analysisData);
                }
            }

            if (!analysisDataList.isEmpty()) {
                clickHouseProducer.sendMessageBatch(analysisDataList);
            }

            // 3. 批量确认消息
            long lastDeliveryTag = currentBatch.get(currentBatch.size() - 1).deliveryTag;
            channel.basicAck(lastDeliveryTag, true); // multiple=true 批量确认

            long duration = System.currentTimeMillis() - startTime;
            log.info("Batch processed: {} messages in {} ms", currentBatch.size(), duration);

        } catch (Exception e) {
            log.error("Batch processing error", e);
            // 批量拒绝并重新入队
            long lastDeliveryTag = currentBatch.get(currentBatch.size() - 1).deliveryTag;
            channel.basicNack(lastDeliveryTag, true, true);
            throw e;
        }
    }

    /**
     * 批量插入到 MongoDB
     */
    private void bulkInsertToMongoDB(List<MsgData> msgDataList) {
        if (msgDataList.isEmpty()) {
            return;
        }

        try {
            // 使用 MongoDB BulkOperations 进行批量插入
            BulkOperations bulkOps = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, MsgData.class);
            bulkOps.insert(msgDataList);
            bulkOps.execute();

            log.debug("Bulk inserted {} messages to MongoDB", msgDataList.size());

        } catch (Exception e) {
            log.error("MongoDB bulk insert error", e);
            throw e;
        }
    }

    /**
     * 内部类：消息批次
     */
    private static class MessageBatch {
        MqMsgItem msg;
        long deliveryTag;

        MessageBatch(MqMsgItem msg, long deliveryTag) {
            this.msg = msg;
            this.deliveryTag = deliveryTag;
        }
    }
}
