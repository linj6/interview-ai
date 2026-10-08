package com.linj6.interviewagent.interview.application;

import com.linj6.interviewagent.interview.domain.InterviewPlan;
import com.linj6.interviewagent.interview.domain.PlanItem;
import com.linj6.interviewagent.interview.domain.QuestionType;
import com.linj6.interviewagent.interview.infrastructure.persistence.repository.QuestionSnapshot;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Turn/Skip 从正式 MAIN 关联推导使用事实；不引入 PlanItem 状态。 */
record InterviewPlanFacts(InterviewPlan plan, List<QuestionSnapshot> questions) {
    InterviewPlanFacts {
        if (plan == null) throw new IllegalStateException("Interview Plan is missing");
        questions = List.copyOf(questions);
        var used = new HashSet<Long>();
        for (var question : questions) {
            if (!Objects.equals(question.interviewId(), plan.interviewId()))
                throw new IllegalStateException("Question does not belong to Interview Plan");
            requireItem(plan, question.planItemId());
            if (question.type() == QuestionType.MAIN) {
                if (question.parentQuestionId() != null || !used.add(question.planItemId()))
                    throw new IllegalStateException("Invalid or duplicate MAIN PlanItem association");
            } else {
                var main = questions.stream()
                        .filter(value -> value.id().equals(question.parentQuestionId()))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("FOLLOW_UP MAIN is missing"));
                if (main.type() != QuestionType.MAIN
                        || main.parentQuestionId() != null
                        || !Objects.equals(main.planItemId(), question.planItemId()))
                    throw new IllegalStateException("FOLLOW_UP must inherit MAIN PlanItem");
            }
        }
    }

    private static PlanItem requireItem(InterviewPlan plan, Long id) {
        return plan.items().stream()
                .filter(item -> Objects.equals(item.id(), id) && item.planId().equals(plan.id()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("PlanItem does not belong to Interview Plan"));
    }

    PlanItem item(Long id) {
        return requireItem(plan, id);
    }

    List<PlanItem> unused() {
        return plan.items().stream()
                .filter(item -> questions.stream()
                        .noneMatch(question -> question.type() == QuestionType.MAIN
                                && item.id().equals(question.planItemId())))
                .toList();
    }

    PlanItem requireUnused(Long id) {
        return unused().stream()
                .filter(item -> item.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("PlanItem is unavailable or already used"));
    }

    List<String> previousMainQuestions() {
        return questions.stream()
                .filter(question -> question.type() == QuestionType.MAIN)
                .map(QuestionSnapshot::content)
                .toList();
    }
}
