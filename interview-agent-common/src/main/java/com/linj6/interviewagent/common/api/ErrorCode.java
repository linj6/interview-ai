package com.linj6.interviewagent.common.api;

/** 稳定的业务错误码契约。HTTP Status 由 Web 层单独决定。 */
public interface ErrorCode {
    int getCode();

    String getMessage();
}
