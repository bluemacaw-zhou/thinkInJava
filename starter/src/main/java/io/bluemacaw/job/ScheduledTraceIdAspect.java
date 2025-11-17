package cn.com.wind.IMStarter.job;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * @author shzhou.michael
 */
@Slf4j
@Aspect
@Component
public class ScheduledTraceIdAspect {
    @Around("@annotation(org.springframework.scheduling.annotation.Scheduled)")
    public Object aroundScheduledTask(ProceedingJoinPoint joinPoint) throws Throwable {
        String traceId = MDC.get("traceId");
        boolean needClean = false;

        try {
            if (null == traceId || "".equals(traceId)) {
                needClean = true;
                traceId = UUID.randomUUID().toString();
            }

            MDC.put("traceId", traceId);
            return joinPoint.proceed();
        } finally {
            if (needClean) {
                MDC.remove("traceId");
            }
        }
    }
}
