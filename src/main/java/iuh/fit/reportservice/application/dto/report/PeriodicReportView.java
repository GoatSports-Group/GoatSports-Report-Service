package iuh.fit.reportservice.application.dto.report;

import iuh.fit.reportservice.application.enums.ReportPeriodType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PeriodicReportView(
        UUID reportId,
        ReportPeriodType periodType,
        LocalDate periodStart,
        LocalDate periodEnd,
        BigDecimal totalRevenue,
        long bookingCount,
        long paidBookingCount,
        BigDecimal revenueChangePercentage,
        BigDecimal bookingCountChangePercentage,
        Instant generatedAt
) {
}
