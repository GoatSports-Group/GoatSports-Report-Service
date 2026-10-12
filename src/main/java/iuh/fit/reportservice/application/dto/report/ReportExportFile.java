package iuh.fit.reportservice.application.dto.report;

public record ReportExportFile(String fileName, String contentType, byte[] data) {
}
