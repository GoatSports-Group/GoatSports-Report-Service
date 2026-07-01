package iuh.fit.reportservice.application.dto.response.base;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BaseResponse<T> {
    private Integer statusCode;
    private T data;
    private Object message;
    private String error;
}
