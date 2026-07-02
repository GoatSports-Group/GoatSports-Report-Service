package iuh.fit.reportservice.presentation.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReportRequest {
    @NotBlank(message = "Định dạng không được để trống")
    private String format;
    @NotBlank(message = "Loại dữ liệu không được để trống")
    private String type;
}
