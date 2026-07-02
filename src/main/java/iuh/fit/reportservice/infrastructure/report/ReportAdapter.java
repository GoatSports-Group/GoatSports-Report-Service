package iuh.fit.reportservice.infrastructure.report;

import iuh.fit.reportservice.application.dto.report.UserDetailReportResponse;
import iuh.fit.reportservice.application.exception.ServiceException;
import iuh.fit.reportservice.application.port.report.ReportPort;
import iuh.fit.reportservice.application.enums.FormatType;
import iuh.fit.reportservice.infrastructure.external.client.AuthClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import net.sf.jasperreports.engine.export.ooxml.JRXlsxExporter;
import net.sf.jasperreports.engine.util.JRLoader;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleOutputStreamExporterOutput;
import net.sf.jasperreports.export.SimpleXlsxReportConfiguration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportAdapter implements ReportPort {

    private final AuthClient authClient;

    @Qualifier("authDataSource")
    private final DataSource authDataSource;

    @Override
    public byte[] exportUsers(
            String format
    ) throws ServiceException, JRException, IOException, SQLException
    {
        InputStream reportStream = new ClassPathResource("templates/jasper/user/users.jasper").getInputStream();
        JasperReport jasperReport = (JasperReport) JRLoader.loadObject(reportStream);

        try (Connection connection = authDataSource.getConnection()) {
            Map<String, Object> parameters = new HashMap<>();
            parameters.put("REPORT_DATE", getDateFormat());

            JasperPrint jasperPrint = JasperFillManager.fillReport(jasperReport, parameters, connection);

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
    }

    @Override
    public byte[] exportUserDetail(String format, String userId) throws ServiceException, JRException, IOException {
        UserDetailReportResponse user = authClient.getUserById(userId).getData();
        List<UserDetailReportResponse> dataList = Collections.singletonList(user);
        JRBeanCollectionDataSource dataSource = new JRBeanCollectionDataSource(dataList);

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("REPORT_DATE", getDateFormat());

        InputStream reportStream = new ClassPathResource("templates/jasper/user/user-detail.jasper").getInputStream();
        JasperReport jasperReport = (JasperReport) JRLoader.loadObject(reportStream);

        JasperPrint jasperPrint = JasperFillManager.fillReport(
                jasperReport, parameters, dataSource
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
