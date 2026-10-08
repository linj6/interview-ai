package com.linj6.interviewagent.common.api;

import com.linj6.interviewagent.common.RequestIdContext;

/** 普通 JSON 业务接口的统一响应结构。 */
public record Result<T>(Integer code, String message, T data, String requestId) {
    public static <T> Result<T> success(T data) {
        return new Result<>(0, "操作成功", data, RequestIdContext.current());
    }

    public static Result<Void> success() {
        return success(null);
    }

    public static Result<Void> failure(ErrorCode errorCode) {
        return failure(errorCode, null);
    }

    public static <T> Result<T> failure(ErrorCode errorCode, T data) {
        return new Result<>(errorCode.getCode(), errorCode.getMessage(), data, RequestIdContext.current());
    }

    public static <T> Result<T> failure(ErrorCode errorCode, T data, String message) {
        return new Result<>(errorCode.getCode(), message, data, RequestIdContext.current());
    }
}
