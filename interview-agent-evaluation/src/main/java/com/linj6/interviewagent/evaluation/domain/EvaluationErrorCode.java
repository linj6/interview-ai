package com.linj6.interviewagent.evaluation.domain;

import com.linj6.interviewagent.common.api.ErrorCode;

/** Evaluation 模块对外稳定的业务错误语义。 */
public enum EvaluationErrorCode implements ErrorCode {
    EVALUATION_PROPOSAL_INVALID(24001, "Evaluation Proposal 无效"),
    EVALUATION_NOT_FOUND(24002, "Evaluation 不存在"),
    EVALUATION_CALCULATION_ERROR(24003, "Evaluation 计算失败"),
    EVALUATION_ALREADY_EXISTS(24004, "Answer 已存在正式 Evaluation"),
    EVALUATION_PROFILE_NOT_FOUND(24005, "Evaluation Profile 不存在");

    private final int code;
    private final String message;

    EvaluationErrorCode(int code, String message) {
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
