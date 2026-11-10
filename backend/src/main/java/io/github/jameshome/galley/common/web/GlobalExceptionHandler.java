package io.github.jameshome.galley.common.web;

import io.github.jameshome.galley.common.error.ApiError;
import io.github.jameshome.galley.common.error.BusinessException;
import io.github.jameshome.galley.common.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import tools.jackson.databind.exc.InvalidFormatException;

/**
 * 把异常统一转成 {@code {code, message, field, traceId}}。
 *
 * <p>原则：能说清"哪里错了、怎么改"就写清；说不清的一律 500 且只记日志，不把堆栈或 SQL 细节吐给前端。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiError> onBusiness(BusinessException ex) {
        ErrorCode code = ex.code();
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(code.status());
        // DES-002 §3：429 限流必须带 Retry-After，告诉调用方什么时候可以再试
        if (ex.retryAfterSeconds() != null) {
            builder.header("Retry-After", String.valueOf(ex.retryAfterSeconds()));
        }
        return builder.body(error(code.name(), ex.getMessage(), ex.field()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> onInvalid(MethodArgumentNotValidException ex) {
        FieldError first = ex.getBindingResult().getFieldErrors().stream().findFirst().orElse(null);
        String field = first == null ? null : first.getField();
        String message = first == null ? "参数校验失败" : first.getDefaultMessage();
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.status())
                .body(error(ErrorCode.VALIDATION_FAILED.name(), message, field));
    }

    /**
     * 请求体无法解析：JSON 语法错、枚举值不在取值集合内、数字格式不对等。
     *
     * <p>这类错误本质是调用方传错，必须返回 400 而不是 500——否则前端会当成服务端故障去重试。 字段名尽量从 Jackson 的路径里取，取不到就给通用提示。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> onUnreadable(HttpMessageNotReadableException ex) {
        String field = null;
        String message = "请求体格式不正确";
        if (ex.getCause() instanceof InvalidFormatException ife) {
            if (!ife.getPath().isEmpty()) {
                field = ife.getPath().get(ife.getPath().size() - 1).getPropertyName();
            }
            if (ife.getTargetType() != null && ife.getTargetType().isEnum()) {
                message = "取值不在允许范围内";
            }
        }
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.status())
                .body(error(ErrorCode.VALIDATION_FAILED.name(), message, field));
    }

    /** 路径或查询参数类型不对，例如 {@code ?categoryId=abc}。 */
    @ExceptionHandler({
        MethodArgumentTypeMismatchException.class,
        MissingServletRequestParameterException.class
    })
    public ResponseEntity<ApiError> onBadParameter(Exception ex) {
        String field = ex instanceof MethodArgumentTypeMismatchException m ? m.getName() : null;
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.status())
                .body(error(ErrorCode.VALIDATION_FAILED.name(), "参数格式不正确", field));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> onDenied(AccessDeniedException ex) {
        return ResponseEntity.status(ErrorCode.PERMISSION_DENIED.status())
                .body(error(ErrorCode.PERMISSION_DENIED.name(), "没有这个权限", null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> onUnexpected(Exception ex) {
        log.error("未预期异常", ex);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.status())
                .body(error(ErrorCode.INTERNAL_ERROR.name(), "服务内部错误", null));
    }

    private static ApiError error(String code, String message, String field) {
        return new ApiError(code, message, field, MDC.get(TraceIdFilter.MDC_KEY));
    }
}
