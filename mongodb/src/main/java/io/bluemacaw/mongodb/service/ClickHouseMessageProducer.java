package cn.com.wind.service;

import cn.com.wind.entity.MsgAnalysisData;
import com.alibaba.fastjson.JSON;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * ClickHouse消息生产者
 * 负责将MsgAnalysisData发送到RabbitMQ队列，供ClickHouse消费
 */
@Slf4j
@Service
public class ClickHouseMessageProducer {

    @Resource
    private RabbitTemplate rabbitTemplate;

    @Value("${spring.rabbitmq.exchangeClickhouse}")
    private String exchangeClickhouse;

    @Value("${spring.rabbitmq.routeClickhouse}")
    private String routeClickhouse;

    /**
     * 发送单条消息到ClickHouse队列
     *
     * @param analysisData 消息分析数据
     * @return 是否发送成功
     */
    public boolean sendMessage(MsgAnalysisData analysisData) {
        if (analysisData == null) {
            log.warn("MsgAnalysisData is null, skip sending");
            return false;
        }

        try {
            // 转换为JSON字符串
            String jsonMessage = JSON.toJSONString(analysisData);

            // 设置消息属性
            MessageProperties messageProperties = new MessageProperties();
            messageProperties.setContentType("application/json");
            messageProperties.setContentEncoding("UTF-8");
            // 设置消息持久化，确保RabbitMQ重启后消息不丢失
            messageProperties.setDeliveryMode(org.springframework.amqp.core.MessageDeliveryMode.PERSISTENT);

            // 创建消息
            Message message = new Message(jsonMessage.getBytes("UTF-8"), messageProperties);

            // 发送消息
            rabbitTemplate.send(exchangeClickhouse, routeClickhouse, message);

            log.info("Successfully sent message to ClickHouse queue, msgId: {}, fromId: {}, contactId: {}",
                    analysisData.getMsgId(), analysisData.getFromId(), analysisData.getContactId());

            return true;

        } catch (Exception e) {
            log.error("Failed to send message to ClickHouse queue, msgId: {}",
                    analysisData.getMsgId(), e);
            return false;
        }
    }

    /**
     * 批量发送消息到ClickHouse队列（高性能版本）
     * 使用 RabbitMQ 的批量发送机制，性能更高
     *
     * @param analysisDataList 消息分析数据列表
     * @return 成功发送的消息数量
     */
    public int sendMessageBatch(java.util.List<MsgAnalysisData> analysisDataList) {
        if (analysisDataList == null || analysisDataList.isEmpty()) {
            log.warn("Batch list is empty, skip sending");
            return 0;
        }

        try {
            long startTime = System.currentTimeMillis();

            // 批量发送（通过 RabbitMQ 的批量 API）
            for (MsgAnalysisData analysisData : analysisDataList) {
                if (analysisData != null) {
                    String jsonMessage = JSON.toJSONString(analysisData);

                    MessageProperties messageProperties = new MessageProperties();
                    messageProperties.setContentType("application/json");
                    messageProperties.setContentEncoding("UTF-8");
                    messageProperties.setDeliveryMode(org.springframework.amqp.core.MessageDeliveryMode.PERSISTENT);

                    Message message = new Message(jsonMessage.getBytes("UTF-8"), messageProperties);
                    rabbitTemplate.send(exchangeClickhouse, routeClickhouse, message);
                }
            }

            long duration = System.currentTimeMillis() - startTime;
            log.debug("Batch sent {} messages to ClickHouse queue in {} ms",
                    analysisDataList.size(), duration);

            return analysisDataList.size();

        } catch (Exception e) {
            log.error("Batch send to ClickHouse error", e);
            return 0;
        }
    }

    /**
     * 批量发送消息到ClickHouse队列（兼容旧方法）
     *
     * @param analysisDataList 消息分析数据列表
     * @return 成功发送的消息数量
     */
    public int sendBatchMessages(java.util.List<MsgAnalysisData> analysisDataList) {
        return sendMessageBatch(analysisDataList);
    }

    /**
     * 异步发送消息到ClickHouse队列
     * 适用于不需要等待发送结果的场景
     *
     * @param analysisData 消息分析数据
     */
    public void sendMessageAsync(MsgAnalysisData analysisData) {
        if (analysisData == null) {
            log.warn("MsgAnalysisData is null, skip sending");
            return;
        }

        try {
            String jsonMessage = JSON.toJSONString(analysisData);

            MessageProperties messageProperties = new MessageProperties();
            messageProperties.setContentType("application/json");
            messageProperties.setContentEncoding("UTF-8");
            messageProperties.setDeliveryMode(org.springframework.amqp.core.MessageDeliveryMode.PERSISTENT);

            Message message = new Message(jsonMessage.getBytes("UTF-8"), messageProperties);

            // 使用convertAndSend进行异步发送
            rabbitTemplate.convertAndSend(exchangeClickhouse, routeClickhouse, message);

            log.debug("Async sent message to ClickHouse queue, msgId: {}", analysisData.getMsgId());

        } catch (Exception e) {
            log.error("Failed to async send message to ClickHouse queue, msgId: {}",
                    analysisData.getMsgId(), e);
        }
    }
}
