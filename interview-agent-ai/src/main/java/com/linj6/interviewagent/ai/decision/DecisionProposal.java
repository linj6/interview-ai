package com.linj6.interviewagent.ai.decision;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.linj6.interviewagent.ai.api.decision.DecisionAction;

/** AI 模块内部的 Decision Structured Output；不属于 Module Public API，也不会直接成为业务事实。 */
public record DecisionProposal(
        @JsonPropertyDescription("从应用提供的允许动作中选择下一步：追问、进入下一主问题或结束面试。") DecisionAction action,
        @JsonPropertyDescription("简要说明所选动作的依据；必须非空。") String reason,
        @JsonPropertyDescription(
                        "仅 FOLLOW_UP 使用；必须引用本次 Evaluation Missing Points 目标列表中一个存在的 M* 引用。NEXT_QUESTION 和 END 填 null 或空白。")
                String targetRef,
        @JsonPropertyDescription("仅 NEXT_QUESTION 使用；选择本次未使用计划项列表中的一个 P* 引用，其他动作为空。") String planItemRef) {
    public DecisionProposal(DecisionAction action, String reason, String targetRef) {
        this(action, reason, targetRef, null);
    }
}
