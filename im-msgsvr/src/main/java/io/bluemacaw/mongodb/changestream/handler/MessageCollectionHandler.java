package io.bluemacaw.mongodb.changestream.handler;

import com.alibaba.fastjson.JSON;
import com.mongodb.client.model.changestream.ChangeStreamDocument;
import io.bluemacaw.mongodb.changestream.ChangeStreamHandler;
import io.bluemacaw.mongodb.entity.Message;
import io.bluemacaw.mongodb.entity.mq.ChangeStreamMessageEvent;
import io.bluemacaw.mongodb.enums.ChangeStreamOperationType;
import io.bluemacaw.mongodb.util.MessageConverter;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * message_ 开头的 collection 的 Change Stream 处理器
 *
 * 处理逻辑：
 * - 监听所有 message_YYYYMM 表的变更
 * - 将变更事件缓存，达到批次大小或调度结束时发送到 RabbitMQ
 * - 由 MQ 消费者负责批量同步到 ClickHouse
 *
 * @author shzhou.michael
 */
@Slf4j
@Component
public class MessageCollectionHandler implements ChangeStreamHandler {

    @Resource
    private RabbitTemplate rabbitTemplate;

    @Resource
    private io.bluemacaw.mongodb.changestream.ChangeStreamManager changeStreamManager;

    @Value("${spring.rabbitmq.exchangeChangeStreamMessage}")
    private String exchangeChangeStreamMessage;

    // 事件缓存 - 按操作类型分组
    private final List<Message> insertCache = new ArrayList<>();
    private final List<Message> updateCache = new ArrayList<>();

    /**
     * 初始化时自动注册到路由器
     */
    @PostConstruct
    public void init() {
        changeStreamManager.registerHandler(this);
    }

    @Override
    public boolean supports(String collectionName) {
        // 支持所有 message_ 开头的 collection
        return collectionName != null && collectionName.startsWith("message_");
    }

    @Override
    public void handleInsert(String collectionName, ChangeStreamDocument<Document> changeEvent) {
        Document fullDocument = changeEvent.getFullDocument();
        if (fullDocument != null) {
            try {
                Message message = MessageConverter.documentToMessage(fullDocument);
                if (message != null) {
                    addToCache(ChangeStreamOperationType.INSERT, message);
                    log.debug("Cached insert event for collection: {}, msgId: {}",
                            collectionName, message.getOldMsgId());
                }
            } catch (Exception e) {
                log.error("Failed to handle insert event for collection: {}, id: {}",
                        collectionName, changeEvent.getDocumentKey(), e);
            }
        } else {
            log.warn("Insert event missing document for collection: {}", collectionName);
        }
    }

    @Override
    public void handleUpdate(String collectionName, ChangeStreamDocument<Document> changeEvent) {
        Document fullDocument = changeEvent.getFullDocument();
        if (fullDocument != null) {
            try {
                Message message = MessageConverter.documentToMessage(fullDocument);
                if (message != null) {
                    addToCache(ChangeStreamOperationType.UPDATE, message);
                    log.debug("Cached update event for collection: {}, msgId: {}",
                            collectionName, message.getOldMsgId());
                }
            } catch (Exception e) {
                log.error("Failed to handle update event for collection: {}, id: {}",
                        collectionName, changeEvent.getDocumentKey(), e);
            }
        } else {
            log.warn("Update event missing document for collection: {}", collectionName);
        }
    }

    @Override
    public void handleDelete(String collectionName, ChangeStreamDocument<Document> changeEvent) {
    }

    @Override
    public void handleReplace(String collectionName, ChangeStreamDocument<Document> changeEvent) {
    }

    @Override
    public String getHandlerName() {
        return "MessageCollectionHandler";
    }

    @Override
    public void flush() {
        log.info("Message insertCache size: {}, updateCache size: {}", insertCache.size(), updateCache.size());
        sendToMQ(ChangeStreamOperationType.INSERT, insertCache);
        sendToMQ(ChangeStreamOperationType.UPDATE, updateCache);
    }

    /**
     * 添加消息到缓存
     */
    private void addToCache(ChangeStreamOperationType operationType, Message message) {
        if (operationType == ChangeStreamOperationType.INSERT) {
            insertCache.add(message);
        } else if (operationType == ChangeStreamOperationType.UPDATE) {
            updateCache.add(message);
        }
    }

    /**
     * 发送缓存到 MQ
     *
     * 策略：
     * - 优先尝试整批发送
     * - 如果序列化后超过50MB，自动拆分成更小的批次
     */
    private void sendToMQ(ChangeStreamOperationType operationType, List<Message> cache) {
        if (cache.isEmpty()) {
            return;
        }

        try {
            int totalSize = cache.size();
            int sentCount = sendBatchToMQ(operationType, cache);

            if (sentCount > 0) {
                log.info("Sent Message ChangeStream events to MQ: operation={}, total={}, sent={}",
                        operationType.getValue(), totalSize, sentCount);
            } else {
                log.warn("Failed to send any messages: operation={}, total={}",
                        operationType.getValue(), totalSize);
            }

            cache.clear();

        } catch (Exception e) {
            log.error("Failed to send Message ChangeStream event to MQ: operation={}", operationType.getValue(), e);
        }
    }

    /**
     * 发送单批消息到 MQ
     * 如果消息过大，会自动递归拆分
     *
     * @return 成功发送的消息数量
     */
    private int sendBatchToMQ(ChangeStreamOperationType operationType, List<Message> batch) {
        if (batch.isEmpty()) {
            return 0;
        }

        try {
            ChangeStreamMessageEvent event = ChangeStreamMessageEvent.builder()
                    .operationType(operationType.getValue())
                    .messages(new ArrayList<>(batch))
                    .timestamp(System.currentTimeMillis())
                    .build();

            String jsonMessage = JSON.toJSONString(event);
            byte[] messageBytes = jsonMessage.getBytes(StandardCharsets.UTF_8);
            int messageSizeInMB = messageBytes.length / (1024 * 1024);

            // 如果消息超过50MB且batch可以拆分，则拆分后重试
            if (messageSizeInMB > 50 && batch.size() > 1) {
                log.warn("Message too large ({}MB), splitting batch from {} messages",
                        messageSizeInMB, batch.size());

                // 拆分成两半
                int mid = batch.size() / 2;
                List<Message> firstHalf = batch.subList(0, mid);
                List<Message> secondHalf = batch.subList(mid, batch.size());

                // 递归发送两半
                int firstSent = sendBatchToMQ(operationType, firstHalf);
                int secondSent = sendBatchToMQ(operationType, secondHalf);

                return firstSent + secondSent;
            }

            MessageProperties messageProperties = new MessageProperties();
            messageProperties.setContentType(MessageProperties.CONTENT_TYPE_TEXT_PLAIN);

            org.springframework.amqp.core.Message message = new org.springframework.amqp.core.Message(
                    messageBytes, messageProperties);

            // Fanout Exchange 不需要 routing key
            rabbitTemplate.send(exchangeChangeStreamMessage, "", message);

            log.debug("Sent Message ChangeStream event batch to MQ: operation={}, count={}, size={}MB",
                    operationType.getValue(), batch.size(), messageSizeInMB);

            return batch.size();

        } catch (Exception e) {
            log.error("Failed to send Message ChangeStream batch to MQ: operation={}, batchSize={}",
                    operationType.getValue(), batch.size(), e);
            return 0;
        }
    }
}
