package io.bluemacaw.mongodb.changestream.handler;

import com.alibaba.fastjson.JSON;
import com.mongodb.client.model.changestream.ChangeStreamDocument;
import io.bluemacaw.mongodb.changestream.ChangeStreamHandler;
import io.bluemacaw.mongodb.changestream.ChangeStreamManager;
import io.bluemacaw.mongodb.entity.UserSubscription;
import io.bluemacaw.mongodb.entity.mq.ChangeStreamUserSubscriptionEvent;
import io.bluemacaw.mongodb.enums.ChangeStreamOperationType;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static io.bluemacaw.mongodb.util.UserSubscriptionConverter.documentToUserSubscription;

/**
 * UserSubscription collection 的 Change Stream 处理器
 *
 * 监听 user_subscription collection 的变更事件
 * 处理逻辑：
 * - 将变更事件缓存，调度结束时发送到 RabbitMQ
 * - 由 MQ 消费者负责后续处理
 *
 * @author shzhou.michael
 */
@Slf4j
@Component
public class UserSubscriptionHandler implements ChangeStreamHandler {

    @Resource
    private ChangeStreamManager changeStreamManager;

    @Resource
    private RabbitTemplate rabbitTemplate;

    @Value("${spring.rabbitmq.exchangeChangeStreamUserSub}")
    private String exchangeChangeStreamUserSub;

    // 事件缓存 - 按操作类型分组
    private final List<UserSubscription> insertCache = new ArrayList<>();
    private final List<UserSubscription> updateCache = new ArrayList<>();

    /**
     * 初始化时自动注册到路由器
     */
    @PostConstruct
    public void init() {
        changeStreamManager.registerHandler(this);
    }

    @Override
    public boolean supports(String collectionName) {
        return "user_subscription".equals(collectionName);
    }

    @Override
    public void handleInsert(String collectionName, ChangeStreamDocument<Document> changeEvent) {
        Document fullDocument = changeEvent.getFullDocument();
        if (fullDocument != null) {
            try {
                UserSubscription userSub = documentToUserSubscription(fullDocument);
                if (userSub != null) {
                    addToCache(ChangeStreamOperationType.INSERT, userSub);
                    log.debug("Cached UserSubscription insert event: userId={}, channelId={}",
                            userSub.getUserId(), userSub.getChannelId());
                }
            } catch (Exception e) {
                log.error("Failed to handle UserSubscription insert event, id: {}",
                        changeEvent.getDocumentKey(), e);
            }
        } else {
            log.warn("UserSubscription insert event missing document");
        }
    }

    @Override
    public void handleUpdate(String collectionName, ChangeStreamDocument<Document> changeEvent) {
        Document fullDocument = changeEvent.getFullDocument();
        if (fullDocument != null) {
            try {
                UserSubscription userSub = documentToUserSubscription(fullDocument);
                if (userSub != null) {
                    addToCache(ChangeStreamOperationType.UPDATE, userSub);
                    log.debug("Cached UserSubscription update event: userId={}, channelId={}",
                            userSub.getUserId(), userSub.getChannelId());
                }
            } catch (Exception e) {
                log.error("Failed to handle UserSubscription update event, id: {}",
                        changeEvent.getDocumentKey(), e);
            }
        } else {
            log.warn("UserSubscription update event missing full document");
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
        return "UserSubscriptionHandler";
    }

    @Override
    public void flush() {
        log.info("UserSubscription insertCache size: {}, updateCache size: {}", insertCache.size(), updateCache.size());
        sendToMQ(ChangeStreamOperationType.INSERT, insertCache);
        sendToMQ(ChangeStreamOperationType.UPDATE, updateCache);
    }

    /**
     * 添加到缓存
     */
    private void addToCache(ChangeStreamOperationType operationType, UserSubscription userSub) {
        if (operationType == ChangeStreamOperationType.INSERT) {
            insertCache.add(userSub);
        } else if (operationType == ChangeStreamOperationType.UPDATE) {
            updateCache.add(userSub);
        }
    }

    /**
     * 发送缓存到 MQ
     */
    private void sendToMQ(ChangeStreamOperationType operationType, List<UserSubscription> cache) {
        if (cache.isEmpty()) {
            return;
        }

        try {
            ChangeStreamUserSubscriptionEvent event = ChangeStreamUserSubscriptionEvent.builder()
                    .operationType(operationType.getValue())
                    .userSubscriptions(new ArrayList<>(cache))
                    .timestamp(System.currentTimeMillis())
                    .build();

            String jsonMessage = JSON.toJSONString(event);

            MessageProperties messageProperties = new MessageProperties();
            messageProperties.setContentType(MessageProperties.CONTENT_TYPE_TEXT_PLAIN);

            Message message = new Message(jsonMessage.getBytes(StandardCharsets.UTF_8), messageProperties);
            // Fanout Exchange 不需要 routing key
            rabbitTemplate.send(exchangeChangeStreamUserSub, "", message);

            log.debug("Sent UserSubscription ChangeStream event to MQ: operation={}, count={}",
                    operationType.getValue(), cache.size());

            cache.clear();

        } catch (Exception e) {
            log.error("Failed to send UserSubscription ChangeStream event to MQ: operation={}", operationType.getValue(), e);
        }
    }
}
