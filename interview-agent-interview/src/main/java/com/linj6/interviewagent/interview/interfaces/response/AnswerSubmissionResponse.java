package com.linj6.interviewagent.interview.interfaces.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.linj6.interviewagent.ai.api.evaluation.FeedbackProposal;
import com.linj6.interviewagent.ai.api.evaluation.MissingPointProposal;
import com.linj6.interviewagent.ai.api.evaluation.ReferenceAnswerProposal;
import com.linj6.interviewagent.ai.api.evaluation.ValidatedEvaluationResult;
import java.math.BigDecimal;
import java.util.List;

public record AnswerSubmissionResponse(AnswerResponse answer, EvaluationResponse evaluation) {
    public static AnswerSubmissionResponse from(
            Long answerId, String content, Long evaluationId, ValidatedEvaluationResult result) {
        List<DimensionScoreResponse> dimensions = result.dimensions().stream()
                .map(value -> new DimensionScoreResponse(
                        value.dimensionCode(),
                        value.dimensionName(),
                        value.applicability().name(),
                        value.score(),
                        value.weight(),
                        value.weightedScore(),
                        value.scoreExplanation(),
                        value.evidence().stream()
                                .map(evidence -> new EvidenceResponse(
                                        evidence.type().name(), evidence.quote(), evidence.explanation()))
                                .toList()))
                .toList();
        return new AnswerSubmissionResponse(
                new AnswerResponse(answerId, content),
                new EvaluationResponse(
                        evaluationId,
                        result.profileId(),
                        result.overallScore10(),
                        result.overallScore100(),
                        dimensions,
                        result.strengths(),
                        result.weaknesses(),
                        result.missingPoints(),
                        result.referenceAnswer(),
                        result.promptVersion(),
                        result.evaluatorVersion(),
                        result.model()));
    }

    public record AnswerResponse(@JsonFormat(shape = JsonFormat.Shape.STRING) Long id, String content) {}

    public record EvaluationResponse(
            @JsonFormat(shape = JsonFormat.Shape.STRING) Long id,
            @JsonFormat(shape = JsonFormat.Shape.STRING) Long profileId,
            BigDecimal overallScore10,
            BigDecimal overallScore100,
            List<DimensionScoreResponse> dimensions,
            List<FeedbackProposal> strengths,
            List<FeedbackProposal> weaknesses,
            List<MissingPointProposal> missingPoints,
            ReferenceAnswerProposal referenceAnswer,
            String promptVersion,
            String evaluatorVersion,
            String model) {}

    public record DimensionScoreResponse(
            String dimensionCode,
            String dimensionName,
            String applicability,
            BigDecimal score,
            BigDecimal weight,
            BigDecimal weightedScore,
            String scoreExplanation,
            List<EvidenceResponse> evidence) {}

    public record EvidenceResponse(String type, String quote, String explanation) {}
}
