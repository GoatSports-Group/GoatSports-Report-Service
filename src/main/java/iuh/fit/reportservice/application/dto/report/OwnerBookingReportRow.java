package iuh.fit.reportservice.application.dto.report;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class OwnerBookingReportRow {
    private Integer sequence;
    private String bookingCode;
    private String customerName;
    private String customerPhone;
    private String venueName;
    private String courtName;
    private String playDate;
    private String timeRange;
    private Double totalPrice;
    private String paymentStatus;
    private String bookingStatus;
}
