package com.linj6.interviewagent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.linj6.interviewagent.ai.api.evaluation.AnswerForm;
import com.linj6.interviewagent.ai.api.evaluation.Applicability;
import com.linj6.interviewagent.ai.api.evaluation.DimensionEvaluationProposal;
import com.linj6.interviewagent.ai.api.evaluation.EvaluationContext;
import com.linj6.interviewagent.ai.api.evaluation.EvaluationDimensionContext;
import com.linj6.interviewagent.ai.api.evaluation.EvaluationProposal;
import com.linj6.interviewagent.ai.api.evaluation.EvidenceProposal;
import com.linj6.interviewagent.ai.api.evaluation.EvidenceType;
import com.linj6.interviewagent.ai.api.evaluation.FeedbackProposal;
import com.linj6.interviewagent.ai.api.evaluation.Importance;
import com.linj6.interviewagent.ai.api.evaluation.MissingPointProposal;
import com.linj6.interviewagent.ai.api.evaluation.ReferenceAnswerProposal;
import com.linj6.interviewagent.ai.evaluator.AnswerFragments;
import com.linj6.interviewagent.ai.evaluator.EvaluationProposalValidator;
import com.linj6.interviewagent.ai.evaluator.EvaluationValidationException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class EvaluationProposalValidatorTest {
    private final EvaluationProposalValidator validator = new EvaluationProposalValidator();

    @Test
    void splitsAnswerIntoOrderedTrimmedNonEmptyLines() {
        assertThat(AnswerFragments.from("  第一行  \r\n\r\n 第二行\n  \t\n第三行 "))
                .extracting(value -> value.evidenceRef() + ":" + value.text())
                .containsExactly("E1:第一行", "E2:第二行", "E3:第三行");
    }

    @Test
    void validatesAllApplicableDimensionsAndCalculatesWithBigDecimal() {
        EvaluationContext context =
                context(List.of(dimension("ACCURACY", "1", false, 1), dimension("DEPTH", "3", false, 2)));
        EvaluationProposal proposal = proposal(List.of(applicable("ACCURACY", "9.5"), applicable("DEPTH", "10.0")));

        var result = validate(context, proposal);

        assertThat(result.overallScore10()).isEqualByComparingTo("9.88");
        assertThat(result.overallScore100()).isEqualByComparingTo("98.80");
        assertThat(result.dimensions())
                .extracting(value -> value.weightedScore().toPlainString())
                .containsExactly("9.5000", "30.0000");
        assertThat(result.dimensions().get(0).evidence().get(0).quote()).isNull();
    }

    @Test
    void excludesNotApplicableDimensionAndRenormalizesWeight() {
        EvaluationContext context = context(
                List.of(dimension("ACCURACY", "0.2", false, 1), dimension("PROJECT_EXPERIENCE", "0.8", true, 2)));
        EvaluationProposal proposal =
                proposal(List.of(applicable("ACCURACY", "7.5"), notApplicable("PROJECT_EXPERIENCE")));

        var result = validate(context, proposal);

        assertThat(result.overallScore10()).isEqualByComparingTo("7.50");
        assertThat(result.overallScore100()).isEqualByComparingTo("75.00");
        assertThat(result.dimensions().get(1).score()).isNull();
        assertThat(result.dimensions().get(1).weightedScore()).isNull();
    }

    @Test
    void rejectsMissingExtraAndDuplicateDimensions() {
        EvaluationContext context =
                context(List.of(dimension("ACCURACY", "1", false, 1), dimension("DEPTH", "1", false, 2)));
        assertInvalid(context, proposal(List.of(applicable("ACCURACY", "8"))));
        assertInvalid(
                context,
                proposal(List.of(applicable("ACCURACY", "8"), applicable("DEPTH", "8"), applicable("LOGIC", "8"))));
        assertInvalid(context, proposal(List.of(applicable("ACCURACY", "8"), applicable("ACCURACY", "8"))));
    }

    @Test
    void rejectsInvalidApplicabilityAndScores() {
        EvaluationContext context = context(List.of(dimension("ACCURACY", "1", false, 1)));
        assertInvalid(
                context,
                proposal(List.of(new DimensionEvaluationProposal(
                        "ACCURACY", null, new BigDecimal("8"), "说明", List.of(observation())))));
        assertInvalid(context, proposal(List.of(applicable("ACCURACY", "-0.5"))));
        assertInvalid(context, proposal(List.of(applicable("ACCURACY", "10.5"))));
        assertInvalid(context, proposal(List.of(applicable("ACCURACY", "8.2"))));
    }

    @Test
    void rejectsIllegalNotApplicableForms() {
        EvaluationContext disallowed = context(List.of(dimension("ACCURACY", "1", false, 1)));
        assertInvalid(disallowed, proposal(List.of(notApplicable("ACCURACY"))));
        EvaluationContext allowed = context(List.of(dimension("ACCURACY", "1", true, 1)));
        assertInvalid(
                allowed,
                proposal(List.of(new DimensionEvaluationProposal(
                        "ACCURACY", Applicability.NOT_APPLICABLE, new BigDecimal("0"), "不适用", List.of()))));
        assertInvalid(
                allowed,
                proposal(List.of(new DimensionEvaluationProposal(
                        "ACCURACY", Applicability.NOT_APPLICABLE, null, " ", List.of()))));
        assertInvalid(
                allowed,
                proposal(List.of(new DimensionEvaluationProposal(
                        "ACCURACY", Applicability.NOT_APPLICABLE, null, "没有评价条件", List.of(observation())))));
    }

    @Test
    void rejectsInvalidEvidence() {
        EvaluationContext context = context(List.of(dimension("ACCURACY", "1", false, 1)));
        assertInvalid(
                context,
                proposal(List.of(new DimensionEvaluationProposal(
                        "ACCURACY", Applicability.APPLICABLE, new BigDecimal("8"), "说明", List.of()))));
        assertInvalid(
                context,
                proposal(List.of(new DimensionEvaluationProposal(
                        "ACCURACY",
                        Applicability.APPLICABLE,
                        new BigDecimal("8"),
                        "说明",
                        List.of(observation(), observation(), observation(), observation())))));
        assertInvalid(
                context,
                proposal(List.of(
                        withEvidence(new EvidenceProposal(EvidenceType.ANSWER_QUOTE, Optional.empty(), "说明")))));
        assertInvalid(
                context,
                proposal(
                        List.of(withEvidence(new EvidenceProposal(EvidenceType.ANSWER_QUOTE, Optional.of(""), "说明")))));
        assertInvalid(
                context,
                proposal(List.of(
                        withEvidence(new EvidenceProposal(EvidenceType.ANSWER_QUOTE, Optional.of(" E1"), "说明")))));
        assertInvalid(
                context,
                proposal(List.of(
                        withEvidence(new EvidenceProposal(EvidenceType.ANSWER_QUOTE, Optional.of("E0"), "说明")))));
        assertInvalid(
                context,
                proposal(List.of(
                        withEvidence(new EvidenceProposal(EvidenceType.ANSWER_QUOTE, Optional.of("E3"), "说明")))));
        assertInvalid(
                context,
                proposal(List.of(withEvidence(
                        new EvidenceProposal(EvidenceType.OVERALL_OBSERVATION, Optional.of("E1"), "说明")))));
    }

    @Test
    void resolvesFragmentReferenceToOriginalAnswerText() {
        EvaluationContext context = context(List.of(dimension("ACCURACY", "1", false, 1)));
        EvaluationProposal proposal = proposal(
                List.of(withEvidence(new EvidenceProposal(EvidenceType.ANSWER_QUOTE, Optional.of("E2"), "引用支持结论"))));

        var result = validate(context, proposal);
        assertThat(result.overallScore10()).isEqualByComparingTo("8.00");
        assertThat(result.dimensions().get(0).evidence().get(0).quote()).isEqualTo("可以保证一致性，并发需要隔离。");
    }

    @Test
    void rejectsMissingReferenceAndFeedbackLimitsOrNonApplicableReferences() {
        EvaluationContext context = context(List.of(dimension("ACCURACY", "1", false, 1)));
        EvaluationProposal missingReference =
                new EvaluationProposal(List.of(applicable("ACCURACY", "8")), List.of(), List.of(), List.of(), null);
        assertInvalid(context, missingReference);
        List<FeedbackProposal> four = List.of(feedback(), feedback(), feedback(), feedback());
        assertInvalid(
                context,
                new EvaluationProposal(List.of(applicable("ACCURACY", "8")), four, List.of(), List.of(), reference()));

        EvaluationContext withNa =
                context(List.of(dimension("ACCURACY", "1", false, 1), dimension("PROJECT_EXPERIENCE", "1", true, 2)));
        EvaluationProposal badReference = new EvaluationProposal(
                List.of(applicable("ACCURACY", "8"), notApplicable("PROJECT_EXPERIENCE")),
                List.of(new FeedbackProposal("错误关联", List.of("PROJECT_EXPERIENCE"))),
                List.of(),
                List.of(),
                reference());
        assertInvalid(withNa, badReference);
    }

    @Test
    void rejectsTooManyMissingPointsAllNaAndZeroApplicableWeight() {
        EvaluationContext context = context(List.of(dimension("ACCURACY", "1", false, 1)));
        List<MissingPointProposal> six = new ArrayList<>();
        for (int index = 0; index < 6; index++) {
            six.add(new MissingPointProposal("缺失" + index, Importance.MEDIUM, "说明", List.of("ACCURACY")));
        }
        assertInvalid(
                context,
                new EvaluationProposal(List.of(applicable("ACCURACY", "8")), List.of(), List.of(), six, reference()));

        EvaluationContext allNa = context(List.of(dimension("ACCURACY", "1", true, 1)));
        assertInvalid(allNa, proposal(List.of(notApplicable("ACCURACY"))));
        EvaluationContext zeroWeight = context(List.of(dimension("ACCURACY", "0", false, 1)));
        assertInvalid(zeroWeight, proposal(List.of(applicable("ACCURACY", "8"))));
    }

    private EvaluationContext context(List<EvaluationDimensionContext> dimensions) {
        return EvaluationContext.builder()
                .interviewId(1L)
                .questionId(2L)
                .answerId(3L)
                .profileId(1L)
                .questionContent("请说明事务")
                .answerContent("事务\n可以保证一致性，并发需要隔离。")
                .candidateName("候选人")
                .resumeSummary("简历")
                .projects(java.util.List.of(new com.linj6.interviewagent.ai.api.ProjectContext("项目", "项目")))
                .positionTitle("Java 工程师")
                .positionDescription("岗位")
                .dimensions(dimensions)
                .build();
    }

    private EvaluationDimensionContext dimension(String code, String weight, boolean naAllowed, int order) {
        return new EvaluationDimensionContext(code, code, "标准", "0-10", new BigDecimal(weight), naAllowed, order);
    }

    private DimensionEvaluationProposal applicable(String code, String score) {
        return new DimensionEvaluationProposal(
                code, Applicability.APPLICABLE, new BigDecimal(score), "评分说明", List.of(observation()));
    }

    private DimensionEvaluationProposal withEvidence(EvidenceProposal evidence) {
        return new DimensionEvaluationProposal(
                "ACCURACY", Applicability.APPLICABLE, new BigDecimal("8"), "评分说明", List.of(evidence));
    }

    private DimensionEvaluationProposal notApplicable(String code) {
        return new DimensionEvaluationProposal(code, Applicability.NOT_APPLICABLE, null, "客观上没有评价条件", List.of());
    }

    private EvidenceProposal observation() {
        return new EvidenceProposal(EvidenceType.OVERALL_OBSERVATION, Optional.empty(), "整体观察");
    }

    private FeedbackProposal feedback() {
        return new FeedbackProposal("优点", List.of("ACCURACY"));
    }

    private ReferenceAnswerProposal reference() {
        return new ReferenceAnswerProposal(AnswerForm.SAMPLE_ANSWER, List.of("要点"), "参考回答");
    }

    private EvaluationProposal proposal(List<DimensionEvaluationProposal> dimensions) {
        return new EvaluationProposal(dimensions, List.of(), List.of(), List.of(), reference());
    }

    private com.linj6.interviewagent.ai.api.evaluation.ValidatedEvaluationResult validate(
            EvaluationContext context, EvaluationProposal proposal) {
        return validator.validateAndCalculate(
                context,
                com.linj6.interviewagent.ai.evaluator.AnswerFragments.from(context.answerContent()),
                proposal,
                "p1",
                "e1",
                "test-model",
                1,
                2,
                3,
                4L);
    }

    private void assertInvalid(EvaluationContext context, EvaluationProposal proposal) {
        assertThatThrownBy(() -> validate(context, proposal)).isInstanceOf(EvaluationValidationException.class);
    }
}
