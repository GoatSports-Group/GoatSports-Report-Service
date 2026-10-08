package iuh.fit.reportservice.infrastructure.persistence.repository;

import iuh.fit.reportservice.infrastructure.persistence.entity.PeriodicReportEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaPeriodicReportRepository extends JpaRepository<PeriodicReportEntity, UUID> {
    Optional<PeriodicReportEntity> findByOwnerIdAndPeriodTypeAndPeriodStartAndPeriodEnd(
            UUID ownerId, String periodType, LocalDate periodStart, LocalDate periodEnd);

    boolean existsByOwnerIdAndPeriodTypeAndPeriodStartAndPeriodEnd(
            UUID ownerId, String periodType, LocalDate periodStart, LocalDate periodEnd);

    List<PeriodicReportEntity> findTop24ByOwnerIdOrderByPeriodEndDescGeneratedAtDesc(UUID ownerId);

    List<PeriodicReportEntity> findTop24ByOwnerIdAndPeriodTypeOrderByPeriodEndDesc(UUID ownerId, String periodType);
}
