package com.linj6.interviewagent.ai.api.planner;

import java.util.List;

/** 已解析临时引用；initialItemIndex 是本次结果列表中的位置，不是持久化标识。 */
public record ValidatedPlanResult(List<Item> items, int initialItemIndex) {
    public ValidatedPlanResult {
        items = List.copyOf(items);
    }

    public record Item(String area, String focus, String reason) {}
}
