package iuh.fit.shared.aspect;

import iuh.fit.shared.annotation.AuditLog;
import iuh.fit.shared.dto.ApiLogEvent;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.StandardReflectionParameterNameDiscoverer;
import org.springframework.expression.common.TemplateParserContext;
import org.aspectj.lang.reflect.MethodSignature;
import java.lang.reflect.Method;

@Aspect
@RequiredArgsConstructor
@Slf4j
public class AuditLogAspect {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ExpressionParser spelParser = new SpelExpressionParser();
    private final ParameterNameDiscoverer nameDiscoverer = new StandardReflectionParameterNameDiscoverer();

    @Value("${spring.kafka.topic.user-log}")
    private String userLogTopic;

    /** GOATSPORTS-AUTH-SERVICE -> auth-service, cùng tên với trang Giám sát. */
    @Value("${spring.application.name:}")
    private String applicationName;

    @Around("@annotation(auditLog)")
    public Object logAuditActivity(ProceedingJoinPoint joinPoint, AuditLog auditLog) throws Throwable {
        long startTime = System.currentTimeMillis();
        int statusCode = 200;
        String errorMessage = null;
        Object result = null;

        try {
            result = joinPoint.proceed();

            HttpServletResponse response = getResponse();
            if (response != null) {
                statusCode = response.getStatus();
            }
            return result;
        } catch (Throwable t) {
            errorMessage = t.getMessage();
            statusCode = 400;
            throw t;
        } finally {
            try {
                HttpServletRequest request = getRequest();

                String userId = getUserId(joinPoint, result);
                String actionDescription = parseDescription(auditLog.description(), joinPoint, result);
                String userDesc = userId.equals("anonymous") ? "Khách ẩn danh" : "Người dùng [" + userId + "]";

                String description;
                if (statusCode >= 200 && statusCode < 300) {
                    if (actionDescription.startsWith("Admin")) {
                        description = actionDescription + " thành công (" + userId + ")";
                    } else {
                        description = userDesc + " đã " + actionDescription.toLowerCase() + " thành công";
                    }
                } else {
                    if (actionDescription.startsWith("Admin")) {
                        description = actionDescription + " thất bại (" + userId + ")"
                                + (errorMessage != null ? " (" + errorMessage + ")" : "");
                    } else {
                        description = userDesc + " thực hiện " + actionDescription.toLowerCase() + " thất bại"
                                + (errorMessage != null ? " (" + errorMessage + ")" : "");
                    }
                }

                String ipAddress = request != null ? getIpAddress(request) : "127.0.0.1";

                ApiLogEvent event = ApiLogEvent.builder()
                        .userId(userId)
                        .action(auditLog.action())
                        .description(description)
                        .ipAddress(ipAddress)
                        .statusCode(statusCode)
                        .timestamp(LocalDateTime.now())
                        .errorMessage(errorMessage)
                        .sourceService(applicationName.toLowerCase().replaceFirst("^goatsports-", ""))
                        .correlationId(correlationId(request))
                        .httpMethod(request != null ? request.getMethod() : null)
                        .requestUri(request != null ? request.getRequestURI() : null)
                        .userAgent(request != null ? request.getHeader("User-Agent") : null)
                        .build();

                kafkaTemplate.send(userLogTopic, event)
                        .whenComplete((sendResult, ex) -> {
                            if (ex != null) {
                                log.error("Failed to send AOP business audit log to Kafka: {}", ex.getMessage());
                            } else {
                                log.debug("AOP Business log sent successfully: {} (Action: {}, User: {})",
                                        description, auditLog.action(), userId);
                            }
                        });

            } catch (Exception ex) {
                log.error("Error generating/sending AOP audit log event", ex);
            }
        }
    }

    /** CorrelationIdFilter của mỗi service đặt MDC "correlationId" theo header gateway gửi xuống. */
    private String correlationId(HttpServletRequest request) {
        String fromMdc = MDC.get("correlationId");
        if (fromMdc != null && !fromMdc.isBlank()) return fromMdc;
        return request != null ? request.getHeader("X-Correlation-Id") : null;
    }

    private HttpServletRequest getRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }

    private HttpServletResponse getResponse() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getResponse() : null;
    }

    private String getIpAddress(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }

    private String getUserId(ProceedingJoinPoint joinPoint, Object result) {
        if (result != null) {
            String val = getFieldValue(result, "userId");
            if (val != null)
                return val;
            val = getFieldValue(result, "username");
            if (val != null)
                return val;
            val = getNestedUserFieldValue(result);
            if (val != null)
                return val;
        }

        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !auth.getName().equals("anonymousUser")) {
                return auth.getName();
            }
        } catch (Exception e) {
            // Ignore
        }

        // Try checking HTTP headers (e.g. X-User-Id, X-Username)
        try {
            HttpServletRequest request = getRequest();
            if (request != null) {
                String headerUserId = request.getHeader("X-User-Id");
                if (headerUserId != null && !headerUserId.trim().isEmpty()) {
                    return headerUserId;
                }
                String headerUsername = request.getHeader("X-Username");
                if (headerUsername != null && !headerUsername.trim().isEmpty()) {
                    return headerUsername;
                }
            }
        } catch (Exception e) {
            // Ignore
        }

        // Try checking parameter names first (for direct String userId / String
        // username / String email args)
        try {
            String[] parameterNames = ((MethodSignature) joinPoint.getSignature())
                    .getParameterNames();
            Object[] args = joinPoint.getArgs();
            if (parameterNames != null && args != null) {
                for (int i = 0; i < Math.min(parameterNames.length, args.length); i++) {
                    String paramName = parameterNames[i];
                    Object arg = args[i];
                    if (arg != null) {
                        if ("userId".equalsIgnoreCase(paramName) || "username".equalsIgnoreCase(paramName)
                                || "email".equalsIgnoreCase(paramName)) {
                            return arg.toString();
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Ignore reflection issues on signature
        }

        // Try checking custom request DTO fields
        for (Object arg : joinPoint.getArgs()) {
            if (arg != null) {
                String val = getFieldValue(arg, "userId");
                if (val != null)
                    return val;
                val = getFieldValue(arg, "username");
                if (val != null)
                    return val;
                val = getFieldValue(arg, "email");
                if (val != null)
                    return val;
            }
        }
        return "anonymous";
    }

    private String getFieldValue(Object obj, String fieldName) {
        try {
            Method method;
            try {
                method = obj.getClass().getMethod(fieldName);
            } catch (NoSuchMethodException e) {
                String getterName = "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
                method = obj.getClass().getMethod(getterName);
            }
            Object val = method.invoke(obj);
            return val != null ? val.toString() : null;
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }

    private String getNestedUserFieldValue(Object obj) {
        try {
            Method userMethod;
            try {
                userMethod = obj.getClass().getMethod("user");
            } catch (NoSuchMethodException e) {
                userMethod = obj.getClass().getMethod("getUser");
            }
            Object userObj = userMethod.invoke(obj);
            if (userObj != null) {
                String val = getFieldValue(userObj, "username");
                if (val != null)
                    return val;
                val = getFieldValue(userObj, "userId");
                if (val != null)
                    return val;
            }
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }

    private String parseDescription(String template, ProceedingJoinPoint joinPoint, Object result) {
        if (template == null || !template.contains("#")) {
            return template;
        }
        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            Object[] args = joinPoint.getArgs();
            String[] parameterNames = nameDiscoverer.getParameterNames(method);
            EvaluationContext context = new StandardEvaluationContext();
            if (parameterNames != null && args != null) {
                for (int i = 0; i < Math.min(parameterNames.length, args.length); i++) {
                    context.setVariable(parameterNames[i], args[i]);
                }
            }
            if (result != null) {
                context.setVariable("result", result);
            }
            Expression expression = spelParser.parseExpression(template, new TemplateParserContext());
            return expression.getValue(context, String.class);
        } catch (Exception e) {
            log.error("Failed to parse SpEL expression for audit log description: {}", template, e);
            return template;
        }
    }
}
