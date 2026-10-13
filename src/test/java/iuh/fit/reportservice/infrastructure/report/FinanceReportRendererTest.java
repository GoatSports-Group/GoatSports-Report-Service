package iuh.fit.reportservice.infrastructure.report;

import iuh.fit.reportservice.application.dto.payment.FinancePayoutResponse;
import iuh.fit.reportservice.application.dto.payment.FinanceRefundResponse;
import iuh.fit.reportservice.application.dto.report.FinanceExportRequest;
import iuh.fit.reportservice.application.dto.response.base.BaseResponse;
import iuh.fit.reportservice.infrastructure.external.client.PaymentClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinanceReportRendererTest {
    private static final LocalDate FROM = LocalDate.of(2026, 10, 1);
    private static final LocalDate TO = LocalDate.of(2026, 10, 13);

    @Mock private PaymentClient paymentClient;
    @Mock private UserDirectory users;
    private FinanceReportRenderer renderer;

    @BeforeEach
    void setUp() {
        renderer = new FinanceReportRenderer(paymentClient, users, new JasperRenderer());
        renderer.loadTemplates();
    }

    @Test
    void mapsRefundsToReadableRowsWithTheRecipientsName() {
        UUID payer = UUID.randomUUID();
        List<Map<String, Object>> rows = FinanceReportRenderer.refundRows(List.of(
                refund(payer, "SUCCEEDED", 60_000, "PAYOS-123", null),
                refund(UUID.randomUUID(), "FAILED", 45_000, null, "payOS từ chối: tài khoản không tồn tại")
        ), Map.of(payer, new UserDirectory.Person("Đặng Gia Huy", "huy@goatsports.vn")));

        assertThat(rows.get(0)).containsEntry("sequence", 1).containsEntry("recipient", "Đặng Gia Huy\nhuy@goatsports.vn")
                .containsEntry("status", "Đã hoàn").containsEntry("note", "Mã GD: PAYOS-123")
                .containsEntry("source", "Đặt cọc sân\nĐặt cọc GSA02RT2").containsEntry("amount", 60_000.0);
        // Người nhận đã xóa tài khoản vẫn có dòng; khoản lỗi ghi lý do lỗi.
        assertThat(rows.get(1)).containsEntry("sequence", 2).containsEntry("recipient", "Tài khoản đã xóa")
                .containsEntry("status", "Chi lỗi").containsEntry("note", "payOS từ chối: tài khoản không tồn tại");
    }

    @Test
    void rendersRefundsAsPdfAndExcelFromTheJasperTemplate() throws Exception {
        UUID payer = UUID.randomUUID();
        List<FinanceRefundResponse> refunds = new ArrayList<>();
        for (int index = 0; index < 40; index++) {
            refunds.add(refund(payer, index % 3 == 0 ? "AWAITING_BANK_ACCOUNT" : "SUCCEEDED", 60_000 + index * 1_000,
                    index % 3 == 0 ? null : "PAYOS-" + index, index % 3 == 0 ? "Vui lòng liên kết tài khoản ngân hàng" : null));
        }
        FinanceExportRequest.Filter filter = new FinanceExportRequest.Filter(null, List.of(), null, List.of(), null, FROM, TO);
        when(paymentClient.exportRefunds(List.of(), null, List.of(), null, FROM, TO))
                .thenReturn(new BaseResponse<>(200, refunds, "ok", null));
        when(users.find(any())).thenReturn(Map.of(payer, new UserDirectory.Person("Nguyễn Thắng Minh Đạt", "dat@goat.test")));

        RenderedExport pdf = renderer.render("FINANCE_REFUNDS", filter, "pdf", "Kỳ: 01/10/2026 – 13/10/2026  |  Tất cả");
        RenderedExport xlsx = renderer.render("FINANCE_REFUNDS", filter, "xlsx", "Kỳ: 01/10/2026 – 13/10/2026");

        assertThat(pdf.rows()).isEqualTo(40);
        assertThat(pdf.filePrefix()).isEqualTo("hoan-tien-");
        assertThat(new String(pdf.data(), 0, 5)).isEqualTo("%PDF-");
        assertThat(xlsx.data()).startsWith((byte) 'P', (byte) 'K');
        Path sample = Path.of("target", "finance-refunds-sample.pdf");
        Files.createDirectories(sample.getParent());
        Files.write(sample, pdf.data());
    }

    @Test
    void rendersOwnerWithdrawalsWithTheBankAccountAndPayosReference() throws Exception {
        UUID owner = UUID.randomUUID();
        FinanceExportRequest.Filter filter = new FinanceExportRequest.Filter("OWNER_WITHDRAWAL", List.of("FAILED"), "goat",
                List.of(owner), null, FROM, TO);
        when(paymentClient.exportPayouts(eq("OWNER_WITHDRAWAL"), eq(List.of("FAILED")), eq("goat"), eq(List.of(owner)),
                eq(FROM), eq(TO))).thenReturn(new BaseResponse<>(200, List.of(new FinancePayoutResponse(UUID.randomUUID(),
                owner, 1_250_000.0, "FAILED", "970436", "****1234", "NGUYEN VAN A", "214",
                "Tài khoản nhận không tồn tại", LocalDateTime.of(2026, 10, 5, 9, 30), null, "settlement-1", "PO-77")),
                "ok", null));
        when(users.find(any())).thenReturn(Map.of());

        RenderedExport pdf = renderer.render("FINANCE_PAYOUTS", filter, "pdf", "Kỳ: 01/10/2026 – 13/10/2026");

        assertThat(pdf.rows()).isEqualTo(1);
        assertThat(pdf.filePrefix()).isEqualTo("chu-san-rut-tien-");
        List<Map<String, Object>> rows = FinanceReportRenderer.payoutRows(List.of(new FinancePayoutResponse(UUID.randomUUID(),
                owner, 1_250_000.0, "FAILED", "970436", "****1234", "NGUYEN VAN A", "214", "Tài khoản nhận không tồn tại",
                null, null, "settlement-1", "PO-77")), Map.of());
        assertThat(rows.getFirst()).containsEntry("account", "NGUYEN VAN A\nBIN 970436 · ****1234")
                .containsEntry("reference", "settlement-1\npayOS: PO-77")
                .containsEntry("failure", "214 Tài khoản nhận không tồn tại").containsEntry("status", "Thất bại");
    }

    @Test
    void anEmptyPeriodStillRendersAReport() throws Exception {
        FinanceExportRequest.Filter filter = new FinanceExportRequest.Filter(null, List.of(), null, List.of(), null, FROM, TO);
        when(paymentClient.exportRefunds(any(), isNull(), any(), isNull(), eq(FROM), eq(TO)))
                .thenReturn(new BaseResponse<>(200, List.of(), "ok", null));
        when(users.find(any())).thenReturn(Map.of());

        RenderedExport pdf = renderer.render("FINANCE_REFUNDS", filter, "pdf", "Kỳ: 01/10/2026 – 13/10/2026");

        assertThat(pdf.rows()).isZero();
        assertThat(new String(pdf.data(), 0, 5)).isEqualTo("%PDF-");
    }

    private static FinanceRefundResponse refund(UUID payer, String status, double amount, String providerId, String failure) {
        return new FinanceRefundResponse(UUID.randomUUID(), payer, "BOOKING_DEPOSIT", amount, status,
                "Hủy booking GSA02RT2: hai đội có việc đột xuất nên xin dời lịch sang tuần sau", failure,
                LocalDateTime.of(2026, 10, 5, 12, 0), "SUCCEEDED".equals(status) ? LocalDateTime.of(2026, 10, 5, 12, 5) : null,
                providerId, "Đặt cọc GSA02RT2");
    }
}
