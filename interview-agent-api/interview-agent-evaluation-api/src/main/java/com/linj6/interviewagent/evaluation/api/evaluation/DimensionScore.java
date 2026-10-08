package com.linj6.interviewagent.evaluation.api.evaluation;

import java.math.BigDecimal;
import java.util.List;

/** 正式 Evaluation 的维度评分快照。 */
public record DimensionScore(
        String dimensionCode,
        String dimensionName,
        String applicability,
        BigDecimal score,
        BigDecimal weight,
        BigDecimal weightedScore,
        String scoreExplanation,
        List<Evidence> evidence) {}
