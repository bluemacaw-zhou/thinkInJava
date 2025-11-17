package cn.com.wind.IMStarter.http;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.UUID;

/**
 * @author shzhou.michael
 */
@Slf4j
@Component
public class RestTemplateConfig {
    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory ();
        factory.setConnectTimeout(60 * 1000);
        factory.setReadTimeout(5 * 60 * 1000);

        RestTemplate restTemplate = new RestTemplate(factory);
        restTemplate.setInterceptors(Collections.singletonList((request, body, execution) -> {
            String traceId = MDC.get("traceId");
            boolean needClean = false;

            try {
                if (traceId == null || "".equals(traceId)) {
                    needClean = true;
                    traceId = UUID.randomUUID().toString();
                }
                request.getHeaders().add("traceId", traceId);
                return execution.execute(request, body);
            } finally {
                if (needClean) {
                    MDC.remove("traceId");
                }
            }
        }));

        log.info("[Wind.IM.Springboot.Starter] http restTemplate init success");
        return restTemplate;
    }
}
