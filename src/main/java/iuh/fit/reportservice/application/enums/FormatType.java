package iuh.fit.reportservice.application.enums;

import lombok.Getter;

@Getter
public enum FormatType {
    PDF("pdf"), XLSX("xlsx"), CSV("csv"), JPG("jpg"), JPEG("jpeg");

    private final String value;
    FormatType(String value) {
        this.value = value;
    }
}
