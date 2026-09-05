package iuh.fit.reportservice.presentation.controller;

import iuh.fit.reportservice.application.dto.report.OwnerBookingReportFilter;
import iuh.fit.reportservice.application.port.report.ReportPort;
import iuh.fit.reportservice.application.enums.FormatType;
import iuh.fit.shared.annotation.AuditLog;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {
    private final ReportPort reportPort;

    @GetMapping("/export/users")
    @PreAuthorize("hasAuthority('EXPORT_USERS')")
    @AuditLog(action = "EXPORT_USERS", description = "Xuất danh sách người dùng")
    public ResponseEntity<byte[]> exportUsers(@RequestParam("format") String format) {
        try {
            byte[] data = reportPort.exportUsers(format);

            return getResponseEntity(format, data);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(null);
        }
    }

    @GetMapping("/export/users/{userId}")
    @PreAuthorize("hasAuthority('EXPORT_USER')")
    @AuditLog(action = "EXPORT_USER", description = "Xuất thông tin người dùng #{#userId}")
    public ResponseEntity<byte[]> exportUserDetail(
            @PathVariable("userId") String userId,
            @RequestParam String format
    ) {
        try {
            byte[] data = reportPort.exportUserDetail(format, userId);

            return getResponseEntity(format, data);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(null);
        }
    }

    @GetMapping("/export/owner-bookings")
    @PreAuthorize("hasAuthority('GET_OWN_BOOKINGS')")
    @AuditLog(action = "EXPORT_OWN_BOOKINGS", description = "Xuất báo cáo đơn đặt sân của chủ sân")
    public ResponseEntity<byte[]> exportOwnerBookings(
            @RequestParam(defaultValue = "xlsx") String format,
            @RequestParam(required = false) UUID venueId,
            @RequestParam(required = false) UUID venueCourtId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String paymentStatus,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) LocalDate fromDate,
            @RequestParam(required = false) LocalDate toDate
    ) throws Exception {
        byte[] data = reportPort.exportOwnerBookings(format, ownerBookingFilter(
                venueId, venueCourtId, status, paymentStatus, query, fromDate, toDate));
        return getResponseEntity(format, "bao-cao-don-dat-san." + format.toLowerCase(), data);
    }

    @GetMapping("/preview/owner-bookings")
    @PreAuthorize("hasAuthority('GET_OWN_BOOKINGS')")
    @AuditLog(action = "PREVIEW_OWN_BOOKINGS", description = "Xem trước báo cáo đơn đặt sân của chủ sân")
    public ResponseEntity<byte[]> previewOwnerBookings(
            @RequestParam(required = false) UUID venueId,
            @RequestParam(required = false) UUID venueCourtId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String paymentStatus,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) LocalDate fromDate,
            @RequestParam(required = false) LocalDate toDate
    ) throws Exception {
        byte[] data = reportPort.exportOwnerBookings("pdf", ownerBookingFilter(
                venueId, venueCourtId, status, paymentStatus, query, fromDate, toDate));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.inline()
                .filename("bao-cao-don-dat-san.pdf")
                .build());
        return ResponseEntity.ok()
                .headers(headers)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(data);
    }

    @GetMapping("/export/owner-bookings/{bookingId}/invoice")
    @PreAuthorize("hasAuthority('GET_OWN_BOOKING')")
    @AuditLog(action = "EXPORT_OWNER_BOOKING_INVOICE", description = "Xuất hóa đơn PDF cho đơn đặt sân #{#bookingId}")
    public ResponseEntity<byte[]> exportOwnerBookingInvoice(@PathVariable UUID bookingId) throws Exception {
        byte[] data = reportPort.exportOwnerBookingInvoice(bookingId);
        return getResponseEntity("pdf", "hoa-don-dat-san-" + bookingId + ".pdf", data);
    }

    @GetMapping("/preview/owner-bookings/{bookingId}/invoice")
    @PreAuthorize("hasAuthority('GET_OWN_BOOKING')")
    @AuditLog(action = "PREVIEW_OWNER_BOOKING_INVOICE", description = "Xem trước hóa đơn PDF cho đơn đặt sân #{#bookingId}")
    public ResponseEntity<byte[]> previewOwnerBookingInvoice(@PathVariable UUID bookingId) throws Exception {
        byte[] data = reportPort.exportOwnerBookingInvoice(bookingId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.inline()
                .filename("hoa-don-dat-san.pdf")
                .build());
        return ResponseEntity.ok()
                .headers(headers)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(data);
    }

    private OwnerBookingReportFilter ownerBookingFilter(
            UUID venueId,
            UUID venueCourtId,
            String status,
            String paymentStatus,
            String query,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        return new OwnerBookingReportFilter(
                venueId, venueCourtId, status, paymentStatus, query, fromDate, toDate
        );
    }

    private ResponseEntity<byte[]> getResponseEntity(@RequestParam("format") String format, byte[] data) {
        return getResponseEntity(format, "bao_cao." + format.toLowerCase(), data);
    }

    private ResponseEntity<byte[]> getResponseEntity(String format, String filename, byte[] data) {
        HttpHeaders headers = new HttpHeaders();

        if (format.equalsIgnoreCase(FormatType.PDF.getValue())) {
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", filename);
        } else if (format.equalsIgnoreCase(FormatType.XLSX.getValue())) {
            headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
            headers.setContentDispositionFormData("attachment", filename);
        }

        return ResponseEntity.ok()
                .headers(headers)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(data);
    }

}
