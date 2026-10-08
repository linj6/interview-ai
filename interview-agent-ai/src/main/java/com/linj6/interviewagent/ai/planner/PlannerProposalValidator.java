package com.linj6.interviewagent.ai.planner;

import com.linj6.interviewagent.ai.api.planner.PlannerContext;
import com.linj6.interviewagent.ai.api.planner.ValidatedPlanResult;
import java.util.*;

/** 确定性结构校验；不宣称保证语义去重。 */
public class PlannerProposalValidator {
    public ValidatedPlanResult validate(PlannerContext context, PlannerProposal proposal) {
        if (context == null
                || context.allowedInterviewModes().isEmpty()
                || context.allowedKnowledgeAreas().isEmpty()
                || context.mode() == null
                || !context.allowedInterviewModes().contains(context.mode())
                || ("TECHNICAL_FOCUS".equals(context.mode()) != (context.focusArea() != null))
                || (context.focusArea() != null
                        && !context.allowedKnowledgeAreas().contains(context.focusArea()))
                || context.maxMainQuestions() < 1) throw invalid();
        if (proposal == null
                || proposal.items() == null
                || proposal.items().size() < context.maxMainQuestions()
                || (long) proposal.items().size() > (long) context.maxMainQuestions() + 3) throw invalid();
        var refs = new HashSet<String>();
        var items = new ArrayList<ValidatedPlanResult.Item>();
        int initial = -1;
        for (var item : proposal.items()) {
            if (item == null
                    || item.planItemRef() == null
                    || !item.planItemRef().matches("P[1-9][0-9]*")
                    || !refs.add(item.planItemRef())
                    || item.area() == null
                    || !context.allowedKnowledgeAreas().contains(item.area())
                    || item.focus() == null
                    || item.focus().isBlank()
                    || item.reason() == null
                    || item.reason().isBlank()
                    || ("TECHNICAL_FOCUS".equals(context.mode())
                            && !context.focusArea().equals(item.area()))) throw invalid();
            if (item.planItemRef().equals(proposal.initialPlanItemRef())) initial = items.size();
            items.add(new ValidatedPlanResult.Item(
                    item.area(), item.focus().trim(), item.reason().trim()));
        }
        if (initial < 0) throw invalid();
        return new ValidatedPlanResult(items, initial);
    }

    private IllegalArgumentException invalid() {
        return new IllegalArgumentException("Invalid Planner context or proposal");
    }
}
