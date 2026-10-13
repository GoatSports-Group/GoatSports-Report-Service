package iuh.fit.reportservice.infrastructure.report;

import iuh.fit.reportservice.application.dto.payment.FinancePayoutResponse;
import iuh.fit.reportservice.application.dto.payment.FinanceRefundResponse;
import iuh.fit.reportservice.application.dto.report.FinanceExportRequest;
import iuh.fit.reportservice.application.dto.response.base.BaseResponse;
import iuh.fit.reportservice.application.exception.ServiceException;
import iuh.fit.reportservice.infrastructure.external.client.PaymentClient;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRParameter;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Báo cáo Tài chính của admin (hoàn tiền / chi trả): lấy mọi dòng khớp bộ lọc từ payment-service một lần, gắn tên
 * người nhận từ DB auth, rồi dựng bằng mẫu Jasper (cùng mẫu cho PDF xem trước và Excel danh sách).
 */
@Component
@RequiredArgsConstructor
public class FinanceReportRenderer {
    static final String REFUNDS = "FINANCE_REFUNDS";
    static final String PAYOUTS = "FINANCE_PAYOUTS";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");
    private static final String DASH = "—";
    private static final Locale VI = Locale.forLanguageTag("vi-VN");

    private final PaymentClient paymentClient;
    private final UserDirectory users;
    private final JasperRenderer renderer;

    private JasperReport refundReport;
    private JasperReport payoutReport;
    private byte[] logo;

    static boolean handles(String reportType) {
        return REFUNDS.equals(reportType) || PAYOUTS.equals(reportType);
    }

    static String reportType(String kind) {
        return "PAYOUTS".equals(kind) ? PAYOUTS : REFUNDS;
    }

    @PostConstruct
    void loadTemplates() {
        try {
            refundReport = renderer.load("templates/jasper/finance/finance-refunds.jasper");
            payoutReport = renderer.load("templates/jasper/finance/finance-payouts.jasper");
            logo = renderer.readBytes("images/goat-report.png");
        } catch (IOException | JRException exception) {
            throw new IllegalStateException("Không thể nạp mẫu báo cáo tài chính.", exception);
        }
    }

    RenderedExport render(String reportType, FinanceExportRequest.Filter filter, String format, String filterSummary)
            throws Exception {
        if (PAYOUTS.equals(reportType)) {
            List<FinancePayoutResponse> payouts = data(paymentClient.exportPayouts(filter.payoutKind(), filter.statuses(),
                    filter.keyword(), filter.people(), filter.fromDate(), filter.toDate()));
            Map<UUID, UserDirectory.Person> people = users.find(payouts.stream().map(FinancePayoutResponse::payeeId).toList());
            JasperPrint print = fill(payoutReport, payoutRows(payouts, people), filterSummary,
                    payouts.stream().mapToDouble(row -> value(row.amount())).sum());
            return output(print, format, payouts.size(), "REFUND".equals(filter.payoutKind()) ? "chi-hoan-" : "chu-san-rut-tien-");
        }
        List<FinanceRefundResponse> refunds = data(paymentClient.exportRefunds(filter.statuses(), filter.keyword(),
                filter.people(), filter.olderThanDays(), filter.fromDate(), filter.toDate()));
        Map<UUID, UserDirectory.Person> people = users.find(refunds.stream().map(FinanceRefundResponse::payerId).toList());
        JasperPrint print = fill(refundReport, refundRows(refunds, people), filterSummary,
                refunds.stream().mapToDouble(row -> value(row.amount())).sum());
        return output(print, format, refunds.size(), "hoan-tien-");
    }

    private RenderedExport output(JasperPrint print, String format, int rows, String filePrefix)
            throws JRException, ServiceException {
        return new RenderedExport(renderer.export(print, format), rows, filePrefix);
    }

    private JasperPrint fill(JasperReport report, List<Map<String, Object>> rows, String filterSummary, double total)
            throws JRException {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("REPORT_DATE", ReportLabels.generatedAt());
        parameters.put("FILTER_SUMMARY", filterSummary);
        parameters.put("TOTAL_RECORDS", rows.size());
        parameters.put("TOTAL_AMOUNT", BookingSummaryPdfRenderer.money(total));
        parameters.put("LOGO", JasperRenderer.stream(logo));
        // Số tiền dạng 60.000 (mẫu "#,##0" theo locale báo cáo, mặc định của JVM là kiểu Anh 60,000).
        parameters.put(JRParameter.REPORT_LOCALE, VI);
        return JasperFillManager.fillReport(report, parameters, new JRMapCollectionDataSource(List.<Map<String, ?>>copyOf(rows)));
    }

    static List<Map<String, Object>> refundRows(List<FinanceRefundResponse> refunds, Map<UUID, UserDirectory.Person> people) {
        return numbered(refunds, refund -> {
            Map<String, Object> row = new HashMap<>();
            row.put("createdAt", time(refund.createdAt()));
            row.put("recipient", person(refund.payerId(), people));
            row.put("source", lines(ReportLabels.paymentPurpose(refund.purpose()), refund.paymentDescription()));
            row.put("reason", orDash(refund.reason()));
            row.put("amount", value(refund.amount()));
            row.put("status", ReportLabels.refundStatus(refund.status()));
            row.put("refundedAt", time(refund.refundedAt()));
            row.put("note", refund.providerRefundId() != null ? "Mã GD: " + refund.providerRefundId()
                    : "SUCCEEDED".equals(refund.status()) ? DASH : orDash(refund.failureReason()));
            return row;
        });
    }

    static List<Map<String, Object>> payoutRows(List<FinancePayoutResponse> payouts, Map<UUID, UserDirectory.Person> people) {
        return numbered(payouts, payout -> {
            Map<String, Object> row = new HashMap<>();
            row.put("requestedAt", time(payout.requestedAt()));
            row.put("recipient", person(payout.payeeId(), people));
            row.put("account", lines(orDash(payout.destinationAccountName()),
                    "BIN " + orDash(payout.destinationBankBin()) + " · " + orDash(payout.destinationAccountMasked())));
            row.put("amount", value(payout.amount()));
            row.put("status", ReportLabels.payoutStatus(payout.status()));
            row.put("completedAt", time(payout.completedAt()));
            row.put("reference", lines(orDash(payout.referenceId()),
                    payout.providerPayoutId() == null ? null : "payOS: " + payout.providerPayoutId()));
            String failure = String.join(" ", java.util.stream.Stream.of(payout.failureCode(), payout.failureReason())
                    .filter(text -> text != null && !text.isBlank()).toList());
            row.put("failure", failure.isEmpty() ? DASH : failure);
            return row;
        });
    }

    private static <T> List<Map<String, Object>> numbered(List<T> items, Function<T, Map<String, Object>> mapper) {
        List<Map<String, Object>> rows = new ArrayList<>(items.size());
        for (T item : items) {
            Map<String, Object> row = mapper.apply(item);
            row.put("sequence", rows.size() + 1);
            rows.add(row);
        }
        return rows;
    }

    private static <T> List<T> data(BaseResponse<List<T>> response) {
        if (response == null || response.getData() == null) {
            throw new IllegalStateException("Dịch vụ thanh toán không trả dữ liệu báo cáo.");
        }
        return response.getData();
    }

    private static String person(UUID id, Map<UUID, UserDirectory.Person> people) {
        if (id == null) return DASH;
        UserDirectory.Person person = people.get(id);
        if (person == null) return "Tài khoản đã xóa";
        return lines(orDash(person.fullName()), person.email());
    }

    private static String lines(String first, String second) {
        return second == null || second.isBlank() ? first : first + "\n" + second;
    }

    private static String time(LocalDateTime value) {
        return value == null ? DASH : TIME.format(value);
    }

    private static String orDash(String value) {
        return value == null || value.isBlank() ? DASH : value;
    }

    private static double value(Double amount) {
        return amount == null ? 0 : amount;
    }
}
