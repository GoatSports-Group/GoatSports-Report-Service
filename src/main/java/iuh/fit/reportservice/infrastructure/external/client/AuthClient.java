package iuh.fit.reportservice.infrastructure.external.client;

import iuh.fit.reportservice.application.dto.report.UserReportResponse;
import iuh.fit.reportservice.application.dto.response.base.BaseResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@FeignClient(
        name = "GOATSPORTS-AUTH-SERVICE",
        url = "${auth.service.url}",
        path = "/internal/users"
)
public interface AuthClient {
    @GetMapping
    BaseResponse<List<UserReportResponse>> getAllUsers();
}
