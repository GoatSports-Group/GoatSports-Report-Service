package iuh.fit.reportservice.presentation.controller;

import iuh.fit.reportservice.application.port.report.ReportPort;
import iuh.fit.reportservice.application.enums.FormatType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {
    private final ReportPort reportPort;

    @GetMapping("/export/users/{userId}")
    public ResponseEntity<byte[]> exportUserDetail(
            @PathVariable("userId") String userId,
            @RequestParam String format
    ) {
        try {
            byte[] data = reportPort.exportUserDetail(format, userId);

            HttpHeaders headers = new HttpHeaders();

            if (format.equalsIgnoreCase(FormatType.PDF.getValue())) {
                headers.setContentType(MediaType.APPLICATION_PDF);
                headers.setContentDispositionFormData("attachment", "bao_cao.pdf");
            } else if (format.equalsIgnoreCase(FormatType.XLSX.getValue())) {
                headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
                headers.setContentDispositionFormData("attachment", "bao_cao.xlsx");
            }

            return ResponseEntity.ok().headers(headers).body(data);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(null);
        }
    }

}
