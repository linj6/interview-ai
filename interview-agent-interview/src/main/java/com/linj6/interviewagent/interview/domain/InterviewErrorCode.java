package com.linj6.interviewagent.interview.domain;

import com.linj6.interviewagent.common.api.ErrorCode;

/** Interview 模块对外稳定的业务错误语义。 */
public enum InterviewErrorCode implements ErrorCode {
    INTERVIEW_NOT_FOUND(23001, "Interview 不存在"),
    INTERVIEW_STATE_CONFLICT(23002, "Interview 当前状态不允许执行该操作"),
    INTERVIEW_EXPIRED(23006, "Interview 已超过截止时间"),
    QUESTION_NOT_FOUND(23003, "Question 不存在"),
    QUESTION_INTERVIEW_MISMATCH(23004, "Question 不属于当前 Interview"),
    ANSWER_ALREADY_EXISTS(23005, "Question 已存在正式 Answer"),
    EVALUATION_PROCESSING_FAILED(23007, "Evaluation 处理失败"),
    INTERVIEW_VERSION_CONFLICT(23008, "Interview 已被其他请求更新"),
    QUESTION_STATE_CONFLICT(23009, "Question 当前状态不允许执行该操作"),
    ANSWER_NOT_FOUND(23010, "Answer 不存在"),
    INTERVIEW_DATA_INCONSISTENT(23011, "Interview 数据不一致");

    private final int code;
    private final String message;

    InterviewErrorCode(int code, String message) {
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
