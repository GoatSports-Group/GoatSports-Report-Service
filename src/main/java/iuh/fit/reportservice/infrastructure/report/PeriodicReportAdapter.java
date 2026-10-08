package iuh.fit.reportservice.infrastructure.report;

import iuh.fit.reportservice.application.dto.report.OwnerRevenueReportFilter;
import iuh.fit.reportservice.application.dto.report.PeriodicReportView;
import iuh.fit.reportservice.application.dto.response.base.BaseResponse;
import iuh.fit.reportservice.application.dto.venue.VenueOwnerRevenuePeriodResponse;
import iuh.fit.reportservice.application.dto.venue.VenueOwnerRevenueResponse;
import iuh.fit.reportservice.application.enums.ReportPeriodType;
import iuh.fit.reportservice.application.exception.ServiceException;
import iuh.fit.reportservice.application.port.report.PeriodicReportPort;
import iuh.fit.reportservice.infrastructure.external.client.VenueClient;
import iuh.fit.reportservice.infrastructure.persistence.entity.PeriodicReportEntity;
import iuh.fit.reportservice.infrastructure.persistence.repository.JpaPeriodicReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jasperreports.engine.JRException;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Báo cáo định kỳ: mỗi đêm job chốt các kỳ (tuần / tháng / quý / năm) vừa kết thúc cho mọi chủ sân còn thiếu báo cáo.
 * Chạy hằng ngày thay vì đúng ngày đầu kỳ nên lần nào lỡ (service tắt, venue-service lỗi) thì đêm sau tự bù.
 * File PDF/XLSX dựng lại từ snapshot đã lưu, nên tải lúc nào cũng khớp số đã chốt.
 */
@Service
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
public class PeriodicReportAdapter implements PeriodicReportPort {
    static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final List<ReportPeriodType> SCHEDULED_TYPES = List.of(
            ReportPeriodType.WEEKLY, ReportPeriodType.MONTHLY, ReportPeriodType.QUARTERLY, ReportPeriodType.YEARLY);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String CSV = "csv";

    private final JpaPeriodicReportRepository repository;
    private final VenueClient venueClient;
    private final ReportAdapter reportAdapter;
    private final ObjectMapper objectMapper;

    @Override
    public List<PeriodicReportView> list(UUID ownerId, ReportPeriodType periodType) {
        List<PeriodicReportEntity> rows = periodType == null
                ? repository.findTop24ByOwnerIdOrderByPeriodEndDescGeneratedAtDesc(ownerId)
                : repository.findTop24ByOwnerIdAndPeriodTypeOrderByPeriodEndDesc(ownerId, periodType.name());
        return rows.stream().map(PeriodicReportAdapter::toView).toList();
    }

    @Override
    public PeriodicReportView generate(UUID ownerId, ReportPeriodType periodType, LocalDate fromDate, LocalDate toDate)
            throws ServiceException {
        if (periodType == null) throw new ServiceException("Loại kỳ báo cáo là bắt buộc.");
        LocalDate today = LocalDate.now(ZONE);
        if (periodType != ReportPeriodType.CUSTOM) {
            ReportPeriodType.Period period = periodType.lastCompleted(today);
            fromDate = period.start();
            toDate = period.end();
        }
        if (fromDate == null || toDate == null || fromDate.isAfter(toDate)) {
            throw new ServiceException("Khoảng ngày báo cáo không hợp lệ.");
        }
        if (toDate.isAfter(today)) throw new ServiceException("Kỳ báo cáo phải kết thúc trước hoặc trong hôm nay.");
        if (ChronoUnit.DAYS.between(fromDate, toDate) >= 366) {
            throw new ServiceException("Khoảng ngày báo cáo không được vượt quá 366 ngày.");
        }

        BaseResponse<VenueOwnerRevenueResponse> response = venueClient.getOwnerRevenueForOwner(ownerId, fromDate, toDate);
        VenueOwnerRevenueResponse revenue = response == null ? null : response.getData();
        if (revenue == null || revenue.getCurrentPeriod() == null || revenue.getPreviousPeriod() == null) {
            throw new ServiceException("Dịch vụ quản lý sân không trả dữ liệu doanh thu để lập báo cáo.");
        }

        // Cùng chủ sân + loại kỳ + khoảng ngày luôn là một bản ghi: chạy lại chỉ chốt lại số liệu.
        PeriodicReportEntity entity = repository
                .findByOwnerIdAndPeriodTypeAndPeriodStartAndPeriodEnd(ownerId, periodType.name(), fromDate, toDate)
                .orElseGet(PeriodicReportEntity::new);
        if (entity.getReportId() == null) entity.setReportId(UUID.randomUUID());
        VenueOwnerRevenuePeriodResponse current = revenue.getCurrentPeriod();
        entity.setOwnerId(ownerId);
        entity.setPeriodType(periodType.name());
        entity.setPeriodStart(fromDate);
        entity.setPeriodEnd(toDate);
        entity.setTotalRevenue(current.getTotalRevenue() == null ? BigDecimal.ZERO : current.getTotalRevenue());
        entity.setBookingCount(current.getBookingCount() == null ? 0 : current.getBookingCount());
        entity.setPaidBookingCount(current.getPaidBookingCount() == null ? 0 : current.getPaidBookingCount());
        entity.setRevenueChangePercentage(revenue.getRevenueChangePercentage());
        entity.setBookingCountChangePercentage(revenue.getBookingCountChangePercentage());
        entity.setSnapshot(objectMapper.writeValueAsString(revenue));
        entity.setGeneratedAt(Instant.now());
        return toView(repository.save(entity));
    }

    @Override
    public byte[] export(UUID ownerId, UUID reportId, String format) throws ServiceException, JRException {
        PeriodicReportEntity entity = repository.findById(reportId)
                .filter(report -> report.getOwnerId().equals(ownerId))
                .orElseThrow(() -> new ServiceException("Không tìm thấy báo cáo."));
        VenueOwnerRevenueResponse revenue = objectMapper.readValue(entity.getSnapshot(), VenueOwnerRevenueResponse.class);
        ReportPeriodType type = ReportPeriodType.valueOf(entity.getPeriodType());
        String label = periodLabel(type, entity.getPeriodStart(), entity.getPeriodEnd());
        if (CSV.equalsIgnoreCase(format)) return toCsv(label, entity, revenue);
        return reportAdapter.renderOwnerRevenue(format, new OwnerRevenueReportFilter(
                null, "Tất cả cơ sở", entity.getPeriodStart(), entity.getPeriodEnd(), label), revenue);
    }

    /**
     * CSV tu snapshot da chot (khong qua Jasper): khoi tong quan, doanh thu theo ngay, doi soat thanh toan.
     * So de dang thuan (khong dau ngan cach) de bang tinh doc duoc; co BOM UTF-8 de Excel hien dung tieng Viet.
     */
    static byte[] toCsv(String periodLabel, PeriodicReportEntity entity, VenueOwnerRevenueResponse revenue) {
        StringBuilder csv = new StringBuilder("\uFEFF");
        VenueOwnerRevenuePeriodResponse current = revenue.getCurrentPeriod();
        VenueOwnerRevenuePeriodResponse previous = revenue.getPreviousPeriod();
        row(csv, "Báo cáo doanh thu định kỳ", periodLabel);
        row(csv, "Phạm vi", "Tất cả cơ sở");
        row(csv, "Từ ngày", entity.getPeriodStart().format(DAY));
        row(csv, "Đến ngày", entity.getPeriodEnd().format(DAY));
        row(csv, "Doanh thu (VND)", number(current == null ? null : current.getTotalRevenue()));
        row(csv, "Tổng lượt đặt", number(current == null ? null : current.getBookingCount()));
        row(csv, "Lượt đã thanh toán", number(current == null ? null : current.getPaidBookingCount()));
        row(csv, "Doanh thu kỳ trước (VND)", number(previous == null ? null : previous.getTotalRevenue()));
        row(csv, "Thay đổi doanh thu so với kỳ trước (%)", number(revenue.getRevenueChangePercentage()));
        row(csv, "Thay đổi lượt đặt so với kỳ trước (%)", number(revenue.getBookingCountChangePercentage()));
        csv.append("\r\n");
        row(csv, "Ngày", "Doanh thu (VND)", "Giao dịch thành công");
        for (var day : revenue.getDailyRevenue()) {
            row(csv, day.getDate() == null ? "" : day.getDate().format(DAY), number(day.getRevenue()), number(day.getSucceededPaymentCount()));
        }
        csv.append("\r\n");
        row(csv, "Trạng thái thanh toán", "Số giao dịch", "Giá trị (VND)");
        for (var status : revenue.getPaymentStatusBreakdown()) {
            row(csv, ReportLabels.paymentStatus(status.getStatus()), number(status.getPaymentCount()), number(status.getNominalAmount()));
        }
        return csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private static void row(StringBuilder csv, String... cells) {
        for (int index = 0; index < cells.length; index++) {
            if (index > 0) csv.append(',');
            String cell = cells[index] == null ? "" : cells[index];
            boolean quote = cell.contains(",") || cell.contains("\"") || cell.contains("\n") || cell.contains("\r");
            csv.append(quote ? '"' + cell.replace("\"", "\"\"") + '"' : cell);
        }
        csv.append("\r\n");
    }

    private static String number(Number value) {
        if (value == null) return "";
        return value instanceof BigDecimal decimal ? decimal.stripTrailingZeros().toPlainString() : value.toString();
    }

    @Scheduled(cron = "0 30 1 * * *", zone = "Asia/Ho_Chi_Minh")
    public void generateDueReportsNightly() {
        int created = generateDueReports(LocalDate.now(ZONE));
        if (created > 0) log.info("Periodic reports generated: {}", created);
    }

    // ponytail: mỗi chủ sân x 4 loại kỳ là một truy vấn exists mỗi đêm; gom thành 1 truy vấn theo kỳ khi số chủ sân lớn.
    @Override
    public int generateDueReports(LocalDate today) {
        List<UUID> ownerIds;
        try {
            BaseResponse<List<UUID>> response = venueClient.getRevenueOwnerIds();
            ownerIds = response == null || response.getData() == null ? List.of() : response.getData();
        } catch (RuntimeException exception) {
            log.warn("Chưa lấy được danh sách chủ sân để tạo báo cáo định kỳ: {}", exception.getMessage());
            return 0;
        }
        int created = 0;
        for (ReportPeriodType type : SCHEDULED_TYPES) {
            ReportPeriodType.Period period = type.lastCompleted(today);
            for (UUID ownerId : ownerIds) {
                if (repository.existsByOwnerIdAndPeriodTypeAndPeriodStartAndPeriodEnd(
                        ownerId, type.name(), period.start(), period.end())) continue;
                try {
                    generate(ownerId, type, period.start(), period.end());
                    created++;
                } catch (ServiceException | RuntimeException exception) {
                    log.warn("Chưa tạo được báo cáo {} {} cho chủ sân {}: {}",
                            type, period.start(), ownerId, exception.getMessage());
                }
            }
        }
        return created;
    }

    static String periodLabel(ReportPeriodType type, LocalDate start, LocalDate end) {
        return switch (type) {
            case WEEKLY -> "Tuần " + start.format(DAY) + " – " + end.format(DAY);
            case MONTHLY -> "Tháng " + start.format(DateTimeFormatter.ofPattern("MM/yyyy"));
            case QUARTERLY -> "Quý " + ((start.getMonthValue() - 1) / 3 + 1) + "/" + start.getYear();
            case YEARLY -> "Năm " + start.getYear();
            case CUSTOM -> start.format(DAY) + " – " + end.format(DAY);
        };
    }

    private static PeriodicReportView toView(PeriodicReportEntity entity) {
        return new PeriodicReportView(
                entity.getReportId(), ReportPeriodType.valueOf(entity.getPeriodType()),
                entity.getPeriodStart(), entity.getPeriodEnd(), entity.getTotalRevenue(),
                entity.getBookingCount(), entity.getPaidBookingCount(),
                entity.getRevenueChangePercentage(), entity.getBookingCountChangePercentage(),
                entity.getGeneratedAt());
    }
}
