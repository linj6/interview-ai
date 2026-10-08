package com.linj6.interviewagent.ai.api.planner;

import com.linj6.interviewagent.ai.api.ProjectContext;
import java.util.List;

/** 仅使用现有候选人摘要和岗位事实的单次 Planner 输入。 */
public record PlannerContext(
        Long interviewId,
        String mode,
        String focusArea,
        int maxMainQuestions,
        String candidateName,
        String resumeSummary,
        List<ProjectContext> projects,
        String positionTitle,
        String positionDescription,
        List<String> allowedInterviewModes,
        List<String> allowedKnowledgeAreas) {
    public PlannerContext {
        projects = projects == null ? List.of() : List.copyOf(projects);
        allowedInterviewModes = List.copyOf(allowedInterviewModes);
        allowedKnowledgeAreas = List.copyOf(allowedKnowledgeAreas);
    }
}
