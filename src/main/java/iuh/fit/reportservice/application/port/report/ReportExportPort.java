package iuh.fit.reportservice.application.port.report;

import iuh.fit.reportservice.application.dto.report.AdminBookingExportRequest;
import iuh.fit.reportservice.application.dto.report.FinanceExportRequest;
import iuh.fit.reportservice.application.dto.report.ReportExportFile;
import iuh.fit.reportservice.application.dto.report.ReportExportView;
import iuh.fit.reportservice.application.exception.ServiceException;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Xuất báo cáo chạy nền: người dùng gửi yêu cầu và nhận ngay một job; file được dựng ở nền, có thông báo khi xong
 * và tải lại được trong 7 ngày. Không còn chờ đồng bộ (và không còn chạm timeout của gateway).
 */
public interface ReportExportPort {
    /** Tạo job (hoặc trả lại job cùng bộ lọc vừa tạo trong vài phút gần đây). */
    ReportExportView requestAdminBookingExport(UUID requesterId, AdminBookingExportRequest request) throws ServiceException;

    /** Báo cáo Tài chính (hoàn tiền / chi trả) theo bộ lọc của trang Tài chính; cùng cơ chế job chạy nền. */
    ReportExportView requestFinanceExport(UUID requesterId, FinanceExportRequest request) throws ServiceException;

    /** Các báo cáo gần đây (thuộc các loại cho trước) của người yêu cầu, chưa hết hạn, mới nhất trước. */
    List<ReportExportView> listExports(UUID requesterId, Collection<String> reportTypes);

    ReportExportView getExport(UUID requesterId, UUID exportId) throws ServiceException;

    ReportExportFile download(UUID requesterId, UUID exportId) throws ServiceException;
}
