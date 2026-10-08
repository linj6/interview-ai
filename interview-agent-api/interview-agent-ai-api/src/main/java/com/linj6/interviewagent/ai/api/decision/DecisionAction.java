package com.linj6.interviewagent.ai.api.decision;

/** 表示面试回答处理后的下一步业务动作。 */
public enum DecisionAction {
    /** 在当前主问题链中继续深入追问。 */
    FOLLOW_UP,
    /** 进入下一个主问题。 */
    NEXT_QUESTION,
    /** 结束当前 Interview。 */
    END
}
