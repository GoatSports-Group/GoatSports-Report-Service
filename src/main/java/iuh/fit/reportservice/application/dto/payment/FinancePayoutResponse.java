package iuh.fit.reportservice.application.dto.payment;

import java.time.LocalDateTime;
import java.util.UUID;

/** Một lượt chi từ payment-service ({@code /internal/finance/payouts}); chỉ giữ trường báo cáo cần. */
public record FinancePayoutResponse(
        UUID payoutId,
        UUID payeeId,
        Double amount,
        String status,
        String destinationBankBin,
        String destinationAccountMasked,
        String destinationAccountName,
        String failureCode,
        String failureReason,
        LocalDateTime requestedAt,
        LocalDateTime completedAt,
        String referenceId,
        String providerPayoutId
) {
}
