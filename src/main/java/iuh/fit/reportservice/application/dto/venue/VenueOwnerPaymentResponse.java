package iuh.fit.reportservice.application.dto.venue;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class VenueOwnerPaymentResponse {
    private UUID paymentId;
    private String purpose;
    private Double amount;
    private String currency;
    private String status;
    private String provider;
    private String method;
    private String providerTransactionId;
    private LocalDateTime paidAt;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
}
