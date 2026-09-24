package iuh.fit.reportservice.infrastructure.report;

import iuh.fit.reportservice.application.dto.report.OwnerBookingInvoiceReport;
import iuh.fit.reportservice.application.dto.report.OwnerBookingReportFilter;
import iuh.fit.reportservice.application.dto.report.OwnerBookingReportRow;
import iuh.fit.reportservice.application.dto.venue.VenueOwnerBookingResponse;
import iuh.fit.reportservice.application.dto.venue.VenueOwnerPaymentResponse;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static iuh.fit.reportservice.infrastructure.report.ReportLabels.blankToNull;
import static iuh.fit.reportservice.infrastructure.report.ReportLabels.valueOrDash;

/**
 * Chuyển dữ liệu đơn đặt sân (lấy từ venue-service) thành dòng báo cáo và hóa đơn.
 * Thuần tính toán, không gọi mạng hay Jasper — nên test được riêng phần tiền và trạng thái thanh toán.
 */
final class OwnerBookingReportMapper {
    private static final Set<String> WAITING = Set.of("CREATED", "PENDING");
    private static final Set<String> UNSUCCESSFUL = Set.of("FAILED", "EXPIRED", "CANCELLED");

    private OwnerBookingReportMapper() {
    }

    // ---------------------------------------------------------------- danh sách đơn

    static List<OwnerBookingReportRow> toRows(List<VenueOwnerBookingResponse> bookings, String paymentFilter) {
        List<VenueOwnerBookingResponse> filtered = bookings.stream()
                .filter(booking -> matchesPaymentFilter(booking, paymentFilter))
                .toList();
        List<OwnerBookingReportRow> rows = new ArrayList<>(filtered.size());
        for (int index = 0; index < filtered.size(); index++) {
            VenueOwnerBookingResponse booking = filtered.get(index);
            rows.add(OwnerBookingReportRow.builder()
                    .sequence(index + 1)
                    .bookingCode(valueOrDash(booking.getBookingCode()))
                    .customerName(customerName(booking))
                    .customerPhone(valueOrDash(booking.getWalkInCustomerPhone()))
                    .venueName(valueOrDash(booking.getVenueName()))
                    .courtName(valueOrDash(booking.getCourtName()))
                    .playDate(booking.getPlayDate() == null ? "—" : ReportLabels.DATE.format(booking.getPlayDate()))
                    .timeRange(timeRange(booking))
                    .totalPrice(booking.getTotalPrice() == null ? 0D : booking.getTotalPrice())
                    .paymentStatus(paymentLabel(booking))
                    .bookingStatus(ReportLabels.bookingStatus(booking.getStatus()))
                    .build());
        }
        return rows;
    }

    /** Dòng mô tả bộ lọc in dưới tiêu đề báo cáo, để người đọc bản in biết phạm vi dữ liệu. */
    static String filterSummary(OwnerBookingReportFilter filter) {
        List<String> values = new ArrayList<>();
        if (filter.fromDate() != null || filter.toDate() != null) {
            values.add("Ngày: " + (filter.fromDate() == null ? "..." : ReportLabels.DATE.format(filter.fromDate()))
                    + " - " + (filter.toDate() == null ? "..." : ReportLabels.DATE.format(filter.toDate())));
        }
        if (blankToNull(filter.status()) != null) {
            values.add("Trạng thái đơn: " + ReportLabels.bookingStatus(filter.status()));
        }
        if (blankToNull(filter.paymentStatus()) != null) {
            values.add("Thanh toán: " + switch (filter.paymentStatus().toUpperCase(Locale.ROOT)) {
                case "PAID" -> "Đã thanh toán";
                case "UNPAID" -> "Chưa thanh toán";
                case "FAILED" -> "Thất bại";
                default -> filter.paymentStatus();
            });
        }
        if (blankToNull(filter.query()) != null) values.add("Từ khóa: " + filter.query().trim());
        return values.isEmpty() ? "Phạm vi: Tất cả đơn đặt sân" : String.join("  |  ", values);
    }

    // ---------------------------------------------------------------- hóa đơn

    static OwnerBookingInvoiceReport toInvoice(VenueOwnerBookingResponse booking) {
        double total = nonNegative(booking.getTotalPrice());
        double paid = paidAmount(booking, total);
        // Sai số làm tròn vài đồng không được biến hóa đơn đã trả đủ thành "còn nợ".
        boolean fullyPaid = isPaid(booking) || paid + 0.01D >= total;
        if (fullyPaid) paid = total;
        double outstanding = fullyPaid ? 0D : Math.max(0D, total - paid);
        VenueOwnerPaymentResponse payment = displayPayment(booking);

        return OwnerBookingInvoiceReport.builder()
                .invoiceNumber("HD-" + valueOrDash(booking.getBookingCode()))
                .issuedAt(ReportLabels.generatedAt())
                .bookingCode(valueOrDash(booking.getBookingCode()))
                .bookingStatus(ReportLabels.bookingStatus(booking.getStatus()))
                .createdAt(ReportLabels.timeline(booking.getCreatedAt()))
                .customerName(customerName(booking))
                .customerPhone(valueOrDash(booking.getWalkInCustomerPhone()))
                .customerType(ReportLabels.bookingChannel(booking.getSource()))
                .venueName(valueOrDash(booking.getVenueName()))
                .courtName(valueOrDash(booking.getCourtName()))
                .playDate(booking.getPlayDate() == null ? "—" : ReportLabels.DATE.format(booking.getPlayDate()))
                .timeRange(timeRange(booking))
                .bookingChannel(ReportLabels.bookingChannel(booking.getSource()))
                .itemDescription("Tiền sân " + valueOrDash(booking.getCourtName()) + " · " + timeRange(booking))
                .totalAmount(ReportLabels.money(total))
                .serviceFeeAmount(ReportLabels.money(0D))
                .discountAmount(ReportLabels.money(0D))
                .finalAmount(ReportLabels.money(total))
                .depositAmount(ReportLabels.money(nonNegative(booking.getDepositAmount())))
                .paidAmount(ReportLabels.money(paid))
                .outstandingAmount(ReportLabels.money(outstanding))
                .paymentMethod(paymentMethodLabel(payment))
                .transactionCode(payment == null ? "—" : valueOrDash(payment.getProviderTransactionId()))
                .paymentStatus(invoicePaymentStatus(booking, paid, total, fullyPaid))
                .paidAt(payment == null || payment.getPaidAt() == null ? "—" : ReportLabels.DATE_TIME.format(payment.getPaidAt()))
                .historyLines(historyLines(booking))
                .build();
    }

    /**
     * Số tiền đã thu = các lần thanh toán thành công + cọc/tiền còn lại mà booking đã ghi nhận nhưng không nằm
     * trong danh sách payment trả về (ví dụ thu tiền mặt ghi thẳng vào booking). Không vượt quá tổng tiền.
     */
    static double paidAmount(VenueOwnerBookingResponse booking, double total) {
        List<VenueOwnerPaymentResponse> succeeded = payments(booking).stream()
                .filter(payment -> "SUCCEEDED".equals(payment.getStatus()))
                .toList();
        double paid = succeeded.stream().mapToDouble(payment -> nonNegative(payment.getAmount())).sum();
        Set<UUID> succeededIds = succeeded.stream()
                .map(VenueOwnerPaymentResponse::getPaymentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (booking.getDepositPaymentId() != null && !succeededIds.contains(booking.getDepositPaymentId())) {
            paid += nonNegative(booking.getDepositAmount());
        }
        if (booking.getRemainingPaymentId() != null && !succeededIds.contains(booking.getRemainingPaymentId())) {
            paid += nonNegative(booking.getRemainingAmount());
        }
        return Math.min(total, Math.max(0D, paid));
    }

    static String invoicePaymentStatus(VenueOwnerBookingResponse booking, double paid, double total, boolean fullyPaid) {
        if (fullyPaid) return "Đã thanh toán đủ";
        if (paid > 0D && paid < total) return "Chưa thanh toán đủ";
        if (payments(booking).stream().anyMatch(payment -> WAITING.contains(payment.getStatus()))) {
            return "Đang chờ thanh toán";
        }
        if (hasFailedPayment(booking)) return "Thanh toán chưa thành công";
        return "Chưa thanh toán";
    }

    private static String historyLines(VenueOwnerBookingResponse booking) {
        List<String> lines = new ArrayList<>();
        lines.add(ReportLabels.timeline(booking.getCreatedAt()) + "   Tạo đơn đặt sân");
        payments(booking).stream()
                .sorted(Comparator.comparing(OwnerBookingReportMapper::paymentTimestamp,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .forEach(payment -> lines.add(
                        ReportLabels.timeline(paymentTimestamp(payment)) + "   " + paymentTimelineLabel(payment)));
        lines.add(ReportLabels.timeline(booking.getUpdatedAt() == null ? booking.getCreatedAt() : booking.getUpdatedAt())
                + "   " + ReportLabels.bookingStatus(booking.getStatus()));
        return String.join("\n", lines);
    }

    private static String paymentTimelineLabel(VenueOwnerPaymentResponse payment) {
        String label = "BOOKING_DEPOSIT".equals(payment.getPurpose()) ? "Thanh toán tiền cọc" : "Thanh toán còn lại";
        if ("SUCCEEDED".equals(payment.getStatus())) return label;
        return label + " – " + ReportLabels.paymentAttemptStatus(payment.getStatus());
    }

    /** Lần thanh toán in trên hóa đơn: mới nhất có thông tin phương thức, không có thì lấy lần mới nhất. */
    private static VenueOwnerPaymentResponse displayPayment(VenueOwnerBookingResponse booking) {
        List<VenueOwnerPaymentResponse> sorted = payments(booking).stream()
                .sorted(Comparator.comparing(OwnerBookingReportMapper::paymentTimestamp,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        return sorted.stream().filter(OwnerBookingReportMapper::hasPaymentMethod).findFirst()
                .orElse(sorted.isEmpty() ? null : sorted.get(0));
    }

    private static LocalDateTime paymentTimestamp(VenueOwnerPaymentResponse payment) {
        return payment.getPaidAt() != null ? payment.getPaidAt() : payment.getCreatedAt();
    }

    private static boolean hasPaymentMethod(VenueOwnerPaymentResponse payment) {
        return payment != null && (blankToNull(payment.getMethod()) != null
                || blankToNull(payment.getProvider()) != null
                || blankToNull(payment.getProviderTransactionId()) != null);
    }

    private static String paymentMethodLabel(VenueOwnerPaymentResponse payment) {
        if (payment == null) return "—";
        String method = blankToNull(payment.getMethod());
        String provider = blankToNull(payment.getProvider());
        String transactionCode = blankToNull(payment.getProviderTransactionId());
        // Thu tiền mặt tại quầy sinh mã giao dịch dạng "CASH-...".
        if ("CASH".equalsIgnoreCase(method)
                || transactionCode != null && transactionCode.toUpperCase(Locale.ROOT).startsWith("CASH-")) {
            return "Tiền mặt";
        }
        if ("BANK_TRANSFER".equalsIgnoreCase(method) || "PAYOS".equalsIgnoreCase(provider)) {
            return "Ví điện tử (PayOS)";
        }
        return "—";
    }

    // ---------------------------------------------------------------- trạng thái thanh toán

    private static boolean matchesPaymentFilter(VenueOwnerBookingResponse booking, String paymentFilter) {
        String normalized = blankToNull(paymentFilter);
        if (normalized == null) return true;
        return switch (normalized.toUpperCase(Locale.ROOT)) {
            case "PAID" -> isPaid(booking);
            case "UNPAID" -> !isPaid(booking);
            case "FAILED" -> hasFailedPayment(booking);
            default -> true;
        };
    }

    /** Đã trả đủ khi phần tiền còn lại (sau cọc) đã được thanh toán. */
    private static boolean isPaid(VenueOwnerBookingResponse booking) {
        return booking.getRemainingPaymentId() != null || payments(booking).stream()
                .anyMatch(payment -> "BOOKING_REMAINING".equals(payment.getPurpose())
                        && "SUCCEEDED".equals(payment.getStatus()));
    }

    private static boolean hasFailedPayment(VenueOwnerBookingResponse booking) {
        return payments(booking).stream().anyMatch(payment -> UNSUCCESSFUL.contains(payment.getStatus()));
    }

    private static String paymentLabel(VenueOwnerBookingResponse booking) {
        if (isPaid(booking)) return "Đã thanh toán";
        if (hasFailedPayment(booking)) return "Thanh toán thất bại";
        boolean pending = payments(booking).stream().anyMatch(payment ->
                "BOOKING_REMAINING".equals(payment.getPurpose()) && WAITING.contains(payment.getStatus()));
        return pending ? "Chờ thanh toán" : "Chưa thanh toán";
    }

    private static List<VenueOwnerPaymentResponse> payments(VenueOwnerBookingResponse booking) {
        return booking.getPayments() == null ? Collections.emptyList() : booking.getPayments();
    }

    // ---------------------------------------------------------------- hiển thị

    /** Khách vãng lai hiện tên nhập tại quầy; người chơi app chỉ hiện mã rút gọn (report-service không giữ tên). */
    private static String customerName(VenueOwnerBookingResponse booking) {
        String walkInName = blankToNull(booking.getWalkInCustomerName());
        if (walkInName != null) return walkInName;
        if (booking.getPlayerId() == null) return "Khách vãng lai";
        String playerId = booking.getPlayerId().toString().replace("-", "");
        return "Khách #" + playerId.substring(0, Math.min(8, playerId.length())).toUpperCase(Locale.ROOT);
    }

    private static String timeRange(VenueOwnerBookingResponse booking) {
        if (booking.getStartTime() == null || booking.getEndTime() == null) return "—";
        return ReportLabels.TIME.format(booking.getStartTime()) + " - " + ReportLabels.TIME.format(booking.getEndTime());
    }

    private static double nonNegative(Double value) {
        return value == null ? 0D : Math.max(0D, value);
    }
}
