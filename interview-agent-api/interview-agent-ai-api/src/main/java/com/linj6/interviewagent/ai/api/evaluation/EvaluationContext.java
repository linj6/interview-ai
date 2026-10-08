package com.linj6.interviewagent.ai.api.evaluation;

import com.linj6.interviewagent.ai.api.ProjectContext;
import java.util.List;
import lombok.Builder;

/**
 * Evaluator 输入，由 Interview Application 提供当前问答、Question 绑定的 Profile 与候选人/岗位资料。
 * 候选人和岗位背景字段按业务资料透传；API 不统一校验其 null 或空白语义。
 *
 * @param interviewId 本次 Interview ID。
 * @param questionId 当前被评价的 Question ID。
 * @param answerId 当前已持久化 Answer 的 ID。
 * @param profileId 该 Question 绑定的 Evaluation Profile ID。
 * @param dimensions 此 Profile 的完整评价维度集合；模型必须对每个维度恰好返回一次。weight 只供 Java 确定性计算，不提供给模型。
 */
@Builder
public record EvaluationContext(
        Long interviewId,
        Long questionId,
        Long answerId,
        Long profileId,
        String questionContent,
        String answerContent,
        String candidateName,
        String resumeSummary,
        List<ProjectContext> projects,
        String positionTitle,
        String positionDescription,
        List<EvaluationDimensionContext> dimensions) {
    public EvaluationContext {
        projects = projects == null ? List.of() : List.copyOf(projects);
    }
}
