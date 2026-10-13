package iuh.fit.reportservice.infrastructure.persistence.repository;

import iuh.fit.reportservice.infrastructure.persistence.entity.ReportExportEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaReportExportRepository extends JpaRepository<ReportExportEntity, UUID> {
    List<ReportExportEntity> findTop20ByRequestedByAndReportTypeInAndExpiresAtAfterOrderByCreatedAtDesc(
            UUID requestedBy, Collection<String> reportTypes, Instant now);

    Optional<ReportExportEntity> findFirstByRequestedByAndFilterKeyAndStatusInAndCreatedAtAfterOrderByCreatedAtDesc(
            UUID requestedBy, String filterKey, Collection<String> statuses, Instant createdAfter);

    /** Job chờ chạy, hoặc đang chạy nhưng lease đã hết (worker trước đã chết). */
    @Query("""
            select e.exportId from ReportExportEntity e
            where e.status = 'PENDING' or (e.status = 'RUNNING' and e.leaseUntil < :now)
            order by e.createdAt
            """)
    List<UUID> findClaimable(@Param("now") Instant now, Pageable pageable);

    /** Nhận job bằng UPDATE có điều kiện: nhiều instance cùng thấy một job thì chỉ một bên nhận được (trả về 1). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            update ReportExportEntity e
            set e.status = 'RUNNING', e.leaseUntil = :leaseUntil, e.startedAt = :now, e.attempts = e.attempts + 1
            where e.exportId = :exportId
              and (e.status = 'PENDING' or (e.status = 'RUNNING' and e.leaseUntil < :now))
            """)
    int claim(@Param("exportId") UUID exportId, @Param("now") Instant now, @Param("leaseUntil") Instant leaseUntil);

    @Modifying
    @Transactional
    @Query("delete from ReportExportEntity e where e.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);
}
