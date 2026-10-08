package com.linj6.interviewagent.ai.api.focus;

import com.linj6.interviewagent.ai.api.ProjectContext;
import java.util.List;
import lombok.Builder;

/**
 * Focus Selector 输入。Candidate/Profile 中的候选人资料是候选人经历事实来源；岗位资料来自 Position，只提供岗位要求，不证明候选人经历。
 * focus 只决定考察方向，不提供事实权限；已选 MAIN focus 也不是候选人事实来源。此输入不包含当前 Turn 的 Answer 或 Evaluation。
 * 背景字符串由业务资料透传；API 不统一校验其 null 或空白语义。
 *
 * @param selectedMainFocuses 当前 Interview 已选的 MAIN focus，用于避免重复；构造参数为 null 时规范化为空列表，不含 FOLLOW_UP focus。
 */
@Builder
public record FocusSelectionContext(
        String candidateName,
        String resumeSummary,
        List<ProjectContext> projects,
        String positionTitle,
        String positionDescription,
        List<String> selectedMainFocuses) {
    public FocusSelectionContext {
        projects = projects == null ? List.of() : List.copyOf(projects);
        selectedMainFocuses = selectedMainFocuses == null ? List.of() : List.copyOf(selectedMainFocuses);
    }
}
