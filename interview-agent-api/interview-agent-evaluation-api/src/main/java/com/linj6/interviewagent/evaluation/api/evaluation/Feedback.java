package com.linj6.interviewagent.evaluation.api.evaluation;

import java.util.List;

/** 正式 Evaluation 的优势或不足反馈。 */
public record Feedback(String description, List<String> relatedDimensionCodes) {}
