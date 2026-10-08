package com.linj6.interviewagent.evaluation.api.evaluation;

import java.math.BigDecimal;
import java.util.List;
import lombok.Builder;

/** 创建不可变正式 Evaluation 的跨模块命令。 */
@Builder
public record EvaluationCommand(
        Long interviewId,
        Long questionId,
        Long answerId,
        Long profileId,
        BigDecimal overallScore10,
        BigDecimal overallScore100,
        List<Feedback> strengths,
        List<Feedback> weaknesses,
        List<MissingPoint> missingPoints,
        ReferenceAnswer referenceAnswer,
        String promptVersion,
        String evaluatorVersion,
        String model,
        Integer inputTokens,
        Integer outputTokens,
        Integer totalTokens,
        Long latencyMs,
        List<DimensionScore> dimensions) {}
