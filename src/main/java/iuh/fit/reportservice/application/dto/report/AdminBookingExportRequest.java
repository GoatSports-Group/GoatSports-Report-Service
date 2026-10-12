package iuh.fit.reportservice.application.dto.report;

public record AdminBookingExportRequest(
        String format,
        OwnerBookingReportFilter filter,
        String venueName,
        String courtName
) {
}
