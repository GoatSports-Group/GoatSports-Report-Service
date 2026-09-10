package iuh.fit.reportservice.application.dto.report;

import iuh.fit.reportservice.application.enums.FormatType;
import iuh.fit.reportservice.application.exception.ServiceException;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

public record OwnerRevenueReportFilter(
        UUID venueId,
        String venueName,
        LocalDate fromDate,
        LocalDate toDate,
        String periodLabel
) {
    public static void validate(OwnerRevenueReportFilter filter, String format) throws ServiceException {
        if (filter == null) {
            throw new ServiceException("Khoảng ngày báo cáo doanh thu là bắt buộc.");
        }
        filter.validate(format);
    }

    public void validate(String format) throws ServiceException {
        if (!FormatType.XLSX.getValue().equalsIgnoreCase(format)
                && !FormatType.PDF.getValue().equalsIgnoreCase(format)) {
            throw new ServiceException("Báo cáo doanh thu chỉ hỗ trợ định dạng PDF hoặc XLSX.");
        }
        if (fromDate == null || toDate == null) {
            throw new ServiceException("Khoảng ngày báo cáo doanh thu là bắt buộc.");
        }
        if (fromDate.isAfter(toDate)) {
            throw new ServiceException("Khoảng ngày báo cáo doanh thu không hợp lệ.");
        }
        if (ChronoUnit.DAYS.between(fromDate, toDate) >= 366) {
            throw new ServiceException("Khoảng ngày báo cáo doanh thu không được vượt quá 366 ngày.");
        }
    }
}
