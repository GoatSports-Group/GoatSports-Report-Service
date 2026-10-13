package iuh.fit.reportservice.infrastructure.report;

import feign.FeignException;
import feign.Request;
import iuh.fit.reportservice.application.dto.response.base.BaseResponse;
import iuh.fit.reportservice.application.dto.venue.VenueOwnerBookingResponse;
import iuh.fit.reportservice.infrastructure.external.client.NotificationClient;
import iuh.fit.reportservice.infrastructure.external.client.VenueClient;
import iuh.fit.reportservice.infrastructure.persistence.entity.ReportExportEntity;
import iuh.fit.reportservice.infrastructure.persistence.entity.ReportExportFileEntity;
import iuh.fit.reportservice.infrastructure.persistence.repository.JpaReportExportFileRepository;
import iuh.fit.reportservice.infrastructure.persistence.repository.JpaReportExportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportExportWorkerTest {
    @Mock private JpaReportExportRepository repository;
    @Mock private JpaReportExportFileRepository fileRepository;
    @Mock private VenueClient venueClient;
    @Mock private ReportAdapter reportAdapter;
    @Mock private BookingSummaryPdfRenderer summaryRenderer;
    @Mock private FinanceReportRenderer financeRenderer;
    @Mock private NotificationClient notificationClient;

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private ReportExportWorker worker;
    private ReportExportEntity job;

    @BeforeEach
    void setUp() {
        worker = new ReportExportWorker(repository, fileRepository, venueClient, reportAdapter, summaryRenderer,
                financeRenderer, notificationClient, objectMapper);
        job = new ReportExportEntity();
        job.setExportId(UUID.randomUUID());
        job.setReportType("ADMIN_BOOKINGS");
        job.setFormat("xlsx");
        job.setRequestedBy(UUID.randomUUID());
        job.setFilterJson("{\"status\":\"COMPLETED\",\"fromDate\":\"2026-10-01\"}");
        job.setFilterSummary("Trạng thái đơn: Hoàn tất");
        job.setStatus("RUNNING");
        job.setAttempts(1);
        job.setCreatedAt(Instant.parse("2026-10-12T04:00:00Z"));
        lenient().when(repository.findById(job.getExportId())).thenReturn(Optional.of(job));
    }

    @Test
    void claimsAJobBuildsTheExcelFromOneVenueCallStoresItAndNotifies() throws Exception {
        when(repository.findClaimable(any(), any())).thenReturn(List.of(job.getExportId()));
        when(repository.claim(eq(job.getExportId()), any(), any())).thenReturn(1);
        when(venueClient.exportBookings(isNull(), isNull(), eq("COMPLETED"), isNull(), eq(java.time.LocalDate.of(2026, 10, 1)), isNull()))
                .thenReturn(new BaseResponse<>(200, List.of(new VenueOwnerBookingResponse(), new VenueOwnerBookingResponse()), "ok", null));
        when(reportAdapter.renderBookingTable(eq("xlsx"), eq("Trạng thái đơn: Hoàn tất"), isNull(), any()))
                .thenReturn(new byte[]{1, 2, 3});

        worker.poll();

        assertThat(job.getStatus()).isEqualTo("DONE");
        assertThat(job.getRowCount()).isEqualTo(2);
        assertThat(job.getFileName()).isEqualTo("don-dat-san-20261012-1100.xlsx");
        assertThat(job.getFileSize()).isEqualTo(3L);
        verify(fileRepository).save(any(ReportExportFileEntity.class));
        ArgumentCaptor<NotificationClient.CreateNotificationRequest> sent =
                ArgumentCaptor.forClass(NotificationClient.CreateNotificationRequest.class);
        verify(notificationClient).createNotification(sent.capture());
        assertThat(sent.getValue().receiverId()).isEqualTo(job.getRequestedBy());
        assertThat(sent.getValue().type()).isEqualTo("REPORT_EXPORT");
        assertThat(sent.getValue().content()).contains("2 đơn");
    }

    @Test
    void buildsAFinanceReportFromPaymentServiceAndPointsTheBellAtTheFinancePage() throws Exception {
        job.setReportType("FINANCE_PAYOUTS");
        job.setFormat("pdf");
        job.setFilterJson("{\"payoutKind\":\"OWNER_WITHDRAWAL\",\"statuses\":[\"FAILED\"],\"fromDate\":\"2026-10-01\",\"toDate\":\"2026-10-13\"}");
        job.setFilterSummary("Kỳ: 01/10/2026 – 13/10/2026");
        when(repository.findClaimable(any(), any())).thenReturn(List.of(job.getExportId()));
        when(repository.claim(eq(job.getExportId()), any(), any())).thenReturn(1);
        when(financeRenderer.render(eq("FINANCE_PAYOUTS"), any(), eq("pdf"), eq("Kỳ: 01/10/2026 – 13/10/2026")))
                .thenReturn(new RenderedExport(new byte[]{9, 9}, 4, "chu-san-rut-tien-"));

        worker.poll();

        ArgumentCaptor<iuh.fit.reportservice.application.dto.report.FinanceExportRequest.Filter> filter =
                ArgumentCaptor.forClass(iuh.fit.reportservice.application.dto.report.FinanceExportRequest.Filter.class);
        verify(financeRenderer).render(eq("FINANCE_PAYOUTS"), filter.capture(), eq("pdf"), any());
        assertThat(filter.getValue().statuses()).containsExactly("FAILED");
        assertThat(filter.getValue().toDate()).isEqualTo(java.time.LocalDate.of(2026, 10, 13));
        verify(venueClient, never()).exportBookings(any(), any(), any(), any(), any(), any());
        assertThat(job.getStatus()).isEqualTo("DONE");
        assertThat(job.getFileName()).isEqualTo("chu-san-rut-tien-20261012-1100.pdf");
        ArgumentCaptor<NotificationClient.CreateNotificationRequest> sent =
                ArgumentCaptor.forClass(NotificationClient.CreateNotificationRequest.class);
        verify(notificationClient).createNotification(sent.capture());
        assertThat(sent.getValue().title()).isEqualTo("Báo cáo chi trả đã sẵn sàng");
        assertThat(sent.getValue().content()).contains("4 lượt chi", "trang Tài chính");
        assertThat(sent.getValue().referenceType()).isEqualTo("FINANCE_REPORT_EXPORT");
    }

    @Test
    void doesNothingWhenAnotherInstanceClaimedTheJobFirst() {
        when(repository.findClaimable(any(), any())).thenReturn(List.of(job.getExportId()));
        when(repository.claim(eq(job.getExportId()), any(), any())).thenReturn(0);

        worker.poll();

        verify(venueClient, never()).exportBookings(any(), any(), any(), any(), any(), any());
    }

    @Test
    void stopsAtOnceOnABusinessErrorAndShowsTheVenueMessage() {
        when(venueClient.exportBookings(any(), any(), any(), any(), any(), any())).thenThrow(feign(400,
                "{\"statusCode\":400,\"message\":\"Báo cáo vượt quá 10.000 đơn. Hãy thu hẹp khoảng ngày hoặc chọn một cơ sở.\"}"));

        worker.process(job.getExportId());

        assertThat(job.getStatus()).isEqualTo("FAILED");
        assertThat(job.getFailureReason()).startsWith("Báo cáo vượt quá 10.000 đơn");
        verify(notificationClient).createNotification(any());
    }

    @Test
    void retriesATemporaryFailureUntilTheThirdAttempt() {
        when(venueClient.exportBookings(any(), any(), any(), any(), any(), any())).thenThrow(feign(503, "busy"));

        worker.process(job.getExportId());
        assertThat(job.getStatus()).isEqualTo("PENDING");
        verify(notificationClient, never()).createNotification(any());

        job.setAttempts(3);
        worker.process(job.getExportId());
        assertThat(job.getStatus()).isEqualTo("FAILED");
        verify(notificationClient).createNotification(any());
    }

    private static FeignException feign(int status, String body) {
        Map<String, java.util.Collection<String>> headers = new HashMap<>();
        Request request = Request.create(Request.HttpMethod.GET, "/internal/bookings/export", headers, null,
                StandardCharsets.UTF_8, null);
        return FeignException.errorStatus("VenueClient#exportBookings", feign.Response.builder()
                .status(status).reason("x").request(request).headers(headers)
                .body(body, StandardCharsets.UTF_8).build());
    }
}
