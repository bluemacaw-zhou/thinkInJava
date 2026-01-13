package io.bluemacaw.mongodb.config;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
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

    // Change Stream 事件队列配置
    // 所有 Collection 都使用 Fanout Exchange（广播模式）

    // Channel Fanout Exchange
    @Value("${spring.rabbitmq.exchangeChangeStreamChannel}")
    private String exchangeChangeStreamChannel;

    @Value("${spring.rabbitmq.queueChangeStreamChannelClickHouse}")
    private String queueChangeStreamChannelClickHouse;

    // UserSubscription Fanout Exchange
    @Value("${spring.rabbitmq.exchangeChangeStreamUserSub}")
    private String exchangeChangeStreamUserSub;

    @Value("${spring.rabbitmq.queueChangeStreamUserSubClickHouse}")
    private String queueChangeStreamUserSubClickHouse;

    // Message Fanout Exchange
    @Value("${spring.rabbitmq.exchangeChangeStreamMessage}")
    private String exchangeChangeStreamMessage;

    @Value("${spring.rabbitmq.queueChangeStreamMessageClickHouse}")
    private String queueChangeStreamMessageClickHouse;

    @Value("${spring.rabbitmq.queueChangeStreamMessageRedis}")
    private String queueChangeStreamMessageRedis;

    @Value("${spring.rabbitmq.queueChangeStreamMessageES}")
    private String queueChangeStreamMessageES;

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

    // ========== Change Stream 队列配置 ==========
    // 所有 Collection 都使用 Fanout Exchange（广播模式，方便后续扩展）

    // ========== Channel Change Stream 广播配置 ==========

    // Channel Fanout Exchange
    @Bean
    public FanoutExchange exchangeChangeStreamChannel() {
        return new FanoutExchange(exchangeChangeStreamChannel);
    }

    // Channel 变更队列 - ClickHouse 消费者
    @Bean
    public Queue queueChangeStreamChannelClickHouse() {
        return new Queue(queueChangeStreamChannelClickHouse, true);
    }

    @Bean
    public Binding bindingQueueChangeStreamChannelClickHouse() {
        return BindingBuilder.bind(queueChangeStreamChannelClickHouse()).to(exchangeChangeStreamChannel());
    }

    // ========== UserSubscription Change Stream 广播配置 ==========

    // UserSubscription Fanout Exchange
    @Bean
    public FanoutExchange exchangeChangeStreamUserSub() {
        return new FanoutExchange(exchangeChangeStreamUserSub);
    }

    // UserSubscription 变更队列 - ClickHouse 消费者
    @Bean
    public Queue queueChangeStreamUserSubClickHouse() {
        return new Queue(queueChangeStreamUserSubClickHouse, true);
    }

    @Bean
    public Binding bindingQueueChangeStreamUserSubClickHouse() {
        return BindingBuilder.bind(queueChangeStreamUserSubClickHouse()).to(exchangeChangeStreamUserSub());
    }

    // ========== Message Change Stream 广播配置 ==========

    // Message Fanout Exchange
    @Bean
    public FanoutExchange exchangeChangeStreamMessage() {
        return new FanoutExchange(exchangeChangeStreamMessage);
    }

    // Message 变更队列 - ClickHouse 消费者
    @Bean
    public Queue queueChangeStreamMessageClickHouse() {
        return new Queue(queueChangeStreamMessageClickHouse, true);
    }

    @Bean
    public Binding bindingQueueChangeStreamMessageClickHouse() {
        return BindingBuilder.bind(queueChangeStreamMessageClickHouse()).to(exchangeChangeStreamMessage());
    }

    // Message 变更队列 - Redis 消费者
    @Bean
    public Queue queueChangeStreamMessageRedis() {
        return new Queue(queueChangeStreamMessageRedis, true);
    }

    @Bean
    public Binding bindingQueueChangeStreamMessageRedis() {
        return BindingBuilder.bind(queueChangeStreamMessageRedis()).to(exchangeChangeStreamMessage());
    }

    // Message 变更队列 - ES 消费者
    @Bean
    public Queue queueChangeStreamMessageES() {
        return new Queue(queueChangeStreamMessageES, true);
    }

    @Bean
    public Binding bindingQueueChangeStreamMessageES() {
        return BindingBuilder.bind(queueChangeStreamMessageES()).to(exchangeChangeStreamMessage());
    }
}
