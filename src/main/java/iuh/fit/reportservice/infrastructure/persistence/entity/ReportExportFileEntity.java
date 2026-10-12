package iuh.fit.reportservice.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/** Nội dung file của một job xuất báo cáo (bytea), xóa cùng job khi hết hạn. */
@Entity
@Table(name = "report_export_files")
@Getter
@Setter
@NoArgsConstructor
public class ReportExportFileEntity {
    @Id
    @Column(name = "export_id")
    private UUID exportId;
    @Column(name = "data", nullable = false, length = 200_000_000)
    private byte[] data;

    public ReportExportFileEntity(UUID exportId, byte[] data) {
        this.exportId = exportId;
        this.data = data;
    }
}
