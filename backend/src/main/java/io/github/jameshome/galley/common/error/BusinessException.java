package io.github.jameshome.galley.common.error;

/** 可预期的业务失败。携带错误码、提示语与（可选的）字段名，由全局处理器转成统一错误体。 */
public class BusinessException extends RuntimeException {

    private final ErrorCode code;
    private final String field;

    public BusinessException(ErrorCode code, String message) {
        this(code, message, null);
    }

    public BusinessException(ErrorCode code, String message, String field) {
        super(message);
        this.code = code;
        this.field = field;
    }

    public ErrorCode code() {
        return code;
    }

    public String field() {
        return field;
    }
}
