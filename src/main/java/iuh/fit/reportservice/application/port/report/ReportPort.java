package iuh.fit.reportservice.application.port.report;

import iuh.fit.reportservice.application.exception.ServiceException;
import net.sf.jasperreports.engine.JRException;

import java.io.IOException;

public interface ReportPort {
    byte[] exportReport(String format, String type) throws ServiceException, JRException, IOException;

    byte[] exportUserDetail(String format, String userId) throws ServiceException, JRException, IOException;
}
