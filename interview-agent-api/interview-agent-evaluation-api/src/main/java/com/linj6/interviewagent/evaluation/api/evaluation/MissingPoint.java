package com.linj6.interviewagent.evaluation.api.evaluation;

import java.util.List;

/** 正式 Evaluation 的缺失要点。 */
public record MissingPoint(
        String description, String importance, String explanation, List<String> relatedDimensionCodes) {}
