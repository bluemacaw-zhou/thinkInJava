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

    @Value("${spring.rabbitmq.queueMessage}")
    private String queueMessage;

    @Value("${spring.rabbitmq.routeMessage}")
    private String routeMessage;

    // ClickHouse专用队列配置
    @Value("${spring.rabbitmq.exchangeClickhouse}")
    private String exchangeClickhouse;

    @Value("${spring.rabbitmq.queueClickhouse}")
    private String queueClickhouse;

    @Value("${spring.rabbitmq.routeClickhouse}")
    private String routeClickhouse;

    // 原有消息队列配置
    @Bean
    public DirectExchange exchangeMessage() {
        return new DirectExchange(exchangeMessage);
    }

    @Bean
    public Queue queueMessage() {
        return new Queue(queueMessage);
    }

    @Bean
    public Binding bindingQueueChatMsgSaveV2() {
        return BindingBuilder.bind(queueMessage()).to(exchangeMessage()).with(routeMessage);
    }

    @Bean
    public DirectExchange exchangeClickhouse() {
        return new DirectExchange(exchangeClickhouse);
    }

    @Bean
    public Queue queueClickhouse() {
        return new Queue(queueClickhouse, true);
    }

    @Bean
    public Binding bindingQueueClickhouse() {
        return BindingBuilder.bind(queueClickhouse()).to(exchangeClickhouse()).with(routeClickhouse);
    }
}
