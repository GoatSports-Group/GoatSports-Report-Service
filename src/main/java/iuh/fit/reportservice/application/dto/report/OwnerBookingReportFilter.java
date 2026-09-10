package iuh.fit.reportservice.application.dto.report;

import iuh.fit.reportservice.application.enums.FormatType;
import iuh.fit.reportservice.application.exception.ServiceException;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public record OwnerBookingReportFilter(
        UUID venueId,
        UUID venueCourtId,
        String status,
        String paymentStatus,
        String query,
        LocalDate fromDate,
        LocalDate toDate
) {
    public static void validate(OwnerBookingReportFilter filter, String format) throws ServiceException {
        if (filter == null) {
            throw new ServiceException("Bộ lọc báo cáo đơn đặt sân là bắt buộc.");
        }
        filter.validate(format);
    }

    public void validate(String format) throws ServiceException {
        if (!FormatType.XLSX.getValue().equalsIgnoreCase(format)
                && !FormatType.PDF.getValue().equalsIgnoreCase(format)) {
            throw new ServiceException("Báo cáo đơn đặt sân chỉ hỗ trợ định dạng PDF hoặc XLSX.");
        }
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new ServiceException("Khoảng ngày đặt sân không hợp lệ.");
        }
        String normalizedPaymentStatus = paymentStatus == null || paymentStatus.isBlank()
                ? null : paymentStatus.trim();
        if (normalizedPaymentStatus != null && !Set.of("PAID", "UNPAID", "FAILED")
                .contains(normalizedPaymentStatus.toUpperCase(Locale.ROOT))) {
            throw new ServiceException("Trạng thái thanh toán không hợp lệ.");
        }
    }
}
