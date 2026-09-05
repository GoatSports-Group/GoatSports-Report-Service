package iuh.fit.reportservice.infrastructure.report;

import iuh.fit.reportservice.application.dto.report.OwnerBookingReportFilter;
import iuh.fit.reportservice.application.dto.report.OwnerBookingReportRow;
import iuh.fit.reportservice.application.dto.report.UserDetailReportResponse;
import iuh.fit.reportservice.application.dto.response.base.BaseResponse;
import iuh.fit.reportservice.application.dto.venue.VenueOwnerBookingPageResponse;
import iuh.fit.reportservice.application.dto.venue.VenueOwnerBookingResponse;
import iuh.fit.reportservice.application.dto.venue.VenueOwnerPaymentResponse;
import iuh.fit.reportservice.application.exception.ServiceException;
import iuh.fit.reportservice.application.port.report.ReportPort;
import iuh.fit.reportservice.application.enums.FormatType;
import iuh.fit.reportservice.infrastructure.external.client.AuthClient;
import iuh.fit.reportservice.infrastructure.external.client.VenueClient;
import jakarta.annotation.PostConstruct;
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
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportAdapter implements ReportPort {
    private static final int BOOKING_PAGE_SIZE = 20;
    private static final int MAX_BOOKING_EXPORT_ROWS = 10_000;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final AuthClient authClient;
    private final VenueClient venueClient;
    private JasperReport ownerBookingReport;
    private byte[] ownerBookingLogo;

    @Qualifier("authDataSource")
    private final DataSource authDataSource;

    @PostConstruct
    void initializeOwnerBookingReport() {
        long startedAt = System.nanoTime();
        try (InputStream reportStream = new ClassPathResource(
                "templates/jasper/owner-booking/owner-bookings.jasper"
        ).getInputStream();
             InputStream logoStream = new ClassPathResource("images/goat-report.png").getInputStream()) {
            JasperReport loadedReport = (JasperReport) JRLoader.loadObject(reportStream);
            byte[] loadedLogo = logoStream.readAllBytes();

            warmUpOwnerBookingExporters(loadedReport, loadedLogo);

            ownerBookingReport = loadedReport;
            ownerBookingLogo = loadedLogo;
            log.info("Owner booking report engine initialized in {} ms", elapsedMillis(startedAt));
        } catch (IOException | JRException exception) {
            throw new IllegalStateException("Không thể khởi tạo báo cáo đơn đặt sân.", exception);
        }
    }

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

    @Override
    public byte[] exportOwnerBookings(String format, OwnerBookingReportFilter filter)
            throws ServiceException, JRException, IOException {
        long startedAt = System.nanoTime();
        validateOwnerBookingExport(format, filter);

        List<VenueOwnerBookingResponse> bookings = loadAllOwnerBookings(filter);
        long dataLoadedAt = System.nanoTime();
        List<OwnerBookingReportRow> rows = toBookingReportRows(bookings, filter.paymentStatus());
        long rowsMappedAt = System.nanoTime();
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("REPORT_DATE", getDateTimeFormat());
        parameters.put("FILTER_SUMMARY", bookingFilterSummary(filter));
        parameters.put("TOTAL_RECORDS", rows.size());

        try (InputStream logoStream = new ByteArrayInputStream(ownerBookingLogo);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            parameters.put("LOGO", logoStream);
            JasperPrint jasperPrint = JasperFillManager.fillReport(
                    ownerBookingReport,
                    parameters,
                    new JRBeanCollectionDataSource(rows)
            );
            long reportFilledAt = System.nanoTime();
            if (FormatType.PDF.getValue().equalsIgnoreCase(format)) {
                JasperExportManager.exportReportToPdfStream(jasperPrint, outputStream);
            } else {
                JRXlsxExporter exporter = getJrXlsxExporter(jasperPrint, outputStream);
                exporter.exportReport();
            }
            byte[] reportBytes = outputStream.toByteArray();
            long completedAt = System.nanoTime();
            log.info(
                    "Owner booking report generated: format={}, rows={}, fetch={} ms, map={} ms, fill={} ms, export={} ms, total={} ms, bytes={}",
                    format.toUpperCase(Locale.ROOT),
                    rows.size(),
                    elapsedMillis(startedAt, dataLoadedAt),
                    elapsedMillis(dataLoadedAt, rowsMappedAt),
                    elapsedMillis(rowsMappedAt, reportFilledAt),
                    elapsedMillis(reportFilledAt, completedAt),
                    elapsedMillis(startedAt, completedAt),
                    reportBytes.length
            );
            return reportBytes;
        }
    }

    private void warmUpOwnerBookingExporters(JasperReport report, byte[] logo) throws JRException {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("REPORT_DATE", "");
        parameters.put("FILTER_SUMMARY", "Phạm vi: Tất cả đơn đặt sân");
        parameters.put("TOTAL_RECORDS", 0);
        parameters.put("LOGO", new ByteArrayInputStream(logo));

        JasperPrint jasperPrint = JasperFillManager.fillReport(
                report,
                parameters,
                new JRBeanCollectionDataSource(Collections.emptyList())
        );
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            JasperExportManager.exportReportToPdfStream(jasperPrint, outputStream);
            outputStream.reset();
            JRXlsxExporter exporter = getJrXlsxExporter(jasperPrint, outputStream);
            exporter.exportReport();
        } catch (IOException exception) {
            throw new JRException("Không thể làm nóng bộ xuất báo cáo đơn đặt sân.", exception);
        }
    }

    private List<VenueOwnerBookingResponse> loadAllOwnerBookings(OwnerBookingReportFilter filter)
            throws ServiceException {
        List<VenueOwnerBookingResponse> bookings = new ArrayList<>();
        int page = 0;
        int pages;

        do {
            BaseResponse<VenueOwnerBookingPageResponse> response = venueClient.getOwnerBookings(
                    filter.venueId(),
                    filter.venueCourtId(),
                    blankToNull(filter.status()),
                    blankToNull(filter.query()),
                    filter.fromDate(),
                    filter.toDate(),
                    page,
                    BOOKING_PAGE_SIZE
            );
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

    private List<OwnerBookingReportRow> toBookingReportRows(
            List<VenueOwnerBookingResponse> bookings,
            String paymentFilter
    ) {
        List<VenueOwnerBookingResponse> filtered = bookings.stream()
                .filter(booking -> matchesPaymentFilter(booking, paymentFilter))
                .toList();
        List<OwnerBookingReportRow> rows = new ArrayList<>(filtered.size());
        for (int index = 0; index < filtered.size(); index++) {
            VenueOwnerBookingResponse booking = filtered.get(index);
            rows.add(OwnerBookingReportRow.builder()
                    .sequence(index + 1)
                    .bookingCode(valueOrDash(booking.getBookingCode()))
                    .customerName(customerName(booking))
                    .customerPhone(valueOrDash(booking.getWalkInCustomerPhone()))
                    .venueName(valueOrDash(booking.getVenueName()))
                    .courtName(valueOrDash(booking.getCourtName()))
                    .playDate(booking.getPlayDate() == null ? "—" : DATE_FORMAT.format(booking.getPlayDate()))
                    .timeRange(timeRange(booking))
                    .totalPrice(booking.getTotalPrice() == null ? 0D : booking.getTotalPrice())
                    .paymentStatus(paymentLabel(booking))
                    .bookingStatus(bookingStatusLabel(booking.getStatus()))
                    .build());
        }
        return rows;
    }

    private boolean matchesPaymentFilter(VenueOwnerBookingResponse booking, String paymentFilter) {
        String normalized = blankToNull(paymentFilter);
        if (normalized == null) return true;
        return switch (normalized.toUpperCase(Locale.ROOT)) {
            case "PAID" -> isPaid(booking);
            case "UNPAID" -> !isPaid(booking);
            case "FAILED" -> hasFailedPayment(booking);
            default -> true;
        };
    }

    private boolean isPaid(VenueOwnerBookingResponse booking) {
        return booking.getRemainingPaymentId() != null || payments(booking).stream()
                .anyMatch(payment -> "BOOKING_REMAINING".equals(payment.getPurpose())
                        && "SUCCEEDED".equals(payment.getStatus()));
    }

    private boolean hasFailedPayment(VenueOwnerBookingResponse booking) {
        return payments(booking).stream()
                .anyMatch(payment -> Set.of("FAILED", "EXPIRED", "CANCELLED").contains(payment.getStatus()));
    }

    private List<VenueOwnerPaymentResponse> payments(VenueOwnerBookingResponse booking) {
        return booking.getPayments() == null ? Collections.emptyList() : booking.getPayments();
    }

    private String paymentLabel(VenueOwnerBookingResponse booking) {
        if (isPaid(booking)) return "Đã thanh toán";
        if (hasFailedPayment(booking)) return "Thanh toán thất bại";
        boolean pending = payments(booking).stream().anyMatch(payment ->
                "BOOKING_REMAINING".equals(payment.getPurpose())
                        && Set.of("CREATED", "PENDING").contains(payment.getStatus())
        );
        return pending ? "Chờ thanh toán" : "Chưa thanh toán";
    }

    private String bookingStatusLabel(String status) {
        if (status == null) return "—";
        return switch (status) {
            case "PENDING_PAYMENT" -> "Chờ thanh toán";
            case "CONFIRMED" -> "Đã xác nhận";
            case "CHECKED_IN" -> "Đã nhận sân";
            case "COMPLETED" -> "Hoàn tất";
            case "CANCELLED" -> "Đã hủy";
            case "REFUND_PENDING" -> "Chờ hoàn tiền";
            case "REFUNDED" -> "Đã hoàn tiền";
            case "EXPIRED" -> "Hết hạn";
            default -> status;
        };
    }

    private String customerName(VenueOwnerBookingResponse booking) {
        String walkInName = blankToNull(booking.getWalkInCustomerName());
        if (walkInName != null) return walkInName;
        if (booking.getPlayerId() == null) return "Khách vãng lai";
        String playerId = booking.getPlayerId().toString().replace("-", "");
        return "Khách #" + playerId.substring(0, Math.min(8, playerId.length())).toUpperCase(Locale.ROOT);
    }

    private String timeRange(VenueOwnerBookingResponse booking) {
        if (booking.getStartTime() == null || booking.getEndTime() == null) return "—";
        return TIME_FORMAT.format(booking.getStartTime()) + " - " + TIME_FORMAT.format(booking.getEndTime());
    }

    private String bookingFilterSummary(OwnerBookingReportFilter filter) {
        List<String> values = new ArrayList<>();
        if (filter.fromDate() != null || filter.toDate() != null) {
            values.add("Ngày: " + (filter.fromDate() == null ? "..." : DATE_FORMAT.format(filter.fromDate()))
                    + " - " + (filter.toDate() == null ? "..." : DATE_FORMAT.format(filter.toDate())));
        }
        if (blankToNull(filter.status()) != null) {
            values.add("Trạng thái đơn: " + bookingStatusLabel(filter.status()));
        }
        if (blankToNull(filter.paymentStatus()) != null) {
            values.add("Thanh toán: " + switch (filter.paymentStatus().toUpperCase(Locale.ROOT)) {
                case "PAID" -> "Đã thanh toán";
                case "UNPAID" -> "Chưa thanh toán";
                case "FAILED" -> "Thất bại";
                default -> filter.paymentStatus();
            });
        }
        if (blankToNull(filter.query()) != null) values.add("Từ khóa: " + filter.query().trim());
        return values.isEmpty() ? "Phạm vi: Tất cả đơn đặt sân" : String.join("  |  ", values);
    }

    private void validateOwnerBookingExport(String format, OwnerBookingReportFilter filter) throws ServiceException {
        if (!FormatType.XLSX.getValue().equalsIgnoreCase(format)
                && !FormatType.PDF.getValue().equalsIgnoreCase(format)) {
            throw new ServiceException("Báo cáo đơn đặt sân chỉ hỗ trợ định dạng PDF hoặc XLSX.");
        }
        if (filter == null) throw new ServiceException("Bộ lọc báo cáo đơn đặt sân là bắt buộc.");
        if (filter.fromDate() != null && filter.toDate() != null && filter.fromDate().isAfter(filter.toDate())) {
            throw new ServiceException("Khoảng ngày đặt sân không hợp lệ.");
        }
        String paymentStatus = blankToNull(filter.paymentStatus());
        if (paymentStatus != null && !Set.of("PAID", "UNPAID", "FAILED")
                .contains(paymentStatus.toUpperCase(Locale.ROOT))) {
            throw new ServiceException("Trạng thái thanh toán không hợp lệ.");
        }
    }

    private String valueOrDash(String value) {
        String normalized = blankToNull(value);
        return normalized == null ? "—" : normalized;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String getDateFormat() {
        return new SimpleDateFormat("dd/MM/yyyy").format(new Date());
    }

    private String getDateTimeFormat() {
        return new SimpleDateFormat("dd/MM/yyyy HH:mm").format(new Date());
    }

    private long elapsedMillis(long startedAt) {
        return elapsedMillis(startedAt, System.nanoTime());
    }

    private long elapsedMillis(long startedAt, long completedAt) {
        return Math.max(0L, (completedAt - startedAt) / 1_000_000L);
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
