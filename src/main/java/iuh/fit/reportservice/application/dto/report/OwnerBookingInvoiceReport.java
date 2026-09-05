package iuh.fit.reportservice.application.dto.report;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class OwnerBookingInvoiceReport {
    private final String invoiceNumber;
    private final String issuedAt;
    private final String bookingCode;
    private final String bookingStatus;
    private final String createdAt;
    private final String customerName;
    private final String customerPhone;
    private final String customerType;
    private final String venueName;
    private final String courtName;
    private final String playDate;
    private final String timeRange;
    private final String bookingChannel;
    private final String itemDescription;
    private final String totalAmount;
    private final String serviceFeeAmount;
    private final String discountAmount;
    private final String finalAmount;
    private final String depositAmount;
    private final String paidAmount;
    private final String outstandingAmount;
    private final String paymentMethod;
    private final String transactionCode;
    private final String paymentStatus;
    private final String paidAt;
    private final String historyLines;
}
