package io.bluemacaw.mongodb.scheduler;

import com.rabbitmq.client.Channel;
import io.bluemacaw.mongodb.entity.MsgData;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 批量消息处理定时任务
 * 定时将缓存的消息批量插入MongoDB
 */
@Slf4j
@Component
public class BatchMessageScheduler {

    @Resource
    private MongoTemplate mongoTemplate;

    @Value("${rabbitmq.consumer.batch-size:100}")
    private int batchSize;

    // 消息批次缓存
    private final List<MessageBatch> messageBatch = new ArrayList<>();

    /**
     * 定时批量处理消息（每10毫秒执行一次）
     */
    @Scheduled(fixedDelay = 10)
    public void processBatchMessages() {
        List<MessageBatch> currentBatch;

        synchronized (messageBatch) {
            if (messageBatch.isEmpty()) {
                return;
            }

            // 复制当前批次并清空缓存
            currentBatch = new ArrayList<>(messageBatch);
            messageBatch.clear();
        }

        try {
            long startTime = System.currentTimeMillis();

            // 1. 批量插入 MongoDB
            List<MsgData> msgDataList = new ArrayList<>();
            for (MessageBatch batch : currentBatch) {
                msgDataList.add(batch.msgData);
            }

            bulkInsertToMongoDB(msgDataList);

            // 2. 批量确认消息
            for (MessageBatch batch : currentBatch) {
                try {
                    batch.channel.basicAck(batch.deliveryTag, false);
                } catch (Exception e) {
                    log.error("Failed to ack message, deliveryTag: {}", batch.deliveryTag, e);
                }
            }

            long duration = System.currentTimeMillis() - startTime;
            log.info("Batch processed: {} messages in {} ms", currentBatch.size(), duration);

        } catch (Exception e) {
            log.error("Batch processing error", e);

            // 批量拒绝并重新入队
            for (MessageBatch batch : currentBatch) {
                try {
                    batch.channel.basicNack(batch.deliveryTag, false, true);
                } catch (Exception ex) {
                    log.error("Failed to nack message, deliveryTag: {}", batch.deliveryTag, ex);
                }
            }
        }
    }

    /**
     * 添加消息到批次
     */
    public void addMessage(MsgData msgData, long deliveryTag, Channel channel) {
        synchronized (messageBatch) {
            messageBatch.add(new MessageBatch(msgData, deliveryTag, channel));

            // 如果达到批量大小，立即触发处理
            if (messageBatch.size() >= batchSize) {
                processBatchMessages();
            }
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
     * 获取当前批次大小（用于监控）
     */
    public int getBatchSize() {
        synchronized (messageBatch) {
            return messageBatch.size();
        }
    }

    /**
     * 内部类：消息批次
     */
    private static class MessageBatch {
        MsgData msgData;
        long deliveryTag;
        Channel channel;

        MessageBatch(MsgData msgData, long deliveryTag, Channel channel) {
            this.msgData = msgData;
            this.deliveryTag = deliveryTag;
            this.channel = channel;
        }
    }
}
