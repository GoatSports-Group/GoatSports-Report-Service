package iuh.fit.reportservice.application.dto.venue;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
public class VenueOwnerRevenueResponse {
    private UUID scopeVenueId;
    private String currency;
    private String periodBasis;
    private VenueOwnerRevenuePeriodResponse currentPeriod;
    private VenueOwnerRevenuePeriodResponse previousPeriod;
    private BigDecimal revenueChangePercentage;
    private BigDecimal bookingCountChangePercentage;
    private List<VenueOwnerRevenueStatusResponse> paymentStatusBreakdown = new ArrayList<>();
    private List<VenueOwnerDailyRevenueResponse> dailyRevenue = new ArrayList<>();
}
