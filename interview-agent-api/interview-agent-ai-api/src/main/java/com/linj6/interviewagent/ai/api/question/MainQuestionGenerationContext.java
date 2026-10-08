package com.linj6.interviewagent.ai.api.question;

import com.linj6.interviewagent.ai.api.ProjectContext;
import java.util.List;
import lombok.Builder;

/**
 * MAIN 问题生成输入。Candidate/Profile 中的候选人资料是候选人经历事实来源；岗位资料只提供岗位要求，不证明候选人经历。
 * focus 是当前 PlanItem 的 MAIN 考察目标；area / focus / reason 仅决定方向，不提供候选人事实权限。
 * V1 Opening/Turn/Skip 提供完整 PlanItem；旧构造入口仅保留兼容，不用于 V1 业务调用。
 * previousMainQuestions 为 null 表示历史未提供；显式空列表表示已提供且没有历史 MAIN。
 * 除 focus 外，背景字符串由业务资料透传；API 不统一校验其 null 或空白语义。
 *
 * @param focus 非空的单一 MAIN 考察目标。
 */
@Builder
public record MainQuestionGenerationContext(
        String focus,
        String candidateName,
        String resumeSummary,
        List<ProjectContext> projects,
        String positionTitle,
        String positionDescription,
        String area,
        String reason,
        List<String> previousMainQuestions) {
    /** 分阶段迁移保留的旧调用入口；完整 V1 输入使用 builder 或完整构造。 */
    public MainQuestionGenerationContext(
            String focus,
            String candidateName,
            String resumeSummary,
            List<ProjectContext> projects,
            String positionTitle,
            String positionDescription) {
        this(focus, candidateName, resumeSummary, projects, positionTitle, positionDescription, null, null, null);
    }

    public MainQuestionGenerationContext {
        projects = projects == null ? List.of() : List.copyOf(projects);
        previousMainQuestions = previousMainQuestions == null ? null : List.copyOf(previousMainQuestions);
        if (focus == null || focus.isBlank()) throw new IllegalArgumentException("focus 不能为空");
    }
    /** 旧入口只有 focus，不将缺失字段当成完整 PlanItem。 */
    public boolean hasCompletePlanItem() {
        return area != null && !area.isBlank() && reason != null && !reason.isBlank();
    }
}
