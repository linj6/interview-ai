package com.linj6.interviewagent.ai.api.evaluation;

import java.math.BigDecimal;
import java.util.List;

/**
 * 经 Java 校验、计算后的 Evaluation 结果及调用追踪信息。
 *
 * @param profileId 当前 Question 绑定的 Evaluation Profile ID。
 * @param overallScore10 按适用维度权重归一化计算并舍入后的 0–10 总分。
 * @param overallScore100 由已舍入的 overallScore10 乘以 10 得到。
 * @param dimensions 按 Profile sortOrder 排列的维度结果；分数和加权分由 Java 计算。
 * @param promptVersion 本次使用的 Prompt 版本标识。
 * @param evaluatorVersion 本次使用的 Evaluator 版本标识。
 * @param inputTokens 模型 Usage 提供的输入 Token 数；Usage 缺失时为 null。
 * @param outputTokens 模型 Usage 提供的输出 Token 数；Usage 缺失时为 null。
 * @param totalTokens 模型 Usage 提供的总 Token 数；Usage 缺失时为 null。
 * @param latencyMs 本次模型评价调用耗时毫秒。
 */
public record ValidatedEvaluationResult(
        Long profileId,
        BigDecimal overallScore10,
        BigDecimal overallScore100,
        List<ValidatedDimensionEvaluation> dimensions,
        List<FeedbackProposal> strengths,
        List<FeedbackProposal> weaknesses,
        List<MissingPointProposal> missingPoints,
        ReferenceAnswerProposal referenceAnswer,
        String promptVersion,
        String evaluatorVersion,
        String model,
        Integer inputTokens,
        Integer outputTokens,
        Integer totalTokens,
        Long latencyMs) {}
