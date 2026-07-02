package iuh.fit.reportservice.application.enums;

import lombok.Getter;

@Getter
public enum DataSourceType {
    USERS("users");

    private final String value;
    DataSourceType(String value) {
        this.value = value;
    }
}
