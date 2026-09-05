package iuh.fit.reportservice.application.dto.venue;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class VenueOwnerRevenuePeriodResponse {
    private LocalDate fromDate;
    private LocalDate toDate;
    private Long bookingCount;
    private Long paidBookingCount;
    private BigDecimal totalRevenue;
}
