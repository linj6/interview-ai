package com.linj6.interviewagent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.linj6.interviewagent.ai.api.decision.DecisionAction;
import com.linj6.interviewagent.ai.api.decision.DecisionContext;
import com.linj6.interviewagent.ai.api.decision.InterviewProgress;
import com.linj6.interviewagent.ai.decision.DecisionProposal;
import com.linj6.interviewagent.ai.decision.DecisionProposalValidator;
import com.linj6.interviewagent.ai.decision.DecisionTargets;
import com.linj6.interviewagent.ai.decision.DecisionValidationException;
import com.linj6.interviewagent.interview.application.AllowedActionsCalculator;
import com.linj6.interviewagent.interview.application.InterviewDecisionLimits;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class DecisionRulesTest {
    private final AllowedActionsCalculator calculator = new AllowedActionsCalculator();
    private final DecisionProposalValidator validator = new DecisionProposalValidator();

    @Test
    void promptDoesNotFollowUpClosedFactsJustBecauseTheyRemainMissingPoints() throws IOException {
        assertThat(decisionPrompt())
                .contains("没有做 / 没发生 / 不知道 / 记不清", "不得仅因为该事实仍是 missingPoint 就继续追问")
                .contains("不得换一种说法重复逼问已关闭的信息", "没有发生过并发问题", "不得继续追问该问题当时如何处理")
                .contains("优先选择允许的 NEXT_QUESTION", "则选择允许的 END", "不得为了用完追问次数选择 FOLLOW_UP");
    }

    @Test
    void promptClosesOnlyTheUnanswerableFactScopeWithinAPartiallyOpenTarget() throws IOException {
        assertThat(decisionPrompt())
                .contains("只关闭明确无法回答的事实范围，不等于整个 target 关闭")
                .contains("同一 target 仍有独立、尚未澄清且可能回答的信息，允许 FOLLOW_UP")
                .contains("记不清具体 QPS，但参与过瓶颈定位", "只关闭具体 QPS", "仍可澄清尚未说明的瓶颈定位过程")
                .contains("部分关闭时明确排除已关闭的事实范围");
    }

    @Test
    void promptUsesFollowUpToObtainGenuinelyUnclarifiedAnswerableInformation() throws IOException {
        assertThat(decisionPrompt())
                .contains("选择 FOLLOW_UP 前", "尚未澄清、候选人可能回答的有效信息（Information Gain）")
                .contains("missingPoint 只是候选目标，不代表必须继续追问", "FOLLOW_UP 用于获取真正尚未澄清的信息")
                .contains("做过压测", "尚未说明如何定位瓶颈", "且未表示无法回答")
                .contains("FOLLOW_UP 时说明该单一 target 内仍可澄清的具体信息")
                .contains("只能包含 action、reason、targetRef 和 planItemRef", "必须是目标列表中存在的单个 M* 引用");
    }

    @Test
    void resolvesInvocationScopedPlanReferencesAndRejectsInvalidOrUnavailableReferences() {
        var context = DecisionContext.builder()
                .allowedActions(Set.of(DecisionAction.NEXT_QUESTION, DecisionAction.END))
                .unusedPlanItems(java.util.List.of(
                        new com.linj6.interviewagent.ai.api.decision.DecisionPlanItem(99L, "MYSQL", "事务", "理由")))
                .build();
        var targets = DecisionTargets.from(context);
        assertThat(targets.renderPlanItems())
                .contains("P1", "MYSQL", "事务", "理由")
                .doesNotContain("99");
        assertThat(validator
                        .validate(
                                context, new DecisionProposal(DecisionAction.NEXT_QUESTION, "下一题", null, "P1"), targets)
                        .planItemId())
                .isEqualTo(99L);
        for (String ref : new String[] {null, "", " ", "P0", "P01", " P1", "P1 ", "P2", "99", "P1,P2", "M1"}) {
            assertThatThrownBy(() -> validator.validate(
                            context, new DecisionProposal(DecisionAction.NEXT_QUESTION, "下一题", null, ref), targets))
                    .isInstanceOf(DecisionValidationException.class);
        }
        assertThatThrownBy(() -> validator.validate(
                        context, new DecisionProposal(DecisionAction.END, "结束", null, "P1"), targets))
                .isInstanceOf(DecisionValidationException.class);
        var follow = DecisionContext.builder()
                .allowedActions(Set.of(DecisionAction.FOLLOW_UP))
                .evaluationMissingPoints(java.util.List.of("目标"))
                .build();
        assertThatThrownBy(() -> validator.validate(
                        follow,
                        new DecisionProposal(DecisionAction.FOLLOW_UP, "追问", "M1", "P1"),
                        DecisionTargets.from(follow)))
                .isInstanceOf(DecisionValidationException.class);
        var empty = DecisionContext.builder()
                .allowedActions(Set.of(DecisionAction.NEXT_QUESTION))
                .unusedPlanItems(java.util.List.of())
                .build();
        assertThatThrownBy(() -> validator.validate(
                        empty,
                        new DecisionProposal(DecisionAction.NEXT_QUESTION, "下一题", null, "P1"),
                        DecisionTargets.from(empty)))
                .isInstanceOf(DecisionValidationException.class);
    }

    private String decisionPrompt() throws IOException {
        return new ClassPathResource("prompts/decision-system.st").getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void rejectsNonPositiveDecisionLimits() {
        assertThatThrownBy(() -> new InterviewDecisionLimits(0, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InterviewDecisionLimits(-1, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InterviewDecisionLimits(1, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InterviewDecisionLimits(1, -1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> calculator.calculate(new InterviewProgress(1, 0, 0, 1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> calculator.calculate(new InterviewProgress(1, 0, 1, 0)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void calculatesAllFourActionSetsWithGreaterThanOrEqualLimits() {
        assertThat(calculator.calculate(new InterviewProgress(1, 0, 2, 1)))
                .containsExactlyInAnyOrder(DecisionAction.FOLLOW_UP, DecisionAction.NEXT_QUESTION);
        assertThat(calculator.calculate(new InterviewProgress(1, 2, 2, 1)))
                .containsExactly(DecisionAction.NEXT_QUESTION);
        assertThat(calculator.calculate(new InterviewProgress(3, 0, 2, 1)))
                .containsExactlyInAnyOrder(DecisionAction.FOLLOW_UP, DecisionAction.END);
        assertThat(calculator.calculate(new InterviewProgress(2, 1, 2, 1))).containsExactly(DecisionAction.END);
    }

    @Test
    void validatesActionReasonAndResolvesOnlyFollowUpTarget() {
        DecisionContext context = DecisionContext.builder()
                .evaluationWeaknesses(java.util.List.of("薄弱目标"))
                .evaluationMissingPoints(java.util.List.of("缺失目标一", "缺失目标二"))
                .allowedActions(Set.of(DecisionAction.FOLLOW_UP, DecisionAction.END))
                .build();
        DecisionTargets targets = DecisionTargets.from(context);
        assertThat(targets.values()).extracting("ref").containsExactly("M1", "M2");
        assertThat(validator.validate(context, new DecisionProposal(DecisionAction.FOLLOW_UP, " why ", "M2"), targets))
                .hasFieldOrPropertyWithValue("followUpTarget", "缺失目标二");
        assertThat(validator
                        .validate(context, new DecisionProposal(DecisionAction.END, "done", " "), targets)
                        .followUpTarget())
                .isNull();
        for (DecisionProposal proposal : new DecisionProposal[] {
            new DecisionProposal(null, "why", null),
            new DecisionProposal(DecisionAction.FOLLOW_UP, " ", "M1"),
            new DecisionProposal(DecisionAction.FOLLOW_UP, "why", null),
            new DecisionProposal(DecisionAction.FOLLOW_UP, "why", " "),
            new DecisionProposal(DecisionAction.FOLLOW_UP, "why", "M0"),
            new DecisionProposal(DecisionAction.FOLLOW_UP, "why", "M9"),
            new DecisionProposal(DecisionAction.FOLLOW_UP, "why", "M1,M2"),
            new DecisionProposal(DecisionAction.NEXT_QUESTION, "why", "M1"),
            new DecisionProposal(DecisionAction.END, "why", "M1")
        }) {
            assertThatThrownBy(() -> validator.validate(context, proposal, targets))
                    .isInstanceOf(DecisionValidationException.class);
        }
        DecisionContext noMissingPoints = DecisionContext.builder()
                .evaluationWeaknesses(java.util.List.of("weakness only"))
                .evaluationMissingPoints(java.util.List.of())
                .allowedActions(Set.of(DecisionAction.FOLLOW_UP, DecisionAction.NEXT_QUESTION))
                .build();
        DecisionTargets noTargets = DecisionTargets.from(noMissingPoints);
        assertThat(noTargets.values()).isEmpty();
        assertThatThrownBy(() -> validator.validate(
                        noMissingPoints, new DecisionProposal(DecisionAction.FOLLOW_UP, "why", "W1"), noTargets))
                .isInstanceOf(DecisionValidationException.class);
        DecisionContext nextContext = DecisionContext.builder()
                .unusedPlanItems(java.util.List.of(
                        new com.linj6.interviewagent.ai.api.decision.DecisionPlanItem(42L, "JAVA", "focus", "reason")))
                .allowedActions(Set.of(DecisionAction.NEXT_QUESTION))
                .build();
        assertThat(validator.validate(
                        nextContext,
                        new DecisionProposal(DecisionAction.NEXT_QUESTION, "why", null, "P1"),
                        DecisionTargets.from(nextContext)))
                .hasFieldOrPropertyWithValue("followUpTarget", null);
    }
}
