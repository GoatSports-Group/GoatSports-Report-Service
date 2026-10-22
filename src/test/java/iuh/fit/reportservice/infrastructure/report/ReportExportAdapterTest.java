package iuh.fit.reportservice.infrastructure.report;

import iuh.fit.reportservice.application.dto.report.AdminBookingExportRequest;
import iuh.fit.reportservice.application.dto.report.FinanceExportRequest;
import iuh.fit.reportservice.application.dto.report.OwnerBookingReportFilter;
import iuh.fit.reportservice.application.dto.report.ReportExportView;
import iuh.fit.reportservice.application.exception.ServiceException;
import iuh.fit.reportservice.infrastructure.persistence.entity.ReportExportEntity;
import iuh.fit.reportservice.infrastructure.persistence.repository.JpaReportExportFileRepository;
import iuh.fit.reportservice.infrastructure.persistence.repository.JpaReportExportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportExportAdapterTest {
    @Mock private JpaReportExportRepository repository;
    @Mock private JpaReportExportFileRepository fileRepository;

    private ReportExportAdapter adapter;
    private final UUID requester = UUID.randomUUID();
    private final UUID venueId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        adapter = new ReportExportAdapter(repository, fileRepository, JsonMapper.builder().build());
    }

    @Test
    void queuesAPendingJobWithAReadableScopeAndSevenDayExpiry() throws ServiceException {
        when(repository.findFirstByRequestedByAndFilterKeyAndStatusInAndCreatedAtAfterOrderByCreatedAtDesc(
                eq(requester), any(), any(), any())).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));

        ReportExportView view = adapter.requestAdminBookingExport(requester, new AdminBookingExportRequest(" XLSX ",
                new OwnerBookingReportFilter(venueId, null, "", "PAID", "  goat ", LocalDate.of(2026, 10, 1), null),
                "Goat Arena", null));

        ArgumentCaptor<ReportExportEntity> saved = ArgumentCaptor.forClass(ReportExportEntity.class);
        verify(repository).save(saved.capture());
        assertThat(view.status()).isEqualTo("PENDING");
        assertThat(saved.getValue().getFormat()).isEqualTo("xlsx");
        // Lọc thanh toán không áp dụng cho admin, chuỗi rỗng bỏ đi, từ khóa được cắt khoảng trắng.
        assertThat(saved.getValue().getFilterJson()).doesNotContain("PAID").contains("\"query\":\"goat\"");
        assertThat(saved.getValue().getFilterSummary()).startsWith("Cơ sở: Goat Arena").contains("Từ khóa: goat");
        assertThat(saved.getValue().getExpiresAt()).isAfter(Instant.now().plus(ReportExportAdapter.RETENTION).minusSeconds(60));
    }

    @Test
    void reusesTheSameRequestMadeAMomentAgo() throws ServiceException {
        ReportExportEntity existing = job(requester, "DONE");
        when(repository.findFirstByRequestedByAndFilterKeyAndStatusInAndCreatedAtAfterOrderByCreatedAtDesc(
                eq(requester), any(), any(), any())).thenReturn(Optional.of(existing));

        ReportExportView view = adapter.requestAdminBookingExport(requester, new AdminBookingExportRequest("pdf",
                new OwnerBookingReportFilter(null, null, null, null, null, null, null), null, null));

        assertThat(view.exportId()).isEqualTo(existing.getExportId());
        verify(repository, never()).save(any());
    }

    @Test
    void queuesAFinanceExportWithANormalizedFilterAndAReadableScope() throws ServiceException {
        when(repository.findFirstByRequestedByAndFilterKeyAndStatusInAndCreatedAtAfterOrderByCreatedAtDesc(
                eq(requester), any(), any(), any())).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));
        UUID person = UUID.randomUUID();

        adapter.requestFinanceExport(requester, new FinanceExportRequest(" refunds ", "PDF",
                new FinanceExportRequest.Filter("OWNER_WITHDRAWAL", List.of("manual_review", "FAILED"), "  GSA02 ",
                        List.of(person, person), 7, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 13)),
                " Cần xử lý "));

        ArgumentCaptor<ReportExportEntity> saved = ArgumentCaptor.forClass(ReportExportEntity.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getReportType()).isEqualTo("FINANCE_REFUNDS");
        assertThat(saved.getValue().getFormat()).isEqualTo("pdf");
        // Hoàn tiền không có loại chi trả; trạng thái viết hoa, sắp xếp; người trùng gộp lại.
        assertThat(saved.getValue().getFilterJson())
                .contains("\"payoutKind\":null", "[\"FAILED\",\"MANUAL_REVIEW\"]", "\"keyword\":\"GSA02\"")
                .containsOnlyOnce(person.toString());
        assertThat(saved.getValue().getFilterSummary())
                .isEqualTo("Kỳ: 01/10/2026 – 13/10/2026  |  Cần xử lý  |  Tìm: \"GSA02\"  |  Tạo quá 7 ngày");
    }

    @Test
    void rejectsFinanceExportsWithoutAPeriodOrWithUnknownValues() {
        LocalDate day = LocalDate.of(2026, 10, 13);
        assertThatThrownBy(() -> adapter.requestFinanceExport(requester, new FinanceExportRequest("REFUNDS", "xlsx",
                new FinanceExportRequest.Filter(null, null, null, null, null, null, day), null)))
                .isInstanceOf(ServiceException.class).hasMessageContaining("kỳ");
        assertThatThrownBy(() -> adapter.requestFinanceExport(requester, new FinanceExportRequest("PAYOUTS", "xlsx",
                new FinanceExportRequest.Filter("OWNER_WITHDRAWAL", List.of("DROP TABLE"), null, null, null, day, day), null)))
                .isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> adapter.requestFinanceExport(requester, new FinanceExportRequest("PAYMENTS", "xlsx",
                new FinanceExportRequest.Filter(null, null, null, null, null, day, day), null)))
                .isInstanceOf(ServiceException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void rejectsUnknownFormatsAndOtherPeoplesReports() {
        assertThatThrownBy(() -> adapter.requestAdminBookingExport(requester, new AdminBookingExportRequest("csv",
                new OwnerBookingReportFilter(null, null, null, null, null, null, null), null, null)))
                .isInstanceOf(ServiceException.class);

        ReportExportEntity someoneElse = job(UUID.randomUUID(), "DONE");
        when(repository.findById(someoneElse.getExportId())).thenReturn(Optional.of(someoneElse));
        assertThatThrownBy(() -> adapter.download(requester, someoneElse.getExportId()))
                .isInstanceOf(ServiceException.class);
        verify(fileRepository, never()).findById(any());
    }

    private static ReportExportEntity job(UUID owner, String status) {
        ReportExportEntity entity = new ReportExportEntity();
        entity.setExportId(UUID.randomUUID());
        entity.setReportType("ADMIN_BOOKINGS");
        entity.setFormat("pdf");
        entity.setRequestedBy(owner);
        entity.setStatus(status);
        entity.setFilterSummary("Phạm vi: Tất cả đơn đặt sân");
        entity.setCreatedAt(Instant.now());
        entity.setExpiresAt(Instant.now().plus(ReportExportAdapter.RETENTION));
        return entity;
    }
}
