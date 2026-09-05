package iuh.fit.reportservice.infrastructure.report;

import iuh.fit.reportservice.application.dto.report.OwnerBookingInvoiceReport;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import net.sf.jasperreports.engine.util.JRLoader;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class OwnerBookingInvoiceReportTemplateTest {

    @Test
    void fillsAndExportsOwnerBookingInvoicePdf() throws Exception {
        OwnerBookingInvoiceReport invoice = OwnerBookingInvoiceReport.builder()
                .invoiceNumber("HD-GSABC12345")
                .issuedAt("05/09/2026 22:00")
                .bookingCode("GSABC12345")
                .bookingStatus("Hoàn tất")
                .createdAt("17:40 04/09/2026")
                .customerName("Nguyễn Văn An")
                .customerPhone("0901234567")
                .customerType("Khách tại quầy")
                .venueName("GOAT")
                .courtName("Sân cầu lông A")
                .playDate("04/09/2026")
                .timeRange("18:00 - 19:00")
                .bookingChannel("Khách tại quầy")
                .itemDescription("Tiền sân Sân cầu lông A · 18:00 - 19:00")
                .totalAmount("200.000đ")
                .serviceFeeAmount("0đ")
                .discountAmount("0đ")
                .finalAmount("200.000đ")
                .depositAmount("0đ")
                .paidAmount("200.000đ")
                .outstandingAmount("0đ")
                .paymentMethod("Tiền mặt")
                .transactionCode("CASH-GSABC12345")
                .paymentStatus("Đã thanh toán đủ")
                .paidAt("04/09/2026 - 19:01")
                .historyLines("17:40 04/09/2026   Tạo đơn đặt sân\n"
                        + "19:01 04/09/2026   Thanh toán còn lại\n"
                        + "19:00 05/09/2026   Hoàn tất")
                .build();

        try (InputStream template = getClass().getResourceAsStream(
                "/templates/jasper/owner-booking/owner-booking-invoice.jasper"
        ); InputStream logo = getClass().getResourceAsStream("/images/goat-report.png")) {
            assertNotNull(template, "Không tìm thấy template Jasper hóa đơn booking");
            assertNotNull(logo, "Không tìm thấy logo hóa đơn booking");
            JasperReport report = (JasperReport) JRLoader.loadObject(template);
            Map<String, Object> parameters = new HashMap<>();
            parameters.put("LOGO", logo);
            byte[] pdf = JasperExportManager.exportReportToPdf(
                    JasperFillManager.fillReport(
                            report,
                            parameters,
                            new JRBeanCollectionDataSource(Collections.singletonList(invoice))
                    )
            );

            assertThat(pdf).hasSizeGreaterThan(10_000);
            assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
        }
    }
}
