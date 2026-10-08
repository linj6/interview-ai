package com.linj6.interviewagent.evaluation.api.evaluation;

import java.math.BigDecimal;
import java.util.List;

/** 正式 Evaluation 的业务数据快照。 */
public record EvaluationDTO(
        Long evaluationId,
        Long interviewId,
        Long questionId,
        Long answerId,
        BigDecimal overallScore,
        List<Feedback> strengths,
        List<Feedback> weaknesses,
        List<MissingPoint> missingPoints,
        ReferenceAnswer referenceAnswer,
        List<DimensionScoreDTO> dimensionScores) {}
