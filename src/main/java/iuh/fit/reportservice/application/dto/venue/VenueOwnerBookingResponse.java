package iuh.fit.reportservice.application.dto.venue;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
public class VenueOwnerBookingResponse {
    private UUID bookingId;
    private UUID playerId;
    private UUID venueId;
    private UUID venueCourtId;
    private String venueName;
    private String courtName;
    private LocalDate playDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private String status;
    private String source;
    private Double totalPrice;
    private Double depositAmount;
    private Double remainingAmount;
    private UUID depositPaymentId;
    private UUID remainingPaymentId;
    private String bookingCode;
    private String qrCode;
    private String walkInCustomerName;
    private String walkInCustomerPhone;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<VenueOwnerPaymentResponse> payments = new ArrayList<>();
}
