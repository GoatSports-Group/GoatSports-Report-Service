package iuh.fit.reportservice.infrastructure.report;

import iuh.fit.reportservice.application.dto.report.AdminBookingExportRequest;
import iuh.fit.reportservice.application.dto.report.FinanceExportRequest;
import iuh.fit.reportservice.application.dto.report.OwnerBookingReportFilter;
import iuh.fit.reportservice.application.dto.report.ReportExportFile;
import iuh.fit.reportservice.application.dto.report.ReportExportView;
import iuh.fit.reportservice.application.exception.ServiceException;
import iuh.fit.reportservice.application.port.report.ReportExportPort;
import iuh.fit.reportservice.infrastructure.persistence.entity.ReportExportEntity;
import iuh.fit.reportservice.infrastructure.persistence.repository.JpaReportExportFileRepository;
import iuh.fit.reportservice.infrastructure.persistence.repository.JpaReportExportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Tạo / tra cứu / tải job xuất báo cáo. Việc dựng file nằm ở {@link ReportExportWorker}. */
@Service
@RequiredArgsConstructor
public class ReportExportAdapter implements ReportExportPort {
    static final String ADMIN_BOOKINGS = "ADMIN_BOOKINGS";
    static final String PENDING = "PENDING";
    static final String RUNNING = "RUNNING";
    static final String DONE = "DONE";
    static final String FAILED = "FAILED";
    static final Duration RETENTION = Duration.ofDays(7);
    /** Bấm xuất lại đúng bộ lọc trong khoảng này thì dùng lại job cũ thay vì dựng lại. */
    static final Duration REUSE_WINDOW = Duration.ofMinutes(5);

    private final JpaReportExportRepository repository;
    private final JpaReportExportFileRepository fileRepository;
    private final ObjectMapper objectMapper;

    @Override
    public ReportExportView requestAdminBookingExport(UUID requesterId, AdminBookingExportRequest request)
            throws ServiceException {
        if (request == null || request.filter() == null) throw new ServiceException("Thiếu bộ lọc báo cáo.");
        String format = request.format() == null ? "" : request.format().trim().toLowerCase(Locale.ROOT);
        OwnerBookingReportFilter filter = normalize(request.filter());
        OwnerBookingReportFilter.validate(filter, format);

        return createJob(requesterId, ADMIN_BOOKINGS, format, objectMapper.writeValueAsString(filter),
                summary(filter, request.venueName(), request.courtName()));
    }

    @Override
    public ReportExportView requestFinanceExport(UUID requesterId, FinanceExportRequest request) throws ServiceException {
        if (request == null) throw new ServiceException("Thiếu yêu cầu xuất báo cáo.");
        FinanceExportRequest normalized = request.normalized();
        return createJob(requesterId, FinanceReportRenderer.reportType(normalized.kind()), normalized.format(),
                objectMapper.writeValueAsString(normalized.filter()), financeSummary(normalized));
    }

    /** Job mới ở PENDING (worker dựng file ở nền); cùng loại, định dạng và bộ lọc trong 5 phút thì dùng lại job cũ. */
    private ReportExportView createJob(UUID requesterId, String reportType, String format, String filterJson,
                                       String filterSummary) {
        String filterKey = sha256(reportType + "|" + format + "|" + filterJson);
        Instant now = Instant.now();
        Optional<ReportExportEntity> reusable = repository.findFirstByRequestedByAndFilterKeyAndStatusInAndCreatedAtAfterOrderByCreatedAtDesc(
                requesterId, filterKey, Set.of(PENDING, RUNNING, DONE), now.minus(REUSE_WINDOW));
        if (reusable.isPresent()) return toView(reusable.get());

        ReportExportEntity entity = new ReportExportEntity();
        entity.setExportId(UUID.randomUUID());
        entity.setReportType(reportType);
        entity.setFormat(format);
        entity.setRequestedBy(requesterId);
        entity.setFilterJson(filterJson);
        entity.setFilterKey(filterKey);
        entity.setFilterSummary(filterSummary);
        entity.setStatus(PENDING);
        entity.setAttempts(0);
        entity.setCreatedAt(now);
        entity.setExpiresAt(now.plus(RETENTION));

        return toView(repository.save(entity));
    }

    @Override
    public List<ReportExportView> listExports(UUID requesterId, Collection<String> reportTypes) {
        Collection<String> types = reportTypes == null || reportTypes.isEmpty() ? List.of(ADMIN_BOOKINGS) : reportTypes;
        return repository.findTop20ByRequestedByAndReportTypeInAndExpiresAtAfterOrderByCreatedAtDesc(
                        requesterId, types, Instant.now())
                .stream().map(ReportExportAdapter::toView).toList();
    }

    @Override
    public ReportExportView getExport(UUID requesterId, UUID exportId) throws ServiceException {
        return toView(owned(requesterId, exportId));
    }

    @Override
    public ReportExportFile download(UUID requesterId, UUID exportId) throws ServiceException {
        ReportExportEntity entity = owned(requesterId, exportId);
        if (!DONE.equals(entity.getStatus())) throw new ServiceException("Báo cáo chưa sẵn sàng để tải.");
        byte[] data = fileRepository.findById(exportId)
                .orElseThrow(() -> new ServiceException("File báo cáo không còn, vui lòng xuất lại."))
                .getData();
        return new ReportExportFile(entity.getFileName(), entity.getContentType(), data);
    }

    /** Chỉ người tạo mới thấy / tải được báo cáo của mình; báo cáo hết hạn coi như không còn. */
    private ReportExportEntity owned(UUID requesterId, UUID exportId) throws ServiceException {
        return repository.findById(exportId)
                .filter(entity -> entity.getRequestedBy().equals(requesterId))
                .filter(entity -> entity.getExpiresAt().isAfter(Instant.now()))
                .orElseThrow(() -> new ServiceException("Không tìm thấy báo cáo hoặc báo cáo đã hết hạn."));
    }

    /** Bộ lọc admin không có lọc theo thanh toán; chuỗi rỗng coi như không lọc (để cùng yêu cầu ra cùng khóa). */
    private static OwnerBookingReportFilter normalize(OwnerBookingReportFilter filter) {
        return new OwnerBookingReportFilter(filter.venueId(), filter.venueCourtId(), ReportLabels.blankToNull(filter.status()),
                null, ReportLabels.blankToNull(filter.query() == null ? null : filter.query().trim()),
                filter.fromDate(), filter.toDate());
    }

    static String summary(OwnerBookingReportFilter filter, String venueName, String courtName) {
        List<String> parts = new ArrayList<>();
        if (filter.venueId() != null) parts.add("Cơ sở: " + (blank(venueName) ? "đã chọn" : venueName.trim()));
        if (filter.venueCourtId() != null) parts.add("Sân: " + (blank(courtName) ? "đã chọn" : courtName.trim()));
        String rest = OwnerBookingReportMapper.filterSummary(filter);
        if (parts.isEmpty() || !rest.startsWith("Phạm vi:")) parts.add(rest);
        String summary = String.join("  |  ", parts);
        return summary.length() > 500 ? summary.substring(0, 497) + "..." : summary;
    }

    /** "Kỳ: 01/10/2026 – 13/10/2026  |  Cần xử lý  |  Tìm: "GSA02"  |  Tạo quá 7 ngày" (in lên báo cáo). */
    static String financeSummary(FinanceExportRequest request) {
        FinanceExportRequest.Filter filter = request.filter();
        List<String> parts = new ArrayList<>();
        parts.add("Kỳ: " + ReportLabels.DATE.format(filter.fromDate()) + " – " + ReportLabels.DATE.format(filter.toDate()));
        if (request.scopeLabel() != null) parts.add(request.scopeLabel());
        if (filter.keyword() != null) parts.add("Tìm: \"" + filter.keyword() + "\"");
        if (filter.olderThanDays() != null) parts.add("Tạo quá " + filter.olderThanDays() + " ngày");
        String summary = String.join("  |  ", parts);
        return summary.length() > 500 ? summary.substring(0, 497) + "..." : summary;
    }

    static ReportExportView toView(ReportExportEntity entity) {
        return new ReportExportView(entity.getExportId(), entity.getReportType(), entity.getFormat(), entity.getStatus(),
                entity.getFilterSummary(), entity.getRowCount(), entity.getFileName(), entity.getFileSize(),
                entity.getFailureReason(), entity.getCreatedAt(), entity.getCompletedAt(), entity.getExpiresAt());
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
