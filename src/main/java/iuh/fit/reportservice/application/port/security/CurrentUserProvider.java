package iuh.fit.reportservice.application.port.security;

import java.util.Optional;

public interface CurrentUserProvider {
    Optional<String> getCurrentUserId();
}
