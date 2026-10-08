package iuh.fit.reportservice.application.port.report;

import iuh.fit.reportservice.application.dto.report.PeriodicReportView;
import iuh.fit.reportservice.application.enums.ReportPeriodType;
import iuh.fit.reportservice.application.exception.ServiceException;
import net.sf.jasperreports.engine.JRException;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Báo cáo định kỳ của chủ sân: chốt số liệu theo kỳ, lưu lại, xuất PDF/XLSX từ đúng bản đã chốt. */
public interface PeriodicReportPort {
    List<PeriodicReportView> list(UUID ownerId, ReportPeriodType periodType);

    /** Tạo (hoặc chốt lại) báo cáo cho một kỳ. Chạy lại cùng kỳ chỉ cập nhật đúng một bản ghi. */
    PeriodicReportView generate(UUID ownerId, ReportPeriodType periodType, LocalDate fromDate, LocalDate toDate)
            throws ServiceException;

    byte[] export(UUID ownerId, UUID reportId, String format) throws ServiceException, JRException;

    /** Job: tạo các báo cáo kỳ vừa kết thúc còn thiếu cho mọi chủ sân. Trả về số báo cáo đã tạo. */
    int generateDueReports(LocalDate today);
}
