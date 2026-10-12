package iuh.fit.reportservice.presentation.controller;

import iuh.fit.reportservice.application.dto.report.AdminBookingExportRequest;
import iuh.fit.reportservice.application.dto.report.ReportExportFile;
import iuh.fit.reportservice.application.dto.report.ReportExportView;
import iuh.fit.reportservice.application.exception.ServiceException;
import iuh.fit.reportservice.application.port.report.ReportExportPort;
import iuh.fit.reportservice.application.port.security.CurrentUserProvider;
import iuh.fit.shared.annotation.AuditLog;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reports/exports")
@RequiredArgsConstructor
public class ReportExportController {
    private final ReportExportPort reportExportPort;
    private final CurrentUserProvider currentUserProvider;

    @PostMapping("/admin-bookings")
    @PreAuthorize("hasAuthority('EXPORT_ALL_BOOKINGS')")
    @AuditLog(action = "EXPORT_ALL_BOOKINGS", description = "Yêu cầu xuất báo cáo đơn đặt sân toàn nền tảng")
    public ResponseEntity<ReportExportView> requestAdminBookingExport(@RequestBody AdminBookingExportRequest request)
            throws ServiceException {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(reportExportPort.requestAdminBookingExport(currentUserId(), request));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('EXPORT_ALL_BOOKINGS')")
    public List<ReportExportView> listExports(@RequestParam(defaultValue = "ADMIN_BOOKINGS") String type)
            throws ServiceException {
        return reportExportPort.listExports(currentUserId(), type);
    }

    @GetMapping("/{exportId}")
    @PreAuthorize("hasAuthority('EXPORT_ALL_BOOKINGS')")
    public ReportExportView getExport(@PathVariable UUID exportId) throws ServiceException {
        return reportExportPort.getExport(currentUserId(), exportId);
    }

    @GetMapping("/{exportId}/file")
    @PreAuthorize("hasAuthority('EXPORT_ALL_BOOKINGS')")
    @AuditLog(action = "DOWNLOAD_REPORT_EXPORT", description = "Tải báo cáo đã xuất #{#exportId}")
    public ResponseEntity<byte[]> download(@PathVariable UUID exportId) throws ServiceException {
        ReportExportFile file = reportExportPort.download(currentUserId(), exportId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(file.contentType()));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(file.fileName(), StandardCharsets.UTF_8).build());
        return ResponseEntity.ok().headers(headers).header(HttpHeaders.CACHE_CONTROL, "no-store").body(file.data());
    }

    private UUID currentUserId() throws ServiceException {
        return UUID.fromString(currentUserProvider.getCurrentUserId()
                .orElseThrow(() -> new ServiceException("Bạn phải đăng nhập để xuất báo cáo.")));
    }
}
