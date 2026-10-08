package com.linj6.interviewagent.ai.api.evaluation;

import java.math.BigDecimal;

/**
 * Evaluation Profile 中传给 Evaluator 的单个维度配置。
 *
 * @param evaluationCriteria 该维度的评价标准。
 * @param scoreGuidance 对应的分数指导。
 * @param weight Java 计算加权分与总分使用的 Profile 权重，不提供给模型。
 * @param naAllowed Profile 是否允许该维度返回 NOT_APPLICABLE；允许不代表回答表现差时可用 N/A。
 * @param sortOrder Profile 中的顺序，用于组装 Validated Result。
 */
public record EvaluationDimensionContext(
        String code,
        String name,
        String evaluationCriteria,
        String scoreGuidance,
        BigDecimal weight,
        Boolean naAllowed,
        Integer sortOrder) {}
