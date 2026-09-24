package iuh.fit.reportservice.infrastructure.report;

import iuh.fit.reportservice.application.dto.report.OwnerBookingInvoiceReport;
import iuh.fit.reportservice.application.dto.venue.VenueOwnerBookingResponse;
import iuh.fit.reportservice.application.dto.venue.VenueOwnerPaymentResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OwnerBookingReportMapperTest {

    private VenueOwnerPaymentResponse payment(String purpose, String status, double amount) {
        VenueOwnerPaymentResponse payment = new VenueOwnerPaymentResponse();
        payment.setPaymentId(UUID.randomUUID());
        payment.setPurpose(purpose);
        payment.setStatus(status);
        payment.setAmount(amount);
        payment.setMethod("BANK_TRANSFER");
        payment.setCreatedAt(LocalDateTime.of(2026, 9, 24, 10, 0));
        return payment;
    }

    private VenueOwnerBookingResponse booking(double total, double deposit, VenueOwnerPaymentResponse... payments) {
        VenueOwnerBookingResponse booking = new VenueOwnerBookingResponse();
        booking.setBookingCode("GS-001");
        booking.setStatus("CONFIRMED");
        booking.setTotalPrice(total);
        booking.setDepositAmount(deposit);
        booking.setRemainingAmount(total - deposit);
        booking.setCreatedAt(LocalDateTime.of(2026, 9, 24, 9, 0));
        booking.setPayments(List.of(payments));
        return booking;
    }

    @Test
    @DisplayName("mới đóng cọc: hóa đơn ghi đã thu phần cọc và còn nợ phần còn lại")
    void depositOnlyLeavesOutstanding() {
        OwnerBookingInvoiceReport invoice = OwnerBookingReportMapper.toInvoice(
                booking(300_000, 90_000, payment("BOOKING_DEPOSIT", "SUCCEEDED", 90_000)));

        assertThat(invoice.getPaidAmount()).isEqualTo(ReportLabels.money(90_000));
        assertThat(invoice.getOutstandingAmount()).isEqualTo(ReportLabels.money(210_000));
        assertThat(invoice.getPaymentStatus()).isEqualTo("Chưa thanh toán đủ");
        assertThat(invoice.getPaymentMethod()).isEqualTo("Ví điện tử (PayOS)");
    }

    @Test
    @DisplayName("tiền còn lại thu tiền mặt ghi thẳng vào booking vẫn được tính là đã trả đủ")
    void remainingRecordedOnBookingCountsAsPaid() {
        VenueOwnerBookingResponse booking = booking(300_000, 90_000, payment("BOOKING_DEPOSIT", "SUCCEEDED", 90_000));
        booking.setRemainingPaymentId(UUID.randomUUID());

        assertThat(OwnerBookingReportMapper.paidAmount(booking, 300_000)).isEqualTo(300_000);
        OwnerBookingInvoiceReport invoice = OwnerBookingReportMapper.toInvoice(booking);
        assertThat(invoice.getOutstandingAmount()).isEqualTo(ReportLabels.money(0));
        assertThat(invoice.getPaymentStatus()).isEqualTo("Đã thanh toán đủ");
    }

    @Test
    @DisplayName("chưa thu đồng nào: phân biệt đang chờ và thanh toán thất bại")
    void unpaidStatusDistinguishesWaitingFromFailed() {
        assertThat(OwnerBookingReportMapper.toInvoice(
                booking(200_000, 60_000, payment("BOOKING_DEPOSIT", "PENDING", 60_000))).getPaymentStatus())
                .isEqualTo("Đang chờ thanh toán");
        assertThat(OwnerBookingReportMapper.toInvoice(
                booking(200_000, 60_000, payment("BOOKING_DEPOSIT", "EXPIRED", 60_000))).getPaymentStatus())
                .isEqualTo("Thanh toán chưa thành công");
    }
}
