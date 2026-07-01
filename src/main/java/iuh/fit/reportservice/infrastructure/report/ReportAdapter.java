package iuh.fit.reportservice.infrastructure.report;

import iuh.fit.reportservice.application.exception.ServiceException;
import iuh.fit.reportservice.application.port.report.ReportPort;
import lombok.RequiredArgsConstructor;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.export.ooxml.JRXlsxExporter;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleOutputStreamExporterOutput;
import net.sf.jasperreports.export.SimpleXlsxReportConfiguration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ReportAdapter implements ReportPort {
    private final DataSource dataSource;

    @Override
    public byte[] exportReport(
            String format, Map<String, Object> parameters
    ) throws ServiceException, JRException, IOException, SQLException
    {
        InputStream reportStream = new ClassPathResource("templates/sports.jrxml").getInputStream();

        JasperReport jasperReport = JasperCompileManager.compileReport(reportStream);

        try (Connection connection = dataSource.getConnection()) {
            SimpleDateFormat formatter = new SimpleDateFormat("dd/MM/yyyy");
            String formattedDate = formatter.format(new Date());
            parameters.put("REPORT_DATE", formattedDate);

            JasperPrint jasperPrint = JasperFillManager.fillReport(jasperReport, parameters, connection);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            if ("pdf".equalsIgnoreCase(format)) {
                JasperExportManager.exportReportToPdfStream(jasperPrint, outputStream);
            } else if ("xlsx".equalsIgnoreCase(format)) {
                final JRXlsxExporter exporter = getJrXlsxExporter(jasperPrint, outputStream);
                exporter.exportReport();
            } else {
                throw new IllegalArgumentException("Định dạng không hỗ trợ: " + format);
            }

            return outputStream.toByteArray();
        }
    }

    private static JRXlsxExporter getJrXlsxExporter(JasperPrint jasperPrint, ByteArrayOutputStream outputStream) {
        JRXlsxExporter exporter = new JRXlsxExporter();
        exporter.setExporterInput(new SimpleExporterInput(jasperPrint));
        exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(outputStream));

        SimpleXlsxReportConfiguration configuration = new SimpleXlsxReportConfiguration();
        configuration.setOnePagePerSheet(false);
        configuration.setRemoveEmptySpaceBetweenRows(true);
        configuration.setDetectCellType(true);
        exporter.setConfiguration(configuration);

        return exporter;
    }
}
