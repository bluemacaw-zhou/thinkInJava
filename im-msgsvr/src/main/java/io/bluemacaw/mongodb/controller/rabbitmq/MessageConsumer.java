package io.bluemacaw.mongodb.controller.rabbitmq;

import com.alibaba.fastjson.JSON;
import com.rabbitmq.client.Channel;
import io.bluemacaw.mongodb.entity.MqMsgItem;
import io.bluemacaw.mongodb.entity.Session;
import io.bluemacaw.mongodb.service.SessionService;
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
public class MessageConsumer {
    @Resource
    private MongoTemplate mongoTemplate;

    @Resource
    private SessionService sessionService;

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

        // 1. 确保 Session 存在 (高并发安全)
        String sessionId = generateSessionId(msg);
        int sessionType = msg.getMsgData().getContactType(); // 0-私聊, 1-群聊
        Session session = sessionService.ensureSessionExists(sessionId, sessionType);

        if (session == null) {
            log.error("创建或获取 Session 失败, sessionId: {}, 消息处理中止", sessionId);
            // 根据业务需求决定是否 nack 消息
            channel.basicNack(deliveryTag, false, true); // requeue=true 重新入队
            return;
        }

        log.debug("Session 已确认存在, sessionId: {}, version: {}", sessionId, session.getVersion());

        // 2. 插入消息数据
        mongoTemplate.insert(msg.getMsgData());

        // 3. 更新 Session 版本号 (可选,根据业务需求)
        // sessionService.incrementVersion(sessionId, 1);

        // 4. 手动确认消息
        channel.basicAck(deliveryTag, false);
    }

    /**
     * 根据消息生成 sessionId
     *
     * @param msg 消息对象
     * @return sessionId
     */
    private String generateSessionId(MqMsgItem msg) {
        int contactType = msg.getMsgData().getContactType();
        long fromId = msg.getMsgData().getFromId();
        long contactId = msg.getMsgData().getContactId();

        if (contactType == 0) {
            // 私聊: 使用 SessionService 的静态方法生成
            return SessionService.generatePrivateSessionId(fromId, contactId);
        } else {
            // 群聊: group_群ID
            return SessionService.generateGroupSessionId(contactId);
        }
    }
}
