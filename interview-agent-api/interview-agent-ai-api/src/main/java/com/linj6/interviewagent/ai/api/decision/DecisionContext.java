package com.linj6.interviewagent.ai.api.decision;

import com.linj6.interviewagent.ai.api.ProjectContext;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import lombok.Builder;

/**
 * Decision 的业务输入，由 Interview Application 从当前 Turn 和已验证 Evaluation 组装；AI 模块不查询业务 Repository。
 * 候选人和岗位背景字段由调用方透传；API 不统一校验其 null 或空白语义。
 *
 * @param interviewId 当前 Interview ID。
 * @param questionId 当前被回答的 Question ID。
 * @param answerId 当前 Turn 已持久化的 Answer ID。
 * @param evaluationScore10 当前已验证 Evaluation 的 0–10 总分。
 * @param evaluationStrengths 当前 Evaluation 的 Strength 描述，以 "; " 拼接；无条目时为空字符串。
 * @param evaluationWeaknesses 当前 Evaluation 的 Weakness 描述列表；可为空列表。
 * @param evaluationMissingPoints 当前 Evaluation 的 Missing Point 描述列表，顺序用于生成 M1、M2 等目标；为空时不允许 FOLLOW_UP。
 * @param unusedPlanItems 当前 Interview Plan 的未使用正式项，顺序用于本次调用生成 P* 引用；不包含已生成 MAIN 的关联。
 * @param allowedActions 应用按 Interview Progress 和业务规则计算出的动作集合，Decision 只能选择其中的动作。
 */
@Builder
public record DecisionContext(
        Long interviewId,
        Long questionId,
        Long answerId,
        String questionContent,
        String answerContent,
        BigDecimal evaluationScore10,
        String evaluationStrengths,
        List<String> evaluationWeaknesses,
        List<String> evaluationMissingPoints,
        String candidateName,
        String resumeSummary,
        List<ProjectContext> projects,
        String positionTitle,
        String positionDescription,
        InterviewProgress progress,
        Set<DecisionAction> allowedActions,
        List<DecisionPlanItem> unusedPlanItems) {
    public DecisionContext {
        projects = projects == null ? List.of() : List.copyOf(projects);
    }
}
