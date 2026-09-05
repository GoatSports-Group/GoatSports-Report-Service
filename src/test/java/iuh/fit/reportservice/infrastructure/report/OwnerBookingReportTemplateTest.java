package iuh.fit.reportservice.infrastructure.report;

import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.util.JRLoader;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class OwnerBookingReportTemplateTest {

    @Test
    void loadsOwnerBookingCompiledTemplate() throws Exception {
        try (InputStream template = getClass().getResourceAsStream(
                "/templates/jasper/owner-booking/owner-bookings.jasper"
        )) {
            assertNotNull(template, "Không tìm thấy template Jasper booking");
            JasperReport report = (JasperReport) JRLoader.loadObject(template);
            assertNotNull(report);
        }
    }
}
