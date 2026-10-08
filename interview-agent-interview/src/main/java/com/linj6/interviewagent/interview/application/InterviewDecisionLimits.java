package com.linj6.interviewagent.interview.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public record InterviewDecisionLimits(int maxMainQuestions, int maxFollowUpsPerMainQuestion) {
    public InterviewDecisionLimits(
            @Value("${app.interview.max-main-questions:5}") int maxMainQuestions,
            @Value("${app.interview.max-follow-ups-per-main-question:2}") int maxFollowUpsPerMainQuestion) {
        if (maxMainQuestions < 1 || maxFollowUpsPerMainQuestion < 1) {
            throw new IllegalArgumentException("Invalid Decision limits");
        }
        this.maxMainQuestions = maxMainQuestions;
        this.maxFollowUpsPerMainQuestion = maxFollowUpsPerMainQuestion;
    }
}
