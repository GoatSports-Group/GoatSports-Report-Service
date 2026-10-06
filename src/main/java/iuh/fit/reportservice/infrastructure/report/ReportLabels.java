package iuh.fit.reportservice.infrastructure.report;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Nhãn tiếng Việt và định dạng hiển thị dùng chung cho mọi báo cáo (một chỗ duy nhất để đổi cách gọi tên). */
final class ReportLabels {
    static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy - HH:mm");
    private static final DateTimeFormatter TIMELINE = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");
    private static final DateTimeFormatter GENERATED_AT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final String DASH = "—";

    private ReportLabels() {
    }

    static String bookingStatus(String status) {
        if (status == null) return DASH;
        return switch (status) {
            case "PENDING_PAYMENT" -> "Chờ thanh toán";
            case "CONFIRMED" -> "Đã xác nhận";
            case "CHECKED_IN" -> "Đã nhận sân";
            case "COMPLETED" -> "Hoàn tất";
            case "CANCELLED" -> "Đã hủy";
            case "REFUND_PENDING" -> "Chờ hoàn tiền";
            case "REFUNDED" -> "Đã hoàn tiền";
            case "EXPIRED" -> "Hết hạn";
            case "NO_SHOW" -> "Không đến nhận sân";
            default -> status;
        };
    }

    /** Trạng thái một lần thanh toán (payment attempt) trên dòng thời gian của hóa đơn. */
    static String paymentAttemptStatus(String status) {
        if (status == null) return "Chưa ghi nhận";
        return switch (status) {
            case "CREATED", "PENDING" -> "Đang chờ";
            case "FAILED" -> "Thất bại";
            case "EXPIRED" -> "Hết hạn";
            case "CANCELLED" -> "Đã hủy";
            case "SUCCEEDED" -> "Thành công";
            default -> status;
        };
    }

    /** Trạng thái khoản thanh toán trong thống kê doanh thu. */
    static String paymentStatus(String status) {
        if (status == null) return "Không xác định";
        return switch (status) {
            case "CREATED" -> "Đã tạo";
            case "PENDING" -> "Đang xử lý";
            case "SUCCEEDED" -> "Thành công";
            case "FAILED" -> "Thất bại";
            case "CANCELLED" -> "Đã hủy";
            case "EXPIRED" -> "Hết hạn";
            case "PARTIALLY_REFUNDED" -> "Hoàn một phần";
            case "REFUNDED" -> "Đã hoàn";
            default -> status;
        };
    }

    static String bookingChannel(String source) {
        if (source == null) return DASH;
        return switch (source) {
            case "WALK_IN" -> "Khách tại quầy";
            case "DIRECT" -> "Đặt trực tuyến";
            case "AI_MATCHMAKING" -> "Ghép trận";
            default -> source;
        };
    }

    static String money(double value) {
        return String.format(Locale.of("vi", "VN"), "%,.0fđ", value);
    }

    static String timeline(LocalDateTime value) {
        return value == null ? DASH : TIMELINE.format(value);
    }

    /** Thời điểm tạo báo cáo, in ở đầu trang. */
    static String generatedAt() {
        return GENERATED_AT.format(LocalDateTime.now());
    }

    static String today() {
        return DATE.format(LocalDateTime.now());
    }

    static String valueOrDash(String value) {
        String normalized = blankToNull(value);
        return normalized == null ? DASH : normalized;
    }

    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
