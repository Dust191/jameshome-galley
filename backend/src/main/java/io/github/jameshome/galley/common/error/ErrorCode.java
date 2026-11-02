package io.github.jameshome.galley.common.error;

import org.springframework.http.HttpStatus;

/**
 * 业务错误码。取值与 DES-002 §3 的错误码表一一对应，前端据此映射文案。
 *
 * <p>新增加码前先确认：它真的是一个新的失败原因，而不是某个既有原因的另一种说法。
 */
public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    AUTH_REQUIRED(HttpStatus.UNAUTHORIZED),
    AUTH_INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),
    PERMISSION_DENIED(HttpStatus.FORBIDDEN),
    CSRF_INVALID(HttpStatus.FORBIDDEN),
    PASSWORD_CHANGE_REQUIRED(HttpStatus.FORBIDDEN),
    NOT_FOUND(HttpStatus.NOT_FOUND),
    VERSION_CONFLICT(HttpStatus.CONFLICT),
    DUPLICATE_KEY(HttpStatus.CONFLICT),
    MOVEMENT_IMMUTABLE(HttpStatus.UNPROCESSABLE_ENTITY),
    MOVEMENT_ALREADY_REVERSED(HttpStatus.UNPROCESSABLE_ENTITY),
    INVITE_CODE_INVALID(HttpStatus.UNPROCESSABLE_ENTITY),
    INVITE_CODE_DISABLED(HttpStatus.UNPROCESSABLE_ENTITY),
    UNIT_IN_USE(HttpStatus.UNPROCESSABLE_ENTITY),
    CATEGORY_IN_USE(HttpStatus.UNPROCESSABLE_ENTITY),
    EXPORT_TOO_LARGE(HttpStatus.UNPROCESSABLE_ENTITY),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
