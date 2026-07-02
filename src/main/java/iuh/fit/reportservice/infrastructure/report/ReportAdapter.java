package iuh.fit.reportservice.infrastructure.report;

import iuh.fit.reportservice.application.dto.report.UserReportResponse;
import iuh.fit.reportservice.application.exception.ServiceException;
import iuh.fit.reportservice.application.port.report.ReportPort;
import iuh.fit.reportservice.application.enums.DataSourceType;
import iuh.fit.reportservice.application.enums.FormatType;
import iuh.fit.reportservice.infrastructure.external.client.AuthClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import net.sf.jasperreports.engine.export.ooxml.JRXlsxExporter;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleOutputStreamExporterOutput;
import net.sf.jasperreports.export.SimpleXlsxReportConfiguration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportAdapter implements ReportPort {

    private final AuthClient authClient;

    @Override
    public byte[] exportReport(
            String format, String type
    ) throws JRException, IOException, ServiceException
    {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("CUSTOM_DS", getDataSource(type));
        parameters.put("REPORT_DATE", getDateFormat());

        InputStream reportStream = new ClassPathResource(getTemplate(type)).getInputStream();
        JasperReport jasperReport = JasperCompileManager.compileReport(reportStream);

        JasperPrint jasperPrint = JasperFillManager.fillReport(
                jasperReport, parameters, new JREmptyDataSource()
        );

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        if (format.equalsIgnoreCase(FormatType.PDF.getValue())) {
            JasperExportManager.exportReportToPdfStream(jasperPrint, outputStream);
        } else if (format.equalsIgnoreCase(FormatType.XLSX.getValue())) {
            final JRXlsxExporter exporter = getJrXlsxExporter(jasperPrint, outputStream);
            exporter.exportReport();
        } else {
            throw new ServiceException("Định dạng không hỗ trợ: " + format);
        }

        return outputStream.toByteArray();
    }

    private JRBeanCollectionDataSource getDataSource(String type) {
        if(type.equalsIgnoreCase(DataSourceType.USERS.getValue())) {
            List<UserReportResponse> users = authClient.getAllUsers().getData();
            log.info(users.toString());
            return new JRBeanCollectionDataSource(users);
        }

        return new JRBeanCollectionDataSource(null);
    }

    private String getTemplate(String type) {
        return "templates/" + type + ".jrxml";
    }

    private String getDateFormat() {
        return new SimpleDateFormat("dd/MM/yyyy").format(new Date());
    }

    private JRXlsxExporter getJrXlsxExporter(JasperPrint jasperPrint, ByteArrayOutputStream outputStream) {
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
