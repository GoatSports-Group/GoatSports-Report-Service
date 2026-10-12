package iuh.fit.reportservice.application.dto.report;

import java.time.Instant;
import java.util.UUID;

/** Một lần xuất báo cáo chạy nền: trạng thái (PENDING → RUNNING → DONE / FAILED) và thông tin file khi xong. */
public record ReportExportView(
        UUID exportId,
        String reportType,
        String format,
        String status,
        String filterSummary,
        Integer rowCount,
        String fileName,
        Long fileSize,
        String failureReason,
        Instant createdAt,
        Instant completedAt,
        Instant expiresAt
) {
}
