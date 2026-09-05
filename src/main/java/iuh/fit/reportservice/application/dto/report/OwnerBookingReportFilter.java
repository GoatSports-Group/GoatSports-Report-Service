package iuh.fit.reportservice.application.dto.report;

import java.time.LocalDate;
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
}
