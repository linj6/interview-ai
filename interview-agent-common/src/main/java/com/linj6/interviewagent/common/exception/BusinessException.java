package com.linj6.interviewagent.common.exception;

import com.linj6.interviewagent.common.api.ErrorCode;

/** 业务流程可预期失败，交由全局异常处理器转换为统一响应。 */
public class BusinessException extends RuntimeException {
    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
