package iuh.fit.reportservice.application.dto.venue;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
public class VenueOwnerBookingResponse {
    private UUID bookingId;
    private UUID playerId;
    private String venueName;
    private String courtName;
    private LocalDate playDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private String status;
    private Double totalPrice;
    private UUID remainingPaymentId;
    private String bookingCode;
    private String walkInCustomerName;
    private String walkInCustomerPhone;
    private List<VenueOwnerPaymentResponse> payments = new ArrayList<>();
}
