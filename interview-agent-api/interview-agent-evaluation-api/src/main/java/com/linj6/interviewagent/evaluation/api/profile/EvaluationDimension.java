package com.linj6.interviewagent.evaluation.api.profile;

import java.math.BigDecimal;

/** Evaluation Profile 中一个维度的跨模块只读契约。 */
public record EvaluationDimension(
        String code,
        String name,
        String evaluationCriteria,
        String scoreGuidance,
        BigDecimal weight,
        Boolean naAllowed,
        Integer sortOrder) {}
