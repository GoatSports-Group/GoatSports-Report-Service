package iuh.fit.reportservice.application.enums;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;

/** Kỳ báo cáo. CUSTOM do chủ sân tự chọn ngày; các kỳ còn lại tính từ "hôm nay" theo giờ Việt Nam. */
public enum ReportPeriodType {
    WEEKLY, MONTHLY, QUARTERLY, YEARLY, CUSTOM;

    public record Period(LocalDate start, LocalDate end) { }

    /** Kỳ gần nhất đã kết thúc trước {@code today} (tuần tính từ thứ Hai). */
    public Period lastCompleted(LocalDate today) {
        return switch (this) {
            case WEEKLY -> {
                LocalDate start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1);
                yield new Period(start, start.plusDays(6));
            }
            case MONTHLY -> {
                YearMonth month = YearMonth.from(today).minusMonths(1);
                yield new Period(month.atDay(1), month.atEndOfMonth());
            }
            case QUARTERLY -> {
                YearMonth quarterStart = YearMonth.of(today.getYear(), (today.getMonthValue() - 1) / 3 * 3 + 1).minusMonths(3);
                yield new Period(quarterStart.atDay(1), quarterStart.plusMonths(2).atEndOfMonth());
            }
            case YEARLY -> new Period(LocalDate.of(today.getYear() - 1, 1, 1), LocalDate.of(today.getYear() - 1, 12, 31));
            case CUSTOM -> throw new IllegalStateException("Kỳ tùy chọn không có kỳ mặc định.");
        };
    }
}
