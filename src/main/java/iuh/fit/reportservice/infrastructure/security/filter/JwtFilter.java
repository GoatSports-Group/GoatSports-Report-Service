package iuh.fit.reportservice.infrastructure.security.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

@Slf4j
@Component
public class JwtFilter extends OncePerRequestFilter {

    @Value("${app.jwt.secret}")
    private String gatewaySecret;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            String secret = request.getHeader("X-Gateway-Secret");
            if (!hasText(secret) || !gatewaySecret.equals(secret)) {
                response.setStatus(HttpStatus.FORBIDDEN.value());
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("""
                        {
                          "statusCode":403,
                          "message":"Forbidden",
                          "error":"Phải đi qua API Gateway"
                        }
                        """);
                return;
            }

            String userId = request.getHeader("X-User-Id");
            String username = request.getHeader("X-User-Username");
            String email = request.getHeader("X-User-Email");
            String authoritiesRaw = request.getHeader("X-User-Authorities");

            List<SimpleGrantedAuthority> authorities = Collections.emptyList();
            if (hasText(authoritiesRaw)) {
                authorities = Arrays.stream(authoritiesRaw.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isBlank())
                        .map(SimpleGrantedAuthority::new)
                        .toList();
            }

            if (hasText(userId) || hasText(email) || hasText(username)) {
                String principal = hasText(email) ? email : username;

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                principal,
                                null,
                                authorities
                        );

                authentication.setDetails(userId);

                SecurityContextHolder.getContext().setAuthentication(authentication);
            }

            log.info("User {}, Authorities: {}", username, authorities);

            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
