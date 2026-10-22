package iuh.fit.reportservice.infrastructure.report;

import iuh.fit.reportservice.application.dto.response.base.BaseResponse;
import iuh.fit.reportservice.infrastructure.external.client.AuthClient;
import iuh.fit.reportservice.infrastructure.external.client.AuthClient.SummariesRequest;
import iuh.fit.reportservice.infrastructure.external.client.AuthClient.UserSummary;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserDirectoryTest {

    @Test
    void asksAuthServiceInBatchesWithinItsLimit() {
        AuthClient authClient = mock(AuthClient.class);
        List<Integer> batchSizes = new ArrayList<>();
        when(authClient.getUserSummaries(any())).thenAnswer(invocation -> {
            SummariesRequest request = invocation.getArgument(0);
            batchSizes.add(request.userIds().size());
            List<UserSummary> found = request.userIds().stream()
                    .map(id -> new UserSummary(id, "Người " + id, id + "@goat.test"))
                    .toList();
            return new BaseResponse<>(200, found, null, null);
        });
        List<UUID> ids = IntStream.range(0, 150).mapToObj(index -> UUID.randomUUID()).toList();
        List<UUID> withDuplicatesAndNull = new ArrayList<>(ids);
        withDuplicatesAndNull.add(ids.getFirst());
        withDuplicatesAndNull.add(null);

        Map<UUID, UserDirectory.Person> people = new UserDirectory(authClient).find(withDuplicatesAndNull);

        assertThat(batchSizes).containsExactly(100, 50);
        assertThat(people).hasSize(150);
        assertThat(people.get(ids.get(7)).email()).isEqualTo(ids.get(7) + "@goat.test");
    }

    @Test
    void noIdsMeansNoCall() {
        AuthClient authClient = mock(AuthClient.class);

        assertThat(new UserDirectory(authClient).find(List.of())).isEmpty();
        verify(authClient, never()).getUserSummaries(any());
    }
}
