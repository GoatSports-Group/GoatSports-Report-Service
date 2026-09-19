package iuh.fit.reportservice.infrastructure.external.client;

import iuh.fit.reportservice.application.dto.report.UserDetailReportResponse;
import iuh.fit.reportservice.application.dto.report.UserReportResponse;
import iuh.fit.reportservice.application.dto.response.base.BaseResponse;
import iuh.fit.reportservice.infrastructure.config.AuthClientConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@FeignClient(
        name = "GOATSPORTS-AUTH-SERVICE",
        url = "${auth.service.url}",
        path = "/internal/users",
        configuration = AuthClientConfig.class
)
public interface AuthClient {
    @GetMapping
    BaseResponse<List<UserReportResponse>> getAllUsers();

    @GetMapping("/{userId}")
    BaseResponse<UserDetailReportResponse> getUserById(@PathVariable String userId);
}
