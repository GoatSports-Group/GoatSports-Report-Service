package iuh.fit.reportservice.infrastructure.report;

import iuh.fit.reportservice.application.dto.report.OwnerBookingInvoiceReport;
import iuh.fit.reportservice.application.dto.report.OwnerBookingReportFilter;
import iuh.fit.reportservice.application.dto.report.OwnerBookingReportRow;
import iuh.fit.reportservice.application.dto.report.OwnerRevenueReportFilter;
import iuh.fit.reportservice.application.dto.report.OwnerRevenueReportRow;
import iuh.fit.reportservice.application.dto.report.UserDetailReportResponse;
import iuh.fit.reportservice.application.dto.response.base.BaseResponse;
import iuh.fit.reportservice.application.dto.venue.VenueOwnerBookingPageResponse;
import iuh.fit.reportservice.application.dto.venue.VenueOwnerBookingResponse;
import iuh.fit.reportservice.application.dto.venue.VenueOwnerRevenueResponse;
import iuh.fit.reportservice.application.exception.ServiceException;
import iuh.fit.reportservice.application.port.report.ReportPort;
import iuh.fit.reportservice.infrastructure.external.client.AuthClient;
import iuh.fit.reportservice.infrastructure.external.client.VenueClient;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Điều phối xuất báo cáo: lấy dữ liệu (auth DB / venue-service) → chuyển thành dòng báo cáo
 * ({@link OwnerBookingReportMapper}, {@link OwnerRevenueReportMapper}) → dựng file ({@link JasperRenderer}).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReportAdapter implements ReportPort {
    private static final int BOOKING_PAGE_SIZE = 20;
    /** API admin của venue-service nhận trang tới 100 dòng (giới hạn 100 reference ID của payment-service). */
    private static final int ADMIN_BOOKING_PAGE_SIZE = 100;
    /** Chặn báo cáo quá lớn: Jasper giữ toàn bộ trang trong bộ nhớ, vài chục nghìn dòng có thể làm tràn heap. */
    private static final int MAX_BOOKING_EXPORT_ROWS = 10_000;

    private final AuthClient authClient;
    private final VenueClient venueClient;
    private final JasperRenderer renderer;

    @Qualifier("authDataSource")
    private final DataSource authDataSource;

    // Mẫu đã biên dịch được nạp một lần lúc khởi động và dùng lại cho mọi yêu cầu.
    private JasperReport ownerBookingReport;
    private JasperReport ownerBookingInvoiceReport;
    private JasperReport ownerRevenueReport;
    private byte[] logo;

    @PostConstruct
    void initializeOwnerReports() {
        long startedAt = System.nanoTime();
        try {
            ownerBookingReport = renderer.load("templates/jasper/owner-booking/owner-bookings.jasper");
            ownerBookingInvoiceReport = renderer.load("templates/jasper/owner-booking/owner-booking-invoice.jasper");
            ownerRevenueReport = renderer.load("templates/jasper/owner-revenue/owner-revenue.jasper");
            logo = renderer.readBytes("images/goat-report.png");
            renderer.warmUp(ownerBookingReport, Map.of("REPORT_DATE", "",
                    "FILTER_SUMMARY", "Phạm vi: Tất cả đơn đặt sân", "TOTAL_RECORDS", 0,
                    "LOGO", JasperRenderer.stream(logo)));
            log.info("Owner booking report engine initialized in {} ms", elapsedMillis(startedAt, System.nanoTime()));
        } catch (IOException | JRException exception) {
            throw new IllegalStateException("Không thể khởi tạo báo cáo đơn đặt sân.", exception);
        }
    }

    /** Danh sách người dùng đọc thẳng từ DB của auth-service (chỉ đọc) qua datasource riêng. */
    @Override
    public byte[] exportUsers(String format) throws ServiceException, JRException, IOException, SQLException {
        JasperReport report = renderer.load("templates/jasper/user/users.jasper");
        try (Connection connection = authDataSource.getConnection()) {
            Map<String, Object> parameters = new HashMap<>();
            parameters.put("REPORT_DATE", ReportLabels.today());
            return renderer.export(JasperFillManager.fillReport(report, parameters, connection), format);
        }
    }

    @Override
    public byte[] exportUserDetail(String format, String userId) throws ServiceException, JRException, IOException {
        UserDetailReportResponse user = authClient.getUserById(userId).getData();
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("REPORT_DATE", ReportLabels.today());
        JasperPrint print = JasperFillManager.fillReport(renderer.load("templates/jasper/user/user-detail.jasper"),
                parameters, new JRBeanCollectionDataSource(Collections.singletonList(user)));
        return renderer.export(print, format);
    }

    @Override
    public byte[] exportOwnerBookings(String format, OwnerBookingReportFilter filter)
            throws ServiceException, JRException, IOException {
        return exportBookings(format, filter, false);
    }

    @Override
    public byte[] exportAdminBookings(String format, OwnerBookingReportFilter filter)
            throws ServiceException, JRException, IOException {
        return exportBookings(format, filter, true);
    }

    private byte[] exportBookings(String format, OwnerBookingReportFilter filter, boolean platformWide)
            throws ServiceException, JRException, IOException {
        long startedAt = System.nanoTime();
        OwnerBookingReportFilter.validate(filter, format);

        List<VenueOwnerBookingResponse> bookings = loadAllBookings(filter, platformWide);
        long dataLoadedAt = System.nanoTime();
        List<OwnerBookingReportRow> rows = OwnerBookingReportMapper.toRows(bookings, filter.paymentStatus());
        long rowsMappedAt = System.nanoTime();
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("REPORT_DATE", ReportLabels.generatedAt());
        parameters.put("FILTER_SUMMARY", OwnerBookingReportMapper.filterSummary(filter));
        parameters.put("TOTAL_RECORDS", rows.size());
        parameters.put("LOGO", JasperRenderer.stream(logo));

        JasperPrint print = JasperFillManager.fillReport(ownerBookingReport, parameters, new JRBeanCollectionDataSource(rows));
        long reportFilledAt = System.nanoTime();
        byte[] bytes = renderer.export(print, format);
        long completedAt = System.nanoTime();
        log.info("Booking report generated: platformWide={}, format={}, rows={}, fetch={} ms, map={} ms, fill={} ms, export={} ms, total={} ms, bytes={}",
                platformWide, format.toUpperCase(Locale.ROOT), rows.size(),
                elapsedMillis(startedAt, dataLoadedAt), elapsedMillis(dataLoadedAt, rowsMappedAt),
                elapsedMillis(rowsMappedAt, reportFilledAt), elapsedMillis(reportFilledAt, completedAt),
                elapsedMillis(startedAt, completedAt), bytes.length);
        return bytes;
    }

    @Override
    public byte[] exportOwnerBookingInvoice(UUID bookingId) throws ServiceException, JRException, IOException {
        if (bookingId == null) throw new ServiceException("Mã đơn đặt sân là bắt buộc để tạo hóa đơn.");
        long startedAt = System.nanoTime();
        // venue-service chỉ trả đơn thuộc cơ sở của chủ sân đang đăng nhập — quyền truy cập được kiểm ở đó.
        BaseResponse<VenueOwnerBookingResponse> response = venueClient.getOwnerBooking(bookingId);
        VenueOwnerBookingResponse booking = response == null ? null : response.getData();
        if (booking == null) {
            throw new ServiceException("Dịch vụ quản lý sân không trả dữ liệu đơn đặt sân để tạo hóa đơn.");
        }
        long dataLoadedAt = System.nanoTime();
        OwnerBookingInvoiceReport invoice = OwnerBookingReportMapper.toInvoice(booking);
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("LOGO", JasperRenderer.stream(logo));
        parameters.put("QR_CODE", JasperRenderer.stream(renderer.qrPng(booking.getQrCode())));

        JasperPrint print = JasperFillManager.fillReport(ownerBookingInvoiceReport, parameters,
                new JRBeanCollectionDataSource(Collections.singletonList(invoice)));
        long reportFilledAt = System.nanoTime();
        byte[] bytes = renderer.exportPdf(print);
        long completedAt = System.nanoTime();
        log.info("Owner booking invoice generated: bookingId={}, fetch={} ms, fill={} ms, export={} ms, total={} ms, bytes={}",
                bookingId, elapsedMillis(startedAt, dataLoadedAt), elapsedMillis(dataLoadedAt, reportFilledAt),
                elapsedMillis(reportFilledAt, completedAt), elapsedMillis(startedAt, completedAt), bytes.length);
        return bytes;
    }

    @Override
    public byte[] exportOwnerRevenue(String format, OwnerRevenueReportFilter filter)
            throws ServiceException, JRException, IOException {
        OwnerRevenueReportFilter.validate(filter, format);
        long startedAt = System.nanoTime();
        BaseResponse<VenueOwnerRevenueResponse> response = venueClient.getOwnerRevenue(
                filter.venueId(), filter.fromDate(), filter.toDate());
        VenueOwnerRevenueResponse revenue = response == null ? null : response.getData();
        if (revenue == null || revenue.getCurrentPeriod() == null || revenue.getPreviousPeriod() == null) {
            throw new ServiceException("Dịch vụ quản lý sân không trả dữ liệu doanh thu để xuất báo cáo.");
        }
        return renderOwnerRevenue(format, filter, revenue, startedAt);
    }

    /** Dựng file từ dữ liệu doanh thu có sẵn (báo cáo định kỳ dùng bản snapshot đã chốt). */
    public byte[] renderOwnerRevenue(String format, OwnerRevenueReportFilter filter, VenueOwnerRevenueResponse revenue)
            throws ServiceException, JRException {
        OwnerRevenueReportFilter.validate(filter, format);
        return renderOwnerRevenue(format, filter, revenue, System.nanoTime());
    }

    private byte[] renderOwnerRevenue(String format, OwnerRevenueReportFilter filter, VenueOwnerRevenueResponse revenue,
                                      long startedAt) throws JRException, ServiceException {
        List<OwnerRevenueReportRow> rows = OwnerRevenueReportMapper.toRows(revenue);
        Map<String, Object> parameters = OwnerRevenueReportMapper.parameters(filter, revenue);
        parameters.put("LOGO", JasperRenderer.stream(logo));

        JasperPrint print = JasperFillManager.fillReport(ownerRevenueReport, parameters, new JRBeanCollectionDataSource(rows));
        byte[] bytes = renderer.export(print, format);
        log.info("Owner revenue report generated: format={}, rows={}, total={} ms, bytes={}",
                format.toUpperCase(Locale.ROOT), rows.size(), elapsedMillis(startedAt, System.nanoTime()), bytes.length);
        return bytes;
    }

    /** venue-service phân trang cố định 20 dòng; đi hết các trang, dừng sớm nếu vượt giới hạn xuất. */
    private List<VenueOwnerBookingResponse> loadAllBookings(OwnerBookingReportFilter filter, boolean platformWide)
            throws ServiceException {
        List<VenueOwnerBookingResponse> bookings = new ArrayList<>();
        int page = 0;
        int pages;
        do {
            String status = ReportLabels.blankToNull(filter.status());
            String query = ReportLabels.blankToNull(filter.query());
            BaseResponse<VenueOwnerBookingPageResponse> response = platformWide
                    ? venueClient.getAdminBookings(filter.venueId(), filter.venueCourtId(), status, query, filter.fromDate(),
                    filter.toDate(), page, ADMIN_BOOKING_PAGE_SIZE)
                    : venueClient.getOwnerBookings(filter.venueId(), filter.venueCourtId(), status, query,
                    filter.fromDate(), filter.toDate(), page, BOOKING_PAGE_SIZE);
            VenueOwnerBookingPageResponse data = response == null ? null : response.getData();
            if (data == null) throw new ServiceException("Dịch vụ quản lý sân không trả dữ liệu đơn đặt sân để xuất báo cáo.");

            if (data.getResult() != null) bookings.addAll(data.getResult());
            if (bookings.size() > MAX_BOOKING_EXPORT_ROWS) {
                throw new ServiceException("Báo cáo vượt quá 10.000 đơn đặt sân. Hãy thu hẹp khoảng ngày hoặc cơ sở.");
            }
            pages = data.getMeta() == null ? 0 : data.getMeta().getPages();
            page++;
        } while (page < pages);
        return bookings;
    }

    private static long elapsedMillis(long startedAt, long completedAt) {
        return Math.max(0L, (completedAt - startedAt) / 1_000_000L);
    }
}
