package com.linj6.interviewagent.interview.domain;

import java.util.Objects;

/** 无状态的正式计划项；ID 是数据库标识，不是 AI 临时引用。 */
public record PlanItem(Long id, Long planId, KnowledgeArea area, String focus, String reason) {
    public PlanItem {
        if (id == null || id <= 0 || planId == null || planId <= 0) {
            throw new IllegalArgumentException("PlanItem requires positive persistent IDs");
        }
        Objects.requireNonNull(area, "area");
        if (focus == null || focus.isBlank() || reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("PlanItem focus and reason must not be blank");
        }
    }
}
