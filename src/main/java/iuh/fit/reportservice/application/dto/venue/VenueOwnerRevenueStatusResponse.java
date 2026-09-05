package iuh.fit.reportservice.application.dto.venue;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class VenueOwnerRevenueStatusResponse {
    private String status;
    private Long paymentCount;
    private BigDecimal nominalAmount;
}
