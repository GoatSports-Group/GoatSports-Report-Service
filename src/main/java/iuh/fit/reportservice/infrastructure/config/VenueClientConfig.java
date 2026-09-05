package iuh.fit.reportservice.infrastructure.config;

import feign.RequestInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public class VenueClientConfig {
    private static final String[] FORWARDED_HEADERS = {
            "X-User-Id",
            "X-User-Username",
            "X-User-Email",
            "X-User-Roles",
            "X-User-Authorities"
    };

    @Bean
    RequestInterceptor venueServiceSecurityInterceptor(
            @Value("${app.jwt.secret}") String gatewaySecret
    ) {
        return template -> {
            template.header("X-Gateway-Secret", gatewaySecret);
            if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
                return;
            }

            HttpServletRequest request = attributes.getRequest();
            for (String header : FORWARDED_HEADERS) {
                String value = request.getHeader(header);
                if (value != null && !value.isBlank()) template.header(header, value);
            }
        };
    }
}
