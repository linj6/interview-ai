package com.linj6.interviewagent.ai.evaluator;

import com.linj6.interviewagent.ai.api.evaluation.Applicability;
import com.linj6.interviewagent.ai.api.evaluation.DimensionEvaluationProposal;
import com.linj6.interviewagent.ai.api.evaluation.EvaluationContext;
import com.linj6.interviewagent.ai.api.evaluation.EvaluationDimensionContext;
import com.linj6.interviewagent.ai.api.evaluation.EvaluationProposal;
import com.linj6.interviewagent.ai.api.evaluation.EvidenceProposal;
import com.linj6.interviewagent.ai.api.evaluation.EvidenceType;
import com.linj6.interviewagent.ai.api.evaluation.FeedbackProposal;
import com.linj6.interviewagent.ai.api.evaluation.MissingPointProposal;
import com.linj6.interviewagent.ai.api.evaluation.ValidatedDimensionEvaluation;
import com.linj6.interviewagent.ai.api.evaluation.ValidatedEvaluationResult;
import com.linj6.interviewagent.ai.api.evaluation.ValidatedEvidence;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public class EvaluationProposalValidator {
    private static final BigDecimal SCORE_STEP = new BigDecimal("0.5");
    private static final Pattern EVIDENCE_REF_PATTERN = Pattern.compile("E[1-9][0-9]*");

    public ValidatedEvaluationResult validateAndCalculate(
            EvaluationContext context,
            List<AnswerFragment> answerFragments,
            EvaluationProposal proposal,
            String promptVersion,
            String evaluatorVersion,
            String model,
            Integer inputTokens,
            Integer outputTokens,
            Integer totalTokens,
            Long latencyMs) {
        require(proposal != null, "EvaluationProposal must not be null");
        require(answerFragments != null, "Answer fragments must not be null");
        Map<String, EvaluationDimensionContext> configured = configuredDimensions(context.dimensions());
        List<DimensionEvaluationProposal> proposed =
                requiredList(proposal.dimensionEvaluations(), "dimensionEvaluations");
        require(proposed.size() == configured.size(), "Proposal dimension set does not match Profile");

        Map<String, DimensionEvaluationProposal> byCode = new HashMap<>();
        for (DimensionEvaluationProposal dimension : proposed) {
            require(dimension != null && !blank(dimension.dimensionCode()), "Dimension code is required");
            require(
                    configured.containsKey(dimension.dimensionCode()),
                    "Unexpected dimension: " + dimension.dimensionCode());
            require(
                    byCode.put(dimension.dimensionCode(), dimension) == null,
                    "Duplicate dimension: " + dimension.dimensionCode());
        }
        require(byCode.keySet().equals(configured.keySet()), "Proposal is missing Profile dimensions");

        List<ValidatedDimensionEvaluation> validated = new ArrayList<>();
        Set<String> applicableCodes = new HashSet<>();
        BigDecimal applicableWeightSum = BigDecimal.ZERO;
        BigDecimal weightedSum = BigDecimal.ZERO;
        for (EvaluationDimensionContext config : context.dimensions().stream()
                .sorted(java.util.Comparator.comparingInt(EvaluationDimensionContext::sortOrder))
                .toList()) {
            DimensionEvaluationProposal dimension = byCode.get(config.code());
            require(dimension.applicability() != null, "Applicability is required for " + config.code());
            require(!blank(dimension.scoreExplanation()), "Score explanation is required for " + config.code());
            List<EvidenceProposal> evidence = requiredList(dimension.evidence(), "evidence");
            List<ValidatedEvidence> resolvedEvidence = List.of();
            BigDecimal weightedScore = null;
            if (dimension.applicability() == Applicability.APPLICABLE) {
                validateScore(dimension.score(), config.code());
                require(
                        evidence.size() >= 1 && evidence.size() <= 3,
                        "Applicable dimension must contain 1 to 3 evidence items: " + config.code());
                resolvedEvidence = evidence.stream()
                        .map(item -> validateEvidence(item, answerFragments, config.code()))
                        .toList();
                applicableCodes.add(config.code());
                applicableWeightSum = applicableWeightSum.add(config.weight());
                BigDecimal rawWeightedScore = dimension.score().multiply(config.weight());
                weightedSum = weightedSum.add(rawWeightedScore);
                // 历史维度加权分固定保留四位；总分继续使用未舍入值计算，避免累计舍入误差。
                weightedScore = rawWeightedScore.setScale(4, RoundingMode.HALF_UP);
            } else {
                require(config.naAllowed(), "Profile does not allow N/A for " + config.code());
                require(dimension.score() == null, "N/A dimension must not contain score: " + config.code());
                require(evidence.isEmpty(), "N/A dimension must not contain evidence: " + config.code());
            }
            validated.add(new ValidatedDimensionEvaluation(
                    config.code(),
                    config.name(),
                    dimension.applicability(),
                    dimension.score(),
                    config.weight(),
                    weightedScore,
                    dimension.scoreExplanation(),
                    dimension.applicability() == Applicability.NOT_APPLICABLE ? dimension.scoreExplanation() : null,
                    resolvedEvidence));
        }
        require(!applicableCodes.isEmpty(), "At least one dimension must be applicable");
        require(applicableWeightSum.compareTo(BigDecimal.ZERO) > 0, "Applicable dimension weight sum must be positive");

        List<FeedbackProposal> strengths = validateFeedback(proposal.strengths(), 3, applicableCodes, "strengths");
        List<FeedbackProposal> weaknesses = validateFeedback(proposal.weaknesses(), 3, applicableCodes, "weaknesses");
        List<MissingPointProposal> missingPoints = validateMissingPoints(proposal.missingPoints(), applicableCodes);
        require(proposal.referenceAnswer() != null, "Reference answer is required");
        require(proposal.referenceAnswer().answerForm() != null, "Reference answer form is required");
        require(!blank(proposal.referenceAnswer().content()), "Reference answer content is required");
        require(proposal.referenceAnswer().keyPoints() != null, "Reference answer keyPoints are required");

        // N/A 维度从分子、分母同时排除，并只在最终总分处 HALF_UP 舍入。
        BigDecimal overall10 = weightedSum
                .divide(applicableWeightSum, 16, RoundingMode.HALF_UP)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal overall100 = overall10.multiply(BigDecimal.TEN).setScale(2, RoundingMode.HALF_UP);
        return new ValidatedEvaluationResult(
                context.profileId(),
                overall10,
                overall100,
                List.copyOf(validated),
                strengths,
                weaknesses,
                missingPoints,
                proposal.referenceAnswer(),
                promptVersion,
                evaluatorVersion,
                model,
                inputTokens,
                outputTokens,
                totalTokens,
                latencyMs);
    }

    private Map<String, EvaluationDimensionContext> configuredDimensions(List<EvaluationDimensionContext> dimensions) {
        require(dimensions != null && !dimensions.isEmpty(), "Evaluation Profile must contain dimensions");
        Map<String, EvaluationDimensionContext> configured = new HashMap<>();
        for (EvaluationDimensionContext dimension : dimensions) {
            require(dimension != null && !blank(dimension.code()), "Profile dimension code is required");
            require(
                    dimension.weight() != null && dimension.weight().compareTo(BigDecimal.ZERO) >= 0,
                    "Profile dimension weight must not be negative: " + dimension.code());
            require(
                    configured.put(dimension.code(), dimension) == null,
                    "Duplicate Profile dimension: " + dimension.code());
        }
        return configured;
    }

    private void validateScore(BigDecimal score, String code) {
        require(score != null, "Applicable dimension score is required: " + code);
        require(
                score.compareTo(BigDecimal.ZERO) >= 0 && score.compareTo(BigDecimal.TEN) <= 0,
                "Score must be between 0 and 10: " + code);
        require(score.remainder(SCORE_STEP).compareTo(BigDecimal.ZERO) == 0, "Score must use a 0.5 step: " + code);
    }

    private ValidatedEvidence validateEvidence(EvidenceProposal evidence, List<AnswerFragment> fragments, String code) {
        require(evidence != null && evidence.type() != null, "Evidence type is required: " + code);
        require(!blank(evidence.explanation()), "Evidence explanation is required: " + code);
        if (evidence.type() == EvidenceType.ANSWER_QUOTE) {
            String ref = evidence.evidenceRef().orElse(null);
            require(
                    ref != null && EVIDENCE_REF_PATTERN.matcher(ref).matches(),
                    "ANSWER_QUOTE requires a valid evidenceRef: " + code);
            AnswerFragment fragment = fragments.stream()
                    .filter(value -> value.evidenceRef().equals(ref))
                    .findFirst()
                    .orElseThrow(() -> new EvaluationValidationException("Unknown evidenceRef: " + code));
            return new ValidatedEvidence(evidence.type(), fragment.text(), evidence.explanation());
        } else {
            require(evidence.evidenceRef().isEmpty(), "OVERALL_OBSERVATION evidenceRef must be null: " + code);
            return new ValidatedEvidence(evidence.type(), null, evidence.explanation());
        }
    }

    private List<FeedbackProposal> validateFeedback(
            List<FeedbackProposal> feedback, int max, Set<String> applicable, String field) {
        List<FeedbackProposal> values = requiredList(feedback, field);
        require(values.size() <= max, field + " exceeds limit " + max);
        for (FeedbackProposal item : values) {
            require(item != null && !blank(item.description()), field + " description is required");
            validateRelatedCodes(item.relatedDimensionCodes(), applicable, field);
        }
        return List.copyOf(values);
    }

    private List<MissingPointProposal> validateMissingPoints(
            List<MissingPointProposal> missingPoints, Set<String> applicable) {
        List<MissingPointProposal> values = requiredList(missingPoints, "missingPoints");
        require(values.size() <= 5, "missingPoints exceeds limit 5");
        for (MissingPointProposal item : values) {
            require(item != null && !blank(item.description()), "Missing point description is required");
            require(item.importance() != null, "Missing point importance is required");
            require(!blank(item.explanation()), "Missing point explanation is required");
            validateRelatedCodes(item.relatedDimensionCodes(), applicable, "missingPoints");
        }
        return List.copyOf(values);
    }

    private void validateRelatedCodes(List<String> codes, Set<String> applicable, String field) {
        require(codes != null && !codes.isEmpty(), field + " must reference an applicable dimension");
        require(new HashSet<>(codes).size() == codes.size(), field + " contains duplicate dimension references");
        require(codes.stream().allMatch(applicable::contains), field + " references a non-applicable dimension");
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private <T> List<T> requiredList(List<T> value, String field) {
        require(value != null, field + " is required");
        return value;
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new EvaluationValidationException(message);
        }
    }
}
