package iuh.fit.reportservice.infrastructure.report;

import iuh.fit.reportservice.application.dto.report.OwnerRevenueReportFilter;
import iuh.fit.reportservice.application.dto.report.PeriodicReportView;
import iuh.fit.reportservice.application.dto.response.base.BaseResponse;
import iuh.fit.reportservice.application.dto.venue.VenueOwnerRevenuePeriodResponse;
import iuh.fit.reportservice.application.dto.venue.VenueOwnerRevenueResponse;
import iuh.fit.reportservice.application.enums.ReportPeriodType;
import iuh.fit.reportservice.application.exception.ServiceException;
import iuh.fit.reportservice.infrastructure.external.client.VenueClient;
import iuh.fit.reportservice.infrastructure.persistence.entity.PeriodicReportEntity;
import iuh.fit.reportservice.infrastructure.persistence.repository.JpaPeriodicReportRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PeriodicReportAdapterTest {
    private final JpaPeriodicReportRepository repository = mock(JpaPeriodicReportRepository.class);
    private final VenueClient venueClient = mock(VenueClient.class);
    private final ReportAdapter reportAdapter = mock(ReportAdapter.class);
    private final PeriodicReportAdapter adapter =
            new PeriodicReportAdapter(repository, venueClient, reportAdapter, JsonMapper.builder().build());

    private final UUID ownerId = UUID.randomUUID();
    private final LocalDate from = LocalDate.of(2026, 9, 1);
    private final LocalDate to = LocalDate.of(2026, 9, 30);

    @Test
    void rerunSamePeriodUpdatesOneReportAndExportUsesSnapshot() throws Exception {
        when(venueClient.getOwnerRevenueForOwner(ownerId, from, to)).thenReturn(revenue(new BigDecimal("1250000")));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.findByOwnerIdAndPeriodTypeAndPeriodStartAndPeriodEnd(ownerId, "CUSTOM", from, to))
                .thenReturn(Optional.empty());

        PeriodicReportView first = adapter.generate(ownerId, ReportPeriodType.CUSTOM, from, to);
        assertThat(first.totalRevenue()).isEqualByComparingTo("1250000");
        assertThat(first.bookingCount()).isEqualTo(8);

        ArgumentCaptor<PeriodicReportEntity> saved = ArgumentCaptor.forClass(PeriodicReportEntity.class);
        verify(repository).save(saved.capture());
        PeriodicReportEntity entity = saved.getValue();

        // Chạy lại cùng kỳ: tái dùng đúng bản ghi (cùng reportId), không tạo bản mới.
        when(repository.findByOwnerIdAndPeriodTypeAndPeriodStartAndPeriodEnd(ownerId, "CUSTOM", from, to))
                .thenReturn(Optional.of(entity));
        PeriodicReportView second = adapter.generate(ownerId, ReportPeriodType.CUSTOM, from, to);
        assertThat(second.reportId()).isEqualTo(first.reportId());

        // Xuất file dựng từ snapshot đã lưu (không gọi lại venue-service).
        when(repository.findById(first.reportId())).thenReturn(Optional.of(entity));
        adapter.export(ownerId, first.reportId(), "pdf");
        ArgumentCaptor<OwnerRevenueReportFilter> filter = ArgumentCaptor.forClass(OwnerRevenueReportFilter.class);
        ArgumentCaptor<VenueOwnerRevenueResponse> snapshot = ArgumentCaptor.forClass(VenueOwnerRevenueResponse.class);
        verify(reportAdapter).renderOwnerRevenue(eq("pdf"), filter.capture(), snapshot.capture());
        assertThat(filter.getValue().periodLabel()).isEqualTo("01/09/2026 – 30/09/2026");
        assertThat(snapshot.getValue().getCurrentPeriod().getTotalRevenue()).isEqualByComparingTo("1250000");
        verify(venueClient, times(2)).getOwnerRevenueForOwner(ownerId, from, to);

        // Chủ sân khác không tải được báo cáo không phải của mình.
        assertThatThrownBy(() -> adapter.export(UUID.randomUUID(), first.reportId(), "pdf"))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    void nightlyJobSkipsExistingPeriodsAndSurvivesOneOwnerFailing() {
        UUID broken = UUID.randomUUID();
        LocalDate today = LocalDate.of(2026, 10, 8);
        when(venueClient.getRevenueOwnerIds()).thenReturn(new BaseResponse<>(200, List.of(broken, ownerId), null, null));
        when(repository.existsByOwnerIdAndPeriodTypeAndPeriodStartAndPeriodEnd(eq(ownerId), any(), any(), any()))
                .thenReturn(true);
        when(venueClient.getOwnerRevenueForOwner(eq(broken), any(), any())).thenThrow(new RuntimeException("venue down"));

        assertThat(adapter.generateDueReports(today)).isZero();
        verify(venueClient, never()).getOwnerRevenueForOwner(eq(ownerId), any(), any());
        verify(venueClient, times(4)).getOwnerRevenueForOwner(eq(broken), any(), any());
    }

    @Test
    void periodLabels() {
        assertThat(PeriodicReportAdapter.periodLabel(ReportPeriodType.MONTHLY, from, to)).isEqualTo("Tháng 09/2026");
        assertThat(PeriodicReportAdapter.periodLabel(ReportPeriodType.QUARTERLY, LocalDate.of(2026, 7, 1), to)).isEqualTo("Quý 3/2026");
        assertThat(PeriodicReportAdapter.periodLabel(ReportPeriodType.WEEKLY, LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 4)))
                .isEqualTo("Tuần 28/09/2026 – 04/10/2026");
    }

    private static BaseResponse<VenueOwnerRevenueResponse> revenue(BigDecimal total) {
        VenueOwnerRevenuePeriodResponse current = new VenueOwnerRevenuePeriodResponse();
        current.setTotalRevenue(total);
        current.setBookingCount(8L);
        current.setPaidBookingCount(7L);
        VenueOwnerRevenueResponse response = new VenueOwnerRevenueResponse();
        response.setCurrentPeriod(current);
        response.setPreviousPeriod(new VenueOwnerRevenuePeriodResponse());
        response.setRevenueChangePercentage(new BigDecimal("12.50"));
        return new BaseResponse<>(200, response, null, null);
    }
}
