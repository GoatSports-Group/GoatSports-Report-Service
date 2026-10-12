package iuh.fit.reportservice.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "report_exports")
@Getter
@Setter
public class ReportExportEntity {
    @Id
    @Column(name = "export_id")
    private UUID exportId;
    @Column(name = "report_type", nullable = false, length = 40)
    private String reportType;
    @Column(name = "format", nullable = false, length = 10)
    private String format;
    @Column(name = "requested_by", nullable = false)
    private UUID requestedBy;
    @Column(name = "filter_json", nullable = false, length = 2000)
    private String filterJson;
    /** SHA-256 của loại + định dạng + bộ lọc: cùng yêu cầu trong vài phút thì dùng lại job cũ. */
    @Column(name = "filter_key", nullable = false, length = 64)
    private String filterKey;
    @Column(name = "filter_summary", nullable = false, length = 500)
    private String filterSummary;
    @Column(name = "status", nullable = false, length = 20)
    private String status;
    @Column(name = "row_count")
    private Integer rowCount;
    @Column(name = "file_name", length = 200)
    private String fileName;
    @Column(name = "content_type", length = 120)
    private String contentType;
    @Column(name = "file_size")
    private Long fileSize;
    @Column(name = "failure_reason", length = 500)
    private String failureReason;
    @Column(name = "attempts", nullable = false)
    private int attempts;
    /** Worker đang giữ job tới lúc này; quá hạn (worker chết giữa chừng) thì worker khác nhận lại. */
    @Column(name = "lease_until")
    private Instant leaseUntil;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "started_at")
    private Instant startedAt;
    @Column(name = "completed_at")
    private Instant completedAt;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
}
