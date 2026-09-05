package iuh.fit.reportservice.application.dto.report;

import java.time.LocalDate;
import java.util.UUID;

public record OwnerRevenueReportFilter(
        UUID venueId,
        String venueName,
        LocalDate fromDate,
        LocalDate toDate,
        String periodLabel
) {
}
