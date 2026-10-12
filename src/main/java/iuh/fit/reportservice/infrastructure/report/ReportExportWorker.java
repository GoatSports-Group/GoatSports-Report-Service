package iuh.fit.reportservice.infrastructure.report;

import feign.FeignException;
import iuh.fit.reportservice.application.dto.report.OwnerBookingReportFilter;
import iuh.fit.reportservice.application.dto.response.base.BaseResponse;
import iuh.fit.reportservice.application.dto.venue.VenueOwnerBookingResponse;
import iuh.fit.reportservice.application.exception.ServiceException;
import iuh.fit.reportservice.infrastructure.external.client.NotificationClient;
import iuh.fit.reportservice.infrastructure.external.client.VenueClient;
import iuh.fit.reportservice.infrastructure.persistence.entity.ReportExportEntity;
import iuh.fit.reportservice.infrastructure.persistence.entity.ReportExportFileEntity;
import iuh.fit.reportservice.infrastructure.persistence.repository.JpaReportExportFileRepository;
import iuh.fit.reportservice.infrastructure.persistence.repository.JpaReportExportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import static iuh.fit.reportservice.infrastructure.report.ReportExportAdapter.DONE;
import static iuh.fit.reportservice.infrastructure.report.ReportExportAdapter.FAILED;
import static iuh.fit.reportservice.infrastructure.report.ReportExportAdapter.PENDING;

/**
 * Dựng file cho các job xuất báo cáo ở nền: nhận job bằng lease (nhiều instance không làm trùng, worker chết giữa chừng
 * thì job được nhận lại khi lease hết), lấy dữ liệu một lần từ venue-service, dựng Excel (danh sách) hoặc PDF (tóm tắt),
 * lưu file và báo cho người yêu cầu qua chuông thông báo.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReportExportWorker {
    static final Duration LEASE = Duration.ofMinutes(10);
    static final int MAX_ATTEMPTS = 3;
    private static final int BATCH = 3;
    private static final String XLSX_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final DateTimeFormatter FILE_STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmm").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    private final JpaReportExportRepository repository;
    private final JpaReportExportFileRepository fileRepository;
    private final VenueClient venueClient;
    private final ReportAdapter reportAdapter;
    private final BookingSummaryPdfRenderer summaryRenderer;
    private final NotificationClient notificationClient;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelayString = "${report.exports.poll-ms:2000}", initialDelayString = "${report.exports.initial-delay-ms:10000}")
    public void poll() {
        Instant now = Instant.now();
        for (UUID exportId : repository.findClaimable(now, PageRequest.of(0, BATCH))) {
            if (repository.claim(exportId, now, now.plus(LEASE)) == 1) process(exportId);
        }
    }

    /** Mỗi giờ: xóa báo cáo quá hạn giữ (file trước, rồi job). */
    @Scheduled(cron = "0 15 * * * *", zone = "Asia/Ho_Chi_Minh")
    public void purgeExpired() {
        Instant now = Instant.now();
        int files = fileRepository.deleteExpired(now);
        int jobs = repository.deleteExpired(now);
        if (jobs > 0) log.info("Purged {} expired report exports ({} files)", jobs, files);
    }

    void process(UUID exportId) {
        ReportExportEntity job = repository.findById(exportId).orElse(null);
        if (job == null) return;
        long startedAt = System.nanoTime();
        try {
            if (job.getAttempts() > MAX_ATTEMPTS) {
                throw new ServiceException("Tạo báo cáo bị gián đoạn nhiều lần. Vui lòng xuất lại.");
            }
            OwnerBookingReportFilter filter = objectMapper.readValue(job.getFilterJson(), OwnerBookingReportFilter.class);
            List<VenueOwnerBookingResponse> bookings = fetch(filter);
            boolean excel = "xlsx".equals(job.getFormat());
            byte[] data = excel
                    ? reportAdapter.renderBookingTable("xlsx", job.getFilterSummary(), null, bookings)
                    : summaryRenderer.render(bookings, job.getFilterSummary(), job.getCreatedAt());

            fileRepository.save(new ReportExportFileEntity(exportId, data));
            job.setStatus(DONE);
            job.setRowCount(bookings.size());
            job.setFileName((excel ? "don-dat-san-" : "tong-hop-don-dat-san-") + FILE_STAMP.format(job.getCreatedAt())
                    + (excel ? ".xlsx" : ".pdf"));
            job.setContentType(excel ? XLSX_TYPE : "application/pdf");
            job.setFileSize((long) data.length);
            job.setFailureReason(null);
            job.setLeaseUntil(null);
            job.setCompletedAt(Instant.now());
            repository.save(job);
            log.info("Report export {} done: format={}, rows={}, bytes={}, took={} ms", exportId, job.getFormat(),
                    bookings.size(), data.length, (System.nanoTime() - startedAt) / 1_000_000);
            notifyRequester(job, true);
        } catch (Exception exception) {
            fail(job, exception);
        }
    }

    private List<VenueOwnerBookingResponse> fetch(OwnerBookingReportFilter filter) throws ServiceException {
        BaseResponse<List<VenueOwnerBookingResponse>> response = venueClient.exportBookings(filter.venueId(),
                filter.venueCourtId(), filter.status(), filter.query(), filter.fromDate(), filter.toDate());
        if (response == null || response.getData() == null) {
            throw new IllegalStateException("Dịch vụ quản lý sân không trả dữ liệu đơn đặt sân.");
        }
        return response.getData();
    }

    /** Lỗi nghiệp vụ (vd quá 10.000 đơn, 4xx) thì dừng ngay; lỗi tạm thời (mất kết nối, 5xx) thì thử lại tới 3 lần. */
    private void fail(ReportExportEntity job, Exception exception) {
        String reason = reason(exception);
        boolean permanent = exception instanceof ServiceException
                || (exception instanceof FeignException feign && feign.status() >= 400 && feign.status() < 500);
        job.setFailureReason(reason);
        job.setLeaseUntil(null);
        if (!permanent && job.getAttempts() < MAX_ATTEMPTS) {
            job.setStatus(PENDING);
            repository.save(job);
            log.warn("Report export {} attempt {} failed, will retry: {}", job.getExportId(), job.getAttempts(), reason);
            return;
        }
        job.setStatus(FAILED);
        job.setCompletedAt(Instant.now());
        repository.save(job);
        log.warn("Report export {} failed: {}", job.getExportId(), reason, exception);
        notifyRequester(job, false);
    }

    private String reason(Exception exception) {
        String message = exception.getMessage();
        if (exception instanceof FeignException feign) {
            message = feign.status() <= 0 ? "Không kết nối được dịch vụ quản lý sân." : backendMessage(feign);
        }
        if (message == null || message.isBlank()) message = "Lỗi khi tạo báo cáo.";
        return message.length() > 500 ? message.substring(0, 497) + "..." : message;
    }

    /** venue-service trả lỗi dạng {"message": "..."}: lấy đúng câu đó để người dùng đọc được. */
    private String backendMessage(FeignException feign) {
        try {
            JsonNode body = objectMapper.readTree(feign.contentUTF8());
            String message = body.path("message").asText("");
            if (!message.isBlank()) return message;
        } catch (Exception ignored) {
            // thân lỗi không phải JSON
        }
        return "Dịch vụ quản lý sân báo lỗi (" + feign.status() + ").";
    }

    private void notifyRequester(ReportExportEntity job, boolean done) {
        String kind = "xlsx".equals(job.getFormat()) ? "Excel danh sách đơn" : "PDF tổng hợp";
        String title = done ? "Báo cáo đơn đặt sân đã sẵn sàng" : "Không tạo được báo cáo đơn đặt sân";
        String content = done
                ? kind + " · " + BookingSummaryPdfRenderer.number(job.getRowCount() == null ? 0 : job.getRowCount())
                + " đơn. Tải tại trang Đơn đặt sân, mục Báo cáo đã xuất (giữ 7 ngày)."
                : kind + ": " + job.getFailureReason();
        // Mỗi kết cục một eventId cố định: worker chạy lại cũng không gửi trùng.
        UUID eventId = UUID.nameUUIDFromBytes((job.getExportId() + (done ? ":done" : ":failed")).getBytes(StandardCharsets.UTF_8));
        try {
            notificationClient.createNotification(new NotificationClient.CreateNotificationRequest(eventId,
                    job.getRequestedBy(), title, content, "REPORT_EXPORT", "REPORT", "REPORT_EXPORT", job.getExportId()));
        } catch (Exception exception) {
            // Không chặn job: người dùng vẫn thấy trạng thái trong mục Báo cáo đã xuất.
            log.warn("Could not notify requester of report export {}: {}", job.getExportId(), exception.getMessage());
        }
    }
}
