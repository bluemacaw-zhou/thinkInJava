package cn.com.wind.IMStarter.rabbitmq;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * @author shzhou.michael
 */
@Slf4j
@Aspect
@Component
@ConditionalOnClass(RabbitListener.class)
public class WmRabbitmqTraceAspect {

    @Around("@annotation(org.springframework.amqp.rabbit.annotation.RabbitListener)")
    public Object aroundRabbitListener(ProceedingJoinPoint joinPoint) throws Throwable {
        Message message = null;

        // 获取消息参数
        for (Object arg : joinPoint.getArgs()) {
            if (arg instanceof Message) {
                message = (Message) arg;
                break;
            }
        }

        // traceId处理
        if (message != null) {
            String traceId = message.getMessageProperties().getHeader("traceId");
            if (null == traceId || "".equals(traceId)) {
                traceId = UUID.randomUUID().toString();
            }
            MDC.put("traceId", traceId);
        }

        try {
            return joinPoint.proceed();
        } finally {
            MDC.remove("traceId");
        }
    }
}
