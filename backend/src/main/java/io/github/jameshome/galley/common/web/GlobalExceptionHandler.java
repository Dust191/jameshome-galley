package io.github.jameshome.galley.common.web;

import io.github.jameshome.galley.common.error.ApiError;
import io.github.jameshome.galley.common.error.BusinessException;
import io.github.jameshome.galley.common.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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
        return ResponseEntity.status(code.status())
                .body(error(code.name(), ex.getMessage(), ex.field()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> onInvalid(MethodArgumentNotValidException ex) {
        FieldError first = ex.getBindingResult().getFieldErrors().stream().findFirst().orElse(null);
        String field = first == null ? null : first.getField();
        String message = first == null ? "参数校验失败" : first.getDefaultMessage();
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.status())
                .body(error(ErrorCode.VALIDATION_FAILED.name(), message, field));
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
