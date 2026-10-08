package com.linj6.interviewagent.interview.interfaces.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record InterviewReviewResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING) Long interviewId,
        Overview overview,
        List<MainQuestionReview> questions) {
    public record Overview(
            LocalDateTime startedAt,
            LocalDateTime completedAt,
            long durationSeconds,
            int mainQuestionCount,
            int followUpQuestionCount,
            int answeredCount,
            int skippedCount,
            int unansweredCount) {}

    public enum Result {
        ANSWERED,
        SKIPPED,
        UNANSWERED
    }

    public record MainQuestionReview(
            @JsonFormat(shape = JsonFormat.Shape.STRING) Long questionId,
            String content,
            Result result,
            String answer,
            EvaluationReview evaluation,
            List<FollowUpQuestionReview> followUps) {}

    public record FollowUpQuestionReview(
            @JsonFormat(shape = JsonFormat.Shape.STRING) Long questionId,
            String content,
            Result result,
            String answer,
            EvaluationReview evaluation) {}

    public record EvaluationReview(
            BigDecimal overallScore,
            List<String> strengths,
            List<String> weaknesses,
            List<MissingPointReview> missingPoints,
            ReferenceAnswerReview referenceAnswer,
            List<DimensionScoreReview> dimensionScores) {}

    public record MissingPointReview(String description, String importance, String explanation) {}

    public record ReferenceAnswerReview(String answerForm, List<String> keyPoints, String content) {}

    public record DimensionScoreReview(String name, BigDecimal score, String explanation) {}
}
