package com.linj6.interviewagent.ai.api.evaluation;

/** 评价维度是否具备客观评分条件。 */
public enum Applicability {
    /** 当前回答具备评分条件，必须给出 0–10 分和 1–3 条评分证据。 */
    APPLICABLE,
    /** 客观上不具备评分条件，仅在 Profile 明确允许时使用；不能用于表示回答质量差。 */
    NOT_APPLICABLE
}
