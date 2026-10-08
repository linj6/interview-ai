package com.linj6.interviewagent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 显式 opt-in 的真实 DashScope Evaluator 验收：真实 Proposal 必须通过 Java 校验与确定性计算，
 * 并通过与生产相同的 T1/T2 路径持久化；默认构建没有密钥时不会执行或产生费用。
 */
@Testcontainers
@ActiveProfiles("local")
@EnabledIfEnvironmentVariable(named = "AI_DASHSCOPE_API_KEY", matches = ".+")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RealEvaluatorPersistenceIT {
    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TestRestTemplate restTemplate;

    @LocalServerPort
    private int port;

    @Test
    void evaluatesAndPersistsEvaluationWithRealDashScope() {
        jdbcTemplate.update(
                """
                INSERT INTO interview (candidate_id, position_id, status, current_phase)
                VALUES (1, 1, 'IN_PROGRESS', 'WAITING_ANSWER')
                """);
        long interviewId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        var itemId = QuestionQualityTestPlans.prepare(jdbcTemplate, interviewId, "事务隔离")
                .get(0);
        jdbcTemplate.update(
                """
                INSERT INTO question (interview_id, sequence_no, type, status, question_content, evaluation_profile_id, plan_item_id)
                VALUES (?, 1, 'MAIN', 'WAITING_ANSWER', ?, 1, ?)
                """,
                interviewId,
                "请说明数据库事务隔离级别及其解决的并发问题。",
                itemId);
        long questionId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        var response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/interviews/" + interviewId + "/questions/" + questionId + "/answer",
                Map.of("content", "读未提交可能出现脏读；读已提交避免脏读；可重复读还避免不可重复读。" + "在 MySQL InnoDB 中我会结合事务边界、索引和锁等待监控处理并发问题。"),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> evaluation = (Map<?, ?>) response.getBody().get("evaluation");
        assertThat(evaluation.get("overallScore10")).isNotNull();
        assertThat((String) evaluation.get("model")).isNotBlank();
        assertThat((java.util.List<?>) evaluation.get("dimensions")).hasSize(7);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM evaluation WHERE question_id = ?", Integer.class, questionId))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT model FROM evaluation WHERE question_id = ?", String.class, questionId))
                .isNotBlank();
        assertThat(jdbcTemplate.queryForObject(
                        """
                SELECT COUNT(*) FROM evaluation_dimension_score s JOIN evaluation e ON e.id = s.evaluation_id
                WHERE e.question_id = ?
                """,
                        Integer.class,
                        questionId))
                .isEqualTo(7);
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM question WHERE id = ?", String.class, questionId))
                .isEqualTo("ANSWERED");
    }
}
