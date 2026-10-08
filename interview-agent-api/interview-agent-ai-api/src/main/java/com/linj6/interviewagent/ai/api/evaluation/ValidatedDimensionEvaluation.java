package com.linj6.interviewagent.ai.api.evaluation;

import java.math.BigDecimal;
import java.util.List;

/**
 * 单个 Profile 维度的 Java 校验结果。
 *
 * @param score NOT_APPLICABLE 时为 null。
 * @param weightedScore Java 计算的维度加权分；NOT_APPLICABLE 时为 null。
 * @param notApplicableReason NOT_APPLICABLE 时复用 Proposal 的 scoreExplanation；APPLICABLE 时为 null。
 * @param evidence NOT_APPLICABLE 时为空列表；APPLICABLE 时为已解析的证据。
 */
public record ValidatedDimensionEvaluation(
        String dimensionCode,
        String dimensionName,
        Applicability applicability,
        BigDecimal score,
        BigDecimal weight,
        BigDecimal weightedScore,
        String scoreExplanation,
        String notApplicableReason,
        List<ValidatedEvidence> evidence) {}
