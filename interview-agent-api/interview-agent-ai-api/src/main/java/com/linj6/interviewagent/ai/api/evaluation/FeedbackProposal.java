package com.linj6.interviewagent.ai.api.evaluation;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/**
 * Evaluation 的 Strength 或 Weakness 条目。
 *
 * @param description 该反馈的具体内容。
 * @param relatedDimensionCodes 至少一个不重复的 APPLICABLE Profile dimension code。
 */
public record FeedbackProposal(
        @JsonPropertyDescription("Strength 或 Weakness 的具体内容。") String description,
        @JsonPropertyDescription("必须包含至少一个不重复的 APPLICABLE Profile dimension code。")
                List<String> relatedDimensionCodes) {}
