package com.linj6.interviewagent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.linj6.interviewagent.ai.api.InterviewAiApi;
import com.linj6.interviewagent.ai.api.decision.DecisionAction;
import com.linj6.interviewagent.ai.api.decision.DecisionContext;
import com.linj6.interviewagent.ai.api.decision.ValidatedDecisionResult;
import com.linj6.interviewagent.ai.api.evaluation.AnswerForm;
import com.linj6.interviewagent.ai.api.evaluation.Applicability;
import com.linj6.interviewagent.ai.api.evaluation.EvaluationContext;
import com.linj6.interviewagent.ai.api.evaluation.EvidenceType;
import com.linj6.interviewagent.ai.api.evaluation.Importance;
import com.linj6.interviewagent.ai.api.evaluation.MissingPointProposal;
import com.linj6.interviewagent.ai.api.evaluation.ReferenceAnswerProposal;
import com.linj6.interviewagent.ai.api.evaluation.ValidatedDimensionEvaluation;
import com.linj6.interviewagent.ai.api.evaluation.ValidatedEvaluationResult;
import com.linj6.interviewagent.ai.api.evaluation.ValidatedEvidence;
import com.linj6.interviewagent.ai.api.focus.FocusProposal;
import com.linj6.interviewagent.ai.api.question.FollowUpQuestionGenerationContext;
import com.linj6.interviewagent.ai.api.question.GeneratedQuestion;
import com.linj6.interviewagent.ai.api.question.MainQuestionGenerationContext;
import com.linj6.interviewagent.common.api.CommonErrorCode;
import com.linj6.interviewagent.common.exception.BusinessException;
import com.linj6.interviewagent.evaluation.api.evaluation.EvaluationCommand;
import com.linj6.interviewagent.evaluation.api.evaluation.ReferenceAnswer;
import com.linj6.interviewagent.evaluation.domain.EvaluationErrorCode;
import com.linj6.interviewagent.evaluation.infrastructure.persistence.repository.EvaluationRepository;
import com.linj6.interviewagent.interview.application.InterviewApplicationService;
import com.linj6.interviewagent.interview.application.InterviewSkipApplicationService;
import com.linj6.interviewagent.interview.application.InterviewTurnApplicationService;
import com.linj6.interviewagent.interview.domain.InterviewErrorCode;
import com.linj6.interviewagent.interview.domain.InterviewMode;
import com.linj6.interviewagent.interview.domain.InterviewPhase;
import com.linj6.interviewagent.interview.domain.InterviewPlan;
import com.linj6.interviewagent.interview.domain.InterviewStatus;
import com.linj6.interviewagent.interview.domain.KnowledgeArea;
import com.linj6.interviewagent.interview.domain.PlanItem;
import com.linj6.interviewagent.interview.domain.QuestionType;
import com.linj6.interviewagent.interview.infrastructure.persistence.entity.InterviewDO;
import com.linj6.interviewagent.interview.infrastructure.persistence.mapper.InterviewMapper;
import com.linj6.interviewagent.interview.infrastructure.persistence.repository.AnswerRepository;
import com.linj6.interviewagent.interview.infrastructure.persistence.repository.InterviewPlanRepository;
import com.linj6.interviewagent.interview.infrastructure.persistence.repository.InterviewRepository;
import com.linj6.interviewagent.interview.infrastructure.persistence.repository.QuestionCreateCommand;
import com.linj6.interviewagent.interview.infrastructure.persistence.repository.QuestionRepository;
import com.linj6.interviewagent.position.infrastructure.persistence.entity.PositionDO;
import com.linj6.interviewagent.position.infrastructure.persistence.mapper.PositionMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.SocketTimeoutException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.interview.duration-minutes=45")
class DatabaseMigrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.4").withCommand("--log-bin-trust-function-creators=1");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Flyway flyway;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private PositionMapper positionMapper;

    @Autowired
    private InterviewMapper interviewMapper;

    @Autowired
    private InterviewRepository interviewRepository;

    @Autowired
    private AnswerRepository answerRepository;

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private EvaluationRepository evaluationRepository;

    @Autowired
    private InterviewApplicationService interviewApplicationService;

    @Autowired
    private InterviewTurnApplicationService interviewTurnApplicationService;

    @Autowired
    private InterviewSkipApplicationService interviewSkipApplicationService;

    @MockBean
    private InterviewAiApi interviewAiApi;

    @MockBean
    private Clock clock;

    @LocalServerPort
    private int port;

    @BeforeEach
    void defaultTurnDecision() {
        given(clock.getZone()).willReturn(ZoneId.of("Asia/Shanghai"));
        given(clock.instant()).willReturn(Instant.parse("2026-09-26T03:00:00Z"));
        given(interviewAiApi.selectFocus(any())).willReturn(new FocusProposal("事务处理"));
        given(interviewAiApi.generateMainQuestion(any())).willReturn(new GeneratedQuestion("默认主问题"));
        given(interviewAiApi.generateFollowUpQuestion(any(FollowUpQuestionGenerationContext.class)))
                .willReturn(new GeneratedQuestion("默认追问"));
        given(interviewAiApi.decideNext(any(DecisionContext.class)))
                .willReturn(new ValidatedDecisionResult(DecisionAction.END, "测试结束", null));
    }

    @Autowired
    private InterviewPlanRepository planRepository;

    @Test
    void mapsPlanFoundationAndEnforcesTransitionConstraints() {
        var interview = interviewRepository.create(1L, 1L, InterviewMode.TECHNICAL_FOCUS, KnowledgeArea.MYSQL);
        assertThat(interviewRepository.findById(interview.id())).isEqualTo(interview);
        assertThat(planRepository.findByInterviewId(interview.id())).isNull();
        long planId = IdWorker.getId();
        long itemId = IdWorker.getId();
        var item = new PlanItem(itemId, planId, KnowledgeArea.MYSQL, "事务隔离", "检验并发事务理解");
        var plan = new InterviewPlan(planId, interview.id(), itemId, List.of(item));
        planRepository.save(plan, 1);
        assertThat(planRepository.findByInterviewId(interview.id())).isEqualTo(plan);
        assertThatThrownBy(() -> planRepository.save(
                        new InterviewPlan(
                                planId + 10,
                                interview.id(),
                                itemId + 10,
                                List.of(new PlanItem(itemId + 10, planId + 10, item.area(), "other", "reason"))),
                        1))
                .isInstanceOf(DuplicateKeyException.class);
        var main = questionRepository.create(QuestionCreateCommand.builder()
                .interviewId(interview.id())
                .sequenceNo(1)
                .type(QuestionType.MAIN)
                .content("main")
                .evaluationProfileId(1L)
                .planItemId(itemId)
                .build());
        assertThat(questionRepository.findById(main.id()).planItemId()).isEqualTo(itemId);
        for (int sequence : List.of(2, 3)) {
            var follow = questionRepository.create(QuestionCreateCommand.builder()
                    .interviewId(interview.id())
                    .sequenceNo(sequence)
                    .parentQuestionId(main.id())
                    .type(QuestionType.FOLLOW_UP)
                    .content("follow")
                    .evaluationProfileId(1L)
                    .planItemId(itemId)
                    .build());
            assertThat(questionRepository.findByInterviewId(interview.id())).contains(follow);
        }
        assertThatThrownBy(() -> questionRepository.create(QuestionCreateCommand.builder()
                        .interviewId(interview.id())
                        .sequenceNo(4)
                        .type(QuestionType.MAIN)
                        .content("duplicate")
                        .evaluationProfileId(1L)
                        .planItemId(itemId)
                        .build()))
                .isInstanceOf(DuplicateKeyException.class);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT IS_NULLABLE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'question' AND COLUMN_NAME = 'plan_item_id'",
                        String.class))
                .isEqualTo("YES");
        assertThatThrownBy(() -> interviewRepository.create(1L, 1L, InterviewMode.TECHNICAL_FOCUS, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA = DATABASE() AND CONSTRAINT_NAME IN ('ck_interview_mode_area', 'ck_plan_item_area')",
                        Integer.class))
                .isZero();
        assertThatThrownBy(() -> jdbcTemplate.update(
                        "INSERT INTO interview_plan (id, interview_id, initial_plan_item_id) VALUES (?, ?, NULL)",
                        planId + 20,
                        interview.id() + 100))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                        "INSERT INTO interview_plan_item (id, plan_id, area, focus, reason) VALUES (?, ?, 'JAVA', NULL, 'reason')",
                        itemId + 20,
                        planId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME IN ('interview_plan', 'interview_plan_item')",
                        Integer.class))
                .isZero();
    }

    @Test
    void rollsBackWholePlanWhenAnItemInsertFails() {
        var interview = interviewRepository.create(1L, 1L);
        long planId = IdWorker.getId();
        long itemId = IdWorker.getId();
        jdbcTemplate.update(
                "INSERT INTO interview_plan_item (id, plan_id, area, focus, reason) VALUES (?, ?, 'JAVA', 'existing', 'reason')",
                itemId,
                planId + 1);
        var plan = new InterviewPlan(
                planId,
                interview.id(),
                itemId,
                List.of(
                        new PlanItem(itemId - 1, planId, KnowledgeArea.JAVA, "first", "reason"),
                        new PlanItem(itemId, planId, KnowledgeArea.JAVA, "second", "reason")));
        assertThatThrownBy(() -> planRepository.save(plan, 2)).isInstanceOf(DuplicateKeyException.class);
        assertThat(planRepository.findByInterviewId(interview.id())).isNull();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM interview_plan_item WHERE plan_id = ?", Integer.class, planId))
                .isZero();
    }

    @Test
    void connectsToMySqlAndAppliesBaselineMigration() {
        Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);

        assertThat(result).isEqualTo(1);
        assertThat(flyway.info().applied())
                .extracting(info -> info.getVersion().getVersion())
                .contains("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM candidate", Integer.class))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT IS_NULLABLE FROM information_schema.COLUMNS "
                                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'question' AND COLUMN_NAME = 'plan_item_id'",
                        String.class))
                .isEqualTo("YES");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM evaluation_profile", Integer.class))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM evaluation_dimension_config WHERE profile_id = 1", Integer.class))
                .isEqualTo(7);
        assertThat(jdbcTemplate.queryForObject(
                        """
                SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE()
                AND TABLE_NAME IN ('answer', 'evaluation', 'evaluation_dimension_score')
                """,
                        Integer.class))
                .isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject(
                        """
                SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS WHERE CONSTRAINT_SCHEMA = DATABASE()
                AND TABLE_NAME IN ('answer', 'evaluation', 'evaluation_dimension_score')
                """,
                        Integer.class))
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                        """
                SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA = DATABASE()
                AND CONSTRAINT_NAME IN ('uq_answer_interview_question', 'uq_evaluation_answer', 'uq_evaluation_dimension')
                AND CONSTRAINT_TYPE = 'UNIQUE'
                """,
                        Integer.class))
                .isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject(
                        """
                SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
                AND TABLE_NAME IN ('user', 'candidate', 'candidate_resume', 'candidate_project', 'position',
                    'interview', 'question', 'answer', 'evaluation_profile', 'evaluation_dimension_config',
                    'evaluation', 'evaluation_dimension_score')
                AND (COLUMN_COMMENT IS NULL OR COLUMN_COMMENT = '')
                """,
                        Integer.class))
                .isZero();
    }

    @Test
    void startsWebApplicationAndExposesHealthyActuator() {
        var response = restTemplate.getForEntity("http://localhost:" + port + "/actuator/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    void createsStartsAndReadsInterviewWithPersistedOpeningQuestion() {
        var requestHeaders = new org.springframework.http.HttpHeaders();
        requestHeaders.set("X-Request-Id", "client-controlled-request-id");
        var created = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/interviews",
                new org.springframework.http.HttpEntity<>(Map.of("mode", "COMPREHENSIVE"), requestHeaders),
                java.util.Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> createdData = data(created.getBody());
        assertThat(created.getHeaders().getFirst("X-Request-Id"))
                .isEqualTo(created.getBody().get("requestId"))
                .isNotEqualTo("client-controlled-request-id");
        long interviewId = Long.parseLong((String) createdData.get("id"));
        assertThat(createdData).containsEntry("status", "CREATED").containsEntry("currentPhase", null);
        assertThat(createdData).containsEntry("expiresAt", null);
        assertThat(jdbcTemplate.queryForMap("SELECT started_at, completed_at FROM interview WHERE id = ?", interviewId))
                .containsEntry("started_at", null)
                .containsEntry("completed_at", null);

        preparePlanForStart(interviewId);
        given(interviewAiApi.generateMainQuestion(any())).willReturn(new GeneratedQuestion("请介绍一次你解决 MySQL 慢查询问题的经历。"));
        var started = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/interviews/" + interviewId + "/start", null, java.util.Map.class);

        assertThat(started.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> startedData = data(started.getBody());
        assertThat(startedData).containsEntry("status", "IN_PROGRESS").containsEntry("currentPhase", "WAITING_ANSWER");
        assertThat(startedData).containsEntry("expiresAt", "2026-09-26T11:45:00");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT expires_at FROM interview WHERE id = ?", LocalDateTime.class, interviewId))
                .isEqualTo(LocalDateTime.parse("2026-09-26T11:45:00"));
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT started_at FROM interview WHERE id = ?", LocalDateTime.class, interviewId))
                .isEqualTo(LocalDateTime.parse("2026-09-26T11:00:00"));
        assertThat(interviewRepository.findById(interviewId).startedAt())
                .isEqualTo(LocalDateTime.parse("2026-09-26T11:00:00"));
        assertThat(interviewRepository.findById(interviewId).completedAt()).isNull();
        java.util.Map<?, ?> question = (java.util.Map<?, ?>) startedData.get("firstQuestion");
        assertThat(question.get("type")).isEqualTo("MAIN");
        assertThat(question.get("status")).isEqualTo("WAITING_ANSWER");
        assertThat(question.get("content")).isEqualTo("请介绍一次你解决 MySQL 慢查询问题的经历。");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT p.focus FROM question q JOIN interview_plan_item p ON p.id=q.plan_item_id WHERE q.interview_id = ?",
                        String.class,
                        interviewId))
                .isEqualTo("事务处理");
        verify(interviewAiApi, never()).selectFocus(any());
        var openingContext = ArgumentCaptor.forClass(MainQuestionGenerationContext.class);
        verify(interviewAiApi).generateMainQuestion(openingContext.capture());
        assertThat(openingContext.getValue().focus()).isEqualTo("事务处理");
        assertThat(openingContext.getValue().area()).isEqualTo("MYSQL");
        assertThat(openingContext.getValue().reason()).isEqualTo("首题理由");
        assertThat(openingContext.getValue().previousMainQuestions()).isEmpty();
        assertThat(questionRepository.findFirstByInterviewId(interviewId).planItemId())
                .isEqualTo(planRepository.findByInterviewId(interviewId).initialPlanItemId());
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM question WHERE interview_id = ?", Integer.class, interviewId))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT evaluation_profile_id FROM question WHERE interview_id = ?", Long.class, interviewId))
                .isEqualTo(1L);

        var read = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/interviews/" + interviewId, java.util.Map.class);
        assertThat(read.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(read.getBody())).isEqualTo(startedData);

        assertThat(interviewApplicationService.pauseInterview(interviewId).expiresAt())
                .isEqualTo(LocalDateTime.parse("2026-09-26T11:45:00"));
        assertThat(interviewApplicationService.resumeInterview(interviewId).expiresAt())
                .isEqualTo(LocalDateTime.parse("2026-09-26T11:45:00"));
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT started_at FROM interview WHERE id = ?", LocalDateTime.class, interviewId))
                .isEqualTo(LocalDateTime.parse("2026-09-26T11:00:00"));

        var repeatedStart = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/interviews/" + interviewId + "/start", null, java.util.Map.class);
        assertThat(repeatedStart.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(repeatedStart.getHeaders().getFirst("X-Request-Id"))
                .isEqualTo(repeatedStart.getBody().get("requestId"));
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM question WHERE interview_id = ?", Integer.class, interviewId))
                .isEqualTo(1);
    }

    @Test
    void getConvergesExpiredWaitingInterviewsAtExactDeadlineButNotBeforeOrDuringProcessing() {
        StartedInterview before = createStartedInterview();
        given(clock.instant()).willReturn(Instant.parse("2026-09-26T03:44:59.999Z"));
        var beforeDeadline = interviewApplicationService.getInterview(before.interviewId());
        assertThat(beforeDeadline.status()).isEqualTo(InterviewStatus.IN_PROGRESS);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT version FROM interview WHERE id = ?", Long.class, before.interviewId()))
                .isEqualTo(2L);

        given(clock.instant()).willReturn(Instant.parse("2026-09-26T03:00:00Z"));
        StartedInterview exact = createStartedInterview();
        given(clock.instant()).willReturn(Instant.parse("2026-09-26T03:45:00Z"));
        var atDeadline = interviewApplicationService.getInterview(exact.interviewId());
        assertThat(atDeadline.status()).isEqualTo(InterviewStatus.COMPLETED);
        assertThat(atDeadline.currentPhase()).isNull();
        assertThat(atDeadline.currentQuestion()).isNull();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT version FROM interview WHERE id = ?", Long.class, exact.interviewId()))
                .isEqualTo(3L);

        given(clock.instant()).willReturn(Instant.parse("2026-09-26T03:00:00Z"));
        StartedInterview after = createStartedInterview();
        given(clock.instant()).willReturn(Instant.parse("2026-09-26T03:45:00.001Z"));
        assertThat(interviewApplicationService.getInterview(after.interviewId()).status())
                .isEqualTo(InterviewStatus.COMPLETED);

        given(clock.instant()).willReturn(Instant.parse("2026-09-26T03:00:00Z"));
        StartedInterview paused = createStartedInterview();
        jdbcTemplate.update("UPDATE interview SET status = 'PAUSED' WHERE id = ?", paused.interviewId());
        given(clock.instant()).willReturn(Instant.parse("2026-09-26T03:45:00Z"));
        assertThat(interviewApplicationService
                        .getInterview(paused.interviewId())
                        .status())
                .isEqualTo(InterviewStatus.COMPLETED);

        given(clock.instant()).willReturn(Instant.parse("2026-09-26T03:00:00Z"));
        StartedInterview processing = createStartedInterview();
        given(clock.instant()).willReturn(Instant.parse("2026-09-26T03:45:00.001Z"));
        jdbcTemplate.update(
                "UPDATE interview SET current_phase = 'PROCESSING_TURN', version = version + 1 WHERE id = ?",
                processing.interviewId());
        var processingDetail = interviewApplicationService.getInterview(processing.interviewId());
        assertThat(processingDetail.status()).isEqualTo(InterviewStatus.IN_PROGRESS);
        assertThat(processingDetail.currentPhase()).isEqualTo(InterviewPhase.PROCESSING_TURN);
    }

    @Test
    void expiredPauseResumeAndSkipCommitConvergenceAndFinishStillSucceeds() {
        given(clock.instant()).willReturn(Instant.parse("2026-09-26T03:00:00Z"));
        StartedInterview pause = createStartedInterview();
        given(clock.instant()).willReturn(Instant.parse("2026-09-26T03:45:00Z"));
        assertThatThrownBy(() -> interviewApplicationService.pauseInterview(pause.interviewId()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(InterviewErrorCode.INTERVIEW_EXPIRED));
        assertThat(jdbcTemplate.queryForMap(
                        "SELECT status, current_phase FROM interview WHERE id = ?", pause.interviewId()))
                .containsEntry("status", "COMPLETED")
                .containsEntry("current_phase", null);

        given(clock.instant()).willReturn(Instant.parse("2026-09-26T03:00:00Z"));
        StartedInterview resume = createStartedInterview();
        jdbcTemplate.update("UPDATE interview SET status = 'PAUSED' WHERE id = ?", resume.interviewId());
        given(clock.instant()).willReturn(Instant.parse("2026-09-26T03:45:00Z"));
        assertThatThrownBy(() -> interviewApplicationService.resumeInterview(resume.interviewId()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(InterviewErrorCode.INTERVIEW_EXPIRED));
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM interview WHERE id = ?", String.class, resume.interviewId()))
                .isEqualTo("COMPLETED");

        given(clock.instant()).willReturn(Instant.parse("2026-09-26T03:00:00Z"));
        StartedInterview skip = createStartedInterview();
        given(clock.instant()).willReturn(Instant.parse("2026-09-26T03:45:00Z"));
        assertThatThrownBy(() -> interviewSkipApplicationService.skipQuestion(skip.interviewId(), skip.questionId()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(InterviewErrorCode.INTERVIEW_EXPIRED));
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM question WHERE id = ?", String.class, skip.questionId()))
                .isEqualTo("WAITING_ANSWER");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM question WHERE interview_id = ?", Integer.class, skip.interviewId()))
                .isEqualTo(1);
        verify(interviewAiApi, never()).generateMainQuestion(any(MainQuestionGenerationContext.class));

        given(clock.instant()).willReturn(Instant.parse("2026-09-26T03:00:00Z"));
        StartedInterview finish = createStartedInterview();
        given(clock.instant()).willReturn(Instant.parse("2026-09-26T03:45:00Z"));
        assertThat(interviewApplicationService
                        .finishInterview(finish.interviewId())
                        .status())
                .isEqualTo(InterviewStatus.COMPLETED);
        LocalDateTime completedAt = jdbcTemplate.queryForObject(
                "SELECT completed_at FROM interview WHERE id = ?", LocalDateTime.class, finish.interviewId());
        assertThat(completedAt).isNotNull();
        assertThat(completedAt)
                .isAfterOrEqualTo(jdbcTemplate.queryForObject(
                        "SELECT started_at FROM interview WHERE id = ?", LocalDateTime.class, finish.interviewId()));
        assertThat(interviewRepository.findById(finish.interviewId()).completedAt())
                .isEqualTo(completedAt);
    }

    @Test
    void recoversCreatedStateAfterOpeningFailureAndAllowsRestart() {
        var created = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/interviews", Map.of("mode", "COMPREHENSIVE"), java.util.Map.class);
        long interviewId = Long.parseLong((String) data(created.getBody()).get("id"));
        InterviewPlan prepared = preparePlanForStart(interviewId);
        given(interviewAiApi.generateMainQuestion(any())).willThrow(new IllegalStateException("测试 Opening 失败"));

        var failedStart = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/interviews/" + interviewId + "/start", null, String.class);

        assertThat(failedStart.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(planRepository.findByInterviewId(interviewId)).isEqualTo(prepared);
        assertThat(jdbcTemplate.queryForMap("SELECT status, current_phase FROM interview WHERE id = ?", interviewId))
                .containsEntry("status", "CREATED")
                .containsEntry("current_phase", null);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT expires_at FROM interview WHERE id = ?", Object.class, interviewId))
                .isNull();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM question WHERE interview_id = ?", Integer.class, interviewId))
                .isZero();

        willReturn(new GeneratedQuestion("请说明你如何分析并优化一次慢查询."))
                .given(interviewAiApi)
                .generateMainQuestion(any());
        var restarted = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/interviews/" + interviewId + "/start", null, java.util.Map.class);

        assertThat(restarted.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(restarted.getBody()))
                .containsEntry("status", "IN_PROGRESS")
                .containsEntry("currentPhase", "WAITING_ANSWER")
                .containsEntry("expiresAt", "2026-09-26T11:45:00");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM question WHERE interview_id = ?", Integer.class, interviewId))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForMap(
                        """
                SELECT type, status, evaluation_profile_id FROM question WHERE interview_id = ?
                """,
                        interviewId))
                .containsEntry("type", "MAIN")
                .containsEntry("status", "WAITING_ANSWER")
                .containsEntry("evaluation_profile_id", 1L);
    }

    @Test
    void mapsEntitiesUsesBaseMapperCrudAndLoadsStateTransitionXml() {
        PositionDO position = positionMapper.selectById(1L);
        assertThat(position.getTitle()).isEqualTo("Java 后端工程师");

        InterviewDO interview = new InterviewDO();
        interview.setCandidateId(1L);
        interview.setPositionId(1L);
        interview.setStatus(InterviewStatus.CREATED);
        interviewMapper.insert(interview);

        InterviewDO loaded = interviewMapper.selectById(interview.getId());
        assertThat(loaded.getVersion()).isZero();
        assertThat(interviewMapper.markOpening(
                        interview.getId(),
                        InterviewStatus.CREATED,
                        null,
                        InterviewStatus.IN_PROGRESS,
                        InterviewPhase.OPENING,
                        999L,
                        LocalDateTime.parse("2026-09-26T10:45:00"),
                        LocalDateTime.parse("2026-09-26T11:45:00")))
                .isZero();
        assertThat(interviewMapper.markOpening(
                        interview.getId(),
                        InterviewStatus.CREATED,
                        null,
                        InterviewStatus.IN_PROGRESS,
                        InterviewPhase.OPENING,
                        loaded.getVersion(),
                        LocalDateTime.parse("2026-09-26T10:45:00"),
                        LocalDateTime.parse("2026-09-26T11:45:00")))
                .isEqualTo(1);
        assertThat(interviewMapper.selectById(interview.getId()).getStatus()).isEqualTo(InterviewStatus.IN_PROGRESS);
        assertThat(interviewMapper.selectById(interview.getId()).getCurrentPhase())
                .isEqualTo(InterviewPhase.OPENING);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM interview WHERE id = ?", String.class, interview.getId()))
                .isEqualTo(InterviewStatus.IN_PROGRESS.name());
        assertThat(interviewMapper.selectById(interview.getId()).getVersion()).isEqualTo(1L);
    }

    @Test
    void repositoriesTranslateDuplicateAnswerAndEvaluation() {
        long interviewId = insertWaitingInterview();
        long questionId = insertWaitingQuestion(interviewId);
        var answer = answerRepository.save(interviewId, questionId, "第一次回答");

        assertThatThrownBy(() -> answerRepository.save(interviewId, questionId, "重复回答"))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(InterviewErrorCode.ANSWER_ALREADY_EXISTS));

        EvaluationCommand command = evaluationCommand(interviewId, questionId, answer.id());
        evaluationRepository.create(command);
        assertThatThrownBy(() -> evaluationRepository.create(command)).isInstanceOf(BusinessException.class);
    }

    @Test
    void repositoriesDistinguishMissingAnswer() {
        assertThatThrownBy(() -> answerRepository.findById(Long.MAX_VALUE))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(InterviewErrorCode.ANSWER_NOT_FOUND));
    }

    @Test
    void concurrentDuplicateAnswerAllowsAtMostOneSuccess() throws Exception {
        long interviewId = insertWaitingInterview();
        long questionId = insertWaitingQuestion(interviewId);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> submit = () -> {
                ready.countDown();
                start.await();
                try {
                    answerRepository.save(interviewId, questionId, "并发回答");
                    return true;
                } catch (BusinessException exception) {
                    return false;
                }
            };
            List<Future<Boolean>> futures = List.of(executor.submit(submit), executor.submit(submit));
            ready.await();
            start.countDown();
            List<Boolean> results = new ArrayList<>();
            for (Future<Boolean> future : futures) {
                results.add(future.get());
            }
            assertThat(results).containsExactlyInAnyOrder(true, false);
            assertThat(jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM answer WHERE interview_id = ? AND question_id = ?",
                            Integer.class,
                            interviewId,
                            questionId))
                    .isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentInterviewUpdateAllowsOnlyOneVersionAdvance() throws Exception {
        long interviewId = insertCreatedInterview();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> update = () -> {
                ready.countDown();
                start.await();
                try {
                    interviewRepository.markOpening(
                            interviewId,
                            0L,
                            LocalDateTime.parse("2026-09-26T10:00:00"),
                            LocalDateTime.parse("2026-09-26T11:45:00"));
                    return true;
                } catch (BusinessException exception) {
                    return false;
                }
            };
            List<Future<Boolean>> futures = List.of(executor.submit(update), executor.submit(update));
            ready.await();
            start.countDown();
            List<Boolean> results = List.of(futures.get(0).get(), futures.get(1).get());
            assertThat(results).containsExactlyInAnyOrder(true, false);
            assertThat(jdbcTemplate.queryForObject(
                            "SELECT version FROM interview WHERE id = ?", Long.class, interviewId))
                    .isEqualTo(1L);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void submitsAnswerAndCompletesTurn() {
        StartedInterview started = createStartedInterview();
        given(interviewAiApi.evaluateAnswer(any(EvaluationContext.class))).willAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                    .isFalse();
            return validEvaluation(invocation.getArgument(0));
        });

        var response = restTemplate.postForEntity(answerUrl(started), Map.of("content", "事务可以保证一致性。"), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> answer = (Map<?, ?>) data(response.getBody()).get("answer");
        Map<?, ?> evaluation = (Map<?, ?>) data(response.getBody()).get("evaluation");
        assertThat(answer.get("content")).isEqualTo("事务可以保证一致性。");
        assertThat(evaluation.get("overallScore10").toString()).isEqualTo("8.0");
        assertThat(evaluation.get("overallScore100").toString()).isEqualTo("80.0");
        assertThat((List<?>) evaluation.get("dimensions")).hasSize(7);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM answer WHERE question_id = ?", Integer.class, started.questionId()))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM evaluation WHERE question_id = ?", Integer.class, started.questionId()))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        """
                SELECT COUNT(*) FROM evaluation_dimension_score s JOIN evaluation e ON e.id = s.evaluation_id
                WHERE e.question_id = ?
                """,
                        Integer.class,
                        started.questionId()))
                .isEqualTo(7);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM question WHERE id = ?", String.class, started.questionId()))
                .isEqualTo("ANSWERED");
        assertThat(jdbcTemplate.queryForMap(
                        "SELECT status, current_phase FROM interview WHERE id = ?", started.interviewId()))
                .containsEntry("status", "COMPLETED")
                .containsEntry("current_phase", null);
        Map<String, Object> detail = data(restTemplate.getForObject(
                "http://localhost:" + port + "/api/interviews/" + started.interviewId(), Map.class));
        assertThat(detail.get("currentQuestion")).isNull();
        assertThat(Long.parseLong((String) ((Map<?, ?>) detail.get("firstQuestion")).get("id")))
                .isEqualTo(started.questionId());
        org.mockito.Mockito.verify(interviewAiApi, org.mockito.Mockito.never())
                .generateMainQuestion(any(MainQuestionGenerationContext.class));

        var duplicate = restTemplate.postForEntity(answerUrl(started), Map.of("content", "覆盖回答"), String.class);
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT content FROM answer WHERE question_id = ?", String.class, started.questionId()))
                .isEqualTo("事务可以保证一致性。");
    }

    @Test
    void submitAtOrAfterDeadlineCompletesWithoutPersistingAnswer() {
        StartedInterview exact = createStartedInterview();
        given(clock.instant()).willReturn(Instant.parse("2026-09-26T03:45:00Z"));

        var exactResponse = restTemplate.postForEntity(answerUrl(exact), Map.of("content", "过期回答"), Map.class);

        assertThat(exactResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(exactResponse.getBody().get("code")).isEqualTo(23006);
        assertExpiredWithoutAnswer(exact);

        given(clock.instant()).willReturn(Instant.parse("2026-09-26T03:00:00Z"));
        StartedInterview after = createStartedInterview();
        given(clock.instant()).willReturn(Instant.parse("2026-09-26T03:45:01Z"));

        var afterResponse = restTemplate.postForEntity(answerUrl(after), Map.of("content", "过期回答"), Map.class);

        assertThat(afterResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(afterResponse.getBody().get("code")).isEqualTo(23006);
        assertExpiredWithoutAnswer(after);
    }

    @Test
    void rollsBackAnswerWhenDeadlineArrivesBeforeFinalT1Cas() {
        StartedInterview started = createStartedInterview();
        given(clock.instant()).willReturn(Instant.parse("2026-09-26T03:00:00Z"), Instant.parse("2026-09-26T03:45:00Z"));

        var response = restTemplate.postForEntity(answerUrl(started), Map.of("content", "边界回答"), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("code")).isEqualTo(23006);
        assertExpiredWithoutAnswer(started);
        verify(interviewAiApi, never()).evaluateAnswer(any(EvaluationContext.class));
    }

    @Test
    void deadlineCrossedBeforeQuestionGenerationSkipsAiAndCommitsEvaluation() {
        StartedInterview started = createStartedInterview();
        given(interviewAiApi.evaluateAnswer(any(EvaluationContext.class))).willAnswer(invocation -> {
            var result = validEvaluation(invocation.getArgument(0));
            given(clock.instant()).willReturn(Instant.parse("2026-09-26T03:45:00Z"));
            return result;
        });
        given(interviewAiApi.selectFocus(any())).willReturn(new FocusProposal("事务处理"));
        given(interviewAiApi.generateMainQuestion(any())).willReturn(new GeneratedQuestion("默认主问题"));
        given(interviewAiApi.decideNext(any(DecisionContext.class)))
                .willReturn(new ValidatedDecisionResult(DecisionAction.FOLLOW_UP, "继续追问", "细节"));

        var response = restTemplate.postForEntity(answerUrl(started), Map.of("content", "正式回答"), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertTurnExpiredAndCommitted(started);
        verify(interviewAiApi, org.mockito.Mockito.never())
                .generateMainQuestion(any(MainQuestionGenerationContext.class));
    }

    @Test
    void deadlineCrossedDuringQuestionGenerationDiscardsGeneratedQuestionAtT2() {
        StartedInterview started = createStartedInterview();
        given(interviewAiApi.evaluateAnswer(any(EvaluationContext.class)))
                .willAnswer(invocation -> validEvaluation(invocation.getArgument(0)));
        given(interviewAiApi.selectFocus(any())).willReturn(new FocusProposal("事务处理"));
        given(interviewAiApi.generateMainQuestion(any())).willReturn(new GeneratedQuestion("默认主问题"));
        given(interviewAiApi.decideNext(any(DecisionContext.class)))
                .willReturn(new ValidatedDecisionResult(
                        DecisionAction.NEXT_QUESTION,
                        "继续",
                        null,
                        QuestionQualityTestPlans.next(jdbcTemplate, started.interviewId())));
        given(interviewAiApi.generateMainQuestion(any(MainQuestionGenerationContext.class)))
                .willAnswer(invocation -> {
                    given(clock.instant()).willReturn(Instant.parse("2026-09-26T03:45:00Z"));
                    return new GeneratedQuestion("不应持久化的下一题");
                });

        var response = restTemplate.postForEntity(answerUrl(started), Map.of("content", "正式回答"), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertTurnExpiredAndCommitted(started);
        verify(interviewAiApi).generateMainQuestion(any(MainQuestionGenerationContext.class));
    }

    private void assertExpiredWithoutAnswer(StartedInterview started) {
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM answer WHERE question_id = ?", Integer.class, started.questionId()))
                .isZero();
        assertThat(jdbcTemplate.queryForMap(
                        "SELECT status, current_phase FROM interview WHERE id = ?", started.interviewId()))
                .containsEntry("status", "COMPLETED")
                .containsEntry("current_phase", null);
    }

    private void assertTurnExpiredAndCommitted(StartedInterview started) {
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM answer WHERE question_id = ?", Integer.class, started.questionId()))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM evaluation WHERE question_id = ?", Integer.class, started.questionId()))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM question WHERE id = ?", String.class, started.questionId()))
                .isEqualTo("ANSWERED");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM question WHERE interview_id = ?", Integer.class, started.interviewId()))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForMap(
                        "SELECT status, current_phase FROM interview WHERE id = ?", started.interviewId()))
                .containsEntry("status", "COMPLETED")
                .containsEntry("current_phase", null);
    }

    @Test
    void followUpCreatesWaitingQuestionAndExposesCurrentQuestion() {
        StartedInterview started = createStartedInterview();
        given(interviewAiApi.evaluateAnswer(any(EvaluationContext.class)))
                .willAnswer(invocation -> validEvaluation(invocation.getArgument(0)));
        given(interviewAiApi.selectFocus(any())).willReturn(new FocusProposal("事务处理"));
        given(interviewAiApi.generateMainQuestion(any())).willReturn(new GeneratedQuestion("默认主问题"));
        given(interviewAiApi.decideNext(any(DecisionContext.class)))
                .willReturn(new ValidatedDecisionResult(DecisionAction.FOLLOW_UP, "继续追问", "事务边界"));
        given(interviewAiApi.generateFollowUpQuestion(any(FollowUpQuestionGenerationContext.class)))
                .willReturn(new GeneratedQuestion("请解释事务回滚。"));

        var submitted = restTemplate.postForEntity(answerUrl(started), Map.of("content", "正式回答"), Map.class);

        assertThat(submitted.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> detail = data(restTemplate.getForObject(
                "http://localhost:" + port + "/api/interviews/" + started.interviewId(), Map.class));
        Map<?, ?> first = (Map<?, ?>) detail.get("firstQuestion");
        Map<?, ?> current = (Map<?, ?>) detail.get("currentQuestion");
        assertThat(Long.parseLong((String) first.get("id"))).isEqualTo(started.questionId());
        assertThat(current.get("type")).isEqualTo("FOLLOW_UP");
        assertThat(current.get("content")).isEqualTo("请解释事务回滚。");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT parent_question_id FROM question WHERE id = ?", Long.class, Long.parseLong((String)
                                current.get("id"))))
                .isEqualTo(started.questionId());
        ArgumentCaptor<FollowUpQuestionGenerationContext> contextCaptor =
                ArgumentCaptor.forClass(FollowUpQuestionGenerationContext.class);
        verify(interviewAiApi).generateFollowUpQuestion(contextCaptor.capture());
        verify(interviewAiApi, never()).selectFocus(any());
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT plan_item_id FROM question WHERE id = ?", Long.class, Long.parseLong((String)
                                current.get("id"))))
                .isEqualTo(questionRepository.findById(started.questionId()).planItemId());
        assertThat(contextCaptor.getValue().followUpTarget()).isEqualTo("事务边界");
        assertThat(contextCaptor.getValue().currentQuestionContent()).isEqualTo("请说明事务与并发控制。");
        assertThat(contextCaptor.getValue().currentAnswerContent()).isEqualTo("正式回答");
    }

    @Test
    void consecutiveFollowUpsKeepMainParentRespectLimitAndAppearInReview() {
        StartedInterview started = createStartedInterview();
        given(interviewAiApi.evaluateAnswer(any(EvaluationContext.class))).willAnswer(invocation -> {
            var result = validEvaluation(invocation.getArgument(0));
            return new ValidatedEvaluationResult(
                    result.profileId(),
                    result.overallScore10(),
                    result.overallScore100(),
                    result.dimensions(),
                    result.strengths(),
                    result.weaknesses(),
                    List.of(new MissingPointProposal("事务边界", Importance.HIGH, "需要说明", List.of())),
                    result.referenceAnswer(),
                    result.promptVersion(),
                    result.evaluatorVersion(),
                    result.model(),
                    result.inputTokens(),
                    result.outputTokens(),
                    result.totalTokens(),
                    result.latencyMs());
        });
        given(interviewAiApi.decideNext(any(DecisionContext.class)))
                .willReturn(
                        new ValidatedDecisionResult(DecisionAction.FOLLOW_UP, "首次追问", "事务边界"),
                        new ValidatedDecisionResult(DecisionAction.FOLLOW_UP, "继续追问", "事务边界"),
                        new ValidatedDecisionResult(
                                DecisionAction.NEXT_QUESTION,
                                "追问已达上限",
                                null,
                                QuestionQualityTestPlans.next(jdbcTemplate, started.interviewId())));
        given(interviewAiApi.generateFollowUpQuestion(any(FollowUpQuestionGenerationContext.class)))
                .willReturn(new GeneratedQuestion("首个追问"), new GeneratedQuestion("第二个追问"));

        String url = "http://localhost:" + port + "/api/interviews/" + started.interviewId();
        List<Long> followUpIds = new ArrayList<>();
        Long currentQuestionId = started.questionId();
        for (int turn = 0; turn < 3; turn++) {
            var submitted = restTemplate.postForEntity(
                    answerUrl(new StartedInterview(started.interviewId(), currentQuestionId)),
                    Map.of("content", "回答 " + turn),
                    Map.class);
            assertThat(submitted.getStatusCode()).isEqualTo(HttpStatus.OK);
            Map<?, ?> current =
                    (Map<?, ?>) data(restTemplate.getForObject(url, Map.class)).get("currentQuestion");
            currentQuestionId = Long.parseLong((String) current.get("id"));
            if (turn < 2) {
                assertThat(current.get("type")).isEqualTo("FOLLOW_UP");
                followUpIds.add(currentQuestionId);
                assertThat(jdbcTemplate.queryForObject(
                                "SELECT parent_question_id FROM question WHERE id = ?", Long.class, currentQuestionId))
                        .isEqualTo(started.questionId());
            } else {
                assertThat(current.get("type")).isEqualTo("MAIN");
                assertThat(jdbcTemplate.queryForObject(
                                "SELECT parent_question_id FROM question WHERE id = ?", Long.class, currentQuestionId))
                        .isNull();
            }
        }

        var decisions = ArgumentCaptor.forClass(DecisionContext.class);
        verify(interviewAiApi, org.mockito.Mockito.times(3)).decideNext(decisions.capture());
        assertThat(decisions.getAllValues())
                .extracting(value -> value.progress().followUpCount())
                .containsExactly(0, 1, 2);
        for (int turn = 0; turn < 2; turn++) {
            assertThat(decisions.getAllValues().get(turn).allowedActions())
                    .containsExactlyInAnyOrder(DecisionAction.FOLLOW_UP, DecisionAction.NEXT_QUESTION);
        }
        assertThat(decisions.getAllValues().get(2).allowedActions()).containsExactly(DecisionAction.NEXT_QUESTION);
        var generated = ArgumentCaptor.forClass(FollowUpQuestionGenerationContext.class);
        verify(interviewAiApi, org.mockito.Mockito.times(2)).generateFollowUpQuestion(generated.capture());
        assertThat(generated.getAllValues().get(1).currentQuestionContent()).isEqualTo("首个追问");
        assertThat(generated.getAllValues().get(1).currentAnswerContent()).isEqualTo("回答 1");
        assertThat(generated.getAllValues().get(1).followUpTarget()).isEqualTo("事务边界");

        assertThat(restTemplate.postForEntity(url + "/finish", null, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        var response = restTemplate.getForEntity(url + "/review", Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> review = data(response.getBody());
        assertThat((Map<String, Object>) review.get("overview"))
                .containsEntry("mainQuestionCount", 2)
                .containsEntry("followUpQuestionCount", 2)
                .containsEntry("answeredCount", 3)
                .containsEntry("unansweredCount", 1);
        List<?> mainQuestions = (List<?>) review.get("questions");
        assertThat(mainQuestions).hasSize(2);
        Map<?, ?> mainReview = (Map<?, ?>) mainQuestions.get(0);
        assertThat(Long.parseLong((String) mainReview.get("questionId"))).isEqualTo(started.questionId());
        List<?> followUpReviews = (List<?>) mainReview.get("followUps");
        assertThat(followUpReviews).hasSize(2);
        for (int index = 0; index < 2; index++) {
            Map<?, ?> followUpReview = (Map<?, ?>) followUpReviews.get(index);
            assertThat(Long.parseLong((String) followUpReview.get("questionId")))
                    .isEqualTo(followUpIds.get(index));
            assertThat(followUpReview.get("result")).isEqualTo("ANSWERED");
            assertThat(followUpReview.get("answer")).isEqualTo("回答 " + (index + 1));
            assertThat(followUpReview.get("evaluation")).isNotNull();
        }
        assertThat((List<?>) ((Map<?, ?>) mainQuestions.get(1)).get("followUps"))
                .isEmpty();
    }

    @Test
    void nextQuestionCreatesMainWithoutParentAndTopic() {
        StartedInterview started = createStartedInterview();
        given(interviewAiApi.evaluateAnswer(any(EvaluationContext.class)))
                .willAnswer(invocation -> validEvaluation(invocation.getArgument(0)));
        given(interviewAiApi.selectFocus(any())).willReturn(new FocusProposal("事务处理"));
        given(interviewAiApi.generateMainQuestion(any())).willReturn(new GeneratedQuestion("默认主问题"));
        given(interviewAiApi.decideNext(any(DecisionContext.class)))
                .willReturn(new ValidatedDecisionResult(
                        DecisionAction.NEXT_QUESTION,
                        "下一题",
                        null,
                        QuestionQualityTestPlans.next(jdbcTemplate, started.interviewId())));
        given(interviewAiApi.generateMainQuestion(any(MainQuestionGenerationContext.class)))
                .willReturn(new GeneratedQuestion("什么是乐观锁？"));

        var submitted = restTemplate.postForEntity(answerUrl(started), Map.of("content", "正式回答"), Map.class);

        assertThat(submitted.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> detail = data(restTemplate.getForObject(
                "http://localhost:" + port + "/api/interviews/" + started.interviewId(), Map.class));
        Map<?, ?> current = (Map<?, ?>) detail.get("currentQuestion");
        assertThat(current.get("type")).isEqualTo("MAIN");
        assertThat(jdbcTemplate.queryForMap(
                        "SELECT parent_question_id, topic_id FROM question WHERE id = ?",
                        Long.parseLong((String) current.get("id"))))
                .containsEntry("parent_question_id", null)
                .containsEntry("topic_id", null);
        verify(interviewAiApi, never()).selectFocus(any());
        var mainContext = ArgumentCaptor.forClass(MainQuestionGenerationContext.class);
        verify(interviewAiApi).generateMainQuestion(mainContext.capture());
        assertThat(mainContext.getValue().area()).isEqualTo("MYSQL");
        assertThat(mainContext.getValue().reason()).isEqualTo("首题理由");
        assertThat(mainContext.getValue().previousMainQuestions()).containsExactly("请说明事务与并发控制。");
        var decisionContext = ArgumentCaptor.forClass(DecisionContext.class);
        verify(interviewAiApi).decideNext(decisionContext.capture());
        assertThat(decisionContext.getValue().unusedPlanItems()).hasSize(4);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT plan_item_id FROM question WHERE id = ?", Long.class, Long.parseLong((String)
                                current.get("id"))))
                .isEqualTo(decisionContext.getValue().unusedPlanItems().get(0).id());
    }

    @Test
    void retriesWholeTurnFromOfficialAnswerWithoutRepeatingT1() {
        StartedInterview started = createStartedInterview();
        given(interviewAiApi.evaluateAnswer(any(EvaluationContext.class)))
                .willThrow(new com.linj6.interviewagent.ai.evaluator.EvaluationValidationException("invalid"))
                .willAnswer(invocation -> validEvaluation(invocation.getArgument(0)));

        var submitted = restTemplate.postForEntity(answerUrl(started), Map.of("content", "正式回答"), Map.class);

        assertThat(submitted.getStatusCode()).isEqualTo(HttpStatus.OK);
        ArgumentCaptor<EvaluationContext> contexts = ArgumentCaptor.forClass(EvaluationContext.class);
        org.mockito.Mockito.verify(interviewAiApi, org.mockito.Mockito.times(2)).evaluateAnswer(contexts.capture());
        assertThat(contexts.getAllValues())
                .extracting(EvaluationContext::answerContent)
                .containsExactly("正式回答", "正式回答");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM answer WHERE question_id = ?", Integer.class, started.questionId()))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT version FROM interview WHERE id = ?", Long.class, started.interviewId()))
                .isEqualTo(4L);
    }

    @Test
    void manualRetryUsesExistingAnswerAndReturnsSubmissionResponse() {
        StartedInterview started = createStartedInterview();
        var first = new com.linj6.interviewagent.ai.evaluator.EvaluationValidationException("first");
        var second = new com.linj6.interviewagent.ai.evaluator.EvaluationValidationException("second");
        var retryFirst = new com.linj6.interviewagent.ai.evaluator.EvaluationValidationException("retry-first");
        given(interviewAiApi.evaluateAnswer(any(EvaluationContext.class)))
                .willThrow(first)
                .willThrow(second)
                .willThrow(retryFirst)
                .willAnswer(invocation -> validEvaluation(invocation.getArgument(0)));

        assertThatThrownBy(() -> interviewTurnApplicationService.submitAnswer(
                        started.interviewId(), started.questionId(), "正式回答"))
                .isInstanceOf(BusinessException.class);
        var response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/interviews/" + started.interviewId() + "/retry", null, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("data");
        Map<?, ?> data = (Map<?, ?>) response.getBody().get("data");
        assertThat(data.containsKey("answer")).isTrue();
        assertThat(data.containsKey("evaluation")).isTrue();
        Map<?, ?> answer = (Map<?, ?>) data.get("answer");
        Map<?, ?> evaluation = (Map<?, ?>) data.get("evaluation");
        Long answerId = jdbcTemplate.queryForObject(
                "SELECT id FROM answer WHERE question_id = ?", Long.class, started.questionId());
        assertThat(Long.parseLong((String) answer.get("id"))).isEqualTo(answerId);
        assertThat(evaluation.get("id")).isNotNull();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM answer WHERE question_id = ?", Integer.class, started.questionId()))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM evaluation WHERE answer_id = ?", Integer.class, answerId))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM question WHERE interview_id = ?", Integer.class, started.interviewId()))
                .isEqualTo(1);
        verify(interviewAiApi, org.mockito.Mockito.times(4)).evaluateAnswer(any(EvaluationContext.class));
    }

    @Test
    void manualRetryAfterDeadlineCompletesExistingTurnWithoutGeneratingQuestion() {
        for (DecisionAction action : List.of(DecisionAction.FOLLOW_UP, DecisionAction.NEXT_QUESTION)) {
            given(clock.instant()).willReturn(Instant.parse("2026-09-26T03:00:00Z"));
            StartedInterview started = prepareFailedTurnForManualRetry();
            Long answerId = jdbcTemplate.queryForObject(
                    "SELECT id FROM answer WHERE question_id = ?", Long.class, started.questionId());
            org.mockito.Mockito.reset(interviewAiApi);
            given(interviewAiApi.evaluateAnswer(any(EvaluationContext.class)))
                    .willAnswer(invocation -> validEvaluation(invocation.getArgument(0)));
            given(interviewAiApi.selectFocus(any())).willReturn(new FocusProposal("事务处理"));
            given(interviewAiApi.generateMainQuestion(any())).willReturn(new GeneratedQuestion("默认主问题"));
            given(interviewAiApi.decideNext(any(DecisionContext.class)))
                    .willReturn(new ValidatedDecisionResult(action, "继续", "deadline retry"));
            given(clock.instant()).willReturn(Instant.parse("2026-09-26T03:45:01Z"));

            var response = interviewTurnApplicationService.retryTurn(started.interviewId());

            assertThat(response.answer().id()).isEqualTo(answerId);
            assertThat(jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM answer WHERE question_id = ?", Integer.class, started.questionId()))
                    .isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM evaluation WHERE answer_id = ?", Integer.class, answerId))
                    .isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject(
                            "SELECT status FROM question WHERE id = ?", String.class, started.questionId()))
                    .isEqualTo("ANSWERED");
            assertThat(jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM question WHERE interview_id = ?",
                            Integer.class,
                            started.interviewId()))
                    .isEqualTo(1);
            assertThat(jdbcTemplate.queryForMap(
                            "SELECT status, current_phase, completed_at FROM interview WHERE id = ?",
                            started.interviewId()))
                    .containsEntry("status", "COMPLETED")
                    .containsEntry("current_phase", null);
            LocalDateTime winningCompletedAt = jdbcTemplate.queryForObject(
                    "SELECT completed_at FROM interview WHERE id = ?", LocalDateTime.class, started.interviewId());
            assertThat(winningCompletedAt).isNotNull();
            assertThatThrownBy(() -> interviewApplicationService.finishInterview(started.interviewId()))
                    .isInstanceOf(BusinessException.class);
            assertThat(jdbcTemplate.queryForObject(
                            "SELECT completed_at FROM interview WHERE id = ?",
                            LocalDateTime.class,
                            started.interviewId()))
                    .isEqualTo(winningCompletedAt);
            verify(interviewAiApi).evaluateAnswer(any(EvaluationContext.class));
            verify(interviewAiApi).decideNext(any(DecisionContext.class));
            verify(interviewAiApi, never()).generateMainQuestion(any(MainQuestionGenerationContext.class));
        }
    }

    @Test
    void manualRetryAtExactDeadlineCompletesWithoutGeneratingQuestion() {
        StartedInterview started = prepareFailedTurnForManualRetry();
        org.mockito.Mockito.reset(interviewAiApi);
        given(interviewAiApi.evaluateAnswer(any(EvaluationContext.class)))
                .willAnswer(invocation -> validEvaluation(invocation.getArgument(0)));
        given(interviewAiApi.selectFocus(any())).willReturn(new FocusProposal("事务处理"));
        given(interviewAiApi.generateMainQuestion(any())).willReturn(new GeneratedQuestion("默认主问题"));
        given(interviewAiApi.decideNext(any(DecisionContext.class)))
                .willReturn(new ValidatedDecisionResult(
                        DecisionAction.NEXT_QUESTION,
                        "继续",
                        null,
                        QuestionQualityTestPlans.next(jdbcTemplate, started.interviewId())));
        given(clock.instant()).willReturn(Instant.parse("2026-09-26T03:45:00Z"));

        interviewTurnApplicationService.retryTurn(started.interviewId());

        assertTurnExpiredAndCommitted(started);
        verify(interviewAiApi, never()).generateMainQuestion(any(MainQuestionGenerationContext.class));
    }

    @Test
    void manualRetryBeforeDeadlineStillGeneratesFollowUp() {
        StartedInterview started = prepareFailedTurnForManualRetry();
        org.mockito.Mockito.reset(interviewAiApi);
        given(interviewAiApi.evaluateAnswer(any(EvaluationContext.class)))
                .willAnswer(invocation -> validEvaluation(invocation.getArgument(0)));
        given(interviewAiApi.selectFocus(any())).willReturn(new FocusProposal("事务处理"));
        given(interviewAiApi.generateMainQuestion(any())).willReturn(new GeneratedQuestion("默认主问题"));
        given(interviewAiApi.decideNext(any(DecisionContext.class)))
                .willReturn(new ValidatedDecisionResult(DecisionAction.FOLLOW_UP, "继续追问", "before deadline"));
        given(interviewAiApi.generateFollowUpQuestion(any(FollowUpQuestionGenerationContext.class)))
                .willReturn(new GeneratedQuestion("正常生成的追问"));
        given(clock.instant()).willReturn(Instant.parse("2026-09-26T03:44:59Z"));

        interviewTurnApplicationService.retryTurn(started.interviewId());

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM question WHERE interview_id = ?", Integer.class, started.interviewId()))
                .isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM interview WHERE id = ?", String.class, started.interviewId()))
                .isEqualTo("IN_PROGRESS");
        verify(interviewAiApi).generateFollowUpQuestion(any(FollowUpQuestionGenerationContext.class));
    }

    private StartedInterview prepareFailedTurnForManualRetry() {
        StartedInterview started = createStartedInterview();
        given(interviewAiApi.evaluateAnswer(any(EvaluationContext.class)))
                .willThrow(new com.linj6.interviewagent.ai.evaluator.EvaluationValidationException("initial-1"))
                .willThrow(new com.linj6.interviewagent.ai.evaluator.EvaluationValidationException("initial-2"));
        assertThatThrownBy(() -> interviewTurnApplicationService.submitAnswer(
                        started.interviewId(), started.questionId(), "固定的正式回答"))
                .isInstanceOf(BusinessException.class);
        return started;
    }

    @Test
    void manualRetryRetainsProcessingAfterTwoAiFailures() {
        StartedInterview started = createStartedInterview();
        given(interviewAiApi.evaluateAnswer(any(EvaluationContext.class)))
                .willThrow(new com.linj6.interviewagent.ai.evaluator.EvaluationValidationException("initial-1"))
                .willThrow(new com.linj6.interviewagent.ai.evaluator.EvaluationValidationException("initial-2"))
                .willThrow(new com.linj6.interviewagent.ai.evaluator.EvaluationValidationException("retry-1"))
                .willThrow(new com.linj6.interviewagent.ai.evaluator.EvaluationValidationException("retry-2"));
        assertThatThrownBy(() ->
                        interviewTurnApplicationService.submitAnswer(started.interviewId(), started.questionId(), "回答"))
                .isInstanceOf(BusinessException.class);

        assertThatThrownBy(() -> interviewTurnApplicationService.retryTurn(started.interviewId()))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(CommonErrorCode.AI_RESPONSE_INVALID));
        assertFailedTurnProcessing(started);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM answer WHERE question_id = ?", Integer.class, started.questionId()))
                .isEqualTo(1);
        verify(interviewAiApi, org.mockito.Mockito.times(4)).evaluateAnswer(any(EvaluationContext.class));
    }

    @Test
    void manualRetryRejectsOtherInterviewStatesWithoutAi() {
        StartedInterview started = createStartedInterview();
        for (String status : List.of("CREATED", "PAUSED", "COMPLETED")) {
            jdbcTemplate.update("UPDATE interview SET status = ? WHERE id = ?", status, started.interviewId());
            assertThatThrownBy(() -> interviewTurnApplicationService.retryTurn(started.interviewId()))
                    .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                            .isEqualTo(InterviewErrorCode.INTERVIEW_STATE_CONFLICT));
        }
        jdbcTemplate.update(
                "UPDATE interview SET status = 'IN_PROGRESS', current_phase = 'WAITING_ANSWER' WHERE id = ?",
                started.interviewId());
        assertThatThrownBy(() -> interviewTurnApplicationService.retryTurn(started.interviewId()))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(InterviewErrorCode.INTERVIEW_STATE_CONFLICT));
        verify(interviewAiApi, org.mockito.Mockito.never()).evaluateAnswer(any(EvaluationContext.class));
    }

    @Test
    void manualRetryKeepsExpectedVersionAcrossAttempts() {
        StartedInterview started = createStartedInterview();
        given(interviewAiApi.evaluateAnswer(any(EvaluationContext.class)))
                .willThrow(new com.linj6.interviewagent.ai.evaluator.EvaluationValidationException("initial-1"))
                .willThrow(new com.linj6.interviewagent.ai.evaluator.EvaluationValidationException("initial-2"));
        assertThatThrownBy(() ->
                        interviewTurnApplicationService.submitAnswer(started.interviewId(), started.questionId(), "回答"))
                .isInstanceOf(BusinessException.class);

        org.mockito.Mockito.reset(interviewAiApi);
        given(interviewAiApi.evaluateAnswer(any(EvaluationContext.class))).willAnswer(invocation -> {
            jdbcTemplate.update("UPDATE interview SET version = version + 1 WHERE id = ?", started.interviewId());
            throw new com.linj6.interviewagent.ai.evaluator.EvaluationValidationException("retryable");
        });
        assertThatThrownBy(() -> interviewTurnApplicationService.retryTurn(started.interviewId()))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(InterviewErrorCode.INTERVIEW_VERSION_CONFLICT));
        verify(interviewAiApi).evaluateAnswer(any(EvaluationContext.class));
    }

    @Test
    void manualRetryUsesExistingMissingRecordErrorsWithoutCallingAi() {
        assertThatThrownBy(() -> interviewTurnApplicationService.retryTurn(999999L))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(InterviewErrorCode.INTERVIEW_NOT_FOUND));

        StartedInterview started = createStartedInterview();
        given(interviewAiApi.evaluateAnswer(any(EvaluationContext.class)))
                .willThrow(new com.linj6.interviewagent.ai.evaluator.EvaluationValidationException("initial-1"))
                .willThrow(new com.linj6.interviewagent.ai.evaluator.EvaluationValidationException("initial-2"));
        assertThatThrownBy(() ->
                        interviewTurnApplicationService.submitAnswer(started.interviewId(), started.questionId(), "回答"))
                .isInstanceOf(BusinessException.class);

        org.mockito.Mockito.reset(interviewAiApi);
        jdbcTemplate.update("DELETE FROM answer WHERE question_id = ?", started.questionId());
        assertThatThrownBy(() -> interviewTurnApplicationService.retryTurn(started.interviewId()))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(InterviewErrorCode.ANSWER_NOT_FOUND));
        jdbcTemplate.update("UPDATE question SET status = 'ANSWERED' WHERE id = ?", started.questionId());
        assertThatThrownBy(() -> interviewTurnApplicationService.retryTurn(started.interviewId()))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(InterviewErrorCode.QUESTION_NOT_FOUND));
        verify(interviewAiApi, org.mockito.Mockito.never()).evaluateAnswer(any(EvaluationContext.class));
    }

    @Test
    void concurrentManualRetriesCommitOnlyOneEvaluation() throws Exception {
        StartedInterview started = createStartedInterview();
        given(interviewAiApi.evaluateAnswer(any(EvaluationContext.class)))
                .willThrow(new com.linj6.interviewagent.ai.evaluator.EvaluationValidationException("initial-1"))
                .willThrow(new com.linj6.interviewagent.ai.evaluator.EvaluationValidationException("initial-2"));
        assertThatThrownBy(() ->
                        interviewTurnApplicationService.submitAnswer(started.interviewId(), started.questionId(), "回答"))
                .isInstanceOf(BusinessException.class);

        org.mockito.Mockito.reset(interviewAiApi);
        CountDownLatch evaluating = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        given(interviewAiApi.evaluateAnswer(any(EvaluationContext.class))).willAnswer(invocation -> {
            evaluating.countDown();
            if (!release.await(10, java.util.concurrent.TimeUnit.SECONDS)) {
                throw new IllegalStateException("test timed out");
            }
            return validEvaluation(invocation.getArgument(0));
        });
        given(interviewAiApi.selectFocus(any())).willReturn(new FocusProposal("事务处理"));
        given(interviewAiApi.generateMainQuestion(any())).willReturn(new GeneratedQuestion("默认主问题"));
        given(interviewAiApi.decideNext(any(DecisionContext.class)))
                .willReturn(new ValidatedDecisionResult(DecisionAction.END, "测试结束", null));
        var executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(() -> retrySucceeded(started.interviewId()));
            Future<Boolean> second = executor.submit(() -> retrySucceeded(started.interviewId()));
            assertThat(evaluating.await(10, java.util.concurrent.TimeUnit.SECONDS))
                    .isTrue();
            release.countDown();
            assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(true, false);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM evaluation WHERE question_id = ?", Integer.class, started.questionId()))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM answer WHERE question_id = ?", Integer.class, started.questionId()))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM question WHERE interview_id = ?", Integer.class, started.interviewId()))
                .isEqualTo(1);
    }

    @Test
    void manualRetryWinningAgainstAutomaticRetryPreventsStaleTurnCommit() throws Exception {
        StartedInterview started = createStartedInterview();
        CountDownLatch automaticAttemptEntered = new CountDownLatch(1);
        CountDownLatch releaseAutomaticAttempt = new CountDownLatch(1);
        given(interviewAiApi.evaluateAnswer(any(EvaluationContext.class)))
                .willAnswer(invocation -> {
                    automaticAttemptEntered.countDown();
                    if (!releaseAutomaticAttempt.await(10, java.util.concurrent.TimeUnit.SECONDS)) {
                        throw new IllegalStateException("test timed out");
                    }
                    throw new com.linj6.interviewagent.ai.evaluator.EvaluationValidationException("automatic retry");
                })
                .willAnswer(invocation -> validEvaluation(invocation.getArgument(0)));
        var executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> automatic = executor.submit(() ->
                    interviewTurnApplicationService.submitAnswer(started.interviewId(), started.questionId(), "正式回答"));
            assertThat(automaticAttemptEntered.await(10, java.util.concurrent.TimeUnit.SECONDS))
                    .isTrue();

            var manualResponse = interviewTurnApplicationService.retryTurn(started.interviewId());
            assertThat(manualResponse.answer().id())
                    .isEqualTo(jdbcTemplate.queryForObject(
                            "SELECT id FROM answer WHERE question_id = ?", Long.class, started.questionId()));
            releaseAutomaticAttempt.countDown();
            assertThatThrownBy(automatic::get).hasCauseInstanceOf(BusinessException.class);
        } finally {
            releaseAutomaticAttempt.countDown();
            executor.shutdownNow();
        }
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM interview WHERE id = ?", String.class, started.interviewId()))
                .isEqualTo("COMPLETED");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM evaluation WHERE question_id = ?", Integer.class, started.questionId()))
                .isEqualTo(1);
        verify(interviewAiApi, org.mockito.Mockito.times(2)).evaluateAnswer(any(EvaluationContext.class));
    }

    private boolean retrySucceeded(long interviewId) {
        try {
            interviewTurnApplicationService.retryTurn(interviewId);
            return true;
        } catch (BusinessException expectedConflict) {
            assertThat(expectedConflict.getErrorCode())
                    .isIn(
                            InterviewErrorCode.INTERVIEW_STATE_CONFLICT,
                            InterviewErrorCode.INTERVIEW_VERSION_CONFLICT,
                            EvaluationErrorCode.EVALUATION_ALREADY_EXISTS);
            return false;
        }
    }

    @Test
    void retryReloadsQuestionAndAnswerFromDatabase() {
        StartedInterview started = createStartedInterview();
        given(interviewAiApi.evaluateAnswer(any(EvaluationContext.class)))
                .willAnswer(invocation -> {
                    jdbcTemplate.update(
                            "UPDATE question SET question_content = ? WHERE id = ?", "重载后的问题", started.questionId());
                    jdbcTemplate.update(
                            "UPDATE answer SET content = ? WHERE question_id = ?", "重载后的回答", started.questionId());
                    throw new com.linj6.interviewagent.ai.evaluator.EvaluationValidationException("invalid");
                })
                .willAnswer(invocation -> validEvaluation(invocation.getArgument(0)));

        interviewTurnApplicationService.submitAnswer(started.interviewId(), started.questionId(), "初始回答");

        ArgumentCaptor<EvaluationContext> contexts = ArgumentCaptor.forClass(EvaluationContext.class);
        org.mockito.Mockito.verify(interviewAiApi, org.mockito.Mockito.times(2)).evaluateAnswer(contexts.capture());
        assertThat(contexts.getAllValues())
                .extracting(EvaluationContext::questionContent)
                .containsExactly("请说明事务与并发控制。", "重载后的问题");
        assertThat(contexts.getAllValues())
                .extracting(EvaluationContext::answerContent)
                .containsExactly("初始回答", "重载后的回答");
    }

    @Test
    void retryKeepsOriginalExpectedVersion() {
        StartedInterview started = createStartedInterview();
        var first = new com.linj6.interviewagent.ai.evaluator.EvaluationValidationException("invalid");
        given(interviewAiApi.evaluateAnswer(any(EvaluationContext.class))).willAnswer(invocation -> {
            jdbcTemplate.update("UPDATE interview SET version = version + 1 WHERE id = ?", started.interviewId());
            throw first;
        });

        assertThatThrownBy(() -> interviewTurnApplicationService.submitAnswer(
                        started.interviewId(), started.questionId(), "正式回答"))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(InterviewErrorCode.INTERVIEW_VERSION_CONFLICT);
                    assertThat(exception.getSuppressed()).containsExactly(first);
                });
        org.mockito.Mockito.verify(interviewAiApi).evaluateAnswer(any(EvaluationContext.class));
        assertFailedTurnProcessing(started);
    }

    @Test
    void concurrentSecondSubmissionFailsBeforeAiAndDoesNotSaveAnswer() throws Exception {
        StartedInterview started = createStartedInterview();
        CountDownLatch evaluating = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        given(interviewAiApi.evaluateAnswer(any(EvaluationContext.class))).willAnswer(invocation -> {
            evaluating.countDown();
            if (!release.await(10, java.util.concurrent.TimeUnit.SECONDS)) {
                throw new IllegalStateException("test timed out");
            }
            return validEvaluation(invocation.getArgument(0));
        });
        var executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> first = executor.submit(() ->
                    interviewTurnApplicationService.submitAnswer(started.interviewId(), started.questionId(), "第一个回答"));
            assertThat(evaluating.await(10, java.util.concurrent.TimeUnit.SECONDS))
                    .isTrue();
            assertThatThrownBy(() -> interviewTurnApplicationService.submitAnswer(
                            started.interviewId(), started.questionId(), "第二个回答"))
                    .isInstanceOf(BusinessException.class);
            release.countDown();
            first.get();
            assertThat(jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM answer WHERE question_id = ?", Integer.class, started.questionId()))
                    .isEqualTo(1);
            org.mockito.Mockito.verify(interviewAiApi).evaluateAnswer(any(EvaluationContext.class));
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void secondAiFailureKeepsProcessingAndSuppressesFirstFailure(CapturedOutput output) {
        StartedInterview started = createStartedInterview();
        var first = new com.linj6.interviewagent.ai.evaluator.EvaluationValidationException(
                "Score must use a 0.5 step: ACCURACY");
        var second = new com.linj6.interviewagent.ai.evaluator.EvaluationValidationException(
                "Evidence quote is not a continuous excerpt of Answer: DEPTH");
        given(interviewAiApi.evaluateAnswer(any(EvaluationContext.class)))
                .willThrow(first)
                .willThrow(second);

        assertThatThrownBy(() -> interviewTurnApplicationService.submitAnswer(
                        started.interviewId(), started.questionId(), "正式回答"))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(CommonErrorCode.AI_RESPONSE_INVALID);
                    assertThat(exception.getCause()).isSameAs(second);
                    assertThat(exception.getSuppressed()).containsExactly(first);
                    assertThat(exception.getCause().getMessage())
                            .isEqualTo("Evidence quote is not a continuous excerpt of Answer: DEPTH");
                });
        assertThat(output.getOut())
                .contains("interviewId=" + started.interviewId())
                .contains("questionId=" + started.questionId())
                .contains("attempt=1 stage=EVALUATE")
                .contains("validationReason=Score must use a 0.5 step: ACCURACY")
                .contains("attempt=2 stage=EVALUATE")
                .contains("validationReason=Evidence quote is not a continuous excerpt of Answer: DEPTH");
        assertFailedTurnProcessing(started);
        Map<String, Object> detail = data(restTemplate.getForObject(
                "http://localhost:" + port + "/api/interviews/" + started.interviewId(), Map.class));
        assertThat(detail.get("currentQuestion")).isNull();
        org.mockito.Mockito.verify(interviewAiApi, org.mockito.Mockito.times(2))
                .evaluateAnswer(any(EvaluationContext.class));
    }

    @Test
    void unknownAiFailureDoesNotRetry() {
        StartedInterview started = createStartedInterview();
        given(interviewAiApi.evaluateAnswer(any(EvaluationContext.class)))
                .willThrow(new IllegalStateException("unexpected"));

        assertThatThrownBy(() -> interviewTurnApplicationService.submitAnswer(
                        started.interviewId(), started.questionId(), "正式回答"))
                .isInstanceOf(IllegalStateException.class);
        org.mockito.Mockito.verify(interviewAiApi).evaluateAnswer(any(EvaluationContext.class));
        assertFailedTurnProcessing(started);
    }

    @Test
    void localIllegalArgumentExceptionDoesNotRetryAiCall() {
        StartedInterview started = createStartedInterview();
        given(interviewAiApi.evaluateAnswer(any(EvaluationContext.class)))
                .willThrow(new IllegalArgumentException("local context bug"));

        assertThatThrownBy(() -> interviewTurnApplicationService.submitAnswer(
                        started.interviewId(), started.questionId(), "正式回答"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("local context bug");
        org.mockito.Mockito.verify(interviewAiApi).evaluateAnswer(any(EvaluationContext.class));
        assertFailedTurnProcessing(started);
    }

    @Test
    void mapsAiTimeoutAndProviderErrors() {
        assertMappedAiFailure(
                new RuntimeException(new SocketTimeoutException("timeout")), CommonErrorCode.AI_SERVICE_TIMEOUT);
        assertMappedAiFailure(
                HttpClientErrorException.create(
                        HttpStatus.TOO_MANY_REQUESTS, "rate limit", new HttpHeaders(), new byte[0], null),
                CommonErrorCode.RATE_LIMIT);
        assertMappedAiFailure(
                HttpServerErrorException.create(
                        HttpStatus.SERVICE_UNAVAILABLE, "unavailable", new HttpHeaders(), new byte[0], null),
                CommonErrorCode.AI_SERVICE_ERROR);
    }

    private void assertMappedAiFailure(RuntimeException providerFailure, CommonErrorCode expectedCode) {
        StartedInterview started = createStartedInterview();
        given(interviewAiApi.evaluateAnswer(any(EvaluationContext.class))).willThrow(providerFailure);
        assertThatThrownBy(() -> interviewTurnApplicationService.submitAnswer(
                        started.interviewId(), started.questionId(), "正式回答"))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(expectedCode);
                    assertThat(exception.getCause()).isSameAs(providerFailure);
                });
        assertFailedTurnProcessing(started);
    }

    @Test
    void validatesAnswerRequestAndBusinessStateBeforeT1() {
        var missingInterview = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/interviews/999999/questions/999999/answer",
                Map.of("content", "回答"),
                Map.class);
        assertThat(missingInterview.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(missingInterview.getBody()).containsEntry("code", InterviewErrorCode.INTERVIEW_NOT_FOUND.getCode());

        StartedInterview first = createStartedInterview();
        var blank = restTemplate.postForEntity(answerUrl(first), Map.of("content", "   "), Map.class);
        assertThat(blank.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(blank.getBody()).containsEntry("code", CommonErrorCode.PARAM_ERROR.getCode());
        var missingQuestion = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/interviews/" + first.interviewId() + "/questions/999999/answer",
                Map.of("content", "回答"),
                String.class);
        assertThat(missingQuestion.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        StartedInterview second = createStartedInterview();
        var wrongOwner = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/interviews/" + first.interviewId() + "/questions/"
                        + second.questionId() + "/answer",
                Map.of("content", "回答"),
                String.class);
        assertThat(wrongOwner.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        jdbcTemplate.update("UPDATE interview SET status = 'PAUSED' WHERE id = ?", first.interviewId());
        var wrongStatus = restTemplate.postForEntity(answerUrl(first), Map.of("content", "回答"), String.class);
        assertThat(wrongStatus.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        jdbcTemplate.update(
                "UPDATE interview SET status = 'IN_PROGRESS', current_phase = 'OPENING' WHERE id = ?",
                first.interviewId());
        var wrongPhase = restTemplate.postForEntity(answerUrl(first), Map.of("content", "回答"), String.class);
        assertThat(wrongPhase.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        jdbcTemplate.update("UPDATE interview SET current_phase = 'WAITING_ANSWER' WHERE id = ?", first.interviewId());
        jdbcTemplate.update("UPDATE question SET status = 'ANSWERED' WHERE id = ?", first.questionId());
        var wrongQuestionStatus = restTemplate.postForEntity(answerUrl(first), Map.of("content", "回答"), String.class);
        assertThat(wrongQuestionStatus.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void keepsT1WhenProfileDoesNotExist() {
        StartedInterview started = createStartedInterview();
        jdbcTemplate.update("UPDATE question SET evaluation_profile_id = 999999 WHERE id = ?", started.questionId());

        var response = restTemplate.postForEntity(answerUrl(started), Map.of("content", "回答"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM answer WHERE question_id = ?", Integer.class, started.questionId()))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT current_phase FROM interview WHERE id = ?", String.class, started.interviewId()))
                .isEqualTo("PROCESSING_TURN");
    }

    @Test
    void keepsAnswerAndProcessingStateWhenEvaluatorFails() {
        StartedInterview started = createStartedInterview();
        given(interviewAiApi.evaluateAnswer(any(EvaluationContext.class)))
                .willThrow(new IllegalStateException("测试模型调用失败"));

        var response = restTemplate.postForEntity(answerUrl(started), Map.of("content", "回答"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertFailedTurnProcessing(started);
        var repeated = restTemplate.postForEntity(answerUrl(started), Map.of("content", "第二次回答"), String.class);
        assertThat(repeated.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void rollsBackAllT2WritesWhenDimensionInsertFails() {
        StartedInterview started = createStartedInterview();
        given(interviewAiApi.evaluateAnswer(any(EvaluationContext.class)))
                .willAnswer(invocation -> validEvaluation(invocation.getArgument(0)));
        jdbcTemplate.execute(
                """
                CREATE TRIGGER fail_dimension_insert BEFORE INSERT ON evaluation_dimension_score
                FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'forced dimension failure'
                """);
        try {
            var response = restTemplate.postForEntity(answerUrl(started), Map.of("content", "回答"), String.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        } finally {
            jdbcTemplate.execute("DROP TRIGGER fail_dimension_insert");
        }
        assertFailedTurnProcessing(started);
    }

    @Test
    void rollsBackEvaluationWhenQuestionTransitionFailsInT2() {
        StartedInterview started = createStartedInterview();
        given(interviewAiApi.evaluateAnswer(any(EvaluationContext.class)))
                .willAnswer(invocation -> validEvaluation(invocation.getArgument(0)));
        jdbcTemplate.execute(
                """
                CREATE TRIGGER fail_question_transition BEFORE UPDATE ON question
                FOR EACH ROW
                BEGIN
                  IF NEW.id = %d AND NEW.status = 'ANSWERED' THEN
                    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'forced question transition failure';
                  END IF;
                END
                """
                        .formatted(started.questionId()));
        try {
            var response = restTemplate.postForEntity(answerUrl(started), Map.of("content", "回答"), String.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        } finally {
            jdbcTemplate.execute("DROP TRIGGER fail_question_transition");
        }
        assertFailedTurnProcessing(started);
    }

    @Test
    void rollsBackEvaluationAndQuestionWhenInterviewTransitionFailsInT2() {
        StartedInterview started = createStartedInterview();
        given(interviewAiApi.evaluateAnswer(any(EvaluationContext.class)))
                .willAnswer(invocation -> validEvaluation(invocation.getArgument(0)));
        jdbcTemplate.execute(
                """
                CREATE TRIGGER fail_interview_transition BEFORE UPDATE ON interview
                FOR EACH ROW
                BEGIN
                  IF NEW.id = %d AND NEW.status = 'COMPLETED'
                      AND OLD.current_phase = 'PROCESSING_TURN'
                      AND NEW.version = OLD.version + 1 THEN
                    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'forced interview transition failure';
                  END IF;
                END
                """
                        .formatted(started.interviewId()));
        try {
            var response = restTemplate.postForEntity(answerUrl(started), Map.of("content", "回答"), String.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        } finally {
            jdbcTemplate.execute("DROP TRIGGER fail_interview_transition");
        }
        assertFailedTurnProcessing(started);
    }

    @Test
    void rejectsT2VersionConflictWithoutPersistingEvaluation() {
        StartedInterview started = createStartedInterview();
        given(interviewAiApi.evaluateAnswer(any(EvaluationContext.class))).willAnswer(invocation -> {
            jdbcTemplate.update("UPDATE interview SET version = version + 1 WHERE id = ?", started.interviewId());
            return validEvaluation(invocation.getArgument(0));
        });

        var response = restTemplate.postForEntity(answerUrl(started), Map.of("content", "回答"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM evaluation WHERE question_id = ?", Integer.class, started.questionId()))
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM question WHERE id = ?", String.class, started.questionId()))
                .isEqualTo("WAITING_ANSWER");
    }

    @Test
    void finishesWaitingInterviewWithoutChangingQuestionOrCreatingTurnFacts() {
        StartedInterview started = createStartedInterview();
        org.mockito.Mockito.clearInvocations(interviewAiApi);
        String url = "http://localhost:" + port + "/api/interviews/" + started.interviewId();
        Long originalVersion = jdbcTemplate.queryForObject(
                "SELECT version FROM interview WHERE id = ?", Long.class, started.interviewId());

        var finished = restTemplate.postForEntity(url + "/finish", null, Map.class);

        assertThat(finished.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(finished.getBody()))
                .containsEntry("status", "COMPLETED")
                .containsEntry("currentPhase", null)
                .containsEntry("currentQuestion", null);
        assertThat(jdbcTemplate.queryForMap(
                        "SELECT status, current_phase, version FROM interview WHERE id = ?", started.interviewId()))
                .containsEntry("status", "COMPLETED")
                .containsEntry("current_phase", null)
                .containsEntry("version", originalVersion + 1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM question WHERE id = ?", String.class, started.questionId()))
                .isEqualTo("WAITING_ANSWER");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM answer WHERE interview_id = ?", Integer.class, started.interviewId()))
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM evaluation WHERE interview_id = ?", Integer.class, started.interviewId()))
                .isZero();
        org.mockito.Mockito.verifyNoInteractions(interviewAiApi);
    }

    @Test
    void finishesPausedWaitingInterview() {
        StartedInterview started = createStartedInterview();
        String url = "http://localhost:" + port + "/api/interviews/" + started.interviewId();
        assertThat(restTemplate.postForEntity(url + "/pause", null, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        org.mockito.Mockito.clearInvocations(interviewAiApi);

        var finished = restTemplate.postForEntity(url + "/finish", null, Map.class);

        assertThat(finished.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(finished.getBody()))
                .containsEntry("status", "COMPLETED")
                .containsEntry("currentPhase", null)
                .containsEntry("currentQuestion", null);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM question WHERE id = ?", String.class, started.questionId()))
                .isEqualTo("WAITING_ANSWER");
        org.mockito.Mockito.verifyNoInteractions(interviewAiApi);
    }

    @Test
    void finishRejectsIllegalStatesAndStaleVersionWithStateConflict() {
        StartedInterview started = createStartedInterview();
        String url = "http://localhost:" + port + "/api/interviews/" + started.interviewId();

        for (String phase : List.of("OPENING", "PROCESSING_TURN")) {
            jdbcTemplate.update(
                    "UPDATE interview SET status = 'IN_PROGRESS', current_phase = ? WHERE id = ?",
                    phase,
                    started.interviewId());
            assertThat(restTemplate
                            .postForEntity(url + "/finish", null, String.class)
                            .getStatusCode())
                    .isEqualTo(HttpStatus.CONFLICT);
        }
        jdbcTemplate.update(
                "UPDATE interview SET status = 'COMPLETED', current_phase = NULL WHERE id = ?", started.interviewId());
        assertThat(restTemplate
                        .postForEntity(url + "/finish", null, String.class)
                        .getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        jdbcTemplate.update(
                "UPDATE interview SET status = 'PAUSED', current_phase = 'PROCESSING_TURN' WHERE id = ?",
                started.interviewId());
        assertThat(restTemplate
                        .postForEntity(url + "/finish", null, String.class)
                        .getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        jdbcTemplate.update(
                "UPDATE interview SET status = 'IN_PROGRESS', current_phase = 'WAITING_ANSWER', version = version + 1 WHERE id = ?",
                started.interviewId());
        Long staleVersion = jdbcTemplate.queryForObject(
                "SELECT version - 1 FROM interview WHERE id = ?", Long.class, started.interviewId());
        assertThatThrownBy(() -> interviewRepository.finish(
                        started.interviewId(),
                        InterviewStatus.IN_PROGRESS,
                        staleVersion,
                        LocalDateTime.parse("2026-09-26T11:00:00")))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(InterviewErrorCode.INTERVIEW_STATE_CONFLICT));
    }

    @Test
    void concurrentFinishAllowsOnlyOneSuccess() throws Exception {
        StartedInterview started = createStartedInterview();
        org.mockito.Mockito.clearInvocations(interviewAiApi);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            Callable<HttpStatus> finish = () -> {
                ready.countDown();
                start.await();
                return (HttpStatus) restTemplate
                        .postForEntity(
                                "http://localhost:" + port + "/api/interviews/" + started.interviewId() + "/finish",
                                null,
                                String.class)
                        .getStatusCode();
            };
            Future<HttpStatus> first = executor.submit(finish);
            Future<HttpStatus> second = executor.submit(finish);
            ready.await();
            start.countDown();

            assertThat(List.of(first.get(), second.get()))
                    .containsExactlyInAnyOrder(HttpStatus.OK, HttpStatus.CONFLICT);
            assertThat(jdbcTemplate.queryForMap(
                            "SELECT status, current_phase FROM interview WHERE id = ?", started.interviewId()))
                    .containsEntry("status", "COMPLETED")
                    .containsEntry("current_phase", null);
            assertThat(jdbcTemplate.queryForObject(
                            "SELECT status FROM question WHERE id = ?", String.class, started.questionId()))
                    .isEqualTo("WAITING_ANSWER");
            assertThat(jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM answer WHERE interview_id = ?", Integer.class, started.interviewId()))
                    .isZero();
            assertThat(jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM evaluation WHERE interview_id = ?",
                            Integer.class,
                            started.interviewId()))
                    .isZero();
            org.mockito.Mockito.verifyNoInteractions(interviewAiApi);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentSubmitAndFinishKeepOnlyTheWinningBusinessFacts() throws Exception {
        StartedInterview started = createStartedInterview();
        given(interviewAiApi.evaluateAnswer(any(EvaluationContext.class)))
                .willAnswer(invocation -> validEvaluation(invocation.getArgument(0)));
        given(interviewAiApi.selectFocus(any())).willReturn(new FocusProposal("事务处理"));
        given(interviewAiApi.generateMainQuestion(any())).willReturn(new GeneratedQuestion("默认主问题"));
        given(interviewAiApi.decideNext(any(DecisionContext.class)))
                .willReturn(new ValidatedDecisionResult(DecisionAction.END, "完成面试", null));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> finish = () -> {
                ready.countDown();
                start.await();
                try {
                    interviewApplicationService.finishInterview(started.interviewId());
                    return true;
                } catch (BusinessException expected) {
                    return false;
                }
            };
            Callable<Boolean> submit = () -> {
                ready.countDown();
                start.await();
                try {
                    interviewTurnApplicationService.submitAnswer(
                            started.interviewId(), started.questionId(), "并发提交的正式回答");
                    return true;
                } catch (BusinessException expected) {
                    return false;
                }
            };
            Future<Boolean> finishResult = executor.submit(finish);
            Future<Boolean> submitResult = executor.submit(submit);
            assertThat(ready.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            start.countDown();

            boolean finishWon = finishResult.get(30, java.util.concurrent.TimeUnit.SECONDS);
            boolean submitWon = submitResult.get(30, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(List.of(finishWon, submitWon)).containsExactlyInAnyOrder(true, false);
            assertThat(jdbcTemplate.queryForMap(
                            "SELECT status, current_phase FROM interview WHERE id = ?", started.interviewId()))
                    .containsEntry("status", "COMPLETED")
                    .containsEntry("current_phase", null);
            if (finishWon) {
                assertThat(jdbcTemplate.queryForObject(
                                "SELECT status FROM question WHERE id = ?", String.class, started.questionId()))
                        .isEqualTo("WAITING_ANSWER");
                assertThat(jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM answer WHERE interview_id = ?",
                                Integer.class,
                                started.interviewId()))
                        .isZero();
                assertThat(jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM evaluation WHERE interview_id = ?",
                                Integer.class,
                                started.interviewId()))
                        .isZero();
            } else {
                assertThat(jdbcTemplate.queryForObject(
                                "SELECT status FROM question WHERE id = ?", String.class, started.questionId()))
                        .isEqualTo("ANSWERED");
                assertThat(jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM answer WHERE interview_id = ?",
                                Integer.class,
                                started.interviewId()))
                        .isEqualTo(1);
                assertThat(jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM evaluation WHERE interview_id = ?",
                                Integer.class,
                                started.interviewId()))
                        .isEqualTo(1);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void pausesAndResumesOriginalQuestionWithoutSideEffects() {
        StartedInterview started = createStartedInterview();
        org.mockito.Mockito.clearInvocations(interviewAiApi);
        String url = "http://localhost:" + port + "/api/interviews/" + started.interviewId();
        Long originalVersion = jdbcTemplate.queryForObject(
                "SELECT version FROM interview WHERE id = ?", Long.class, started.interviewId());

        var paused = restTemplate.postForEntity(url + "/pause", null, Map.class);
        assertThat(paused.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(paused.getBody()))
                .containsEntry("status", "PAUSED")
                .containsEntry("currentPhase", "WAITING_ANSWER");
        assertThat(Long.parseLong((String) ((Map<?, ?>) data(paused.getBody()).get("currentQuestion")).get("id")))
                .isEqualTo(started.questionId());
        Map<String, Object> pausedDetail = data(restTemplate.getForObject(url, Map.class));
        assertThat(Long.parseLong((String) ((Map<?, ?>) pausedDetail.get("currentQuestion")).get("id")))
                .isEqualTo(started.questionId());

        var resumed = restTemplate.postForEntity(url + "/resume", null, Map.class);
        assertThat(resumed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(resumed.getBody()))
                .containsEntry("status", "IN_PROGRESS")
                .containsEntry("currentPhase", "WAITING_ANSWER");
        assertThat(Long.parseLong((String) ((Map<?, ?>) data(resumed.getBody()).get("currentQuestion")).get("id")))
                .isEqualTo(started.questionId());
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT version FROM interview WHERE id = ?", Long.class, started.interviewId()))
                .isEqualTo(originalVersion + 2);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM question WHERE interview_id = ?", Integer.class, started.interviewId()))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM question WHERE id = ?", String.class, started.questionId()))
                .isEqualTo("WAITING_ANSWER");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM answer WHERE interview_id = ?", Integer.class, started.interviewId()))
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM evaluation WHERE interview_id = ?", Integer.class, started.interviewId()))
                .isZero();
        org.mockito.Mockito.verifyNoInteractions(interviewAiApi);
    }

    @Test
    void pauseAndResumeRejectInvalidStatesAndStaleVersion() {
        StartedInterview started = createStartedInterview();
        String url = "http://localhost:" + port + "/api/interviews/" + started.interviewId();
        Long version = jdbcTemplate.queryForObject(
                "SELECT version FROM interview WHERE id = ?", Long.class, started.interviewId());

        assertThat(restTemplate
                        .postForEntity(url + "/resume", null, String.class)
                        .getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        interviewRepository.pause(started.interviewId(), version);
        assertThatThrownBy(() -> interviewRepository.resume(started.interviewId(), version))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(InterviewErrorCode.INTERVIEW_STATE_CONFLICT));
        assertThat(restTemplate
                        .postForEntity(url + "/pause", null, String.class)
                        .getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        interviewRepository.resume(started.interviewId(), version + 1);
        assertThatThrownBy(() -> interviewRepository.pause(started.interviewId(), version))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(InterviewErrorCode.INTERVIEW_STATE_CONFLICT));

        for (String phase : List.of("OPENING", "PROCESSING_TURN")) {
            jdbcTemplate.update("UPDATE interview SET current_phase = ? WHERE id = ?", phase, started.interviewId());
            assertThat(restTemplate
                            .postForEntity(url + "/pause", null, String.class)
                            .getStatusCode())
                    .isEqualTo(HttpStatus.CONFLICT);
        }
        jdbcTemplate.update(
                "UPDATE interview SET status = 'COMPLETED', current_phase = NULL WHERE id = ?", started.interviewId());
        assertThat(restTemplate
                        .postForEntity(url + "/pause", null, String.class)
                        .getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(restTemplate
                        .postForEntity(url + "/resume", null, String.class)
                        .getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(data(restTemplate.getForObject(url, Map.class)).get("currentQuestion"))
                .isNull();
        for (String phase : List.of("OPENING", "PROCESSING_TURN")) {
            jdbcTemplate.update(
                    "UPDATE interview SET status = 'PAUSED', current_phase = ? WHERE id = ?",
                    phase,
                    started.interviewId());
            assertThat(restTemplate
                            .postForEntity(url + "/resume", null, String.class)
                            .getStatusCode())
                    .isEqualTo(HttpStatus.CONFLICT);
            assertThat(data(restTemplate.getForObject(url, Map.class)).get("currentQuestion"))
                    .isNull();
        }
    }

    @Test
    void pauseAndSubmitAnswerRaceAllowsOnlyOneSuccess() throws Exception {
        StartedInterview started = createStartedInterview();
        given(interviewAiApi.evaluateAnswer(any(EvaluationContext.class)))
                .willAnswer(invocation -> validEvaluation(invocation.getArgument(0)));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            Callable<HttpStatus> pause = () -> {
                ready.countDown();
                start.await();
                return (HttpStatus) restTemplate
                        .postForEntity(
                                "http://localhost:" + port + "/api/interviews/" + started.interviewId() + "/pause",
                                null,
                                String.class)
                        .getStatusCode();
            };
            Callable<HttpStatus> answer = () -> {
                ready.countDown();
                start.await();
                return (HttpStatus) restTemplate
                        .postForEntity(answerUrl(started), Map.of("content", "并发回答"), String.class)
                        .getStatusCode();
            };
            Future<HttpStatus> pauseResult = executor.submit(pause);
            Future<HttpStatus> answerResult = executor.submit(answer);
            ready.await();
            start.countDown();
            assertThat(List.of(pauseResult.get(), answerResult.get()))
                    .containsExactlyInAnyOrder(HttpStatus.OK, HttpStatus.CONFLICT);
            Map<String, Object> row = jdbcTemplate.queryForMap(
                    "SELECT status, current_phase FROM interview WHERE id = ?", started.interviewId());
            if (pauseResult.get() == HttpStatus.OK) {
                assertThat(row).containsEntry("status", "PAUSED").containsEntry("current_phase", "WAITING_ANSWER");
                assertThat(jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM answer WHERE interview_id = ?",
                                Integer.class,
                                started.interviewId()))
                        .isZero();
            } else {
                assertThat(row).containsEntry("status", "COMPLETED").containsEntry("current_phase", null);
                assertThat(jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM answer WHERE interview_id = ?",
                                Integer.class,
                                started.interviewId()))
                        .isEqualTo(1);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    /** 测试准备正式 Plan，不伪造 AI 临时引用，initial 选择非列表首项。 */
    private InterviewPlan preparePlanForStart(Long interviewId) {
        long planId = IdWorker.getId();
        var items = java.util.stream.IntStream.range(0, 5)
                .mapToObj(i -> new PlanItem(IdWorker.getId(), planId, KnowledgeArea.MYSQL, "事务处理", "首题理由"))
                .toList();
        return planRepository.save(
                new InterviewPlan(planId, interviewId, items.get(2).id(), items), 5);
    }

    @Test
    void startRequiresPreparedPlanAndRejectsIllegalState() {
        Long id = interviewRepository.create(1L, 1L).id();
        assertThatThrownBy(() -> interviewApplicationService.startInterview(id))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(InterviewErrorCode.INTERVIEW_STATE_CONFLICT));
        assertThat(interviewRepository.findById(id).status()).isEqualTo(InterviewStatus.CREATED);
        var prepared = preparePlanForStart(id);
        jdbcTemplate.update("UPDATE interview SET status='PAUSED' WHERE id=?", id);
        assertThatThrownBy(() -> interviewApplicationService.startInterview(id)).isInstanceOf(BusinessException.class);
        assertThat(planRepository.findByInterviewId(id)).isEqualTo(prepared);
        verify(interviewAiApi, never()).generateMainQuestion(any());
    }

    @Test
    void openingRejectsForeignInitialPlanItem() {
        Long id = interviewRepository.create(1L, 1L).id();
        var plan = preparePlanForStart(id);
        Long other = interviewRepository.create(1L, 1L).id();
        var otherPlan = preparePlanForStart(other);
        jdbcTemplate.update(
                "UPDATE interview_plan SET initial_plan_item_id=? WHERE id=?",
                otherPlan.initialPlanItemId(),
                plan.id());
        assertThatThrownBy(() -> interviewApplicationService.startInterview(id)).isInstanceOf(BusinessException.class);
        assertThat(interviewRepository.findById(id).status()).isEqualTo(InterviewStatus.CREATED);
        verify(interviewAiApi, never()).generateMainQuestion(any());
    }

    @Test
    void openingRejectsMovedItemOwnership() {
        Long id = interviewRepository.create(1L, 1L).id();
        var plan = preparePlanForStart(id);
        Long other = interviewRepository.create(1L, 1L).id();
        var otherPlan = preparePlanForStart(other);
        jdbcTemplate.update(
                "UPDATE interview_plan_item SET plan_id=? WHERE id=?", otherPlan.id(), plan.initialPlanItemId());
        assertThatThrownBy(() -> interviewApplicationService.startInterview(id)).isInstanceOf(BusinessException.class);
        assertThat(questionRepository.findByInterviewId(id)).isEmpty();
        verify(interviewAiApi, never()).generateMainQuestion(any());
    }

    @Test
    void openingRejectsUsedInitialPlanItem() {
        Long id = interviewRepository.create(1L, 1L).id();
        var plan = preparePlanForStart(id);
        questionRepository.create(QuestionCreateCommand.builder()
                .interviewId(id)
                .sequenceNo(1)
                .type(QuestionType.MAIN)
                .content("existing")
                .evaluationProfileId(1L)
                .planItemId(plan.initialPlanItemId())
                .build());
        assertThatThrownBy(() -> interviewApplicationService.startInterview(id)).isInstanceOf(BusinessException.class);
        assertThat(questionRepository.findByInterviewId(id)).hasSize(1);
        verify(interviewAiApi, never()).generateMainQuestion(any());
    }

    @Test
    void openingRechecksPlanBeforeCommit() {
        Long id = interviewRepository.create(1L, 1L).id();
        var plan = preparePlanForStart(id);
        given(interviewAiApi.generateMainQuestion(any())).willAnswer(call -> {
            jdbcTemplate.update("UPDATE interview_plan_item SET reason='changed' WHERE id=?", plan.initialPlanItemId());
            return new GeneratedQuestion("首题");
        });
        assertThatThrownBy(() -> interviewApplicationService.startInterview(id)).isInstanceOf(BusinessException.class);
        assertThat(questionRepository.findByInterviewId(id)).isEmpty();
        assertThat(interviewRepository.findById(id).status()).isEqualTo(InterviewStatus.CREATED);
        assertThat(planRepository.findByInterviewId(id).id()).isEqualTo(plan.id());
    }

    @Test
    void openingRollbackKeepsPlanAndAllowsRetry() {
        Long id = interviewRepository.create(1L, 1L).id();
        var plan = preparePlanForStart(id);
        jdbcTemplate.execute(
                "CREATE TRIGGER fail_opening_commit BEFORE UPDATE ON interview FOR EACH ROW BEGIN IF NEW.id=" + id
                        + " AND NEW.current_phase='WAITING_ANSWER' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='test rollback'; END IF; END");
        try {
            assertThatThrownBy(() -> interviewApplicationService.startInterview(id))
                    .isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThat(questionRepository.findByInterviewId(id)).isEmpty();
            assertThat(interviewRepository.findById(id).status()).isEqualTo(InterviewStatus.CREATED);
            assertThat(planRepository.findByInterviewId(id)).isEqualTo(plan);
        } finally {
            jdbcTemplate.execute("DROP TRIGGER fail_opening_commit");
        }
        interviewApplicationService.startInterview(id);
        assertThat(questionRepository.findFirstByInterviewId(id).planItemId()).isEqualTo(plan.initialPlanItemId());
    }

    @Test
    void openingAiRunsOutsideTransactionAndCanRetrySameInitialItem() {
        Long id = interviewRepository.create(1L, 1L).id();
        var plan = preparePlanForStart(id);
        var contexts = new java.util.ArrayList<MainQuestionGenerationContext>();
        given(interviewAiApi.generateMainQuestion(any())).willAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                    .isFalse();
            var state = interviewRepository.findById(id);
            assertThat(state.currentPhase()).isEqualTo(InterviewPhase.OPENING);
            contexts.add(call.getArgument(0));
            if (contexts.size() == 1) throw new IllegalStateException("retry");
            return new GeneratedQuestion("首题");
        });
        assertThatThrownBy(() -> interviewApplicationService.startInterview(id))
                .isInstanceOf(IllegalStateException.class);
        assertThat(interviewRepository.findById(id).currentPhase()).isNull();
        assertThat(planRepository.findByInterviewId(id)).isEqualTo(plan);
        interviewApplicationService.startInterview(id);
        assertThat(contexts).hasSize(2);
        assertThat(contexts.get(0)).isEqualTo(contexts.get(1));
        assertThat(questionRepository.findByInterviewId(id)).hasSize(1);
        assertThat(questionRepository.findFirstByInterviewId(id).planItemId()).isEqualTo(plan.initialPlanItemId());
        verify(interviewAiApi, never()).selectFocus(any());
    }

    @Test
    void nextMainReceivesEntireMainHistoryIncludingSkippedButExcludesFollowUps() {
        StartedInterview started = createStartedInterview();
        var plan = planRepository.findByInterviewId(started.interviewId());
        var skippedItem = plan.items().get(0);
        jdbcTemplate.update("UPDATE question SET sequence_no = 3 WHERE id = ?", started.questionId());
        jdbcTemplate.update(
                "INSERT INTO question (interview_id, sequence_no, type, status, question_content, evaluation_profile_id, plan_item_id) VALUES (?, 1, 'MAIN', 'SKIPPED', '已跳过历史主问题', 1, ?)",
                started.interviewId(),
                skippedItem.id());
        jdbcTemplate.update(
                "INSERT INTO question (interview_id, sequence_no, type, status, question_content, evaluation_profile_id, parent_question_id, plan_item_id) VALUES (?, 2, 'FOLLOW_UP', 'SKIPPED', '不应传入的追问', 1, ?, ?)",
                started.interviewId(),
                started.questionId(),
                plan.initialPlanItemId());
        given(interviewAiApi.evaluateAnswer(any()))
                .willAnswer(invocation -> validEvaluation(invocation.getArgument(0)));
        given(interviewAiApi.decideNext(any())).willAnswer(invocation -> {
            DecisionContext context = invocation.getArgument(0);
            assertThat(context.unusedPlanItems())
                    .extracting("id")
                    .doesNotContain(skippedItem.id(), plan.initialPlanItemId());
            return new ValidatedDecisionResult(
                    DecisionAction.NEXT_QUESTION,
                    "下一题",
                    null,
                    context.unusedPlanItems().get(0).id());
        });
        given(interviewAiApi.generateMainQuestion(any()))
                .willAnswer(invocation -> {
                    assertThat(org.springframework.transaction.support.TransactionSynchronizationManager
                                    .isActualTransactionActive())
                            .isFalse();
                    MainQuestionGenerationContext context = invocation.getArgument(0);
                    assertThat(context.previousMainQuestions()).containsExactly("已跳过历史主问题", "请说明事务与并发控制。");
                    return new GeneratedQuestion("新主问题");
                });
        interviewTurnApplicationService.submitAnswer(started.interviewId(), started.questionId(), "正式回答");
        verify(interviewAiApi, never()).selectFocus(any());
    }

    @Test
    void turnPlanExhaustionLeavesOfficialAnswerProcessingAndDoesNotEnd() {
        StartedInterview started = createStartedInterview();
        var plan = planRepository.findByInterviewId(started.interviewId());
        jdbcTemplate.update(
                "DELETE FROM interview_plan_item WHERE plan_id = ? AND id <> ?", plan.id(), plan.initialPlanItemId());
        assertThatThrownBy(() -> interviewTurnApplicationService.submitAnswer(
                        started.interviewId(), started.questionId(), "正式回答"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exhausted");
        assertFailedTurnProcessing(started);
        verify(interviewAiApi, never()).evaluateAnswer(any());
        verify(interviewAiApi, never()).decideNext(any());
    }

    @Test
    void turnRejectsPlanOwnershipChangeDuringAiAndRollsBackT2() {
        StartedInterview started = createStartedInterview();
        given(interviewAiApi.evaluateAnswer(any()))
                .willAnswer(invocation -> validEvaluation(invocation.getArgument(0)));
        given(interviewAiApi.decideNext(any())).willAnswer(invocation -> {
            DecisionContext context = invocation.getArgument(0);
            return new ValidatedDecisionResult(
                    DecisionAction.NEXT_QUESTION,
                    "下一题",
                    null,
                    context.unusedPlanItems().get(0).id());
        });
        given(interviewAiApi.generateMainQuestion(any())).willAnswer(invocation -> {
            jdbcTemplate.update(
                    "UPDATE interview_plan SET interview_id = ? WHERE interview_id = ?",
                    insertCreatedInterview(),
                    started.interviewId());
            return new GeneratedQuestion("丢弃的主问题");
        });
        assertThatThrownBy(() -> interviewTurnApplicationService.submitAnswer(
                        started.interviewId(), started.questionId(), "正式回答"))
                .isInstanceOf(IllegalStateException.class);
        assertFailedTurnProcessing(started);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM question WHERE interview_id = ?", Integer.class, started.interviewId()))
                .isEqualTo(1);
    }

    @Autowired
    private com.linj6.interviewagent.candidate.api.CandidateQueryApi candidateProfiles;

    @Autowired
    private com.linj6.interviewagent.interview.application.InterviewPrepareApplicationService prepareService;

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {0, 1, 7})
    void propagatesAllProjectsThroughPrepareOpeningTurnAndSkip(int count) {
        long candidateId = IdWorker.getId();
        long resumeId = IdWorker.getId();
        jdbcTemplate.update("INSERT INTO candidate (id, user_id, name) VALUES (?, 1, '项目测试候选人')", candidateId);
        jdbcTemplate.update(
                "INSERT INTO candidate_resume (id, candidate_id, summary) VALUES (?, ?, '原简历摘要')",
                resumeId,
                candidateId);
        long baseId = IdWorker.getId();
        // 故意逆序插入，读取必须依 project id 稳定排序，七项证明无五条截断。
        for (int i = count; i > 0; i--) {
            jdbcTemplate.update(
                    "INSERT INTO candidate_project (id, resume_id, name, description) VALUES (?, ?, ?, ?)",
                    baseId + i,
                    resumeId,
                    "项目" + i,
                    "描述" + i);
        }
        var expected = java.util.stream.IntStream.rangeClosed(1, count)
                .mapToObj(i -> new com.linj6.interviewagent.ai.api.ProjectContext("项目" + i, "描述" + i))
                .toList();
        var profile = candidateProfiles.getInterviewProfile(candidateId);
        assertThat(profile.candidateName()).isEqualTo("项目测试候选人");
        assertThat(profile.resumeSummary()).isEqualTo("原简历摘要");
        assertThat(profile.projects())
                .extracting("name")
                .containsExactlyElementsOf(
                        expected.stream().map(value -> value.name()).toList());
        assertThat(profile.projects())
                .extracting("description")
                .containsExactlyElementsOf(
                        expected.stream().map(value -> value.description()).toList());
        given(interviewAiApi.preparePlan(any())).willAnswer(invocation -> {
            com.linj6.interviewagent.ai.api.planner.PlannerContext context = invocation.getArgument(0);
            assertThat(context.projects()).isEqualTo(expected);
            return new com.linj6.interviewagent.ai.api.planner.ValidatedPlanResult(
                    java.util.stream.IntStream.rangeClosed(1, context.maxMainQuestions())
                            .mapToObj(i -> new com.linj6.interviewagent.ai.api.planner.ValidatedPlanResult.Item(
                                    "JAVA", "方向" + i, "理由" + i))
                            .toList(),
                    0);
        });
        given(interviewAiApi.generateMainQuestion(any())).willAnswer(invocation -> {
            MainQuestionGenerationContext context = invocation.getArgument(0);
            assertThat(context.projects()).isEqualTo(expected);
            return new GeneratedQuestion("项目主问题");
        });
        given(interviewAiApi.evaluateAnswer(any())).willAnswer(invocation -> {
            EvaluationContext context = invocation.getArgument(0);
            assertThat(context.projects()).isEqualTo(expected);
            return validEvaluation(context);
        });
        given(interviewAiApi.decideNext(any())).willAnswer(invocation -> {
            DecisionContext context = invocation.getArgument(0);
            assertThat(context.projects()).isEqualTo(expected);
            return new ValidatedDecisionResult(DecisionAction.FOLLOW_UP, "澄清", "澄清项目职责");
        });
        given(interviewAiApi.generateFollowUpQuestion(any())).willAnswer(invocation -> {
            FollowUpQuestionGenerationContext context = invocation.getArgument(0);
            assertThat(context.projects()).isEqualTo(expected);
            return new GeneratedQuestion("项目追问");
        });
        var interview = interviewRepository.create(candidateId, 1L, InterviewMode.COMPREHENSIVE, null);
        prepareService.prepareInterview(interview.id());
        interviewApplicationService.startInterview(interview.id());
        var opening = questionRepository.findFirstByInterviewId(interview.id());
        interviewTurnApplicationService.submitAnswer(interview.id(), opening.id(), "我负责项目实现");
        var followUp = questionRepository.findByInterviewId(interview.id()).get(1);
        org.mockito.BDDMockito.willAnswer(invocation -> {
                    DecisionContext context = invocation.getArgument(0);
                    assertThat(context.projects()).isEqualTo(expected);
                    return new ValidatedDecisionResult(
                            DecisionAction.NEXT_QUESTION,
                            "下一题",
                            null,
                            context.unusedPlanItems().get(0).id());
                })
                .given(interviewAiApi)
                .decideNext(any());
        interviewTurnApplicationService.submitAnswer(interview.id(), followUp.id(), "补充说明");
        var main = questionRepository.findByInterviewId(interview.id()).get(2);
        interviewSkipApplicationService.skipQuestion(interview.id(), main.id());
        verify(interviewAiApi).preparePlan(any());
        verify(interviewAiApi, org.mockito.Mockito.times(3)).generateMainQuestion(any());
        verify(interviewAiApi).generateFollowUpQuestion(any());
        verify(interviewAiApi, org.mockito.Mockito.times(2)).evaluateAnswer(any());
        verify(interviewAiApi, org.mockito.Mockito.times(2)).decideNext(any());
    }

    private StartedInterview createStartedInterview() {
        given(interviewAiApi.generateMainQuestion(any())).willReturn(new GeneratedQuestion("请说明事务与并发控制。"));
        var created = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/interviews", Map.of("mode", "COMPREHENSIVE"), Map.class);
        long interviewId = Long.parseLong((String) data(created.getBody()).get("id"));
        preparePlanForStart(interviewId);
        var started = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/interviews/" + interviewId + "/start", null, Map.class);
        Map<?, ?> question = (Map<?, ?>) data(started.getBody()).get("firstQuestion");
        org.mockito.Mockito.clearInvocations(interviewAiApi);
        return new StartedInterview(interviewId, Long.parseLong((String) question.get("id")));
    }

    private long insertCreatedInterview() {
        jdbcTemplate.update(
                """
                INSERT INTO interview (candidate_id, position_id, status, current_phase, version)
                VALUES (1, 1, 'CREATED', NULL, 0)
                """);
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private long insertWaitingInterview() {
        jdbcTemplate.update(
                """
                INSERT INTO interview (candidate_id, position_id, status, current_phase, version)
                VALUES (1, 1, 'IN_PROGRESS', 'WAITING_ANSWER', 0)
                """);
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private long insertWaitingQuestion(long interviewId) {
        var itemId = QuestionQualityTestPlans.prepare(jdbcTemplate, interviewId, "并发测试方向")
                .get(0);
        jdbcTemplate.update(
                """
                INSERT INTO question
                    (interview_id, sequence_no, type, status, question_content, evaluation_profile_id, plan_item_id)
                VALUES (?, 1, 'MAIN', 'WAITING_ANSWER', '并发测试问题', 1, ?)
                """,
                interviewId,
                itemId);
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private EvaluationCommand evaluationCommand(long interviewId, long questionId, long answerId) {
        return EvaluationCommand.builder()
                .interviewId(interviewId)
                .questionId(questionId)
                .answerId(answerId)
                .profileId(1L)
                .overallScore10(new BigDecimal("8.00"))
                .overallScore100(new BigDecimal("80.00"))
                .strengths(List.of())
                .weaknesses(List.of())
                .missingPoints(List.of())
                .referenceAnswer(new ReferenceAnswer("SAMPLE_ANSWER", List.of(), "测试参考答案"))
                .promptVersion("test-prompt")
                .evaluatorVersion("test-evaluator")
                .model("test-model")
                .inputTokens(1)
                .outputTokens(1)
                .totalTokens(2)
                .latencyMs(1L)
                .dimensions(List.of())
                .build();
    }

    private String answerUrl(StartedInterview started) {
        return "http://localhost:" + port + "/api/interviews/" + started.interviewId() + "/questions/"
                + started.questionId() + "/answer";
    }

    private ValidatedEvaluationResult validEvaluation(EvaluationContext context) {
        List<ValidatedDimensionEvaluation> dimensions = context.dimensions().stream()
                .map(dimension -> new ValidatedDimensionEvaluation(
                        dimension.code(),
                        dimension.name(),
                        Applicability.APPLICABLE,
                        new BigDecimal("8.0"),
                        dimension.weight(),
                        new BigDecimal("8.0").multiply(dimension.weight()).setScale(4, RoundingMode.HALF_UP),
                        "测试评分说明",
                        null,
                        List.of(new ValidatedEvidence(EvidenceType.OVERALL_OBSERVATION, null, "测试整体观察"))))
                .toList();
        return new ValidatedEvaluationResult(
                context.profileId(),
                new BigDecimal("8.00"),
                new BigDecimal("80.00"),
                dimensions,
                List.of(),
                List.of(),
                List.of(),
                new ReferenceAnswerProposal(AnswerForm.SAMPLE_ANSWER, List.of("事务边界"), "测试参考回答"),
                "test-prompt",
                "test-evaluator",
                "test-model",
                10,
                20,
                30,
                5L);
    }

    private void assertFailedTurnProcessing(StartedInterview started) {
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM answer WHERE question_id = ?", Integer.class, started.questionId()))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM evaluation WHERE question_id = ?", Integer.class, started.questionId()))
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                        """
                SELECT COUNT(*) FROM evaluation_dimension_score s JOIN evaluation e ON e.id = s.evaluation_id
                WHERE e.question_id = ?
                """,
                        Integer.class,
                        started.questionId()))
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM question WHERE id = ?", String.class, started.questionId()))
                .isEqualTo("WAITING_ANSWER");
        assertThat(jdbcTemplate.queryForMap(
                        "SELECT status, current_phase FROM interview WHERE id = ?", started.interviewId()))
                .containsEntry("status", "IN_PROGRESS")
                .containsEntry("current_phase", "PROCESSING_TURN");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> data(Map<?, ?> response) {
        assertThat((Object) response.get("code")).isEqualTo(0);
        Object requestId = response.get("requestId");
        assertThat(requestId).isNotNull();
        return (Map<String, Object>) response.get("data");
    }

    private record StartedInterview(long interviewId, long questionId) {}
}
