package com.linj6.interviewagent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.linj6.interviewagent.ai.api.InterviewAiApi;
import com.linj6.interviewagent.ai.api.decision.DecisionAction;
import com.linj6.interviewagent.ai.api.decision.ValidatedDecisionResult;
import com.linj6.interviewagent.ai.api.evaluation.AnswerForm;
import com.linj6.interviewagent.ai.api.evaluation.ReferenceAnswerProposal;
import com.linj6.interviewagent.ai.api.evaluation.ValidatedEvaluationResult;
import com.linj6.interviewagent.ai.api.question.GeneratedQuestion;
import com.linj6.interviewagent.candidate.api.CandidateQueryApi;
import com.linj6.interviewagent.common.exception.BusinessException;
import com.linj6.interviewagent.evaluation.api.evaluation.EvaluationApi;
import com.linj6.interviewagent.evaluation.api.evaluation.EvaluationCommand;
import com.linj6.interviewagent.evaluation.api.profile.EvaluationProfileApi;
import com.linj6.interviewagent.interview.application.AllowedActionsCalculator;
import com.linj6.interviewagent.interview.application.InterviewApplicationService;
import com.linj6.interviewagent.interview.application.InterviewDecisionLimits;
import com.linj6.interviewagent.interview.application.InterviewTurnApplicationService;
import com.linj6.interviewagent.interview.domain.InterviewPhase;
import com.linj6.interviewagent.interview.domain.InterviewStatus;
import com.linj6.interviewagent.interview.domain.QuestionStatus;
import com.linj6.interviewagent.interview.domain.QuestionType;
import com.linj6.interviewagent.interview.infrastructure.persistence.repository.AnswerRepository;
import com.linj6.interviewagent.interview.infrastructure.persistence.repository.AnswerSnapshot;
import com.linj6.interviewagent.interview.infrastructure.persistence.repository.InterviewRepository;
import com.linj6.interviewagent.interview.infrastructure.persistence.repository.InterviewSnapshot;
import com.linj6.interviewagent.interview.infrastructure.persistence.repository.QuestionCreateCommand;
import com.linj6.interviewagent.interview.infrastructure.persistence.repository.QuestionRepository;
import com.linj6.interviewagent.interview.infrastructure.persistence.repository.QuestionSnapshot;
import com.linj6.interviewagent.position.api.PositionQueryApi;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

class ProcessedTurnPersistenceTest {
    private final InterviewRepository interviews = mock(InterviewRepository.class);
    private final QuestionRepository questions = mock(QuestionRepository.class);
    private final com.linj6.interviewagent.interview.infrastructure.persistence.repository.InterviewPlanRepository
            plans = mock(
                    com.linj6.interviewagent.interview.infrastructure.persistence.repository.InterviewPlanRepository
                            .class);
    private final AnswerRepository answers = mock(AnswerRepository.class);
    private final EvaluationApi evaluations = mock(EvaluationApi.class);
    private final TransactionTemplate transactions = mock(TransactionTemplate.class);
    private final InterviewTurnApplicationService application = new InterviewTurnApplicationService(
            interviews,
            mock(InterviewApplicationService.class),
            questions,
            plans,
            answers,
            mock(EvaluationProfileApi.class),
            evaluations,
            transactions,
            mock(CandidateQueryApi.class),
            mock(PositionQueryApi.class),
            mock(InterviewAiApi.class),
            new AllowedActionsCalculator(),
            new InterviewDecisionLimits(5, 2),
            Clock.fixed(Instant.parse("2026-09-26T03:00:00Z"), ZoneId.of("Asia/Shanghai")));

    @BeforeEach
    void arrangeTransactionAndFacts() {
        when(transactions.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
        when(interviews.findById(1L))
                .thenReturn(interview(InterviewStatus.IN_PROGRESS, InterviewPhase.PROCESSING_TURN, 6L));
        QuestionSnapshot question = new QuestionSnapshot(
                2L, 1L, 4, null, QuestionType.MAIN, QuestionStatus.WAITING_ANSWER, "旧问题", 7L, 8L, 11L);
        when(questions.findById(2L)).thenReturn(question);
        when(answers.findById(3L)).thenReturn(new AnswerSnapshot(3L, 1L, 2L, "回答"));
        when(questions.findByInterviewId(1L)).thenReturn(List.of(question));
        when(plans.findByInterviewId(1L))
                .thenReturn(new com.linj6.interviewagent.interview.domain.InterviewPlan(
                        10L,
                        1L,
                        11L,
                        List.of(
                                new com.linj6.interviewagent.interview.domain.PlanItem(
                                        11L,
                                        10L,
                                        com.linj6.interviewagent.interview.domain.KnowledgeArea.JAVA,
                                        "原方向",
                                        "原理由"),
                                new com.linj6.interviewagent.interview.domain.PlanItem(
                                        12L,
                                        10L,
                                        com.linj6.interviewagent.interview.domain.KnowledgeArea.MYSQL,
                                        "事务处理",
                                        "新理由"))));
    }

    @Test
    void commitsFollowUpWithInheritedTopicAndFinalCas() {
        persist(DecisionAction.FOLLOW_UP, new GeneratedQuestion("追问"), 6L);
        assertCreatedQuestion(QuestionCreateCommand.builder()
                .interviewId(1L)
                .sequenceNo(5)
                .parentQuestionId(2L)
                .type(QuestionType.FOLLOW_UP)
                .planItemId(11L)
                .content("追问")
                .evaluationProfileId(7L)
                .topicId(8L)
                .build());
        verifyOrder(false);
    }

    @Test
    void commitsSecondFollowUpWithMainParent() {
        QuestionSnapshot main =
                new QuestionSnapshot(1L, 1L, 3, null, QuestionType.MAIN, QuestionStatus.ANSWERED, "主问题", 7L, 8L, 11L);
        QuestionSnapshot followUp = new QuestionSnapshot(
                2L, 1L, 4, 1L, QuestionType.FOLLOW_UP, QuestionStatus.WAITING_ANSWER, "首个追问", 7L, 8L, 11L);
        when(questions.findById(2L)).thenReturn(followUp);
        when(questions.findByInterviewId(1L)).thenReturn(List.of(main, followUp));

        persist(DecisionAction.FOLLOW_UP, new GeneratedQuestion("第二个追问"), 6L);

        assertCreatedQuestion(QuestionCreateCommand.builder()
                .interviewId(1L)
                .sequenceNo(5)
                .parentQuestionId(1L)
                .type(QuestionType.FOLLOW_UP)
                .planItemId(11L)
                .content("第二个追问")
                .evaluationProfileId(7L)
                .topicId(8L)
                .build());
        verifyOrder(false);
    }

    @Test
    void rejectsFollowUpParentThatIsAnotherFollowUpBeforeWrites() {
        QuestionSnapshot main =
                new QuestionSnapshot(1L, 1L, 2, null, QuestionType.MAIN, QuestionStatus.ANSWERED, "主问题", 7L, 8L, 11L);
        QuestionSnapshot parentFollowUp = new QuestionSnapshot(
                3L, 1L, 3, 1L, QuestionType.FOLLOW_UP, QuestionStatus.ANSWERED, "首个追问", 7L, 8L, 11L);
        QuestionSnapshot currentFollowUp = new QuestionSnapshot(
                2L, 1L, 4, 3L, QuestionType.FOLLOW_UP, QuestionStatus.WAITING_ANSWER, "第二个追问", 7L, 8L, 11L);

        assertInvalidFollowUpParent(
                currentFollowUp,
                List.of(main, parentFollowUp, currentFollowUp),
                "FOLLOW_UP parent must be a root MAIN question");
    }

    @Test
    void rejectsFollowUpParentFromAnotherInterviewBeforeWrites() {
        QuestionSnapshot foreignMain = new QuestionSnapshot(
                1L, 9L, 2, null, QuestionType.MAIN, QuestionStatus.ANSWERED, "其他面试主问题", 7L, 8L, 11L);
        QuestionSnapshot currentFollowUp = new QuestionSnapshot(
                2L, 1L, 3, 1L, QuestionType.FOLLOW_UP, QuestionStatus.WAITING_ANSWER, "追问", 7L, 8L, 11L);

        assertInvalidFollowUpParent(
                currentFollowUp,
                List.of(foreignMain, currentFollowUp),
                "FOLLOW_UP parent must belong to the same Interview");
    }

    @Test
    void rejectsMissingFollowUpParentBeforeWrites() {
        QuestionSnapshot currentFollowUp = new QuestionSnapshot(
                2L, 1L, 3, 99L, QuestionType.FOLLOW_UP, QuestionStatus.WAITING_ANSWER, "追问", 7L, 8L, 11L);

        assertInvalidFollowUpParent(currentFollowUp, List.of(currentFollowUp), "FOLLOW_UP parent question is missing");
    }

    @Test
    void rejectsFollowUpParentWithItsOwnParentBeforeWrites() {
        QuestionSnapshot invalidMain =
                new QuestionSnapshot(1L, 1L, 2, 4L, QuestionType.MAIN, QuestionStatus.ANSWERED, "非法主问题", 7L, 8L, 11L);
        QuestionSnapshot currentFollowUp = new QuestionSnapshot(
                2L, 1L, 3, 1L, QuestionType.FOLLOW_UP, QuestionStatus.WAITING_ANSWER, "追问", 7L, 8L, 11L);

        assertInvalidFollowUpParent(
                currentFollowUp,
                List.of(invalidMain, currentFollowUp),
                "FOLLOW_UP parent must be a root MAIN question");
    }

    @Test
    void commitsNextMainWithoutTopic() {
        persist(DecisionAction.NEXT_QUESTION, new GeneratedQuestion("下一题"), 6L);
        assertCreatedQuestion(QuestionCreateCommand.builder()
                .interviewId(1L)
                .sequenceNo(5)
                .parentQuestionId(null)
                .type(QuestionType.MAIN)
                .content("下一题")
                .planItemId(12L)
                .evaluationProfileId(7L)
                .topicId(null)
                .build());
        verifyOrder(false);
    }

    @Test
    void completesAfterEvaluationWithoutCreatingQuestion() {
        persist(DecisionAction.END, null, 6L);
        verify(questions, never()).create(any());
        verifyOrder(true);
    }

    @Test
    void expiredT2CompletesAndDiscardsOptionalGeneratedQuestion() {
        when(interviews.findById(1L))
                .thenReturn(new InterviewSnapshot(
                        1L,
                        4L,
                        5L,
                        InterviewStatus.IN_PROGRESS,
                        InterviewPhase.PROCESSING_TURN,
                        6L,
                        java.time.LocalDateTime.parse("2026-09-26T11:00:00")));

        persist(DecisionAction.FOLLOW_UP, new GeneratedQuestion("已生成但过期"), 6L);

        verify(evaluations).create(any(EvaluationCommand.class));
        verify(questions).markAnswered(2L, 1L);
        verify(questions, never()).create(any());
        verify(interviews).completeAfterTurn(eq(1L), eq(6L), any());
    }

    @Test
    void expiredT2AllowsAndIgnoresGeneratedQuestionForEndDecision() {
        when(interviews.findById(1L))
                .thenReturn(new InterviewSnapshot(
                        1L,
                        4L,
                        5L,
                        InterviewStatus.IN_PROGRESS,
                        InterviewPhase.PROCESSING_TURN,
                        6L,
                        java.time.LocalDateTime.parse("2026-09-26T11:00:00")));

        persist(DecisionAction.END, new GeneratedQuestion("过期结果"), 6L);

        verify(evaluations).create(any(EvaluationCommand.class));
        verify(questions).markAnswered(2L, 1L);
        verify(questions, never()).create(any());
        verify(interviews).completeAfterTurn(eq(1L), eq(6L), any());
    }

    @Test
    void rejectsInvalidRuntimeCombinationsBeforeWrites() {
        assertThatThrownBy(() -> persist(DecisionAction.FOLLOW_UP, null, 6L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> persist(DecisionAction.NEXT_QUESTION, null, 6L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> persist(DecisionAction.END, new GeneratedQuestion("多余"), 6L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> application.persistProcessedTurn(
                        1L,
                        2L,
                        3L,
                        6L,
                        null,
                        new ValidatedDecisionResult(DecisionAction.END, "done", null),
                        null,
                        null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> application.persistProcessedTurn(1L, 2L, 3L, 6L, evaluation(), null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> application.persistProcessedTurn(
                        1L, 2L, 3L, 6L, evaluation(), new ValidatedDecisionResult(null, "invalid", null), null, null))
                .isInstanceOf(IllegalArgumentException.class);
        verify(evaluations, never()).create(any());
        verify(questions, never()).markAnswered(any(), any());
    }

    @Test
    void rejectsWrongInterviewStateAndCallerVersion() {
        when(interviews.findById(1L))
                .thenReturn(interview(InterviewStatus.IN_PROGRESS, InterviewPhase.WAITING_ANSWER, 6L));
        assertThatThrownBy(() -> persist(DecisionAction.END, null, 6L)).isInstanceOf(BusinessException.class);
        when(interviews.findById(1L))
                .thenReturn(interview(InterviewStatus.IN_PROGRESS, InterviewPhase.PROCESSING_TURN, 7L));
        assertThatThrownBy(() -> persist(DecisionAction.END, null, 6L)).isInstanceOf(BusinessException.class);
        verify(evaluations, never()).create(any());
    }

    @Test
    void rejectsQuestionAndAnswerOwnership() {
        when(questions.findById(2L))
                .thenReturn(new QuestionSnapshot(
                        2L, 9L, 4, null, QuestionType.MAIN, QuestionStatus.WAITING_ANSWER, "旧问题", 7L, 8L, 11L));
        assertThatThrownBy(() -> persist(DecisionAction.END, null, 6L)).isInstanceOf(BusinessException.class);
        when(questions.findById(2L))
                .thenReturn(new QuestionSnapshot(
                        2L, 1L, 4, null, QuestionType.MAIN, QuestionStatus.WAITING_ANSWER, "旧问题", 7L, 8L, 11L));
        when(answers.findById(3L)).thenReturn(new AnswerSnapshot(3L, 9L, 2L, "回答"));
        assertThatThrownBy(() -> persist(DecisionAction.END, null, 6L)).isInstanceOf(BusinessException.class);
        when(answers.findById(3L)).thenReturn(new AnswerSnapshot(3L, 1L, 9L, "回答"));
        assertThatThrownBy(() -> persist(DecisionAction.END, null, 6L)).isInstanceOf(BusinessException.class);
        verify(evaluations, never()).create(any());
    }

    @Test
    void rejectsUsedForeignAndMissingPlanItemIdsBeforeAnyWrite() {
        for (Long id : new Long[] {null, 11L, 999L}) {
            assertThatThrownBy(() -> application.persistProcessedTurn(
                            1L,
                            2L,
                            3L,
                            6L,
                            evaluation(),
                            new ValidatedDecisionResult(DecisionAction.NEXT_QUESTION, "下一题", null, id),
                            new GeneratedQuestion("下一题"),
                            "事务处理"))
                    .isInstanceOf(IllegalStateException.class);
        }
        verify(evaluations, never()).create(any());
        verify(questions, never()).markAnswered(any(), any());
        verify(questions, never()).create(any());
    }

    @Test
    void rejectsFollowUpThatDoesNotInheritMainPlanItem() {
        var main =
                new QuestionSnapshot(1L, 1L, 3, null, QuestionType.MAIN, QuestionStatus.ANSWERED, "主问题", 7L, 8L, 11L);
        var follow = new QuestionSnapshot(
                2L, 1L, 4, 1L, QuestionType.FOLLOW_UP, QuestionStatus.WAITING_ANSWER, "追问", 7L, 8L, 12L);
        when(questions.findById(2L)).thenReturn(follow);
        when(questions.findByInterviewId(1L)).thenReturn(List.of(main, follow));
        assertThatThrownBy(() -> persist(DecisionAction.FOLLOW_UP, new GeneratedQuestion("新追问"), 6L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("inherit");
        verify(evaluations, never()).create(any());
    }

    private void persist(DecisionAction action, GeneratedQuestion generated, Long version) {
        application.persistProcessedTurn(
                1L,
                2L,
                3L,
                version,
                evaluation(),
                new ValidatedDecisionResult(
                        action, "reason", null, action == DecisionAction.NEXT_QUESTION ? 12L : null),
                generated,
                action == DecisionAction.NEXT_QUESTION ? "事务处理" : null);
    }

    private void assertInvalidFollowUpParent(
            QuestionSnapshot current, List<QuestionSnapshot> interviewQuestions, String expectedMessage) {
        when(questions.findById(2L)).thenReturn(current);
        when(questions.findByInterviewId(1L)).thenReturn(interviewQuestions);

        assertThatThrownBy(() -> persist(DecisionAction.FOLLOW_UP, new GeneratedQuestion("新追问"), 6L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(expectedMessage);
        verify(evaluations, never()).create(any(EvaluationCommand.class));
        verify(questions, never()).markAnswered(any(), any());
        verify(questions, never()).create(any());
    }

    private InterviewSnapshot interview(InterviewStatus status, InterviewPhase phase, Long version) {
        return new InterviewSnapshot(1L, 4L, 5L, status, phase, version, null);
    }

    private ValidatedEvaluationResult evaluation() {
        return new ValidatedEvaluationResult(
                7L,
                BigDecimal.ONE,
                BigDecimal.TEN,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                new ReferenceAnswerProposal(AnswerForm.SAMPLE_ANSWER, List.of(), "参考"),
                "p1",
                "e1",
                "test",
                1,
                1,
                2,
                1L);
    }

    private void assertCreatedQuestion(QuestionCreateCommand expected) {
        ArgumentCaptor<QuestionCreateCommand> command = ArgumentCaptor.forClass(QuestionCreateCommand.class);
        verify(questions).create(command.capture());
        assertThat(command.getValue()).isEqualTo(expected);
    }

    private void verifyOrder(boolean end) {
        InOrder order = inOrder(evaluations, questions, interviews);
        order.verify(evaluations).create(any(EvaluationCommand.class));
        order.verify(questions).markAnswered(2L, 1L);
        if (end) {
            order.verify(interviews).completeAfterTurn(eq(1L), eq(6L), any());
        } else {
            order.verify(questions).create(any(QuestionCreateCommand.class));
            order.verify(interviews).markWaitingAfterTurn(1L, 6L);
        }
    }
}
