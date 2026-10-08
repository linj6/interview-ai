package com.linj6.interviewagent.interview.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.linj6.interviewagent.common.exception.BusinessException;
import com.linj6.interviewagent.evaluation.api.evaluation.DimensionScoreDTO;
import com.linj6.interviewagent.evaluation.api.evaluation.EvaluationApi;
import com.linj6.interviewagent.evaluation.api.evaluation.EvaluationDTO;
import com.linj6.interviewagent.evaluation.api.evaluation.Feedback;
import com.linj6.interviewagent.evaluation.api.evaluation.MissingPoint;
import com.linj6.interviewagent.evaluation.api.evaluation.ReferenceAnswer;
import com.linj6.interviewagent.interview.domain.InterviewErrorCode;
import com.linj6.interviewagent.interview.domain.InterviewPhase;
import com.linj6.interviewagent.interview.domain.InterviewStatus;
import com.linj6.interviewagent.interview.domain.QuestionStatus;
import com.linj6.interviewagent.interview.domain.QuestionType;
import com.linj6.interviewagent.interview.infrastructure.persistence.repository.AnswerRepository;
import com.linj6.interviewagent.interview.infrastructure.persistence.repository.AnswerSnapshot;
import com.linj6.interviewagent.interview.infrastructure.persistence.repository.InterviewRepository;
import com.linj6.interviewagent.interview.infrastructure.persistence.repository.InterviewSnapshot;
import com.linj6.interviewagent.interview.infrastructure.persistence.repository.QuestionRepository;
import com.linj6.interviewagent.interview.infrastructure.persistence.repository.QuestionSnapshot;
import com.linj6.interviewagent.interview.interfaces.response.InterviewReviewResponse;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InterviewReviewApplicationServiceTest {
    private final InterviewRepository interviewRepository = mock(InterviewRepository.class);
    private final QuestionRepository questionRepository = mock(QuestionRepository.class);
    private final AnswerRepository answerRepository = mock(AnswerRepository.class);
    private final EvaluationApi evaluationApi = mock(EvaluationApi.class);
    private final InterviewReviewApplicationService service = new InterviewReviewApplicationService(
            interviewRepository, questionRepository, answerRepository, evaluationApi);
    private final LocalDateTime startedAt = LocalDateTime.parse("2026-09-27T10:00:00");
    private final LocalDateTime completedAt = startedAt.plusSeconds(125);

    @BeforeEach
    void setup() {
        when(interviewRepository.findById(1L))
                .thenReturn(interview(InterviewStatus.COMPLETED, null, startedAt, completedAt));
    }

    @Test
    void assemblesOrderedTwoLevelReviewAndOverviewForAllQuestionResults() {
        QuestionSnapshot main = question(10L, 1, null, QuestionType.MAIN, QuestionStatus.ANSWERED);
        QuestionSnapshot mainWithoutFollowUps =
                question(13L, 4, null, QuestionType.MAIN, QuestionStatus.WAITING_ANSWER);
        QuestionSnapshot followUp = question(12L, 3, 10L, QuestionType.FOLLOW_UP, QuestionStatus.WAITING_ANSWER);
        QuestionSnapshot skippedFollowUp = question(11L, 2, 10L, QuestionType.FOLLOW_UP, QuestionStatus.SKIPPED);
        when(questionRepository.findByInterviewId(1L))
                .thenReturn(List.of(followUp, skippedFollowUp, mainWithoutFollowUps, main));
        when(answerRepository.findByInterviewId(1L)).thenReturn(List.of(new AnswerSnapshot(20L, 1L, 10L, "回答内容")));
        when(evaluationApi.findByInterviewId(1L)).thenReturn(List.of(evaluation(10L, 20L)));

        var review = service.getReview(1L);

        assertThat(review.interviewId()).isEqualTo(1L);
        assertThat(review.overview()).satisfies(overview -> {
            assertThat(overview.startedAt()).isEqualTo(startedAt);
            assertThat(overview.completedAt()).isEqualTo(completedAt);
            assertThat(overview.durationSeconds()).isEqualTo(125);
            assertThat(overview.mainQuestionCount()).isEqualTo(2);
            assertThat(overview.followUpQuestionCount()).isEqualTo(2);
            assertThat(overview.answeredCount()).isEqualTo(1);
            assertThat(overview.skippedCount()).isEqualTo(1);
            assertThat(overview.unansweredCount()).isEqualTo(2);
        });
        assertThat(review.questions()).hasSize(2);
        assertThat(review.questions().get(0)).satisfies(question -> {
            assertThat(question.questionId()).isEqualTo(10L);
            assertThat(question.result()).isEqualTo(InterviewReviewResponse.Result.ANSWERED);
            assertThat(question.answer()).isEqualTo("回答内容");
            assertThat(question.evaluation().overallScore()).isEqualByComparingTo("8.5");
            assertThat(question.evaluation().strengths()).containsExactly("清晰");
            assertThat(question.evaluation().weaknesses()).containsExactly("不够深入");
            assertThat(question.evaluation().missingPoints())
                    .containsExactly(new InterviewReviewResponse.MissingPointReview("边界条件", "HIGH", "需说明限制"));
            assertThat(question.evaluation().referenceAnswer())
                    .isEqualTo(new InterviewReviewResponse.ReferenceAnswerReview("STAR", List.of("背景"), "示例"));
            assertThat(question.evaluation().dimensionScores())
                    .containsExactly(
                            new InterviewReviewResponse.DimensionScoreReview("表达", new BigDecimal("8.0"), "清楚"));
            assertThat(question.followUps()).extracting("questionId").containsExactly(11L, 12L);
            assertThat(question.followUps().get(0).result()).isEqualTo(InterviewReviewResponse.Result.SKIPPED);
            assertThat(question.followUps().get(0).answer()).isNull();
            assertThat(question.followUps().get(0).evaluation()).isNull();
            assertThat(question.followUps().get(1).result()).isEqualTo(InterviewReviewResponse.Result.UNANSWERED);
            assertThat(question.followUps().get(1).answer()).isNull();
            assertThat(question.followUps().get(1).evaluation()).isNull();
        });
        assertThat(review.questions().get(1)).satisfies(question -> {
            assertThat(question.questionId()).isEqualTo(13L);
            assertThat(question.result()).isEqualTo(InterviewReviewResponse.Result.UNANSWERED);
            assertThat(question.followUps()).isEmpty();
        });
        verify(questionRepository).findByInterviewId(1L);
        verify(answerRepository).findByInterviewId(1L);
        verify(evaluationApi).findByInterviewId(1L);
    }

    @Test
    void rejectsIncompleteInterviewBeforeLoadingReviewFacts() {
        when(interviewRepository.findById(1L))
                .thenReturn(interview(InterviewStatus.IN_PROGRESS, InterviewPhase.WAITING_ANSWER, startedAt, null));

        assertThatThrownBy(() -> service.getReview(1L))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(InterviewErrorCode.INTERVIEW_STATE_CONFLICT);
        verifyNoInteractions(questionRepository, answerRepository, evaluationApi);
    }

    @Test
    void reportsInconsistentLifecycleAndDuplicateOrMissingFacts() {
        when(interviewRepository.findById(1L))
                .thenReturn(interview(InterviewStatus.COMPLETED, null, null, completedAt));
        assertInconsistent();

        when(interviewRepository.findById(1L))
                .thenReturn(interview(InterviewStatus.COMPLETED, null, startedAt, completedAt));
        QuestionSnapshot answered = question(10L, 1, null, QuestionType.MAIN, QuestionStatus.ANSWERED);
        when(questionRepository.findByInterviewId(1L)).thenReturn(List.of(answered));
        when(answerRepository.findByInterviewId(1L)).thenReturn(List.of());
        when(evaluationApi.findByInterviewId(1L)).thenReturn(List.of(evaluation(10L, 20L)));
        assertInconsistent();

        when(answerRepository.findByInterviewId(1L))
                .thenReturn(List.of(new AnswerSnapshot(20L, 1L, 10L, "a"), new AnswerSnapshot(21L, 1L, 10L, "b")));
        assertInconsistent();
    }

    @Test
    void rejectsInvalidParentAndSkippedQuestionWithAnswer() {
        when(questionRepository.findByInterviewId(1L))
                .thenReturn(List.of(question(11L, 2, 999L, QuestionType.FOLLOW_UP, QuestionStatus.SKIPPED)));
        when(answerRepository.findByInterviewId(1L)).thenReturn(List.of());
        when(evaluationApi.findByInterviewId(1L)).thenReturn(List.of());
        assertInconsistent();

        when(questionRepository.findByInterviewId(1L))
                .thenReturn(List.of(question(10L, 1, null, QuestionType.MAIN, QuestionStatus.SKIPPED)));
        when(answerRepository.findByInterviewId(1L))
                .thenReturn(List.of(new AnswerSnapshot(20L, 1L, 10L, "unexpected")));
        assertInconsistent();
    }

    @Test
    void rejectsCompletedInterviewWithPhaseAndWithoutMainQuestion() {
        when(interviewRepository.findById(1L))
                .thenReturn(interview(InterviewStatus.COMPLETED, InterviewPhase.OPENING, startedAt, completedAt));
        assertInconsistent();

        when(interviewRepository.findById(1L))
                .thenReturn(interview(InterviewStatus.COMPLETED, null, startedAt, completedAt));
        when(questionRepository.findByInterviewId(1L)).thenReturn(List.of());
        when(answerRepository.findByInterviewId(1L)).thenReturn(List.of());
        when(evaluationApi.findByInterviewId(1L)).thenReturn(List.of());
        assertInconsistent();
    }

    @Test
    void rejectsDuplicateSequenceNumbersAndInvalidAnswerAssociations() {
        QuestionSnapshot main = question(10L, 1, null, QuestionType.MAIN, QuestionStatus.ANSWERED);
        QuestionSnapshot followUp = question(11L, 1, 10L, QuestionType.FOLLOW_UP, QuestionStatus.ANSWERED);
        when(questionRepository.findByInterviewId(1L)).thenReturn(List.of(main, followUp));
        when(answerRepository.findByInterviewId(1L)).thenReturn(List.of());
        when(evaluationApi.findByInterviewId(1L)).thenReturn(List.of());
        assertInconsistent();

        when(questionRepository.findByInterviewId(1L))
                .thenReturn(List.of(main, question(11L, 2, 10L, QuestionType.FOLLOW_UP, QuestionStatus.ANSWERED)));
        when(answerRepository.findByInterviewId(1L))
                .thenReturn(
                        List.of(new AnswerSnapshot(20L, 1L, 10L, "main"), new AnswerSnapshot(20L, 1L, 11L, "follow")));
        assertInconsistent();

        when(answerRepository.findByInterviewId(1L)).thenReturn(List.of(new AnswerSnapshot(20L, 2L, 10L, "foreign")));
        when(evaluationApi.findByInterviewId(1L)).thenReturn(List.of(evaluation(10L, 20L)));
        assertInconsistent();

        when(answerRepository.findByInterviewId(1L)).thenReturn(List.of(new AnswerSnapshot(20L, 1L, 99L, "unknown")));
        assertInconsistent();
    }

    private void assertInconsistent() {
        assertThatThrownBy(() -> service.getReview(1L))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(InterviewErrorCode.INTERVIEW_DATA_INCONSISTENT);
    }

    private InterviewSnapshot interview(
            InterviewStatus status, InterviewPhase phase, LocalDateTime started, LocalDateTime completed) {
        return new InterviewSnapshot(1L, 1L, 1L, status, phase, 1L, null, started, completed);
    }

    private QuestionSnapshot question(Long id, int sequence, Long parent, QuestionType type, QuestionStatus status) {
        return new QuestionSnapshot(id, 1L, sequence, parent, type, status, "问题 " + id, 1L, null, 11L);
    }

    private EvaluationDTO evaluation(Long questionId, Long answerId) {
        return new EvaluationDTO(
                questionId + 100,
                1L,
                questionId,
                answerId,
                new BigDecimal("8.5"),
                List.of(new Feedback("清晰", List.of("COMMUNICATION"))),
                List.of(new Feedback("不够深入", List.of())),
                List.of(new MissingPoint("边界条件", "HIGH", "需说明限制", List.of())),
                new ReferenceAnswer("STAR", List.of("背景"), "示例"),
                List.of(
                        new DimensionScoreDTO("表达", true, new BigDecimal("8.0"), "清楚"),
                        new DimensionScoreDTO("不适用", false, null, "过滤")));
    }
}
