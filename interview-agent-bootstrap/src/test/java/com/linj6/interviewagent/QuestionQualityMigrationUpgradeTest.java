package com.linj6.interviewagent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.DriverManager;
import java.sql.SQLException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** 验证历史分阶段升级及 V12 删除 focus、保留可空关联的过渡 Contract。 */
@Testcontainers
class QuestionQualityMigrationUpgradeTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @BeforeEach
    void resetsDisposableContainerSchema() {
        Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .cleanDisabled(false)
                .load()
                .clean();
    }

    @Test
    void removesEnumChecksFromV10AndPreservesStructuralUniqueness() throws Exception {
        Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .target("10")
                .load()
                .migrate();
        try (var connection =
                        DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
                var statement = connection.createStatement()) {
            String enumChecks =
                    "SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA = DATABASE() AND CONSTRAINT_TYPE = 'CHECK' AND CONSTRAINT_NAME IN ('ck_interview_mode_area', 'ck_plan_item_area')";
            try (var result = statement.executeQuery(enumChecks)) {
                assertThat(result.next()).isTrue();
                assertThat(result.getInt(1)).isEqualTo(2);
            }
            statement.executeUpdate(
                    "INSERT INTO interview (id, candidate_id, position_id, status, mode, focus_area) VALUES (200, 1, 1, 'CREATED', 'TECHNICAL_FOCUS', 'JAVA')");
            statement.executeUpdate(
                    "INSERT INTO interview_plan (id, interview_id, initial_plan_item_id) VALUES (300, 200, 400)");
            statement.executeUpdate(
                    "INSERT INTO interview_plan_item (id, plan_id, area, focus, reason) VALUES (400, 300, 'JAVA', 'focus', 'reason')");
            Flyway.configure()
                    .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                    .target("11")
                    .load()
                    .migrate();
            try (var result = statement.executeQuery(enumChecks)) {
                assertThat(result.next()).isTrue();
                assertThat(result.getInt(1)).isZero();
            }
            // Direct SQL deliberately bypasses Java: the schema must not enumerate values or combinations.
            statement.executeUpdate(
                    "INSERT INTO interview (id, candidate_id, position_id, status, mode, focus_area) VALUES (201, 1, 1, 'CREATED', 'FUTURE_MODE', 'FUTURE_AREA')");
            statement.executeUpdate("UPDATE interview SET mode = 'COMPREHENSIVE', focus_area = 'JAVA' WHERE id = 201");
            statement.executeUpdate("UPDATE interview SET focus_area = NULL WHERE id = 200");
            statement.executeUpdate(
                    "INSERT INTO interview_plan_item (id, plan_id, area, focus, reason) VALUES (401, 300, 'FUTURE_AREA', 'focus', 'reason')");
            assertThatThrownBy(
                            () -> statement.executeUpdate(
                                    "INSERT INTO interview_plan (id, interview_id, initial_plan_item_id) VALUES (301, 200, 400)"))
                    .isInstanceOfSatisfying(SQLException.class, exception -> assertThat(exception.getErrorCode())
                            .isEqualTo(1062));
            statement.executeUpdate(
                    "INSERT INTO question (interview_id, sequence_no, type, status, question_content, evaluation_profile_id, plan_item_id) VALUES (200, 1, 'MAIN', 'WAITING_ANSWER', 'main', 1, 400)");
            for (int sequence : new int[] {2, 3}) {
                statement.executeUpdate(
                        "INSERT INTO question (interview_id, sequence_no, parent_question_id, type, status, question_content, evaluation_profile_id, plan_item_id) SELECT 200, "
                                + sequence
                                + ", id, 'FOLLOW_UP', 'WAITING_ANSWER', 'follow', 1, 400 FROM question WHERE interview_id = 200 AND sequence_no = 1");
            }
            assertThatThrownBy(
                            () -> statement.executeUpdate(
                                    "INSERT INTO question (interview_id, sequence_no, type, status, question_content, evaluation_profile_id, plan_item_id) VALUES (200, 4, 'MAIN', 'WAITING_ANSWER', 'duplicate main', 1, 400)"))
                    .isInstanceOfSatisfying(SQLException.class, exception -> assertThat(exception.getErrorCode())
                            .isEqualTo(1062));
            try (var result = statement.executeQuery(
                    "SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA = DATABASE() AND CONSTRAINT_TYPE = 'UNIQUE' AND CONSTRAINT_NAME IN ('uq_interview_plan_interview', 'uq_question_main_plan_item')")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getInt(1)).isEqualTo(2);
            }
            try (var result = statement.executeQuery(
                    "SELECT COUNT(*) FROM question WHERE interview_id = 200 AND plan_item_id = 400")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getInt(1)).isEqualTo(3);
            }
        }
    }

    @Test
    void preservesLegacyDataWhenUpgradingFromV9() throws Exception {
        Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .target("9")
                .load()
                .migrate();
        try (var connection =
                        DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
                var statement = connection.createStatement()) {
            statement.executeUpdate(
                    "INSERT INTO interview (id, candidate_id, position_id, status) VALUES (100, 1, 1, 'IN_PROGRESS')");
            statement.executeUpdate(
                    "INSERT INTO question (interview_id, sequence_no, type, status, question_content, evaluation_profile_id, focus) VALUES (100, 1, 'MAIN', 'WAITING_ANSWER', 'legacy question', 1, 'legacy focus')");
            Flyway.configure()
                    .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                    .target("10")
                    .load()
                    .migrate();
            try (var result = statement.executeQuery(
                    "SELECT q.question_content, q.focus, q.plan_item_id, i.mode, i.focus_area FROM question q JOIN interview i ON i.id = q.interview_id WHERE i.id = 100")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString(1)).isEqualTo("legacy question");
                assertThat(result.getString(2)).isEqualTo("legacy focus");
                assertThat(result.getObject(3)).isNull();
                assertThat(result.getString(4)).isEqualTo("COMPREHENSIVE");
                assertThat(result.getObject(5)).isNull();
            }
            for (String table : new String[] {"interview_plan", "interview_plan_item"}) {
                try (var result = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getInt(1)).isZero();
                }
            }
        }
    }

    @Test
    void focusRemovalPreservesRealAssociationsAndRejectsDuplicateMain() throws Exception {
        migrateTo("11");
        try (var connection =
                        DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
                var statement = connection.createStatement()) {
            statement.executeUpdate(
                    "INSERT INTO interview (id, candidate_id, position_id, status) VALUES (200, 1, 1, 'IN_PROGRESS')");
            statement.executeUpdate(
                    "INSERT INTO interview_plan (id, interview_id, initial_plan_item_id) VALUES (300, 200, 400)");
            statement.executeUpdate(
                    "INSERT INTO interview_plan_item (id, plan_id, area, focus, reason) VALUES (400, 300, 'JAVA', 'official focus', 'reason')");
            statement.executeUpdate(
                    "INSERT INTO question (id, interview_id, sequence_no, type, status, question_content, evaluation_profile_id, plan_item_id, focus) VALUES (500, 200, 1, 'MAIN', 'SKIPPED', 'main', 1, 400, 'obsolete focus')");
            statement.executeUpdate(
                    "INSERT INTO question (id, interview_id, sequence_no, parent_question_id, type, status, question_content, evaluation_profile_id, plan_item_id) VALUES (501, 200, 2, 500, 'FOLLOW_UP', 'ANSWERED', 'follow', 1, 400)");
            migrateTo("12");
            try (var result = statement.executeQuery(
                    "SELECT id, plan_item_id, question_content, status, parent_question_id FROM question ORDER BY id")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getLong(1)).isEqualTo(500);
                assertThat(result.getLong(2)).isEqualTo(400);
                assertThat(result.getString(3)).isEqualTo("main");
                assertThat(result.getString(4)).isEqualTo("SKIPPED");
                assertThat(result.getObject(5)).isNull();
                assertThat(result.next()).isTrue();
                assertThat(result.getLong(1)).isEqualTo(501);
                assertThat(result.getLong(2)).isEqualTo(400);
                assertThat(result.getString(3)).isEqualTo("follow");
                assertThat(result.getString(4)).isEqualTo("ANSWERED");
                assertThat(result.getLong(5)).isEqualTo(500);
                assertThat(result.next()).isFalse();
            }
            try (var result = statement.executeQuery(
                    "SELECT IS_NULLABLE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='question' AND COLUMN_NAME='plan_item_id'")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString(1)).isEqualTo("YES");
            }
            try (var result = statement.executeQuery(
                    "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='question' AND COLUMN_NAME='focus'")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getInt(1)).isZero();
            }
            try (var result = statement.executeQuery("SELECT focus FROM interview_plan_item WHERE id=400")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString(1)).isEqualTo("official focus");
            }
            assertThatThrownBy(
                            () -> statement.executeUpdate(
                                    "INSERT INTO question (interview_id, sequence_no, type, status, question_content, evaluation_profile_id, plan_item_id) VALUES (200, 3, 'MAIN', 'WAITING_ANSWER', 'duplicate', 1, 400)"))
                    .isInstanceOfSatisfying(SQLException.class, e -> assertThat(e.getErrorCode())
                            .isEqualTo(1062));
            statement.executeUpdate(
                    "INSERT INTO question (interview_id, sequence_no, parent_question_id, type, status, question_content, evaluation_profile_id, plan_item_id) VALUES (200, 3, 500, 'FOLLOW_UP', 'WAITING_ANSWER', 'second follow', 1, 400)");
        }
    }

    @Test
    void focusRemovalPreservesLegacyNullWithoutChangingOrInventingFacts() throws Exception {
        migrateTo("11");
        try (var connection =
                        DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
                var statement = connection.createStatement()) {
            statement.executeUpdate(
                    "INSERT INTO interview (id, candidate_id, position_id, status) VALUES (200, 1, 1, 'IN_PROGRESS')");
            statement.executeUpdate(
                    "INSERT INTO question (interview_id, sequence_no, type, status, question_content, evaluation_profile_id, focus) VALUES (200, 1, 'MAIN', 'WAITING_ANSWER', 'legacy', 1, 'legacy focus')");
            var originalQuestions = questionRows(connection);
            migrateTo("12");
            assertThat(questionRows(connection)).isEqualTo(originalQuestions);
            try (var result = statement.executeQuery(
                    "SELECT question_content, plan_item_id FROM question WHERE interview_id=200")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString(1)).isEqualTo("legacy");
                assertThat(result.getObject(2)).isNull();
                assertThat(result.next()).isFalse();
            }
            try (var result = statement.executeQuery(
                    "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='question' AND COLUMN_NAME='focus'")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getInt(1)).isZero();
            }
            try (var result = statement.executeQuery(
                    "SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE() AND CONSTRAINT_NAME='ck_v12_no_null_question_plan_item'")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getInt(1)).isZero();
            }
            try (var result = statement.executeQuery("SELECT success FROM flyway_schema_history WHERE version='12'")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getBoolean(1)).isTrue();
            }
            for (String table : new String[] {"interview_plan", "interview_plan_item"}) {
                try (var result = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getInt(1)).isZero();
                }
            }
            try (var result = statement.executeQuery(
                    "SELECT IS_NULLABLE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='question' AND COLUMN_NAME='plan_item_id'")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString(1)).isEqualTo("YES");
            }
        }
    }

    private java.util.List<java.util.List<Object>> questionRows(java.sql.Connection connection) throws SQLException {
        var rows = new java.util.ArrayList<java.util.List<Object>>();
        try (var statement = connection.createStatement();
                var result = statement.executeQuery("SELECT * FROM question ORDER BY id")) {
            while (result.next()) {
                var row = new java.util.ArrayList<Object>();
                for (int column = 1; column <= result.getMetaData().getColumnCount(); column++) {
                    if (!"focus".equals(result.getMetaData().getColumnName(column))) {
                        row.add(result.getObject(column));
                    }
                }
                rows.add(row);
            }
        }
        return rows;
    }

    private void migrateTo(String version) {
        Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .target(version)
                .load()
                .migrate();
    }
}
