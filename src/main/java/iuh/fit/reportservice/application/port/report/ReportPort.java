package iuh.fit.reportservice.application.port.report;

import iuh.fit.reportservice.application.exception.ServiceException;
import net.sf.jasperreports.engine.JRException;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;

public interface ReportPort {
    byte[] exportReport(String format, Map<String, Object> parameters) throws ServiceException, JRException, IOException, SQLException;
}
