package iuh.fit.reportservice.infrastructure.external.client;

import iuh.fit.reportservice.infrastructure.config.AuthClientConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.UUID;

@FeignClient(
        name = "GOATSPORTS-NOTIFICATION-SERVICE",
        url = "${notification.service.url}",
        path = "/internal/notifications",
        configuration = AuthClientConfig.class
)
public interface NotificationClient {
    @PostMapping
    void createNotification(@RequestBody CreateNotificationRequest request);

    record CreateNotificationRequest(
            UUID eventId,
            UUID receiverId,
            String title,
            String content,
            String type,
            String sourceService,
            String referenceType,
            UUID referenceId
    ) {
    }
}
