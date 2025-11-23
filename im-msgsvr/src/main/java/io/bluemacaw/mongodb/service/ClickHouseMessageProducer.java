package io.bluemacaw.mongodb.service;


import com.alibaba.fastjson.JSON;
import io.bluemacaw.mongodb.entity.MsgAnalysisData;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * ClickHouse消息生产者
 * 支持单条和批量发送到RabbitMQ队列，供ClickHouse消费
 */
@Slf4j
@Service
public class ClickHouseMessageProducer {

    @Resource
    private RabbitTemplate rabbitTemplate;

    @Value("${spring.rabbitmq.exchangeClickhouse}")
    private String exchangeClickhouse;

    // 批量消息路由
    @Value("${spring.rabbitmq.routeClickhouseBatch}")
    private String routeClickhouseBatch;

    /**
     * 批量发送消息到ClickHouse队列（合并为一条消息）
     * 使用 JSONEachRow 格式，ClickHouse 会自动解析每一行
     *
     * @param analysisDataList 消息分析数据列表
     * @return 是否发送成功
     */
    public boolean sendMessageBatch(List<MsgAnalysisData> analysisDataList) {
        if (analysisDataList == null || analysisDataList.isEmpty()) {
            log.warn("Batch list is empty, skip sending");
            return false;
        }

        try {
            long startTime = System.currentTimeMillis();

            // 为所有数据自动生成 sessionId（如果未设置）
            analysisDataList.forEach(data -> {
                if (data != null && (data.getSessionId() == null || data.getSessionId().isEmpty())) {
                    data.generateSessionId();
                }
            });

            // 将列表转换为 JSONEachRow 格式（每行一个JSON对象，用换行符分隔）
            // ClickHouse RabbitMQ 引擎支持这种格式
            String jsonEachRow = analysisDataList.stream()
                    .filter(data -> data != null)
                    .map(JSON::toJSONString)
                    .collect(Collectors.joining("\n"));

            // 设置消息属性
            MessageProperties messageProperties = new MessageProperties();
            messageProperties.setContentType("application/json");
            messageProperties.setContentEncoding("UTF-8");
            messageProperties.setDeliveryMode(org.springframework.amqp.core.MessageDeliveryMode.PERSISTENT);

            // 添加自定义头部，标记批量大小
            messageProperties.setHeader("batch-size", analysisDataList.size());
            messageProperties.setHeader("message-type", "batch");

            // 创建消息
            Message message = new Message(jsonEachRow.getBytes("UTF-8"), messageProperties);

            // 发送到批量消息队列
            rabbitTemplate.send(exchangeClickhouse, routeClickhouseBatch, message);

            long duration = System.currentTimeMillis() - startTime;
            log.info("Successfully sent batch message with {} items to ClickHouse queue in {} ms",
                    analysisDataList.size(), duration);

            return true;

        } catch (Exception e) {
            log.error("Failed to send batch message to ClickHouse queue", e);
            return false;
        }
    }
}
