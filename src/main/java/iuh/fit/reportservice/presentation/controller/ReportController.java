package iuh.fit.reportservice.presentation.controller;

import iuh.fit.reportservice.application.port.report.ReportPort;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {
    private final ReportPort reportPort;

    @GetMapping("/download")
    public ResponseEntity<byte[]> downloadReport(@RequestParam("format") String format) {
        try {
            Map<String, Object> parameters = new HashMap<>();

            byte[] data = reportPort.exportReport(format, parameters);

            HttpHeaders headers = new HttpHeaders();

            if ("pdf".equalsIgnoreCase(format)) {
                headers.setContentType(MediaType.APPLICATION_PDF);
                headers.setContentDispositionFormData("attachment", "bao_cao_nguoi_dung.pdf");
            } else if ("xlsx".equalsIgnoreCase(format)) {
                headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
                headers.setContentDispositionFormData("attachment", "bao_cao_nguoi_dung.xlsx");
            }

            return ResponseEntity.ok().headers(headers).body(data);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(null);
        }
    }

}
