package iuh.fit.reportservice.infrastructure.report;

import iuh.fit.reportservice.application.dto.venue.VenueOwnerBookingResponse;
import iuh.fit.reportservice.application.dto.venue.VenueOwnerPaymentResponse;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BookingSummaryTest {

    @Test
    void totalsCollectedMoneyAndLostRateAndGroupsByStatusVenueSource() {
        List<VenueOwnerBookingResponse> bookings = List.of(
                booking("Goat Arena", "COMPLETED", "DIRECT", 300_000, 300_000),
                booking("Goat Arena", "CANCELLED", "WALK_IN", 200_000, 0),
                booking("Sân Mỹ Đình", "CONFIRMED", "DIRECT", 500_000, 150_000));

        BookingSummary summary = BookingSummary.of(bookings);

        assertThat(summary.bookings()).isEqualTo(3);
        assertThat(summary.totalValue()).isEqualTo(1_000_000);
        assertThat(summary.collected()).isEqualTo(450_000);
        assertThat(summary.lostRate()).isEqualTo(1.0 / 3);
        // Thứ tự trạng thái theo vòng đời đơn, không theo số lượng.
        assertThat(summary.byStatus()).extracting(BookingSummary.Line::label)
                .containsExactly("Đã xác nhận", "Hoàn tất", "Đã hủy");
        assertThat(summary.byVenue().get(0)).isEqualTo(new BookingSummary.Line("Goat Arena", 2, 500_000, 300_000));
        assertThat(summary.bySource().get(0).label()).isEqualTo("Đặt trực tuyến");
    }

    @Test
    void foldsVenuesBeyondTheTop15IntoOneRow() {
        List<VenueOwnerBookingResponse> bookings = new ArrayList<>();
        for (int venue = 1; venue <= 18; venue++) {
            for (int count = 0; count < venue; count++) bookings.add(booking("Cơ sở " + venue, "COMPLETED", "DIRECT", 100_000, 0));
        }

        List<BookingSummary.Line> byVenue = BookingSummary.of(bookings).byVenue();

        assertThat(byVenue).hasSize(16);
        assertThat(byVenue.get(0).label()).isEqualTo("Cơ sở 18");
        assertThat(byVenue.get(15)).isEqualTo(new BookingSummary.Line("Các cơ sở khác (3)", 1 + 2 + 3, 600_000, 0));
    }

    @Test
    void rendersAVietnamesePdfEvenWithNoBookings() throws Exception {
        BookingSummaryPdfRenderer renderer = new BookingSummaryPdfRenderer(new JasperRenderer());

        byte[] pdf = renderer.render(List.of(
                        booking("Goat Arena", "COMPLETED", "DIRECT", 300_000, 300_000),
                        booking("Goat Arena", "CANCELLED", "WALK_IN", 200_000, 0),
                        booking("Sân Thể Thao Bình Thạnh", "CONFIRMED", "DIRECT", 448_000, 134_400),
                        booking("Khu Liên Hợp Pickleball Sơn Trà", "COMPLETED", "MATCHMAKING", 210_000, 210_000)),
                "Phạm vi: Tất cả đơn đặt sân", Instant.parse("2026-10-12T04:00:00Z"));
        byte[] empty = renderer.render(List.of(), "Ngày: 01/10/2026 - 12/10/2026", Instant.now());

        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        // Bản mẫu để xem bằng mắt sau khi chạy test.
        java.nio.file.Files.write(java.nio.file.Path.of("target", "admin-booking-summary-sample.pdf"), pdf);
        assertThat(new String(empty, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        assertThat(BookingSummaryPdfRenderer.money(1_234_000)).isEqualTo("1.234.000 đ");
        assertThat(BookingSummaryPdfRenderer.percent(1.0 / 3)).isEqualTo("33,3%");
    }

    private static VenueOwnerBookingResponse booking(String venue, String status, String source, double total, double paid) {
        VenueOwnerBookingResponse booking = new VenueOwnerBookingResponse();
        booking.setBookingId(UUID.randomUUID());
        booking.setVenueName(venue);
        booking.setStatus(status);
        booking.setSource(source);
        booking.setTotalPrice(total);
        if (paid > 0) {
            VenueOwnerPaymentResponse payment = new VenueOwnerPaymentResponse();
            payment.setPaymentId(UUID.randomUUID());
            payment.setStatus("SUCCEEDED");
            payment.setAmount(paid);
            booking.setPayments(List.of(payment));
        }
        return booking;
    }
}
