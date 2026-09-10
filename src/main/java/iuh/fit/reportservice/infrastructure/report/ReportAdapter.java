package iuh.fit.reportservice.infrastructure.report;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import iuh.fit.reportservice.application.dto.report.OwnerBookingReportFilter;
import iuh.fit.reportservice.application.dto.report.OwnerBookingInvoiceReport;
import iuh.fit.reportservice.application.dto.report.OwnerBookingReportRow;
import iuh.fit.reportservice.application.dto.report.OwnerRevenueReportFilter;
import iuh.fit.reportservice.application.dto.report.OwnerRevenueReportRow;
import iuh.fit.reportservice.application.dto.report.UserDetailReportResponse;
import iuh.fit.reportservice.application.dto.response.base.BaseResponse;
import iuh.fit.reportservice.application.dto.venue.VenueOwnerBookingPageResponse;
import iuh.fit.reportservice.application.dto.venue.VenueOwnerBookingResponse;
import iuh.fit.reportservice.application.dto.venue.VenueOwnerPaymentResponse;
import iuh.fit.reportservice.application.dto.venue.VenueOwnerRevenueResponse;
import iuh.fit.reportservice.application.dto.venue.VenueOwnerDailyRevenueResponse;
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

import javax.imageio.ImageIO;
import javax.sql.DataSource;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportAdapter implements ReportPort {
    private static final int BOOKING_PAGE_SIZE = 20;
    private static final int MAX_BOOKING_EXPORT_ROWS = 10_000;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy - HH:mm");

    private final AuthClient authClient;
    private final VenueClient venueClient;
    private JasperReport ownerBookingReport;
    private JasperReport ownerBookingInvoiceReport;
    private JasperReport ownerRevenueReport;
    private byte[] ownerBookingLogo;

    @Qualifier("authDataSource")
    private final DataSource authDataSource;

    @PostConstruct
    void initializeOwnerBookingReport() {
        long startedAt = System.nanoTime();
        try (InputStream reportStream = new ClassPathResource(
                "templates/jasper/owner-booking/owner-bookings.jasper"
        ).getInputStream();
             InputStream invoiceStream = new ClassPathResource(
                     "templates/jasper/owner-booking/owner-booking-invoice.jasper"
             ).getInputStream();
             InputStream revenueStream = new ClassPathResource(
                     "templates/jasper/owner-revenue/owner-revenue.jasper"
             ).getInputStream();
             InputStream logoStream = new ClassPathResource("images/goat-report.png").getInputStream()) {
            JasperReport loadedReport = (JasperReport) JRLoader.loadObject(reportStream);
            JasperReport loadedInvoiceReport = (JasperReport) JRLoader.loadObject(invoiceStream);
            JasperReport loadedRevenueReport = (JasperReport) JRLoader.loadObject(revenueStream);
            byte[] loadedLogo = logoStream.readAllBytes();

            warmUpOwnerBookingExporters(loadedReport, loadedLogo);

            ownerBookingReport = loadedReport;
            ownerBookingInvoiceReport = loadedInvoiceReport;
            ownerRevenueReport = loadedRevenueReport;
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
        OwnerBookingReportFilter.validate(filter, format);

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

    @Override
    public byte[] exportOwnerBookingInvoice(UUID bookingId)
            throws ServiceException, JRException, IOException {
        if (bookingId == null) throw new ServiceException("Mã đơn đặt sân là bắt buộc để tạo hóa đơn.");

        long startedAt = System.nanoTime();
        BaseResponse<VenueOwnerBookingResponse> response = venueClient.getOwnerBooking(bookingId);
        VenueOwnerBookingResponse booking = response == null ? null : response.getData();
        if (booking == null) {
            throw new ServiceException("Dịch vụ quản lý sân không trả dữ liệu đơn đặt sân để tạo hóa đơn.");
        }
        long dataLoadedAt = System.nanoTime();
        OwnerBookingInvoiceReport invoice = toOwnerBookingInvoice(booking);
        Map<String, Object> parameters = new HashMap<>();
        byte[] bookingQr = generateQrCode(booking.getQrCode());

        try (InputStream logoStream = new ByteArrayInputStream(ownerBookingLogo);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            parameters.put("LOGO", logoStream);
            parameters.put("QR_CODE", bookingQr == null ? null : new ByteArrayInputStream(bookingQr));
            JasperPrint jasperPrint = JasperFillManager.fillReport(
                    ownerBookingInvoiceReport,
                    parameters,
                    new JRBeanCollectionDataSource(Collections.singletonList(invoice))
            );
            long reportFilledAt = System.nanoTime();
            JasperExportManager.exportReportToPdfStream(jasperPrint, outputStream);
            byte[] reportBytes = outputStream.toByteArray();
            long completedAt = System.nanoTime();
            log.info(
                    "Owner booking invoice generated: bookingId={}, fetch={} ms, fill={} ms, export={} ms, total={} ms, bytes={}",
                    bookingId,
                    elapsedMillis(startedAt, dataLoadedAt),
                    elapsedMillis(dataLoadedAt, reportFilledAt),
                    elapsedMillis(reportFilledAt, completedAt),
                    elapsedMillis(startedAt, completedAt),
                    reportBytes.length
            );
            return reportBytes;
        }
    }

    @Override
    public byte[] exportOwnerRevenue(String format, OwnerRevenueReportFilter filter)
            throws ServiceException, JRException, IOException {
        OwnerRevenueReportFilter.validate(filter, format);
        long startedAt = System.nanoTime();
        BaseResponse<VenueOwnerRevenueResponse> response = venueClient.getOwnerRevenue(
                filter.venueId(), filter.fromDate(), filter.toDate()
        );
        VenueOwnerRevenueResponse revenue = response == null ? null : response.getData();
        if (revenue == null || revenue.getCurrentPeriod() == null || revenue.getPreviousPeriod() == null) {
            throw new ServiceException("Dịch vụ quản lý sân không trả dữ liệu doanh thu để xuất báo cáo.");
        }
        List<VenueOwnerDailyRevenueResponse> dailyRevenue = revenue.getDailyRevenue() == null
                ? Collections.emptyList()
                : revenue.getDailyRevenue();
        double totalRevenue = moneyValue(revenue.getCurrentPeriod().getTotalRevenue());
        List<OwnerRevenueReportRow> rows = new ArrayList<>(dailyRevenue.size());
        for (int index = 0; index < dailyRevenue.size(); index++) {
            VenueOwnerDailyRevenueResponse day = dailyRevenue.get(index);
            double dayRevenue = moneyValue(day.getRevenue());
            rows.add(OwnerRevenueReportRow.builder()
                    .sequence(index + 1)
                    .reportDate(day.getDate() == null ? "—" : DATE_FORMAT.format(day.getDate()))
                    .revenue(dayRevenue)
                    .succeededPaymentCount(day.getSucceededPaymentCount() == null ? 0L : day.getSucceededPaymentCount())
                    .revenueShare(totalRevenue <= 0D ? 0D : dayRevenue * 100D / totalRevenue)
                    .build());
        }
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("REPORT_DATE", getDateTimeFormat());
        parameters.put("PERIOD_LABEL", valueOrDash(filter.periodLabel()));
        parameters.put("VENUE_NAME", valueOrDash(filter.venueName()));
        parameters.put("DATE_RANGE", DATE_FORMAT.format(filter.fromDate()) + " - " + DATE_FORMAT.format(filter.toDate()));
        parameters.put("CURRENT_REVENUE", totalRevenue);
        parameters.put("PREVIOUS_REVENUE", moneyValue(revenue.getPreviousPeriod().getTotalRevenue()));
        parameters.put("REVENUE_CHANGE", moneyValue(revenue.getRevenueChangePercentage()));
        parameters.put("BOOKING_COUNT", revenue.getCurrentPeriod().getBookingCount() == null ? 0L : revenue.getCurrentPeriod().getBookingCount());
        parameters.put("PAID_BOOKING_COUNT", revenue.getCurrentPeriod().getPaidBookingCount() == null ? 0L : revenue.getCurrentPeriod().getPaidBookingCount());
        parameters.put("AVERAGE_REVENUE", revenue.getCurrentPeriod().getPaidBookingCount() == null
                || revenue.getCurrentPeriod().getPaidBookingCount() == 0L
                ? 0D : totalRevenue / revenue.getCurrentPeriod().getPaidBookingCount());
        parameters.put("STATUS_SUMMARY", revenueStatusSummary(revenue));
        parameters.put(JRParameter.REPORT_LOCALE, Locale.forLanguageTag("vi-VN"));

        try (InputStream logoStream = new ByteArrayInputStream(ownerBookingLogo);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            parameters.put("LOGO", logoStream);
            JasperPrint jasperPrint = JasperFillManager.fillReport(
                    ownerRevenueReport,
                    parameters,
                    new JRBeanCollectionDataSource(rows)
            );
            if (FormatType.PDF.getValue().equalsIgnoreCase(format)) {
                JasperExportManager.exportReportToPdfStream(jasperPrint, outputStream);
            } else {
                JRXlsxExporter exporter = getJrXlsxExporter(jasperPrint, outputStream);
                exporter.exportReport();
            }
            byte[] bytes = outputStream.toByteArray();
            log.info("Owner revenue report generated: format={}, rows={}, total={} ms, bytes={}",
                    format.toUpperCase(Locale.ROOT), rows.size(), elapsedMillis(startedAt), bytes.length);
            return bytes;
        }
    }

    private OwnerBookingInvoiceReport toOwnerBookingInvoice(VenueOwnerBookingResponse booking) {
        double total = nonNegative(booking.getTotalPrice());
        double paid = paidAmount(booking, total);
        boolean fullyPaid = isPaid(booking) || paid + 0.01D >= total;
        if (fullyPaid) paid = total;
        double outstanding = fullyPaid ? 0D : Math.max(0D, total - paid);
        VenueOwnerPaymentResponse payment = displayPayment(booking);

        return OwnerBookingInvoiceReport.builder()
                .invoiceNumber("HD-" + valueOrDash(booking.getBookingCode()))
                .issuedAt(getDateTimeFormat())
                .bookingCode(valueOrDash(booking.getBookingCode()))
                .bookingStatus(bookingStatusLabel(booking.getStatus()))
                .createdAt(formatTimelineDateTime(booking.getCreatedAt()))
                .customerName(customerName(booking))
                .customerPhone(valueOrDash(booking.getWalkInCustomerPhone()))
                .customerType(bookingChannelLabel(booking.getSource()))
                .venueName(valueOrDash(booking.getVenueName()))
                .courtName(valueOrDash(booking.getCourtName()))
                .playDate(booking.getPlayDate() == null ? "—" : DATE_FORMAT.format(booking.getPlayDate()))
                .timeRange(timeRange(booking))
                .bookingChannel(bookingChannelLabel(booking.getSource()))
                .itemDescription("Tiền sân " + valueOrDash(booking.getCourtName()) + " · " + timeRange(booking))
                .totalAmount(formatMoney(total))
                .serviceFeeAmount(formatMoney(0D))
                .discountAmount(formatMoney(0D))
                .finalAmount(formatMoney(total))
                .depositAmount(formatMoney(nonNegative(booking.getDepositAmount())))
                .paidAmount(formatMoney(paid))
                .outstandingAmount(formatMoney(outstanding))
                .paymentMethod(paymentMethodLabel(payment))
                .transactionCode(payment == null ? "—" : valueOrDash(payment.getProviderTransactionId()))
                .paymentStatus(invoicePaymentStatus(booking, paid, total, fullyPaid))
                .paidAt(payment == null || payment.getPaidAt() == null
                        ? "—"
                        : DATE_TIME_FORMAT.format(payment.getPaidAt()))
                .historyLines(historyLines(booking))
                .build();
    }

    private byte[] generateQrCode(String content) throws IOException {
        String normalized = blankToNull(content);
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

    private String historyLines(VenueOwnerBookingResponse booking) {
        List<String> lines = new ArrayList<>();
        lines.add(formatTimelineDateTime(booking.getCreatedAt()) + "   Tạo đơn đặt sân");
        payments(booking).stream()
                .sorted(Comparator.comparing(this::paymentTimestamp, Comparator.nullsLast(Comparator.naturalOrder())))
                .forEach(payment -> lines.add(
                        formatTimelineDateTime(paymentTimestamp(payment)) + "   " + paymentTimelineLabel(payment)
                ));
        lines.add(formatTimelineDateTime(
                booking.getUpdatedAt() == null ? booking.getCreatedAt() : booking.getUpdatedAt()
        ) + "   " + bookingStatusLabel(booking.getStatus()));
        return String.join("\n", lines);
    }

    private String paymentTimelineLabel(VenueOwnerPaymentResponse payment) {
        String label = "BOOKING_DEPOSIT".equals(payment.getPurpose())
                ? "Thanh toán tiền cọc"
                : "Thanh toán còn lại";
        if ("SUCCEEDED".equals(payment.getStatus())) return label;
        return label + " – " + paymentAttemptStatusLabel(payment.getStatus());
    }

    private String paymentAttemptStatusLabel(String status) {
        if (status == null) return "Chưa ghi nhận";
        return switch (status) {
            case "CREATED", "PENDING" -> "Đang chờ";
            case "FAILED" -> "Thất bại";
            case "EXPIRED" -> "Hết hạn";
            case "CANCELLED" -> "Đã hủy";
            case "SUCCEEDED" -> "Thành công";
            default -> status;
        };
    }

    private String formatTimelineDateTime(LocalDateTime value) {
        return value == null ? "—" : DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy").format(value);
    }

    private double paidAmount(VenueOwnerBookingResponse booking, double total) {
        List<VenueOwnerPaymentResponse> succeeded = payments(booking).stream()
                .filter(payment -> "SUCCEEDED".equals(payment.getStatus()))
                .toList();
        double paid = succeeded.stream().mapToDouble(payment -> nonNegative(payment.getAmount())).sum();
        Set<UUID> succeededIds = succeeded.stream()
                .map(VenueOwnerPaymentResponse::getPaymentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (booking.getDepositPaymentId() != null && !succeededIds.contains(booking.getDepositPaymentId())) {
            paid += nonNegative(booking.getDepositAmount());
        }
        if (booking.getRemainingPaymentId() != null && !succeededIds.contains(booking.getRemainingPaymentId())) {
            paid += nonNegative(booking.getRemainingAmount());
        }
        return Math.min(total, Math.max(0D, paid));
    }

    private VenueOwnerPaymentResponse displayPayment(VenueOwnerBookingResponse booking) {
        List<VenueOwnerPaymentResponse> sorted = payments(booking).stream()
                .sorted(Comparator.comparing(this::paymentTimestamp, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        return sorted.stream().filter(this::hasPaymentMethod).findFirst()
                .orElse(sorted.isEmpty() ? null : sorted.get(0));
    }

    private LocalDateTime paymentTimestamp(VenueOwnerPaymentResponse payment) {
        return payment.getPaidAt() != null ? payment.getPaidAt() : payment.getCreatedAt();
    }

    private boolean hasPaymentMethod(VenueOwnerPaymentResponse payment) {
        return payment != null && (blankToNull(payment.getMethod()) != null
                || blankToNull(payment.getProvider()) != null
                || blankToNull(payment.getProviderTransactionId()) != null);
    }

    private String paymentMethodLabel(VenueOwnerPaymentResponse payment) {
        if (payment == null) return "—";
        String method = blankToNull(payment.getMethod());
        String provider = blankToNull(payment.getProvider());
        String transactionCode = blankToNull(payment.getProviderTransactionId());
        if ("CASH".equalsIgnoreCase(method)
                || transactionCode != null && transactionCode.toUpperCase(Locale.ROOT).startsWith("CASH-")) {
            return "Tiền mặt";
        }
        if ("BANK_TRANSFER".equalsIgnoreCase(method) || "PAYOS".equalsIgnoreCase(provider)) {
            return "Ví điện tử (PayOS)";
        }
        return "—";
    }

    private String invoicePaymentStatus(
            VenueOwnerBookingResponse booking,
            double paid,
            double total,
            boolean fullyPaid
    ) {
        if (fullyPaid) return "Đã thanh toán đủ";
        if (paid > 0D && paid < total) return "Chưa thanh toán đủ";
        if (payments(booking).stream().anyMatch(payment -> Set.of("CREATED", "PENDING").contains(payment.getStatus()))) {
            return "Đang chờ thanh toán";
        }
        if (hasFailedPayment(booking)) return "Thanh toán chưa thành công";
        return "Chưa thanh toán";
    }

    private String bookingChannelLabel(String source) {
        if (source == null) return "—";
        return switch (source) {
            case "WALK_IN" -> "Khách tại quầy";
            case "DIRECT" -> "Đặt trực tuyến";
            case "AI_MATCHMAKING" -> "Ghép trận";
            default -> source;
        };
    }

    private double nonNegative(Double value) {
        return value == null ? 0D : Math.max(0D, value);
    }

    private String formatMoney(double value) {
        return String.format(Locale.of("vi", "VN"), "%,.0fđ", value);
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

    private String revenueStatusSummary(VenueOwnerRevenueResponse revenue) {
        if (revenue.getPaymentStatusBreakdown() == null || revenue.getPaymentStatusBreakdown().isEmpty()) {
            return "Chưa có giao dịch thanh toán trong kỳ";
        }
        return revenue.getPaymentStatusBreakdown().stream()
                .map(row -> paymentStatusLabel(row.getStatus()) + ": "
                        + (row.getPaymentCount() == null ? 0L : row.getPaymentCount()) + " giao dịch · "
                        + formatMoney(moneyValue(row.getNominalAmount())))
                .collect(Collectors.joining("  |  "));
    }

    private String paymentStatusLabel(String status) {
        if (status == null) return "Không xác định";
        return switch (status) {
            case "CREATED" -> "Đã tạo";
            case "PENDING" -> "Đang xử lý";
            case "SUCCEEDED" -> "Thành công";
            case "FAILED" -> "Thất bại";
            case "CANCELLED" -> "Đã hủy";
            case "EXPIRED" -> "Hết hạn";
            case "PARTIALLY_REFUNDED" -> "Hoàn một phần";
            case "REFUNDED" -> "Đã hoàn";
            default -> status;
        };
    }

    private double moneyValue(BigDecimal value) {
        return value == null ? 0D : value.doubleValue();
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
