package iuh.fit.reportservice.application.dto.report;

import iuh.fit.reportservice.application.enums.FormatType;
import iuh.fit.reportservice.application.exception.ServiceException;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Yêu cầu xuất báo cáo Tài chính (trang Tài chính của admin): {@code kind} REFUNDS (hoàn tiền) hoặc PAYOUTS (chi trả),
 * bộ lọc đúng như danh sách đang xem, trong kỳ [fromDate, toDate]. {@code scopeLabel} chỉ để in lên báo cáo
 * (vd "Cần xử lý", "Chủ sân rút tiền · Lỗi / đã hủy").
 */
public record FinanceExportRequest(
        String kind,
        String format,
        Filter filter,
        String scopeLabel
) {
    public static final Set<String> KINDS = Set.of("REFUNDS", "PAYOUTS");
    static final Set<String> REFUND_STATUSES = Set.of("CREATED", "AWAITING_CLAIM", "AWAITING_BANK_ACCOUNT", "PENDING",
            "PROCESSING", "SUCCEEDED", "FAILED", "MANUAL_REVIEW");
    static final Set<String> PAYOUT_STATUSES = Set.of("PENDING", "PROCESSING", "SUCCEEDED", "FAILED", "CANCELLED",
            "MANUAL_REVIEW");
    static final Set<String> PAYOUT_KINDS = Set.of("OWNER_WITHDRAWAL", "REFUND");
    static final int MAX_PERIOD_DAYS = 366;
    static final int MAX_PEOPLE = 100;

    /** {@code people}: mã người dùng khớp tên/email từ ô tìm (payment-service không giữ tên). */
    public record Filter(
            String payoutKind,
            List<String> statuses,
            String keyword,
            List<UUID> people,
            Integer olderThanDays,
            LocalDate fromDate,
            LocalDate toDate
    ) {
    }

    /** Chuẩn hóa để cùng yêu cầu ra cùng khóa dùng lại job, và chặn giá trị lạ trước khi tới payment-service. */
    public FinanceExportRequest normalized() throws ServiceException {
        String normalizedKind = kind == null ? "" : kind.trim().toUpperCase(Locale.ROOT);
        if (!KINDS.contains(normalizedKind)) throw new ServiceException("Loại báo cáo tài chính không hợp lệ.");
        String normalizedFormat = format == null ? "" : format.trim().toLowerCase(Locale.ROOT);
        if (!FormatType.XLSX.getValue().equals(normalizedFormat) && !FormatType.PDF.getValue().equals(normalizedFormat)) {
            throw new ServiceException("Báo cáo tài chính chỉ hỗ trợ định dạng PDF hoặc XLSX.");
        }
        if (filter == null || filter.fromDate() == null || filter.toDate() == null) {
            throw new ServiceException("Cần chọn kỳ (từ ngày, đến ngày) để xuất báo cáo.");
        }
        if (filter.fromDate().isAfter(filter.toDate())) throw new ServiceException("Kỳ báo cáo không hợp lệ.");
        if (filter.fromDate().plusDays(MAX_PERIOD_DAYS).isBefore(filter.toDate())) {
            throw new ServiceException("Kỳ báo cáo tối đa " + MAX_PERIOD_DAYS + " ngày.");
        }
        boolean refunds = "REFUNDS".equals(normalizedKind);
        Set<String> allowed = refunds ? REFUND_STATUSES : PAYOUT_STATUSES;
        List<String> statuses = filter.statuses() == null ? List.of() : filter.statuses().stream()
                .map(value -> value == null ? "" : value.trim().toUpperCase(Locale.ROOT)).distinct().sorted().toList();
        if (!allowed.containsAll(statuses)) throw new ServiceException("Trạng thái lọc không hợp lệ.");
        String payoutKind = null;
        if (!refunds) {
            payoutKind = filter.payoutKind() == null ? "OWNER_WITHDRAWAL" : filter.payoutKind().trim().toUpperCase(Locale.ROOT);
            if (!PAYOUT_KINDS.contains(payoutKind)) throw new ServiceException("Loại chi trả không hợp lệ.");
        }
        String keyword = filter.keyword() == null || filter.keyword().isBlank() ? null : filter.keyword().trim();
        if (keyword != null && keyword.length() > 100) keyword = keyword.substring(0, 100);
        List<UUID> people = keyword == null || filter.people() == null ? List.of()
                : filter.people().stream().filter(Objects::nonNull).distinct().sorted().limit(MAX_PEOPLE).toList();
        Integer olderThanDays = refunds && filter.olderThanDays() != null && filter.olderThanDays() > 0
                ? Math.min(filter.olderThanDays(), 3650) : null;
        String label = scopeLabel == null || scopeLabel.isBlank() ? null : scopeLabel.trim();
        if (label != null && label.length() > 120) label = label.substring(0, 120);
        return new FinanceExportRequest(normalizedKind, normalizedFormat, new Filter(payoutKind, statuses, keyword, people,
                olderThanDays, filter.fromDate(), filter.toDate()), label);
    }
}
