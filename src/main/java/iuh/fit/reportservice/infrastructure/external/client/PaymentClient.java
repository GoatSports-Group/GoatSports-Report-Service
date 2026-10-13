package iuh.fit.reportservice.infrastructure.external.client;

import iuh.fit.reportservice.application.dto.payment.FinancePayoutResponse;
import iuh.fit.reportservice.application.dto.payment.FinanceRefundResponse;
import iuh.fit.reportservice.application.dto.response.base.BaseResponse;
import iuh.fit.reportservice.infrastructure.config.AuthClientConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Dữ liệu báo cáo Tài chính, gọi từ job chạy nền (không có người dùng đăng nhập): chỉ gửi secret của gateway
 * ({@link AuthClientConfig}), quyền xuất đã kiểm lúc tạo job.
 */
@FeignClient(
        name = "GOATSPORTS-PAYMENT-SERVICE",
        url = "${payment.service.url}",
        path = "/internal/finance",
        configuration = AuthClientConfig.class
)
public interface PaymentClient {
    @GetMapping("/refunds")
    BaseResponse<List<FinanceRefundResponse>> exportRefunds(
            @RequestParam(required = false) List<String> status,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) List<UUID> people,
            @RequestParam(required = false) Integer olderThanDays,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    );

    @GetMapping("/payouts")
    BaseResponse<List<FinancePayoutResponse>> exportPayouts(
            @RequestParam String kind,
            @RequestParam(required = false) List<String> status,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) List<UUID> people,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    );
}
