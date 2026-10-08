package com.linj6.interviewagent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import com.linj6.interviewagent.ai.api.decision.DecisionAction;
import com.linj6.interviewagent.ai.api.decision.ValidatedDecisionResult;
import com.linj6.interviewagent.ai.api.evaluation.AnswerForm;
import com.linj6.interviewagent.ai.api.evaluation.ReferenceAnswerProposal;
import com.linj6.interviewagent.ai.api.evaluation.ValidatedEvaluationResult;
import com.linj6.interviewagent.ai.api.question.GeneratedQuestion;
import com.linj6.interviewagent.common.exception.BusinessException;
import com.linj6.interviewagent.interview.application.InterviewTurnApplicationService;
import com.linj6.interviewagent.interview.infrastructure.persistence.repository.QuestionRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
class ProcessedTurnTransactionTest {
    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private InterviewTurnApplicationService application;

    @SpyBean
    private QuestionRepository questions;

    @Test
    void commitsFollowUp() {
        Turn turn = prepareTurn();

        persist(turn, DecisionAction.FOLLOW_UP, "追问");

        assertSuccessfulTurn(turn, "FOLLOW_UP", turn.questionId(), turn.topicId());
    }

    @Test
    void commitsNextQuestion() {
        Turn turn = prepareTurn();

        persist(turn, DecisionAction.NEXT_QUESTION, "下一道主问题");

        assertSuccessfulTurn(turn, "MAIN", null, null);
    }

    @Test
    void commitsEnd() {
        Turn turn = prepareTurn();

        persist(turn, DecisionAction.END, null);

        assertThat(count("evaluation", "question_id", turn.questionId())).isEqualTo(1);
        assertThat(questionStatus(turn.questionId())).isEqualTo("ANSWERED");
        assertThat(count("question", "interview_id", turn.interviewId())).isEqualTo(1);
        assertThat(jdbc.queryForMap(
                        "SELECT status, current_phase, version FROM interview WHERE id = ?", turn.interviewId()))
                .containsEntry("status", "COMPLETED")
                .containsEntry("current_phase", null)
                .containsEntry("version", turn.version() + 1);
        assertThat(jdbc.queryForObject(
                        "SELECT completed_at FROM interview WHERE id = ?", Object.class, turn.interviewId()))
                .isNotNull();
    }

    @Test
    void rollsBackAllWritesWhenFinalInterviewCasLoses() {
        Turn turn = prepareTurn();
        // The spy only coordinates the test. The real repository INSERT runs before a second connection commits
        // a competing Interview version update; the production CAS then executes against that database state.
        doAnswer(invocation -> {
                    Object created = invocation.callRealMethod();
                    assertThat(count("evaluation", "question_id", turn.questionId()))
                            .isEqualTo(1);
                    assertThat(questionStatus(turn.questionId())).isEqualTo("ANSWERED");
                    assertThat(count("question", "interview_id", turn.interviewId()))
                            .isEqualTo(2);
                    var executor = Executors.newSingleThreadExecutor();
                    try {
                        assertThat(executor.submit(() -> jdbc.update(
                                                "UPDATE interview SET version = version + 1 WHERE id = ? AND version = ?",
                                                turn.interviewId(),
                                                turn.version()))
                                        .get(10, TimeUnit.SECONDS))
                                .isEqualTo(1);
                    } finally {
                        executor.shutdownNow();
                    }
                    return created;
                })
                .when(questions)
                .create(any());

        assertThatThrownBy(() -> persist(turn, DecisionAction.NEXT_QUESTION, "回滚的主问题"))
                .isInstanceOf(BusinessException.class);

        assertThat(count("evaluation", "question_id", turn.questionId())).isZero();
        assertThat(questionStatus(turn.questionId())).isEqualTo("WAITING_ANSWER");
        assertThat(count("question", "interview_id", turn.interviewId())).isEqualTo(1);
        assertThat(count("answer", "question_id", turn.questionId())).isEqualTo(1);
        assertThat(jdbc.queryForMap(
                        "SELECT status, current_phase, version FROM interview WHERE id = ?", turn.interviewId()))
                .containsEntry("status", "IN_PROGRESS")
                .containsEntry("current_phase", "PROCESSING_TURN")
                .containsEntry("version", turn.version() + 1);
    }

    @Test
    void concurrentNextMainCommitsOnlyOnePlanItemUseAndOneEvaluation() throws Exception {
        Turn turn = prepareTurn();
        var start = new java.util.concurrent.CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<Boolean> attempt = () -> {
                start.await();
                try {
                    persist(turn, DecisionAction.NEXT_QUESTION, "并发主问题");
                    return true;
                } catch (RuntimeException failure) {
                    return false;
                }
            };
            var first = pool.submit(attempt);
            var second = pool.submit(attempt);
            start.countDown();
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        } finally {
            pool.shutdownNow();
        }
        assertThat(count("evaluation", "question_id", turn.questionId())).isEqualTo(1);
        assertThat(count("question", "interview_id", turn.interviewId())).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(DISTINCT plan_item_id) FROM question WHERE interview_id = ? AND type = 'MAIN'",
                        Integer.class,
                        turn.interviewId()))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT version FROM interview WHERE id = ?", Long.class, turn.interviewId()))
                .isEqualTo(7L);
    }

    private Turn prepareTurn() {
        jdbc.update(
                "INSERT INTO interview (candidate_id, position_id, status, current_phase, version) VALUES (1, 1, 'IN_PROGRESS', 'PROCESSING_TURN', 6)");
        long interviewId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        var itemId = QuestionQualityTestPlans.prepare(jdbc, interviewId, "并发控制").get(0);
        jdbc.update(
                "INSERT INTO question (interview_id, sequence_no, type, status, question_content, evaluation_profile_id, topic_id, plan_item_id) VALUES (?, 4, 'MAIN', 'WAITING_ANSWER', '当前问题', 1, 8, ?)",
                interviewId,
                itemId);
        long questionId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        jdbc.update(
                "INSERT INTO answer (interview_id, question_id, content) VALUES (?, ?, '已提交回答')",
                interviewId,
                questionId);
        long answerId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return new Turn(interviewId, questionId, answerId, 6L, 8L);
    }

    private void persist(Turn turn, DecisionAction action, String nextContent) {
        application.persistProcessedTurn(
                turn.interviewId(),
                turn.questionId(),
                turn.answerId(),
                turn.version(),
                new ValidatedEvaluationResult(
                        1L,
                        new BigDecimal("8.00"),
                        new BigDecimal("80.00"),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        new ReferenceAnswerProposal(AnswerForm.SAMPLE_ANSWER, List.of(), "参考回答"),
                        "test-prompt",
                        "test-evaluator",
                        "test-model",
                        1,
                        1,
                        2,
                        1L),
                new ValidatedDecisionResult(
                        action,
                        "测试决策",
                        null,
                        action == DecisionAction.NEXT_QUESTION
                                ? QuestionQualityTestPlans.next(jdbc, turn.interviewId())
                                : null),
                nextContent == null ? null : new GeneratedQuestion(nextContent),
                action == DecisionAction.NEXT_QUESTION ? "并发控制" : null);
    }

    private void assertSuccessfulTurn(Turn turn, String type, Long parentId, Long topicId) {
        assertThat(count("evaluation", "question_id", turn.questionId())).isEqualTo(1);
        assertThat(questionStatus(turn.questionId())).isEqualTo("ANSWERED");
        List<Map<String, Object>> next = jdbc.queryForList(
                "SELECT sequence_no, parent_question_id, type, status, evaluation_profile_id, topic_id, plan_item_id FROM question WHERE interview_id = ? AND id <> ?",
                turn.interviewId(),
                turn.questionId());
        assertThat(next).hasSize(1);
        assertThat(next.get(0))
                .containsEntry("sequence_no", 5)
                .containsEntry("parent_question_id", parentId)
                .containsEntry("type", type)
                .containsEntry("status", "WAITING_ANSWER")
                .containsEntry("evaluation_profile_id", 1L)
                .containsEntry("topic_id", topicId);
        Long originalItem =
                jdbc.queryForObject("SELECT plan_item_id FROM question WHERE id = ?", Long.class, turn.questionId());
        if ("FOLLOW_UP".equals(type))
            assertThat(next.get(0).get("plan_item_id")).isEqualTo(originalItem);
        else assertThat(next.get(0).get("plan_item_id")).isNotNull().isNotEqualTo(originalItem);
        assertThat(jdbc.queryForMap(
                        "SELECT status, current_phase, version FROM interview WHERE id = ?", turn.interviewId()))
                .containsEntry("status", "IN_PROGRESS")
                .containsEntry("current_phase", "WAITING_ANSWER")
                .containsEntry("version", turn.version() + 1);
    }

    private int count(String table, String column, long id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?", Integer.class, id);
    }

    private String questionStatus(long questionId) {
        return jdbc.queryForObject("SELECT status FROM question WHERE id = ?", String.class, questionId);
    }

    private record Turn(long interviewId, long questionId, long answerId, long version, long topicId) {}
}
