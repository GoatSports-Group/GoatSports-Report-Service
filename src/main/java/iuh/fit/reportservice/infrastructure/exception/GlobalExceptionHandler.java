package iuh.fit.reportservice.infrastructure.exception;

import iuh.fit.reportservice.application.dto.response.base.BaseResponse;
import iuh.fit.reportservice.application.exception.ServiceException;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import org.springframework.http.MediaType;
import java.util.List;

@ControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    ResponseEntity<BaseResponse<Object>> handleAllExceptions(Exception e){
        e.printStackTrace();
        BaseResponse<Object> response = new BaseResponse<>();
        response.setStatusCode(HttpStatus.BAD_REQUEST.value());
        response.setMessage(e.getMessage());
        response.setError("Có lỗi xảy ra");

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }

    @ExceptionHandler(value = {
            ServiceException.class
    })
    ResponseEntity<Object> handleServiceException(ServiceException e) {
        e.printStackTrace();
        BaseResponse<Object> response = new BaseResponse<>();

        response.setStatusCode(HttpStatus.BAD_REQUEST.value());
        response.setMessage(e.getMessage());
        response.setError("Có lỗi xảy ra");

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<BaseResponse<Object>> handleValidationException(MethodArgumentNotValidException e) {
        BindingResult result = e.getBindingResult();
        final List<FieldError> list = result.getFieldErrors();
        BaseResponse<Object> res = new BaseResponse<>();
        res.setStatusCode(HttpStatus.BAD_REQUEST.value());

        if (list.isEmpty()) {
            res.setError("Có lỗi xảy ra");
            res.setMessage("Không tìm thấy dữ liệu hợp lệ");
            return ResponseEntity.badRequest()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(res);
        }

        List<String> messages = list.stream().map(DefaultMessageSourceResolvable::getDefaultMessage).toList();
        res.setError(e.getBody().getDetail());
        res.setMessage(messages.size() > 1 ? messages : messages.getFirst());

        return ResponseEntity.badRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .body(res);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<BaseResponse<Object>> handleResourceException(Exception e) {
        BaseResponse<Object> response = new BaseResponse<>();
        response.setStatusCode(HttpStatus.NOT_FOUND.value());
        response.setMessage(e.getMessage());
        response.setError("Đường dẫn không tồn tại");

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }

}
