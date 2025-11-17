package cn.com.wind.IMStarter.rabbitmq;

import org.slf4j.MDC;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.amqp.RabbitTemplateCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;

/**
 * @author shzhou.michael
 */
@Configuration
@ConditionalOnClass(RabbitTemplate.class)
public class WmRabbitMqConfig {
    @Bean
    public RabbitTemplateCustomizer customRabbitTemplate() {
        return rabbitTemplate -> {
            // 发送消息前处理
            rabbitTemplate.setBeforePublishPostProcessors(message -> {
                String traceId = MDC.get("traceId");
                if (null == traceId || "".equals(traceId)) {
                    traceId = UUID.randomUUID().toString();
                    message.getMessageProperties().setHeader("mq-generated-trace", "true");
                }
                message.getMessageProperties().setHeader("traceId", traceId);
                return message;
            });

            rabbitTemplate.setAfterReceivePostProcessors(message -> {
                Boolean mqGeneratedTrace = message.getMessageProperties().getHeader("mq-generated-trace");
                if (mqGeneratedTrace) {
                    MDC.remove("traceId");
                }
                return message;
            });
        };
    }
}
