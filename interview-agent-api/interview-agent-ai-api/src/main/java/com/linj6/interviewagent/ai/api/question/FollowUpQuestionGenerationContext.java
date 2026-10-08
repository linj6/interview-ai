package com.linj6.interviewagent.ai.api.question;

import com.linj6.interviewagent.ai.api.ProjectContext;
import java.util.List;
import lombok.Builder;

/**
 * FOLLOW_UP 最小生成输入：Decision 已选定的单一目标、当前问答、候选人及岗位背景；不消费 Evaluation。
 * currentAnswerContent 是本轮陈述的事实来源；currentQuestionContent 仅提供考察上下文，followUpTarget 仅提供追问方向。
 * Candidate/Profile 提供经历背景，但不能替代当前 Answer 确认本轮事实，也不能覆盖明确否定或将假设、建议写成历史实现。
 * 未提及的信息应开放询问；明确否定的信息不得预设发生；假设方案只能作为假设讨论；明确确认的信息才可归因，且不得补全细节或跨场景组合。
 * 除 followUpTarget 外，其他字符串由业务资料透传；API 不统一校验其 null 或空白语义。
 *
 * @param followUpTarget Java 从 Decision targetRef 解析出的当前 Evaluation Missing Point 原始描述；仅表示唯一追问方向，不是候选人事实，且必须非空。
 * @param currentQuestionContent 当前被回答的 Question 正文；不是 Candidate Fact Source。
 * @param currentAnswerContent 该 Question 对应的 Answer 正文；候选人本轮自述，不代表外部核实。
 */
@Builder
public record FollowUpQuestionGenerationContext(
        String followUpTarget,
        String currentQuestionContent,
        String currentAnswerContent,
        String candidateName,
        String resumeSummary,
        List<ProjectContext> projects,
        String positionTitle,
        String positionDescription) {
    public FollowUpQuestionGenerationContext {
        projects = projects == null ? List.of() : List.copyOf(projects);
        if (followUpTarget == null || followUpTarget.isBlank())
            throw new IllegalArgumentException("followUpTarget 不能为空");
    }
}
