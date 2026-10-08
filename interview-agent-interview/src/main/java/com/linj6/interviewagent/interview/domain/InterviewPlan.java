package com.linj6.interviewagent.interview.domain;

import java.util.HashSet;
import java.util.List;

/** Interview 的无状态计划聚合，initialPlanItemId 指向聚合内的真实 ID。 */
public record InterviewPlan(Long id, Long interviewId, Long initialPlanItemId, List<PlanItem> items) {
    public InterviewPlan {
        if (id == null || id <= 0 || interviewId == null || interviewId <= 0) {
            throw new IllegalArgumentException("Plan requires positive persistent IDs");
        }
        items = List.copyOf(items);
        var ids = new HashSet<Long>();
        for (PlanItem item : items) {
            if (!id.equals(item.planId()) || !ids.add(item.id())) {
                throw new IllegalArgumentException("PlanItem ownership or duplicate ID is invalid");
            }
        }
        if (!ids.contains(initialPlanItemId)) {
            throw new IllegalArgumentException("initialPlanItemId must belong to this Plan");
        }
    }

    /** 保存时按当前面试的 MAIN 上限校验数量。 */
    public void validateItemCount(int maxMainQuestions) {
        if (maxMainQuestions < 1
                || items.size() < maxMainQuestions
                || (long) items.size() > (long) maxMainQuestions + 3) {
            throw new IllegalArgumentException(
                    "PlanItem count must be between maxMainQuestions and maxMainQuestions + 3");
        }
    }
}
