package iuh.fit.reportservice.infrastructure.report;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import iuh.fit.reportservice.application.enums.FormatType;
import iuh.fit.reportservice.application.exception.ServiceException;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import net.sf.jasperreports.engine.export.ooxml.JRXlsxExporter;
import net.sf.jasperreports.engine.util.JRLoader;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleOutputStreamExporterOutput;
import net.sf.jasperreports.export.SimpleXlsxReportConfiguration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Phần kỹ thuật của JasperReports: nạp mẫu, xuất PDF/XLSX, logo và mã QR. Không biết gì về nghiệp vụ báo cáo. */
@Component
class JasperRenderer {

    JasperReport load(String classpathTemplate) throws IOException, JRException {
        try (InputStream stream = new ClassPathResource(classpathTemplate).getInputStream()) {
            return (JasperReport) JRLoader.loadObject(stream);
        }
    }

    byte[] readBytes(String classpathResource) throws IOException {
        try (InputStream stream = new ClassPathResource(classpathResource).getInputStream()) {
            return stream.readAllBytes();
        }
    }

    /** Xuất theo định dạng người dùng chọn; định dạng lạ thì báo lỗi nghiệp vụ thay vì lỗi 500. */
    byte[] export(JasperPrint print, String format) throws JRException, ServiceException {
        if (FormatType.PDF.getValue().equalsIgnoreCase(format)) return exportPdf(print);
        if (FormatType.XLSX.getValue().equalsIgnoreCase(format)) return exportXlsx(print);
        throw new ServiceException("Định dạng không hỗ trợ: " + format);
    }

    byte[] exportPdf(JasperPrint print) throws JRException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        JasperExportManager.exportReportToPdfStream(print, output);
        return output.toByteArray();
    }

    byte[] exportXlsx(JasperPrint print) throws JRException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        JRXlsxExporter exporter = new JRXlsxExporter();
        exporter.setExporterInput(new SimpleExporterInput(print));
        exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(output));
        SimpleXlsxReportConfiguration configuration = new SimpleXlsxReportConfiguration();
        configuration.setOnePagePerSheet(false);
        configuration.setRemoveEmptySpaceBetweenRows(true);
        configuration.setDetectCellType(true);
        exporter.setConfiguration(configuration);
        exporter.exportReport();
        return output.toByteArray();
    }

    /**
     * Xuất thử một báo cáo rỗng lúc khởi động: lần đầu Jasper nạp font và bộ xuất mất vài giây, làm nóng trước
     * để yêu cầu xuất báo cáo đầu tiên của người dùng không bị chậm.
     */
    void warmUp(JasperReport report, Map<String, Object> parameters) throws JRException {
        JasperPrint print = JasperFillManager.fillReport(report, new HashMap<>(parameters),
                new JRBeanCollectionDataSource(Collections.emptyList()));
        exportPdf(print);
        exportXlsx(print);
    }

    /** Ảnh PNG của mã QR (in trên hóa đơn để check-in), null nếu không có nội dung. */
    byte[] qrPng(String content) throws IOException {
        String normalized = ReportLabels.blankToNull(content);
        if (normalized == null) return null;
        try {
            BitMatrix matrix = new QRCodeWriter().encode(normalized, BarcodeFormat.QR_CODE, 240, 240);
            BufferedImage image = new BufferedImage(matrix.getWidth(), matrix.getHeight(), BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < matrix.getHeight(); y++) {
                for (int x = 0; x < matrix.getWidth(); x++) {
                    image.setRGB(x, y, matrix.get(x, y) ? 0xFF0B201B : 0xFFFFFFFF);
                }
            }
            try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                ImageIO.write(image, "png", output);
                return output.toByteArray();
            }
        } catch (WriterException exception) {
            throw new IOException("Không thể tạo mã QR cho hóa đơn đơn đặt sân.", exception);
        }
    }

    static InputStream stream(byte[] bytes) {
        return bytes == null ? null : new ByteArrayInputStream(bytes);
    }
}
