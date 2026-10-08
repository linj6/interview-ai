package com.linj6.interviewagent.ai.decision;

import com.linj6.interviewagent.ai.api.decision.DecisionContext;
import com.linj6.interviewagent.ai.api.decision.DecisionPlanItem;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

/** One invocation-scoped target collection shared by Decision prompt rendering and resolution. */
public record DecisionTargets(List<Target> values, List<PlanTarget> planItems) {
    public DecisionTargets {
        values = List.copyOf(values);
        planItems = List.copyOf(planItems);
    }

    public static DecisionTargets from(DecisionContext context) {
        List<Target> targets = new ArrayList<>();
        add(targets, "M", context.evaluationMissingPoints());
        List<PlanTarget> plans = new ArrayList<>();
        if (context.unusedPlanItems() != null) {
            for (DecisionPlanItem item : context.unusedPlanItems()) {
                if (item == null
                        || item.id() == null
                        || plans.stream().anyMatch(value -> value.item().id().equals(item.id())))
                    throw new DecisionValidationException("Invalid unused PlanItem collection");
                plans.add(new PlanTarget("P" + (plans.size() + 1), item));
            }
        }
        return new DecisionTargets(targets, plans);
    }

    private static void add(List<Target> targets, String prefix, List<String> descriptions) {
        if (descriptions == null) return;
        IntStream.range(0, descriptions.size()).forEach(i -> {
            String description = descriptions.get(i);
            if (description != null && !description.isBlank()) {
                targets.add(new Target(prefix + (i + 1), description));
            }
        });
    }

    public String resolve(String ref) {
        return values.stream()
                .filter(target -> target.ref().equals(ref))
                .findFirst()
                .map(Target::description)
                .orElse(null);
    }

    public String render() {
        if (values.isEmpty()) return "（无可用追问目标）";
        return values.stream()
                .map(target -> target.ref() + "：" + target.description())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }

    public Long resolvePlanItem(String ref) {
        return planItems.stream()
                .filter(value -> value.ref().equals(ref))
                .map(value -> value.item().id())
                .findFirst()
                .orElse(null);
    }

    public String renderPlanItems() {
        return planItems.stream()
                .map(value -> value.ref() + "：area=" + value.item().area() + "；focus="
                        + value.item().focus() + "；reason=" + value.item().reason())
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    public record PlanTarget(String ref, DecisionPlanItem item) {}

    public record Target(String ref, String description) {}
}
