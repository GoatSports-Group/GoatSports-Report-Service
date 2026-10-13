package iuh.fit.reportservice.application.port.report;

import iuh.fit.reportservice.application.dto.report.OwnerBookingReportFilter;
import iuh.fit.reportservice.application.dto.report.OwnerRevenueReportFilter;
import iuh.fit.reportservice.application.exception.ServiceException;
import net.sf.jasperreports.engine.JRException;

import java.io.IOException;
import java.util.UUID;

public interface ReportPort {


    byte[] exportOwnerBookings(String format, OwnerBookingReportFilter filter)
            throws ServiceException, JRException, IOException;

    byte[] exportOwnerBookingInvoice(UUID bookingId)
            throws ServiceException, JRException, IOException;

    byte[] exportOwnerRevenue(String format, OwnerRevenueReportFilter filter)
            throws ServiceException, JRException, IOException;
}
