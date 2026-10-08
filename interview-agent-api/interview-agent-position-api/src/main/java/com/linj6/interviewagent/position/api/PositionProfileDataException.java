package com.linj6.interviewagent.position.api;
/** Profile 持久化事实缺失或关键写入未命中；由聚合调用方映射业务错误。 */
public class PositionProfileDataException extends RuntimeException {
    public PositionProfileDataException(String message) {
        super(message);
    }
}
