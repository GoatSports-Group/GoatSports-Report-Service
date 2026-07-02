package iuh.fit.reportservice.application.dto.report;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserReportResponse {
    private UUID userId;
    private String fullName;
    private String email;
    private String roleName;
    private String status;
    private LocalDateTime createdAt;
}
