package iuh.fit.reportservice.infrastructure.report;

import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.util.JRLoader;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class AdminBookingSummaryTemplateTest {

    @Test
    void loadsAdminBookingSummaryCompiledTemplate() throws Exception {
        try (InputStream template = getClass().getResourceAsStream(
                "/templates/jasper/admin-booking/admin-booking-summary.jasper"
        )) {
            assertNotNull(template, "Không tìm thấy template Jasper báo cáo tổng hợp");
            JasperReport report = (JasperReport) JRLoader.loadObject(template);
            assertNotNull(report);
        }
    }
}
