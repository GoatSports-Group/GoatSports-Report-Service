package iuh.fit.reportservice.infrastructure.report;

import iuh.fit.reportservice.application.dto.venue.VenueOwnerBookingResponse;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Bản PDF tổng hợp đơn đặt sân (mẫu Jasper admin-booking-summary.jasper): chỉ số chính rồi các bảng theo trạng thái,
 * cơ sở, nguồn đặt.
 */
@Component
public class BookingSummaryPdfRenderer {
    private static final String TEMPLATE = "templates/jasper/admin-booking/admin-booking-summary.jasper";
    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    private final JasperRenderer renderer;
    private final JasperReport template;
    private final byte[] logo;

    BookingSummaryPdfRenderer(JasperRenderer renderer) {
        this.renderer = renderer;
        try {
            template = renderer.load(TEMPLATE);
            logo = renderer.readBytes("images/goat-report.png");
        } catch (IOException | JRException exception) {
            throw new IllegalStateException("Không khởi tạo được mẫu báo cáo tổng hợp đơn đặt sân.", exception);
        }
    }

    public byte[] render(List<VenueOwnerBookingResponse> bookings, String filterSummary, Instant generatedAt)
            throws JRException {
        BookingSummary summary = BookingSummary.of(bookings);
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("LOGO", JasperRenderer.stream(logo));
        parameters.put("REPORT_DATE", STAMP.format(generatedAt));
        parameters.put("FILTER_SUMMARY", filterSummary);
        parameters.put("KPI_BOOKINGS", number(summary.bookings()));
        parameters.put("KPI_VALUE", money(summary.totalValue()));
        parameters.put("KPI_COLLECTED", money(summary.collected()));
        parameters.put("KPI_LOST_RATE", percent(summary.lostRate()));

        List<Map<String, ?>> rows = new ArrayList<>();
        addSection(rows, "Theo trạng thái", summary.byStatus(), summary.bookings());
        addSection(rows, "Theo cơ sở (" + BookingSummary.TOP_VENUES + " cơ sở nhiều đơn nhất)", summary.byVenue(), summary.bookings());
        addSection(rows, "Theo nguồn đặt", summary.bySource(), summary.bookings());
        return renderer.exportPdf(JasperFillManager.fillReport(template, parameters, new JRMapCollectionDataSource(rows)));
    }

    private static void addSection(List<Map<String, ?>> rows, String section, List<BookingSummary.Line> lines, int total) {
        for (BookingSummary.Line line : lines) {
            rows.add(Map.of(
                    "section", section,
                    "label", line.label(),
                    "bookings", number(line.bookings()),
                    "share", percent(total == 0 ? 0 : (double) line.bookings() / total),
                    "value", money(line.value()),
                    "collected", money(line.collected())));
        }
    }

    static String money(double value) {
        return NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN")).format(Math.round(value)) + " đ";
    }

    static String number(int value) {
        return NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN")).format(value);
    }

    static String percent(double ratio) {
        NumberFormat format = NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN"));
        format.setMaximumFractionDigits(1);
        format.setMinimumFractionDigits(1);
        return format.format(ratio * 100) + "%";
    }
}
