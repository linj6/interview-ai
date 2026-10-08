package com.linj6.interviewagent.ai.planner;

import java.util.List;

/** LLM 内部契约；临时引用不进入业务与 HTTP 契约。 */
public record PlannerProposal(List<Item> items, String initialPlanItemRef) {
    public record Item(String planItemRef, String area, String focus, String reason) {}
}
