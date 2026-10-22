package iuh.fit.reportservice.infrastructure.report;

import iuh.fit.reportservice.application.dto.response.base.BaseResponse;
import iuh.fit.reportservice.infrastructure.external.client.AuthClient;
import iuh.fit.reportservice.infrastructure.external.client.AuthClient.SummariesRequest;
import iuh.fit.reportservice.infrastructure.external.client.AuthClient.UserSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Tên và email người dùng cho báo cáo chỉ có mã (Tài chính), hỏi auth-service theo lô qua API nội bộ: auth-service sở
 * hữu dữ liệu người dùng, report-service không đọc thẳng DB của nó. Mã không còn tài khoản thì không có trong kết quả.
 */
@Component
@RequiredArgsConstructor
class UserDirectory {
    private final AuthClient authClient;

    record Person(String fullName, String email) {
    }

    Map<UUID, Person> find(Collection<UUID> ids) {
        List<UUID> unique = ids.stream().filter(Objects::nonNull).distinct().toList();
        Map<UUID, Person> people = new HashMap<>();
        for (int start = 0; start < unique.size(); start += AuthClient.MAX_SUMMARY_IDS) {
            List<UUID> batch = unique.subList(start, Math.min(start + AuthClient.MAX_SUMMARY_IDS, unique.size()));
            BaseResponse<List<UserSummary>> response = authClient.getUserSummaries(new SummariesRequest(batch));
            List<UserSummary> summaries = response == null || response.getData() == null ? List.of() : response.getData();
            summaries.forEach(summary -> people.put(summary.userId(), new Person(summary.fullName(), summary.email())));
        }
        return people;
    }
}
