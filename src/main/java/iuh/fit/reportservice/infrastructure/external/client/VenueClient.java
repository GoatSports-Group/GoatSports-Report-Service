package iuh.fit.reportservice.infrastructure.external.client;

import iuh.fit.reportservice.application.dto.response.base.BaseResponse;
import iuh.fit.reportservice.application.dto.venue.VenueOwnerBookingPageResponse;
import iuh.fit.reportservice.application.dto.venue.VenueOwnerBookingResponse;
import iuh.fit.reportservice.application.dto.venue.VenueOwnerRevenueResponse;
import iuh.fit.reportservice.infrastructure.config.VenueClientConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@FeignClient(
        name = "GOATSPORTS-VENUE-SERVICE",
        url = "${venue.service.url}",
        configuration = VenueClientConfig.class
)
public interface VenueClient {
    @GetMapping("/api/v1/owner/bookings/{bookingId}")
    BaseResponse<VenueOwnerBookingResponse> getOwnerBooking(
            @PathVariable("bookingId") UUID bookingId
    );

    @GetMapping("/api/v1/owner/bookings")
    BaseResponse<VenueOwnerBookingPageResponse> getOwnerBookings(
            @RequestParam(required = false) UUID venueId,
            @RequestParam(required = false) UUID venueCourtId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam int page,
            @RequestParam int size
    );

    /** Nội bộ (job báo cáo định kỳ, không có người dùng đăng nhập): chủ của ít nhất một cơ sở. */
    @GetMapping("/internal/owner-revenue/owners")
    BaseResponse<List<UUID>> getRevenueOwnerIds();

    /** Nội bộ: doanh thu mọi cơ sở của {@code ownerId}. */
    @GetMapping("/internal/owner-revenue")
    BaseResponse<VenueOwnerRevenueResponse> getOwnerRevenueForOwner(
            @RequestParam UUID ownerId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate
    );

    @GetMapping("/api/v1/owner/revenue")
    BaseResponse<VenueOwnerRevenueResponse> getOwnerRevenue(
            @RequestParam(required = false) UUID venueId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate
    );
}
