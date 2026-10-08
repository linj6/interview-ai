package com.linj6.interviewagent.ai.api.decision;

/**
 * Java 校验后的 Decision 结果；动作必须属于输入 DecisionContext 的 allowedActions。
 *
 * @param planItemId NEXT_QUESTION 时 Java 解析得到的真实 PlanItem ID；其他动作为空。
 * @param action 已校验的业务动作。
 * @param reason 非空的动作理由，已去除首尾空白。
 * @param followUpTarget FOLLOW_UP 时为 Java 解析 targetRef 得到的 Missing Point 原始描述；其他动作时为 null，不是 targetRef 或持久化 ID。
 */
public record ValidatedDecisionResult(DecisionAction action, String reason, String followUpTarget, Long planItemId) {
    public ValidatedDecisionResult(DecisionAction action, String reason, String followUpTarget) {
        this(action, reason, followUpTarget, null);
    }
}
