package com.linj6.interviewagent.ai.api.evaluation;

/** 维度评分证据的来源类型。 */
public enum EvidenceType {
    /** 当前回答中的一个非空行片段原文。 */
    ANSWER_QUOTE,
    /** 对当前回答整体表现的观察，不携带原文引用。 */
    OVERALL_OBSERVATION
}
