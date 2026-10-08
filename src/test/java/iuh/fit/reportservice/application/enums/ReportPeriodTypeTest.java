package iuh.fit.reportservice.application.enums;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ReportPeriodTypeTest {
    private static ReportPeriodType.Period period(LocalDate start, LocalDate end) {
        return new ReportPeriodType.Period(start, end);
    }

    @Test
    void lastCompletedPeriodsEndBeforeToday() {
        LocalDate thursday = LocalDate.of(2026, 10, 8);
        assertThat(ReportPeriodType.WEEKLY.lastCompleted(thursday))
                .isEqualTo(period(LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 4)));
        assertThat(ReportPeriodType.MONTHLY.lastCompleted(thursday))
                .isEqualTo(period(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)));
        assertThat(ReportPeriodType.QUARTERLY.lastCompleted(thursday))
                .isEqualTo(period(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 9, 30)));
        assertThat(ReportPeriodType.YEARLY.lastCompleted(thursday))
                .isEqualTo(period(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31)));
    }

    @Test
    void boundaryDaysPickThePeriodThatJustEnded() {
        // Thứ Hai: tuần trước vừa xong. Ngày 1/1: tháng 12, quý 4 và năm trước vừa xong.
        assertThat(ReportPeriodType.WEEKLY.lastCompleted(LocalDate.of(2026, 10, 5)))
                .isEqualTo(period(LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 4)));
        LocalDate newYear = LocalDate.of(2027, 1, 1);
        assertThat(ReportPeriodType.MONTHLY.lastCompleted(newYear))
                .isEqualTo(period(LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 31)));
        assertThat(ReportPeriodType.QUARTERLY.lastCompleted(newYear))
                .isEqualTo(period(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 31)));
        assertThat(ReportPeriodType.YEARLY.lastCompleted(newYear))
                .isEqualTo(period(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)));
    }
}
