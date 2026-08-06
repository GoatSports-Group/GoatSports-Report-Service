package iuh.fit.shared.config;

import iuh.fit.shared.aspect.AuditLogAspect;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.kafka.core.KafkaTemplate;

@Configuration
public class SharedAuditLogAutoConfiguration {

    @Bean
    public AuditLogAspect auditLogAspect(KafkaTemplate<String, Object> kafkaTemplate) {
        return new AuditLogAspect(kafkaTemplate);
    }

    @Configuration
    @ConditionalOnClass(name = "feign.RequestInterceptor")
    public static class FeignInterceptorConfiguration {
        @Bean
        public feign.RequestInterceptor feignClientInterceptor() {
            return new FeignClientInterceptor();
        }
    }
}
