package com.linj6.interviewagent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.linj6.interviewagent.ai.api.InterviewAiApi;
import com.linj6.interviewagent.ai.api.decision.DecisionAction;
import com.linj6.interviewagent.ai.api.decision.DecisionContext;
import com.linj6.interviewagent.ai.api.decision.ValidatedDecisionResult;
import com.linj6.interviewagent.ai.api.evaluation.AnswerForm;
import com.linj6.interviewagent.ai.api.evaluation.EvaluationContext;
import com.linj6.interviewagent.ai.api.evaluation.ReferenceAnswerProposal;
import com.linj6.interviewagent.ai.api.evaluation.ValidatedEvaluationResult;
import com.linj6.interviewagent.ai.api.question.GeneratedQuestion;
import com.linj6.interviewagent.ai.api.question.MainQuestionGenerationContext;
import com.linj6.interviewagent.common.api.CommonErrorCode;
import com.linj6.interviewagent.common.exception.BusinessException;
import com.linj6.interviewagent.interview.application.InterviewSkipApplicationService;
import com.linj6.interviewagent.interview.application.InterviewTurnApplicationService;
import com.linj6.interviewagent.interview.domain.InterviewErrorCode;
import com.linj6.interviewagent.interview.infrastructure.persistence.repository.QuestionRepository;
import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SkipQuestionTest {
    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    InterviewSkipApplicationService skip;

    @Autowired
    InterviewTurnApplicationService turns;

    @Autowired
    TestRestTemplate rest;

    @LocalServerPort
    int port;

    @MockBean
    InterviewAiApi ai;

    @SpyBean
    QuestionRepository questions;

    @Test
    void skipsMainAndCreatesNewMainWithoutAnswerEvaluationOrDecision() {
        Case c = prepare("MAIN", "WAITING_ANSWER");
        given(ai.generateMainQuestion(any(MainQuestionGenerationContext.class)))
                .willReturn(new GeneratedQuestion("新的主问题"));

        var response = rest.postForEntity(url(c), null, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(status(c.questionId())).isEqualTo("SKIPPED");
        assertThat(jdbc.queryForList(
                        "SELECT sequence_no, type, status, parent_question_id, topic_id, evaluation_profile_id FROM question WHERE interview_id = ? AND id <> ?",
                        c.interviewId(),
                        c.questionId()))
                .singleElement()
                .satisfies(row -> assertThat(row)
                        .containsEntry("sequence_no", 2)
                        .containsEntry("type", "MAIN")
                        .containsEntry("status", "WAITING_ANSWER")
                        .containsEntry("parent_question_id", null)
                        .containsEntry("topic_id", null)
                        .containsEntry("evaluation_profile_id", 1L));
        assertThat(interview(c))
                .containsEntry("status", "IN_PROGRESS")
                .containsEntry("current_phase", "WAITING_ANSWER")
                .containsEntry("version", 7L);
        assertNoAnswerOrEvaluation(c);
        ArgumentCaptor<MainQuestionGenerationContext> captured =
                ArgumentCaptor.forClass(MainQuestionGenerationContext.class);
        verify(ai).generateMainQuestion(captured.capture());
        assertThat(captured.getValue().focus()).isEqualTo("事务处理");
        assertThat(captured.getValue().area()).isEqualTo("JAVA");
        assertThat(captured.getValue().reason()).isEqualTo("计划理由");
        assertThat(captured.getValue().previousMainQuestions()).containsExactly("当前问题");
        verify(ai, never()).selectFocus(any());
        verify(ai, never()).evaluateAnswer(any(EvaluationContext.class));
        verify(ai, never()).decideNext(any(DecisionContext.class));
    }

    @Test
    void skipsFollowUpAndCreatesMain() {
        Case c = prepare("FOLLOW_UP", "WAITING_ANSWER");
        given(ai.generateMainQuestion(any(MainQuestionGenerationContext.class)))
                .willReturn(new GeneratedQuestion("新主问题"));

        skip.skipQuestion(c.interviewId(), c.questionId());

        assertThat(jdbc.queryForMap(
                        "SELECT sequence_no, type, parent_question_id, topic_id FROM question WHERE interview_id = ? ORDER BY sequence_no DESC LIMIT 1",
                        c.interviewId()))
                .containsEntry("sequence_no", 3)
                .containsEntry("type", "MAIN")
                .containsEntry("parent_question_id", null)
                .containsEntry("topic_id", null);
        assertNoAnswerOrEvaluation(c);
    }

    @Test
    void atMainLimitCompletesWithoutAi() {
        Case c = prepare("MAIN", "WAITING_ANSWER");
        for (int sequence = 2; sequence <= 5; sequence++) {
            jdbc.update(
                    "INSERT INTO question (interview_id, sequence_no, type, status, question_content, evaluation_profile_id, plan_item_id) VALUES (?, ?, 'MAIN', 'SKIPPED', '旧主问题', 1, ?)",
                    c.interviewId(),
                    sequence,
                    QuestionQualityTestPlans.next(jdbc, c.interviewId()));
        }
        // 当前待答题始终是序号最新的题。
        jdbc.update("UPDATE question SET sequence_no = 6 WHERE id = ?", c.questionId());

        skip.skipQuestion(c.interviewId(), c.questionId());

        assertThat(status(c.questionId())).isEqualTo("SKIPPED");
        assertThat(interview(c))
                .containsEntry("status", "COMPLETED")
                .containsEntry("current_phase", null)
                .containsEntry("version", 7L);
        assertThat(jdbc.queryForObject(
                        "SELECT completed_at FROM interview WHERE id = ?", Object.class, c.interviewId()))
                .isNotNull();
        assertThat(questionCount(c)).isEqualTo(5);
        verify(ai, never()).generateMainQuestion(any(MainQuestionGenerationContext.class));
        assertNoAnswerOrEvaluation(c);
    }

    @Test
    void rejectsIllegalInterviewStatesAndStaleQuestions() {
        for (String state : List.of("PAUSED:WAITING_ANSWER", "IN_PROGRESS:PROCESSING_TURN", "COMPLETED:null")) {
            Case c = prepare("MAIN", "WAITING_ANSWER");
            String[] parts = state.split(":");
            jdbc.update(
                    "UPDATE interview SET status = ?, current_phase = ? WHERE id = ?",
                    parts[0],
                    "null".equals(parts[1]) ? null : parts[1],
                    c.interviewId());
            assertThatThrownBy(() -> skip.skipQuestion(c.interviewId(), c.questionId()))
                    .isInstanceOf(BusinessException.class);
            assertThat(status(c.questionId())).isEqualTo("WAITING_ANSWER");
        }
        Case c = prepare("MAIN", "WAITING_ANSWER");
        Case other = prepare("MAIN", "WAITING_ANSWER");
        assertThatThrownBy(() -> skip.skipQuestion(c.interviewId(), other.questionId()))
                .isInstanceOf(BusinessException.class);
        jdbc.update("UPDATE question SET status = 'ANSWERED' WHERE id = ?", c.questionId());
        assertThatThrownBy(() -> skip.skipQuestion(c.interviewId(), c.questionId()))
                .isInstanceOf(BusinessException.class);
        jdbc.update("UPDATE question SET status = 'SKIPPED' WHERE id = ?", c.questionId());
        assertThatThrownBy(() -> skip.skipQuestion(c.interviewId(), c.questionId()))
                .isInstanceOf(BusinessException.class);
        jdbc.update("UPDATE question SET status = 'WAITING_ANSWER' WHERE id = ?", c.questionId());
        jdbc.update(
                "INSERT INTO question (interview_id, sequence_no, type, status, question_content, evaluation_profile_id, plan_item_id) VALUES (?, 2, 'MAIN', 'WAITING_ANSWER', '新题', 1, ?)",
                c.interviewId(),
                QuestionQualityTestPlans.next(jdbc, c.interviewId()));
        assertThatThrownBy(() -> skip.skipQuestion(c.interviewId(), c.questionId()))
                .isInstanceOf(BusinessException.class);
        verify(ai, never()).generateMainQuestion(any(MainQuestionGenerationContext.class));
    }

    @Test
    void retriesOnlyRecoverableGenerationFailureAndKeepsDatabaseOnTerminalFailure() {
        Case success = prepare("MAIN", "WAITING_ANSWER");
        given(ai.generateMainQuestion(any(MainQuestionGenerationContext.class)))
                .willThrow(new RuntimeException(new SocketTimeoutException("timeout")))
                .willReturn(new GeneratedQuestion("重试成功"));
        skip.skipQuestion(success.interviewId(), success.questionId());
        verify(ai, times(2)).generateMainQuestion(any(MainQuestionGenerationContext.class));

        org.mockito.Mockito.reset(ai);
        Case failure = prepare("MAIN", "WAITING_ANSWER");
        given(ai.generateMainQuestion(any(MainQuestionGenerationContext.class)))
                .willThrow(new RuntimeException(new SocketTimeoutException("timeout")));
        assertThatThrownBy(() -> skip.skipQuestion(failure.interviewId(), failure.questionId()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(CommonErrorCode.AI_SERVICE_TIMEOUT));
        verify(ai, times(2)).generateMainQuestion(any(MainQuestionGenerationContext.class));
        assertThat(status(failure.questionId())).isEqualTo("WAITING_ANSWER");
        assertThat(interview(failure)).containsEntry("version", 6L);
        assertThat(questionCount(failure)).isEqualTo(1);
        assertNoAnswerOrEvaluation(failure);
    }

    @Test
    void finalCasConflictRollsBackQuestionAndInsert() {
        Case c = prepare("MAIN", "WAITING_ANSWER");
        given(ai.generateMainQuestion(any(MainQuestionGenerationContext.class)))
                .willReturn(new GeneratedQuestion("候选新题"));
        doAnswer(invocation -> {
                    Object created = invocation.callRealMethod();
                    var executor = Executors.newSingleThreadExecutor();
                    try {
                        assertThat(executor.submit(() -> jdbc.update(
                                                "UPDATE interview SET version = version + 1 WHERE id = ? AND version = 6",
                                                c.interviewId()))
                                        .get(10, TimeUnit.SECONDS))
                                .isEqualTo(1);
                    } finally {
                        executor.shutdownNow();
                    }
                    return created;
                })
                .when(questions)
                .create(any());

        assertThatThrownBy(() -> skip.skipQuestion(c.interviewId(), c.questionId()))
                .isInstanceOf(BusinessException.class);
        assertThat(status(c.questionId())).isEqualTo("WAITING_ANSWER");
        assertThat(questionCount(c)).isEqualTo(1);
        assertThat(interview(c)).containsEntry("version", 7L);
    }

    @Test
    void concurrentSkipsAllowAtMostOneSuccess() throws Exception {
        Case c = prepare("MAIN", "WAITING_ANSWER");
        CountDownLatch bothAtAi = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        given(ai.generateMainQuestion(any(MainQuestionGenerationContext.class))).willAnswer(invocation -> {
            bothAtAi.countDown();
            assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
            return new GeneratedQuestion("并发新题");
        });
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> attempt(c));
            var second = executor.submit(() -> attempt(c));
            assertThat(bothAtAi.await(10, TimeUnit.SECONDS)).isTrue();
            release.countDown();
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
        assertThat(questionCount(c)).isEqualTo(2);
        assertThat(interview(c)).containsEntry("version", 7L);
    }

    @Test
    void concurrentSkipAndSubmitAllowOnlyOneConsistentBusinessPath() throws Exception {
        Case c = prepare("MAIN", "WAITING_ANSWER");
        given(ai.generateMainQuestion(any(MainQuestionGenerationContext.class)))
                .willReturn(new GeneratedQuestion("Skip 路径的新主问题"));
        given(ai.evaluateAnswer(any(EvaluationContext.class)))
                .willAnswer(invocation -> validEvaluation(invocation.getArgument(0)));
        given(ai.decideNext(any(DecisionContext.class)))
                .willReturn(new ValidatedDecisionResult(DecisionAction.END, "完成面试", null));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        OperationResult skipResultValue;
        OperationResult submitResultValue;
        try {
            var skipResult = executor.submit(() -> {
                ready.countDown();
                start.await();
                return runOperation(() -> skip.skipQuestion(c.interviewId(), c.questionId()));
            });
            var submitResult = executor.submit(() -> {
                ready.countDown();
                start.await();
                return runOperation(() -> turns.submitAnswer(c.interviewId(), c.questionId(), "并发提交的正式回答"));
            });
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            skipResultValue = skipResult.get(30, TimeUnit.SECONDS);
            submitResultValue = submitResult.get(30, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertThat(List.of(skipResultValue.succeeded(), submitResultValue.succeeded()))
                .containsExactlyInAnyOrder(true, false);
        OperationResult loser = skipResultValue.succeeded() ? submitResultValue : skipResultValue;
        assertThat(loser.failure()).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) loser.failure()).getErrorCode())
                .isIn(
                        InterviewErrorCode.INTERVIEW_STATE_CONFLICT,
                        InterviewErrorCode.INTERVIEW_VERSION_CONFLICT,
                        InterviewErrorCode.QUESTION_STATE_CONFLICT);
        if (skipResultValue.succeeded()) {
            assertThat(status(c.questionId())).isEqualTo("SKIPPED");
            assertNoAnswerOrEvaluation(c);
            assertThat(jdbc.queryForMap(
                            "SELECT sequence_no, type, status, parent_question_id, topic_id FROM question WHERE interview_id = ? ORDER BY sequence_no DESC LIMIT 1",
                            c.interviewId()))
                    .containsEntry("sequence_no", 2)
                    .containsEntry("type", "MAIN")
                    .containsEntry("status", "WAITING_ANSWER")
                    .containsEntry("parent_question_id", null)
                    .containsEntry("topic_id", null);
            assertThat(interview(c))
                    .containsEntry("status", "IN_PROGRESS")
                    .containsEntry("current_phase", "WAITING_ANSWER")
                    .containsEntry("version", 7L);
        } else {
            assertThat(status(c.questionId())).isEqualTo("ANSWERED");
            assertThat(jdbc.queryForObject(
                            "SELECT content FROM answer WHERE question_id = ?", String.class, c.questionId()))
                    .isEqualTo("并发提交的正式回答");
            assertThat(jdbc.queryForObject(
                            "SELECT COUNT(*) FROM evaluation WHERE question_id = ?", Integer.class, c.questionId()))
                    .isEqualTo(1);
            assertThat(questionCount(c)).isEqualTo(1);
            assertThat(interview(c))
                    .containsEntry("status", "COMPLETED")
                    .containsEntry("current_phase", null)
                    .containsEntry("version", 8L);
        }
    }

    private OperationResult runOperation(Runnable operation) {
        try {
            operation.run();
            return new OperationResult(true, null);
        } catch (RuntimeException conflict) {
            return new OperationResult(false, conflict);
        }
    }

    private ValidatedEvaluationResult validEvaluation(EvaluationContext context) {
        return new ValidatedEvaluationResult(
                context.profileId(),
                new BigDecimal("8.00"),
                new BigDecimal("80.00"),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                new ReferenceAnswerProposal(AnswerForm.SAMPLE_ANSWER, List.of("事务边界"), "参考回答"),
                "test-prompt",
                "test-evaluator",
                "test-model",
                10,
                20,
                30,
                5L);
    }

    private boolean attempt(Case c) {
        try {
            skip.skipQuestion(c.interviewId(), c.questionId());
            return true;
        } catch (BusinessException conflict) {
            return false;
        }
    }

    @Test
    void repeatedSkipConsumesItemsInPlanOrderAndPassesAllMainIncludingSkipped() {
        Case c = prepare("FOLLOW_UP", "WAITING_ANSWER");
        var firstUnused = QuestionQualityTestPlans.next(jdbc, c.interviewId());
        given(ai.generateMainQuestion(any())).willReturn(new GeneratedQuestion("新主问题"));
        skip.skipQuestion(c.interviewId(), c.questionId());
        long nextQuestion = jdbc.queryForObject(
                "SELECT id FROM question WHERE interview_id = ? ORDER BY sequence_no DESC LIMIT 1",
                Long.class,
                c.interviewId());
        assertThat(jdbc.queryForObject("SELECT plan_item_id FROM question WHERE id = ?", Long.class, nextQuestion))
                .isEqualTo(firstUnused);
        var secondUnused = QuestionQualityTestPlans.next(jdbc, c.interviewId());
        skip.skipQuestion(c.interviewId(), nextQuestion);
        var contexts = ArgumentCaptor.forClass(MainQuestionGenerationContext.class);
        verify(ai, times(2)).generateMainQuestion(contexts.capture());
        assertThat(contexts.getAllValues().get(0).previousMainQuestions()).containsExactly("主问题");
        assertThat(contexts.getAllValues().get(1).previousMainQuestions()).containsExactly("主问题", "新主问题");
        assertThat(status(nextQuestion)).isEqualTo("SKIPPED");
        assertThat(jdbc.queryForObject(
                        "SELECT plan_item_id FROM question WHERE interview_id = ? ORDER BY sequence_no DESC LIMIT 1",
                        Long.class,
                        c.interviewId()))
                .isEqualTo(secondUnused);
        verify(ai, never()).selectFocus(any());
    }

    @Test
    void exhaustedPlanIsAnExceptionAndDoesNotSkipOrEnd() {
        Case c = prepare("MAIN", "WAITING_ANSWER");
        jdbc.update(
                "DELETE FROM interview_plan_item WHERE plan_id = (SELECT id FROM interview_plan WHERE interview_id = ?) AND id <> (SELECT plan_item_id FROM question WHERE id = ?)",
                c.interviewId(),
                c.questionId());
        assertThatThrownBy(() -> skip.skipQuestion(c.interviewId(), c.questionId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exhausted");
        assertThat(status(c.questionId())).isEqualTo("WAITING_ANSWER");
        assertThat(interview(c)).containsEntry("version", 6L);
        verify(ai, never()).generateMainQuestion(any());
    }

    @Test
    void planChangedDuringAiIsRejectedWithoutWrites() {
        Case c = prepare("MAIN", "WAITING_ANSWER");
        given(ai.generateMainQuestion(any()))
                .willAnswer(invocation -> {
                    assertThat(org.springframework.transaction.support.TransactionSynchronizationManager
                                    .isActualTransactionActive())
                            .isFalse();
                    jdbc.update(
                            "UPDATE interview_plan_item SET focus = '改变方向' WHERE id = ?",
                            QuestionQualityTestPlans.next(jdbc, c.interviewId()));
                    return new GeneratedQuestion("候选问题");
                });
        assertThatThrownBy(() -> skip.skipQuestion(c.interviewId(), c.questionId()))
                .isInstanceOf(BusinessException.class);
        assertThat(status(c.questionId())).isEqualTo("WAITING_ANSWER");
        assertThat(questionCount(c)).isEqualTo(1);
        assertThat(interview(c)).containsEntry("version", 6L);
    }

    private Case prepare(String type, String phase) {
        jdbc.update(
                "INSERT INTO interview (candidate_id, position_id, status, current_phase, version) VALUES (1, 1, 'IN_PROGRESS', ?, 6)",
                phase);
        long interviewId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        var itemId = QuestionQualityTestPlans.prepare(jdbc, interviewId, "事务处理").get(0);
        Long parent = null;
        int sequence = 1;
        if ("FOLLOW_UP".equals(type)) {
            jdbc.update(
                    "INSERT INTO question (interview_id, sequence_no, type, status, question_content, evaluation_profile_id, plan_item_id) VALUES (?, 1, 'MAIN', 'ANSWERED', '主问题', 1, ?)",
                    interviewId,
                    itemId);
            parent = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
            sequence = 2;
        }
        jdbc.update(
                "INSERT INTO question (interview_id, sequence_no, parent_question_id, type, status, question_content, evaluation_profile_id, topic_id, plan_item_id) VALUES (?, ?, ?, ?, 'WAITING_ANSWER', '当前问题', 1, 8, ?)",
                interviewId,
                sequence,
                parent,
                type,
                itemId);
        long questionId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return new Case(interviewId, questionId);
    }

    private String url(Case c) {
        return "http://localhost:" + port + "/api/interviews/" + c.interviewId() + "/questions/" + c.questionId()
                + "/skip";
    }

    private String status(long questionId) {
        return jdbc.queryForObject("SELECT status FROM question WHERE id = ?", String.class, questionId);
    }

    private Map<String, Object> interview(Case c) {
        return jdbc.queryForMap("SELECT status, current_phase, version FROM interview WHERE id = ?", c.interviewId());
    }

    private int questionCount(Case c) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM question WHERE interview_id = ?", Integer.class, c.interviewId());
    }

    private void assertNoAnswerOrEvaluation(Case c) {
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM answer WHERE interview_id = ?", Integer.class, c.interviewId()))
                .isZero();
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM evaluation WHERE interview_id = ?", Integer.class, c.interviewId()))
                .isZero();
    }

    private record Case(long interviewId, long questionId) {}

    private record OperationResult(boolean succeeded, RuntimeException failure) {}
}
