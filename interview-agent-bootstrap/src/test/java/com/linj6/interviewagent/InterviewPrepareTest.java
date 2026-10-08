package com.linj6.interviewagent;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.linj6.interviewagent.ai.api.InterviewAiApi;
import com.linj6.interviewagent.ai.api.planner.*;
import com.linj6.interviewagent.common.exception.BusinessException;
import com.linj6.interviewagent.interview.application.*;
import com.linj6.interviewagent.interview.domain.*;
import com.linj6.interviewagent.interview.infrastructure.persistence.repository.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class InterviewPrepareTest {
    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.4").withCommand("--log-bin-trust-function-creators=1");

    @Autowired
    InterviewPrepareApplicationService prepare;

    @Autowired
    InterviewRepository interviews;

    @org.springframework.boot.test.mock.mockito.SpyBean
    InterviewPlanRepository plans;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TestRestTemplate http;

    @MockBean
    InterviewAiApi ai;

    ValidatedPlanResult result() {
        return new ValidatedPlanResult(
                java.util.stream.IntStream.range(0, 5)
                        .mapToObj(i -> new ValidatedPlanResult.Item("JAVA", "focus" + i, "reason"))
                        .toList(),
                2);
    }

    Long create() {
        return interviews.create(1L, 1L).id();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
        "0, PROJECT_DEEP_DIVE, 400",
        "1, PROJECT_DEEP_DIVE, 200",
        "2, PROJECT_DEEP_DIVE, 200",
        "0, COMPREHENSIVE, 200",
        "0, TECHNICAL_FOCUS, 200"
    })
    void currentProjectsOnlyRestrictDeepDiveCreation(int projectCount, InterviewMode mode, int expectedStatus) {
        Long resumeId = jdbc.queryForObject("SELECT id FROM candidate_resume WHERE candidate_id=1", Long.class);
        var originalProjects = jdbc.queryForList("SELECT * FROM candidate_project WHERE resume_id=?", resumeId);
        Integer before = jdbc.queryForObject("SELECT COUNT(*) FROM interview", Integer.class);
        try {
            jdbc.update("DELETE FROM candidate_project WHERE resume_id=?", resumeId);
            for (int i = 0; i < projectCount; i++) {
                jdbc.update(
                        "INSERT INTO candidate_project(id,resume_id,name,description) VALUES(?,?,?,?)",
                        9000L + i,
                        resumeId,
                        "项目" + i,
                        "项目描述" + i);
            }
            Map<String, Object> request = new HashMap<>();
            request.put("mode", mode.name());
            if (mode == InterviewMode.TECHNICAL_FOCUS) request.put("focusArea", "MYSQL");
            var response = http.postForEntity("/api/interviews", request, Map.class);
            assertThat(response.getStatusCode().value()).isEqualTo(expectedStatus);
            assertThat(response.getBody().get("code")).isEqualTo(expectedStatus == 400 ? 10001 : 0);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM interview", Integer.class))
                    .isEqualTo(before + (expectedStatus == 400 ? 0 : 1));
            if (expectedStatus == 400) {
                assertThat(response.getBody().get("data")).isNull();
            } else {
                Map<?, ?> data = (Map<?, ?>) response.getBody().get("data");
                var persisted = interviews.findById(Long.parseLong((String) data.get("id")));
                assertThat(persisted.mode()).isEqualTo(mode);
                assertThat(persisted.focusArea())
                        .isEqualTo(mode == InterviewMode.TECHNICAL_FOCUS ? KnowledgeArea.MYSQL : null);
                assertThat(persisted.status()).isEqualTo(InterviewStatus.CREATED);
            }
            verifyNoInteractions(ai);
        } finally {
            jdbc.update("DELETE FROM candidate_project WHERE resume_id=?", resumeId);
            for (var project : originalProjects) {
                jdbc.update(
                        "INSERT INTO candidate_project(id,resume_id,name,description) VALUES(?,?,?,?)",
                        project.get("id"),
                        project.get("resume_id"),
                        project.get("name"),
                        project.get("description"));
            }
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(InterviewMode.class)
    void createAndGetPersistModeAndUnpreparedProjection(InterviewMode mode) {
        Map<String, Object> request = new HashMap<>();
        request.put("mode", mode.name());
        if (mode == InterviewMode.TECHNICAL_FOCUS) request.put("focusArea", "MYSQL");
        var created = http.postForEntity("/api/interviews", request, Map.class);
        assertThat(created.getStatusCode().value()).isEqualTo(200);
        Map<?, ?> data = (Map<?, ?>) created.getBody().get("data");
        Long id = Long.parseLong((String) data.get("id"));
        assertThat(data.get("mode")).isEqualTo(mode.name());
        assertThat(data.get("focusArea")).isEqualTo(request.get("focusArea"));
        assertThat(data.get("planPrepared")).isEqualTo(false);
        assertThat(data.get("status")).isEqualTo("CREATED");
        assertThat(data.get("currentPhase")).isNull();
        assertThat(data.get("firstQuestion")).isNull();
        assertThat(data.get("currentQuestion")).isNull();
        assertThat(data.get("expiresAt")).isNull();
        assertThat(interviews.findById(id).mode()).isEqualTo(mode);
        assertThat(interviews.findById(id).focusArea())
                .isEqualTo(mode == InterviewMode.TECHNICAL_FOCUS ? KnowledgeArea.MYSQL : null);
        var read = http.getForEntity("/api/interviews/" + id, Map.class);
        assertThat(read.getStatusCode().value()).isEqualTo(200);
        assertThat(read.getBody().get("data")).isEqualTo(data);
        assertThat(created.getBody().get("requestId"))
                .isEqualTo(created.getHeaders().getFirst("X-Request-Id"));
        verifyNoInteractions(ai);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(KnowledgeArea.class)
    void technicalFocusAcceptsEveryConfirmedArea(KnowledgeArea area) {
        var created = http.postForEntity(
                "/api/interviews", Map.of("mode", "TECHNICAL_FOCUS", "focusArea", area.name()), Map.class);
        assertThat(created.getStatusCode().value()).isEqualTo(200);
        Map<?, ?> data = (Map<?, ?>) created.getBody().get("data");
        assertThat(data.get("focusArea")).isEqualTo(area.name());
        assertThat(interviews.findById(Long.parseLong((String) data.get("id"))).focusArea())
                .isEqualTo(area);
        verifyNoInteractions(ai);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(
            strings = {
                "{}",
                "{\"mode\":null}",
                "{\"mode\":\"TECHNICAL_FOCUS\"}",
                "{\"mode\":\"COMPREHENSIVE\",\"focusArea\":\"JAVA\"}",
                "{\"mode\":\"PROJECT_DEEP_DIVE\",\"focusArea\":\"JAVA\"}",
                "{\"mode\":\"UNKNOWN\"}",
                "{\"mode\":\"TECHNICAL_FOCUS\",\"focusArea\":\"UNKNOWN\"}",
                "{\"mode\":\"\"}",
                "{\"mode\":\"TECHNICAL_FOCUS\",\"focusArea\":null}"
            })
    void rejectsInvalidCreateWithoutPersisting(String body) {
        Integer before = jdbc.queryForObject("SELECT COUNT(*) FROM interview", Integer.class);
        var headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        var response = http.postForEntity(
                "/api/interviews", new org.springframework.http.HttpEntity<>(body, headers), Map.class);
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().get("code")).isIn(10001, 10003);
        assertThat(response.getBody().get("data")).isNull();
        assertThat(response.getBody().get("requestId"))
                .isEqualTo(response.getHeaders().getFirst("X-Request-Id"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM interview", Integer.class))
                .isEqualTo(before);
        verifyNoInteractions(ai);
    }

    @Test
    void rejectsMissingCreateBodyAndMissingGet() {
        assertThat(http.postForEntity("/api/interviews", null, Map.class)
                        .getStatusCode()
                        .value())
                .isEqualTo(400);
        var missing = http.getForEntity("/api/interviews/9223372036854775807", Map.class);
        assertThat(missing.getStatusCode().value()).isEqualTo(404);
        assertThat(missing.getBody().get("code")).isEqualTo(23001);
        verifyNoInteractions(ai);
    }

    private void assertPlanPrepared(Long id, boolean prepared) {
        var response = http.getForEntity("/api/interviews/" + id, Map.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        Map<?, ?> data = (Map<?, ?>) response.getBody().get("data");
        assertThat(data.get("planPrepared")).isEqualTo(prepared);
        assertThat(data.get("status")).isEqualTo("CREATED");
        assertThat(data.get("currentPhase")).isNull();
    }

    @Test
    void httpSuccessAndDuplicateConflictUseRealIdsAndKeepCreated() {
        Long id = create();
        assertPlanPrepared(id, false);
        when(ai.preparePlan(any())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                    .isFalse();
            PlannerContext c = call.getArgument(0);
            assertThat(c.mode()).isEqualTo("COMPREHENSIVE");
            assertThat(c.focusArea()).isNull();
            assertThat(c.candidateName()).isNotBlank();
            assertThat(c.positionTitle()).isNotBlank();
            return result();
        });
        var response = http.postForEntity("/api/interviews/" + id + "/prepare", null, Map.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertPlanPrepared(id, true);
        var plan = plans.findByInterviewId(id);
        assertThat(plan.items()).hasSize(5);
        assertThat(plan.items()).extracting(PlanItem::id).contains(plan.initialPlanItemId());
        assertThat(response.getBody().get("data")).isInstanceOf(Map.class);
        assertThat(interviews.findById(id).status()).isEqualTo(InterviewStatus.CREATED);
        assertThat(interviews.findById(id).currentPhase()).isNull();
        assertThat(http.postForEntity("/api/interviews/" + id + "/prepare", null, Map.class)
                        .getStatusCode()
                        .value())
                .isEqualTo(409);
        verify(ai, times(1)).preparePlan(any());
    }

    @Test
    void failureLeavesNoPlanAndAllowsRetry() {
        Long id = create();
        when(ai.preparePlan(any()))
                .thenThrow(new com.linj6.interviewagent.ai.evaluator.EvaluationValidationException("invalid"))
                .thenReturn(result());
        assertThat(http.postForEntity("/api/interviews/" + id + "/prepare", null, Map.class)
                        .getStatusCode()
                        .value())
                .isEqualTo(502);
        assertThat(plans.findByInterviewId(id)).isNull();
        assertPlanPrepared(id, false);
        prepare.prepareInterview(id);
        assertPlanPrepared(id, true);
        assertThat(plans.findByInterviewId(id)).isNotNull();
    }

    @Test
    void rejectsWrongStateAndInvalidValidatedResult() {
        Long id = create();
        jdbc.update("UPDATE interview SET status='IN_PROGRESS',current_phase='OPENING' WHERE id=?", id);
        assertThatThrownBy(() -> prepare.prepareInterview(id)).isInstanceOf(BusinessException.class);
        verify(ai, never()).preparePlan(any());
        Long fresh = create();
        when(ai.preparePlan(any()))
                .thenReturn(new ValidatedPlanResult(List.of(new ValidatedPlanResult.Item("OTHER", "f", "r")), 0));
        assertThatThrownBy(() -> prepare.prepareInterview(fresh)).isInstanceOf(BusinessException.class);
        assertThat(plans.findByInterviewId(fresh)).isNull();
    }

    @Test
    void concurrentCalculationsPersistExactlyOneAggregate() throws Exception {
        Long id = create();
        var barrier = new CyclicBarrier(2);
        when(ai.preparePlan(any())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                    .isFalse();
            barrier.await(15, TimeUnit.SECONDS);
            return result();
        });
        var executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> task = () -> {
                try {
                    prepare.prepareInterview(id);
                    return true;
                } catch (BusinessException e) {
                    assertThat(e.getErrorCode()).isEqualTo(InterviewErrorCode.INTERVIEW_STATE_CONFLICT);
                    return false;
                }
            };
            var a = executor.submit(task);
            var b = executor.submit(task);
            assertThat(List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
            assertThat(jdbc.queryForObject(
                            "SELECT COUNT(*) FROM interview_plan WHERE interview_id=?", Integer.class, id))
                    .isEqualTo(1);
            assertThat(plans.findByInterviewId(id).items()).hasSize(5);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void itemInsertFailureRollsBackWholeAggregateAndCanRetry() {
        Long id = create();
        when(ai.preparePlan(any())).thenReturn(result());
        jdbc.execute(
                "CREATE TRIGGER fail_prepare_item BEFORE INSERT ON interview_plan_item FOR EACH ROW BEGIN IF NEW.focus = 'focus2' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'test rollback'; END IF; END");
        try {
            assertThatThrownBy(() -> prepare.prepareInterview(id))
                    .isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThat(plans.findByInterviewId(id)).isNull();
            assertThat(jdbc.queryForObject(
                            "SELECT COUNT(*) FROM interview_plan_item i LEFT JOIN interview_plan p ON p.id=i.plan_id WHERE p.id IS NULL",
                            Integer.class))
                    .isZero();
        } finally {
            jdbc.execute("DROP TRIGGER fail_prepare_item");
        }
        prepare.prepareInterview(id);
        assertThat(plans.findByInterviewId(id).items()).hasSize(5);
    }

    @Test
    void stateChangeDuringAiIsRejectedAtCommit() {
        Long id = create();
        when(ai.preparePlan(any())).thenAnswer(call -> {
            jdbc.update(
                    "UPDATE interview SET status='IN_PROGRESS',current_phase='OPENING',version=version+1 WHERE id=?",
                    id);
            return result();
        });
        assertThatThrownBy(() -> prepare.prepareInterview(id)).isInstanceOf(BusinessException.class);
        assertThat(plans.findByInterviewId(id)).isNull();
    }

    @Test
    void databaseUniqueConflictIsBusinessConflictAndRollsBack() {
        Long id = create();
        when(ai.preparePlan(any())).thenReturn(result());
        doAnswer(call -> {
                    var row = (InterviewPlan) call.getArgument(0);
                    jdbc.update(
                            "INSERT INTO interview_plan(id,interview_id,initial_plan_item_id) VALUES(?,?,?)",
                            row.id() + 1,
                            row.interviewId(),
                            row.initialPlanItemId());
                    return call.callRealMethod();
                })
                .when(plans)
                .save(any(InterviewPlan.class), org.mockito.ArgumentMatchers.anyInt());
        try {
            assertThatThrownBy(() -> prepare.prepareInterview(id))
                    .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                            .isEqualTo(InterviewErrorCode.INTERVIEW_STATE_CONFLICT));
            assertThat(plans.findByInterviewId(id)).isNull();
        } finally {
            reset(plans);
        }
        prepare.prepareInterview(id);
        assertThat(plans.findByInterviewId(id).items()).hasSize(5);
    }
}
