package iuh.fit.reportservice.infrastructure.report;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Tên và email người dùng cho báo cáo chỉ có mã (Tài chính), đọc thẳng DB của auth-service qua datasource chỉ đọc,
 * như báo cáo danh sách người dùng. Mã không còn tài khoản thì không có trong kết quả.
 */
@Component
class UserDirectory {
    private static final int BATCH = 1_000;
    private final DataSource authDataSource;

    UserDirectory(@Qualifier("authDataSource") DataSource authDataSource) {
        this.authDataSource = authDataSource;
    }

    record Person(String fullName, String email) {
    }

    Map<UUID, Person> find(Collection<UUID> ids) throws SQLException {
        List<UUID> unique = ids.stream().filter(Objects::nonNull).distinct().toList();
        Map<UUID, Person> people = new HashMap<>();
        if (unique.isEmpty()) return people;
        try (Connection connection = authDataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "select user_id, full_name, email from users where user_id = any (?)")) {
            for (int start = 0; start < unique.size(); start += BATCH) {
                List<UUID> batch = new ArrayList<>(unique.subList(start, Math.min(start + BATCH, unique.size())));
                Array array = connection.createArrayOf("uuid", batch.toArray());
                statement.setArray(1, array);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        people.put(rows.getObject("user_id", UUID.class),
                                new Person(rows.getString("full_name"), rows.getString("email")));
                    }
                }
                array.free();
            }
        }
        return people;
    }
}
