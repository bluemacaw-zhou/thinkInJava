package io.bluemacaw.mongodb.config;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@Getter
@Setter
public class RabbitmqConfig {
    @Value("${spring.rabbitmq.exchangeMessage}")
    private String exchangeMessage;

    // 单条消息队列（实时数据）
    @Value("${spring.rabbitmq.queueMessage}")
    private String queueMessage;

    @Value("${spring.rabbitmq.routeMessage}")
    private String routeMessage;

    // 批量消息队列（历史数据导入）
    @Value("${spring.rabbitmq.queueMessageBatch}")
    private String queueMessageBatch;

    @Value("${spring.rabbitmq.routeMessageBatch}")
    private String routeMessageBatch;

    // ClickHouse专用队列配置
    @Value("${spring.rabbitmq.exchangeClickhouse}")
    private String exchangeClickhouse;

    // 批量消息队列
    @Value("${spring.rabbitmq.queueClickhouseBatch}")
    private String queueClickhouseBatch;

    @Value("${spring.rabbitmq.routeClickhouseBatch}")
    private String routeClickhouseBatch;

    // 原有消息队列配置
    @Bean
    public DirectExchange exchangeMessage() {
        return new DirectExchange(exchangeMessage);
    }

    // 单条消息队列（实时数据）
    @Bean
    public Queue queueMessage() {
        return new Queue(queueMessage, true);
    }

    @Bean
    public Binding bindingQueueMessage() {
        return BindingBuilder.bind(queueMessage()).to(exchangeMessage()).with(routeMessage);
    }

    // 批量消息队列（历史数据导入）
    @Bean
    public Queue queueMessageBatch() {
        return new Queue(queueMessageBatch, true);
    }

    @Bean
    public Binding bindingQueueMessageBatch() {
        return BindingBuilder.bind(queueMessageBatch()).to(exchangeMessage()).with(routeMessageBatch);
    }

    // ClickHouse Exchange
    @Bean
    public DirectExchange exchangeClickhouse() {
        return new DirectExchange(exchangeClickhouse);
    }

    // 批量消息队列
    @Bean
    public Queue queueClickhouseBatch() {
        return new Queue(queueClickhouseBatch, true);
    }

    @Bean
    public Binding bindingQueueClickhouseBatch() {
        return BindingBuilder.bind(queueClickhouseBatch()).to(exchangeClickhouse()).with(routeClickhouseBatch);
    }
}
