package iuh.fit.reportservice.infrastructure.report;

import iuh.fit.reportservice.application.dto.venue.VenueOwnerBookingResponse;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Số liệu tổng hợp cho bản PDF tóm tắt: tổng đơn, giá trị, đã thu, tỉ lệ hủy; rồi chia theo trạng thái, cơ sở, nguồn đặt.
 * Danh sách từng đơn nằm ở bản Excel.
 */
record BookingSummary(
        int bookings,
        double totalValue,
        double collected,
        double lostRate,
        List<Line> byStatus,
        List<Line> byVenue,
        List<Line> bySource
) {
    static final int TOP_VENUES = 15;
    /** Đơn không thành: hủy, hết hạn giữ chỗ, hoàn tiền, không đến nhận sân. */
    static final Set<String> LOST = Set.of("CANCELLED", "EXPIRED", "REFUND_PENDING", "REFUNDED", "NO_SHOW");
    private static final List<String> STATUS_ORDER = List.of("PENDING_PAYMENT", "CONFIRMED", "CHECKED_IN", "COMPLETED",
            "CANCELLED", "REFUND_PENDING", "REFUNDED", "EXPIRED", "NO_SHOW");

    record Line(String label, int bookings, double value, double collected) {
    }

    static BookingSummary of(List<VenueOwnerBookingResponse> bookings) {
        double totalValue = 0;
        double collected = 0;
        int lost = 0;
        for (VenueOwnerBookingResponse booking : bookings) {
            double price = price(booking);
            totalValue += price;
            collected += OwnerBookingReportMapper.paidAmount(booking, price);
            if (LOST.contains(booking.getStatus())) lost++;
        }

        List<Line> byStatus = group(bookings, VenueOwnerBookingResponse::getStatus).entrySet().stream()
                .sorted(Comparator.comparingInt(entry -> statusRank(entry.getKey())))
                .map(entry -> line(ReportLabels.bookingStatus(entry.getKey()), entry.getValue()))
                .toList();

        List<Line> venues = group(bookings, booking -> blank(booking.getVenueName(), "Chưa rõ cơ sở")).entrySet().stream()
                .map(entry -> line(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingInt(Line::bookings).reversed().thenComparing(Line::label))
                .toList();
        List<Line> byVenue = new ArrayList<>(venues.subList(0, Math.min(TOP_VENUES, venues.size())));
        if (venues.size() > TOP_VENUES) {
            List<Line> rest = venues.subList(TOP_VENUES, venues.size());
            byVenue.add(new Line("Các cơ sở khác (" + rest.size() + ")",
                    rest.stream().mapToInt(Line::bookings).sum(),
                    rest.stream().mapToDouble(Line::value).sum(),
                    rest.stream().mapToDouble(Line::collected).sum()));
        }

        List<Line> bySource = group(bookings, booking -> sourceLabel(booking.getSource())).entrySet().stream()
                .map(entry -> line(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingInt(Line::bookings).reversed())
                .toList();

        return new BookingSummary(bookings.size(), totalValue, collected,
                bookings.isEmpty() ? 0 : (double) lost / bookings.size(), byStatus, byVenue, bySource);
    }

    private static Map<String, List<VenueOwnerBookingResponse>> group(List<VenueOwnerBookingResponse> bookings,
                                                                     Function<VenueOwnerBookingResponse, String> key) {
        Map<String, List<VenueOwnerBookingResponse>> groups = new LinkedHashMap<>();
        for (VenueOwnerBookingResponse booking : bookings) {
            groups.computeIfAbsent(blank(key.apply(booking), "—"), ignored -> new ArrayList<>()).add(booking);
        }
        return groups;
    }

    private static Line line(String label, List<VenueOwnerBookingResponse> bookings) {
        double value = 0;
        double collected = 0;
        for (VenueOwnerBookingResponse booking : bookings) {
            double price = price(booking);
            value += price;
            collected += OwnerBookingReportMapper.paidAmount(booking, price);
        }
        return new Line(label, bookings.size(), value, collected);
    }

    private static double price(VenueOwnerBookingResponse booking) {
        return booking.getTotalPrice() == null ? 0 : Math.max(0, booking.getTotalPrice());
    }

    private static int statusRank(String status) {
        int index = STATUS_ORDER.indexOf(status);
        return index < 0 ? STATUS_ORDER.size() : index;
    }

    static String sourceLabel(String source) {
        if (source == null) return "Khác";
        return switch (source) {
            case "DIRECT" -> "Đặt trực tuyến";
            case "WALK_IN" -> "Khách tại quầy";
            case "MATCHMAKING" -> "Ghép trận";
            default -> source;
        };
    }

    private static String blank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
