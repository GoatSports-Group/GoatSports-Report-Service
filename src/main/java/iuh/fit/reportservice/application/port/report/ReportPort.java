package iuh.fit.reportservice.application.port.report;

import iuh.fit.reportservice.application.exception.ServiceException;
import net.sf.jasperreports.engine.JRException;

import java.io.IOException;

public interface ReportPort {
    byte[] exportReport(String format, String type) throws ServiceException, JRException, IOException;
}
