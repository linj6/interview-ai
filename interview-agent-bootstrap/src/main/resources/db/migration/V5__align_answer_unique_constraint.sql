-- 在调整唯一约束前显式检查历史数据；若存在重复键，命名明确的 CHECK 约束会阻止 Migration 继续执行。
CREATE TEMPORARY TABLE v5_answer_duplicate_guard (
    duplicate_group_count BIGINT NOT NULL,
    CONSTRAINT ck_v5_no_duplicate_answer_keys CHECK (duplicate_group_count = 0)
);

INSERT INTO v5_answer_duplicate_guard (duplicate_group_count)
SELECT COUNT(*)
FROM (
    SELECT interview_id, question_id
    FROM answer
    GROUP BY interview_id, question_id
    HAVING COUNT(*) > 1
) duplicate_answer_keys;

DROP TEMPORARY TABLE v5_answer_duplicate_guard;

ALTER TABLE answer
    DROP INDEX uq_answer_question,
    ADD CONSTRAINT uq_answer_interview_question UNIQUE (interview_id, question_id);
