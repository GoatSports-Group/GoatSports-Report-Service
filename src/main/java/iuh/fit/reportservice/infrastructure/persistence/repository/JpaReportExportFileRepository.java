package iuh.fit.reportservice.infrastructure.persistence.repository;

import iuh.fit.reportservice.infrastructure.persistence.entity.ReportExportFileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

public interface JpaReportExportFileRepository extends JpaRepository<ReportExportFileEntity, UUID> {
    @Modifying
    @Transactional
    @Query("""
            delete from ReportExportFileEntity f
            where f.exportId in (select e.exportId from ReportExportEntity e where e.expiresAt < :now)
            """)
    int deleteExpired(@Param("now") Instant now);
}
