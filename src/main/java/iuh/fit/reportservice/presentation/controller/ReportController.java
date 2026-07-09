package iuh.fit.reportservice.presentation.controller;

import iuh.fit.reportservice.application.port.report.ReportPort;
import iuh.fit.reportservice.application.enums.FormatType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ADMIN')")
public class ReportController {
    private final ReportPort reportPort;

    @GetMapping("/export/users")
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

    private ResponseEntity<byte[]> getResponseEntity(@RequestParam("format") String format, byte[] data) {
        HttpHeaders headers = new HttpHeaders();

        if (format.equalsIgnoreCase(FormatType.PDF.getValue())) {
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", "bao_cao.pdf");
        } else if (format.equalsIgnoreCase(FormatType.XLSX.getValue())) {
            headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
            headers.setContentDispositionFormData("attachment", "bao_cao.xlsx");
        }

        return ResponseEntity.ok().headers(headers).body(data);
    }

}
