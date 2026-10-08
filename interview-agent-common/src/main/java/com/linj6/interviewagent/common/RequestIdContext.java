package com.linj6.interviewagent.common;

import org.slf4j.MDC;

/** 读取当前请求由服务端生成的 requestId。 */
public final class RequestIdContext {
    public static final String HEADER_NAME = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    private RequestIdContext() {}

    public static String current() {
        return MDC.get(MDC_KEY);
    }
}
