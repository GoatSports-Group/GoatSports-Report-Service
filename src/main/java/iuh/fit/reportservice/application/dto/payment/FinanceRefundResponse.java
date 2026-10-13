package iuh.fit.reportservice.application.dto.payment;

import java.time.LocalDateTime;
import java.util.UUID;

/** Một khoản hoàn từ payment-service ({@code /internal/finance/refunds}); chỉ giữ trường báo cáo cần. */
public record FinanceRefundResponse(
        UUID refundId,
        UUID payerId,
        String purpose,
        Double amount,
        String status,
        String reason,
        String failureReason,
        LocalDateTime createdAt,
        LocalDateTime refundedAt,
        String providerRefundId,
        String paymentDescription
) {
}
