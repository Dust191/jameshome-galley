package io.github.jameshome.galley.common.error;

/** 可预期的业务失败。携带错误码、提示语与（可选的）字段名，由全局处理器转成统一错误体。 */
public class BusinessException extends RuntimeException {

    private final ErrorCode code;
    private final String field;
    private final Long retryAfterSeconds;

    public BusinessException(ErrorCode code, String message) {
        this(code, message, null, null);
    }

    public BusinessException(ErrorCode code, String message, String field) {
        this(code, message, field, null);
    }

    /**
     * @param retryAfterSeconds 限流类错误的 {@code Retry-After} 响应头秒数；非限流错误传 {@code null}
     */
    public BusinessException(ErrorCode code, String message, String field, Long retryAfterSeconds) {
        super(message);
        this.code = code;
        this.field = field;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public ErrorCode code() {
        return code;
    }

    public String field() {
        return field;
    }

    public Long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
