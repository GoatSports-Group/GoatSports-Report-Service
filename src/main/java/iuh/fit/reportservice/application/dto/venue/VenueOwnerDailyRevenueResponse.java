package iuh.fit.reportservice.application.dto.venue;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class VenueOwnerDailyRevenueResponse {
    private LocalDate date;
    private BigDecimal revenue;
    private Long succeededPaymentCount;
}
