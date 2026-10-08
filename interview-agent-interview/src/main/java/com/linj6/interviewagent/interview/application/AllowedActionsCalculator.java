package com.linj6.interviewagent.interview.application;

import com.linj6.interviewagent.ai.api.decision.DecisionAction;
import com.linj6.interviewagent.ai.api.decision.InterviewProgress;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class AllowedActionsCalculator {
    public Set<DecisionAction> calculate(InterviewProgress progress) {
        if (progress == null
                || progress.mainQuestionCount() < 1
                || progress.followUpCount() < 0
                || progress.maxMainQuestions() < 1
                || progress.maxFollowUpsPerMainQuestion() < 1) {
            throw new IllegalArgumentException("Invalid interview progress");
        }
        boolean mainLimitReached = progress.mainQuestionCount() >= progress.maxMainQuestions();
        boolean followUpLimitReached = progress.followUpCount() >= progress.maxFollowUpsPerMainQuestion();
        if (mainLimitReached && followUpLimitReached) {
            return Set.of(DecisionAction.END);
        }
        if (mainLimitReached) {
            return EnumSet.of(DecisionAction.FOLLOW_UP, DecisionAction.END);
        }
        if (followUpLimitReached) {
            return Set.of(DecisionAction.NEXT_QUESTION);
        }
        return EnumSet.of(DecisionAction.FOLLOW_UP, DecisionAction.NEXT_QUESTION);
    }
}
