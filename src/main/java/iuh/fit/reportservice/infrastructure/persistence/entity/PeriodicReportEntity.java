package iuh.fit.reportservice.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "periodic_reports")
@Getter
@Setter
public class PeriodicReportEntity {
    @Id
    @Column(name = "report_id")
    private UUID reportId;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "period_type", nullable = false, length = 20)
    private String periodType;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "total_revenue", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalRevenue;

    @Column(name = "booking_count", nullable = false)
    private long bookingCount;

    @Column(name = "paid_booking_count", nullable = false)
    private long paidBookingCount;

    @Column(name = "revenue_change_percentage", precision = 10, scale = 2)
    private BigDecimal revenueChangePercentage;

    @Column(name = "booking_count_change_percentage", precision = 10, scale = 2)
    private BigDecimal bookingCountChangePercentage;

    @Column(name = "snapshot", nullable = false, columnDefinition = "text")
    private String snapshot;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;
}
