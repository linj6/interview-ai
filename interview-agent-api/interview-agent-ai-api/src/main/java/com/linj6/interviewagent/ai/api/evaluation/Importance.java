package com.linj6.interviewagent.ai.api.evaluation;

/** 缺失要点对理想回答的重要程度。 */
public enum Importance {
    /** 理想回答必须覆盖。 */
    HIGH,
    /** 理想回答应尽量覆盖。 */
    MEDIUM,
    /** 可选择性补充。 */
    LOW
}
