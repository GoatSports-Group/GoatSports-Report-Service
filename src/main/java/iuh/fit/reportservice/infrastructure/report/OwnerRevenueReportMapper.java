package iuh.fit.reportservice.infrastructure.report;

import iuh.fit.reportservice.application.dto.report.OwnerRevenueReportFilter;
import iuh.fit.reportservice.application.dto.report.OwnerRevenueReportRow;
import iuh.fit.reportservice.application.dto.venue.VenueOwnerDailyRevenueResponse;
import iuh.fit.reportservice.application.dto.venue.VenueOwnerRevenuePeriodResponse;
import iuh.fit.reportservice.application.dto.venue.VenueOwnerRevenueResponse;
import net.sf.jasperreports.engine.JRParameter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/** Chuyển số liệu doanh thu (tính sẵn ở venue-service) thành dòng theo ngày và các chỉ số tổng hợp của báo cáo. */
final class OwnerRevenueReportMapper {

    private OwnerRevenueReportMapper() {
    }

    static List<OwnerRevenueReportRow> toRows(VenueOwnerRevenueResponse revenue) {
        List<VenueOwnerDailyRevenueResponse> days = revenue.getDailyRevenue() == null
                ? Collections.emptyList() : revenue.getDailyRevenue();
        double total = money(revenue.getCurrentPeriod().getTotalRevenue());
        List<OwnerRevenueReportRow> rows = new ArrayList<>(days.size());
        for (int index = 0; index < days.size(); index++) {
            VenueOwnerDailyRevenueResponse day = days.get(index);
            double dayRevenue = money(day.getRevenue());
            rows.add(OwnerRevenueReportRow.builder()
                    .sequence(index + 1)
                    .reportDate(day.getDate() == null ? "—" : ReportLabels.DATE.format(day.getDate()))
                    .revenue(dayRevenue)
                    .succeededPaymentCount(day.getSucceededPaymentCount() == null ? 0L : day.getSucceededPaymentCount())
                    // Tỷ trọng của ngày trong tổng doanh thu kỳ; kỳ không có doanh thu thì 0 (tránh chia cho 0).
                    .revenueShare(total <= 0D ? 0D : dayRevenue * 100D / total)
                    .build());
        }
        return rows;
    }

    static Map<String, Object> parameters(OwnerRevenueReportFilter filter, VenueOwnerRevenueResponse revenue) {
        VenueOwnerRevenuePeriodResponse current = revenue.getCurrentPeriod();
        double total = money(current.getTotalRevenue());
        long paidBookings = current.getPaidBookingCount() == null ? 0L : current.getPaidBookingCount();
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("REPORT_DATE", ReportLabels.generatedAt());
        parameters.put("PERIOD_LABEL", ReportLabels.valueOrDash(filter.periodLabel()));
        parameters.put("VENUE_NAME", ReportLabels.valueOrDash(filter.venueName()));
        parameters.put("DATE_RANGE", ReportLabels.DATE.format(filter.fromDate()) + " - " + ReportLabels.DATE.format(filter.toDate()));
        parameters.put("CURRENT_REVENUE", total);
        parameters.put("PREVIOUS_REVENUE", money(revenue.getPreviousPeriod().getTotalRevenue()));
        parameters.put("REVENUE_CHANGE", money(revenue.getRevenueChangePercentage()));
        parameters.put("BOOKING_COUNT", current.getBookingCount() == null ? 0L : current.getBookingCount());
        parameters.put("PAID_BOOKING_COUNT", paidBookings);
        parameters.put("AVERAGE_REVENUE", paidBookings == 0L ? 0D : total / paidBookings);
        parameters.put("STATUS_SUMMARY", statusSummary(revenue));
        parameters.put(JRParameter.REPORT_LOCALE, Locale.forLanguageTag("vi-VN"));
        return parameters;
    }

    private static String statusSummary(VenueOwnerRevenueResponse revenue) {
        if (revenue.getPaymentStatusBreakdown() == null || revenue.getPaymentStatusBreakdown().isEmpty()) {
            return "Chưa có giao dịch thanh toán trong kỳ";
        }
        return revenue.getPaymentStatusBreakdown().stream()
                .map(row -> ReportLabels.paymentStatus(row.getStatus()) + ": "
                        + (row.getPaymentCount() == null ? 0L : row.getPaymentCount()) + " giao dịch · "
                        + ReportLabels.money(money(row.getNominalAmount())))
                .collect(Collectors.joining("  |  "));
    }

    private static double money(BigDecimal value) {
        return value == null ? 0D : value.doubleValue();
    }
}
