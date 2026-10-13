package iuh.fit.reportservice.infrastructure.jobs;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/**
 * Tình trạng job nền của service cho trang Giám sát (audit-service gọi; gateway chặn /internal/** từ ngoài).
 * Đếm thẳng bằng SQL: chỉ đọc, không cần qua use case.
 */
@RestController
@RequiredArgsConstructor
public class JobSummaryInternalController {
    private final JdbcTemplate jdbc;

    public record JobSummary(String job, String label, long pending, long running, long failedLast24h, Instant oldestPendingAt) {
    }

    @GetMapping("/internal/jobs/summary")
    public List<JobSummary> summary() {
        return List.of(new JobSummary("REPORT_EXPORTS", "Xuất báo cáo chạy nền",
                count("select count(*) from report_exports where status = 'PENDING'"),
                count("select count(*) from report_exports where status = 'RUNNING'"),
                count("select count(*) from report_exports where status = 'FAILED' and completed_at >= ?",
                        Timestamp.from(Instant.now().minusSeconds(86_400))),
                oldest("select min(created_at) from report_exports where status = 'PENDING'")));
    }

    private long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    /** Cột "timestamp" (không múi giờ) lưu theo giờ ứng dụng; "timestamptz" là thời điểm tuyệt đối. */
    private Instant oldest(String sql) {
        Timestamp value = jdbc.queryForObject(sql, Timestamp.class);
        return value == null ? null : value.toInstant();
    }

}
