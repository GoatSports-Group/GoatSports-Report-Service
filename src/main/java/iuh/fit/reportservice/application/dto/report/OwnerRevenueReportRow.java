package iuh.fit.reportservice.application.dto.report;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class OwnerRevenueReportRow {
    private Integer sequence;
    private String reportDate;
    private Double revenue;
    private Long succeededPaymentCount;
    private Double revenueShare;
}
