package com.linj6.interviewagent.candidate.api;
/** Profile 持久化事实缺失或关键写入未命中；由聚合调用方映射业务错误。 */
public class CandidateProfileDataException extends RuntimeException {
    public CandidateProfileDataException(String message) {
        super(message);
    }
}
