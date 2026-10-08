package com.linj6.interviewagent.common.api;

/** 通用请求、系统和第三方服务错误。 */
public enum CommonErrorCode implements ErrorCode {
    PARAM_ERROR(10001, "参数校验失败"),
    PARAM_MISSING(10002, "缺少必要参数"),
    PARAM_TYPE_ERROR(10003, "参数类型错误"),
    REQUEST_METHOD_NOT_SUPPORT(10004, "请求方法不支持"),
    REQUEST_BODY_MISSING(10005, "请求体不能为空"),
    UNAUTHORIZED(11001, "未授权"),
    FORBIDDEN(11002, "无权限"),
    SYSTEM_ERROR(50001, "系统内部错误"),
    SERVICE_UNAVAILABLE(50002, "服务暂不可用"),
    DB_ERROR(50003, "数据服务异常"),
    TIMEOUT(50004, "请求处理超时"),
    RATE_LIMIT(50005, "请求过于频繁"),
    THIRD_PARTY_ERROR(90001, "第三方服务异常"),
    AI_SERVICE_ERROR(90002, "AI 服务异常"),
    AI_SERVICE_TIMEOUT(90003, "AI 服务超时"),
    AI_RESPONSE_INVALID(90004, "AI 响应无效");

    private final int code;
    private final String message;

    CommonErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public int getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
