package iuh.fit.reportservice.application.dto.report;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDetailReportResponse {
    private UUID userId;
    private String email;
    private String username;
    private String fullName;
    private String avatarUrl;
    private String status;
    private LocalDateTime createdAt;
    private String roleName;
}
