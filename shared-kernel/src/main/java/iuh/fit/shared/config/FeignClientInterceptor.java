package iuh.fit.shared.config;

import jakarta.servlet.http.HttpServletRequest;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public class FeignClientInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            
            // Propagate X-User-Id header
            String userId = request.getHeader("X-User-Id");
            if (userId != null && !userId.trim().isEmpty()) {
                template.header("X-User-Id", userId);
            }
            
            // Propagate X-Username header
            String username = request.getHeader("X-Username");
            if (username != null && !username.trim().isEmpty()) {
                template.header("X-Username", username);
            }
            
            // Propagate Authorization header (JWT)
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && !authHeader.trim().isEmpty()) {
                template.header("Authorization", authHeader);
            }
        }
    }
}
