package iuh.fit.reportservice.infrastructure.config;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

public class AuthClientConfig {
    @Bean
    RequestInterceptor authServiceSecurityInterceptor(
            @Value("${app.jwt.secret}") String gatewaySecret
    ) {
        return template -> template.header("X-Gateway-Secret", gatewaySecret);
    }
}
