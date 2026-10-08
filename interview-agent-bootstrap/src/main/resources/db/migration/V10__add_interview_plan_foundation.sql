-- Task 2 additive transition: keep legacy question.focus and nullable plan_item_id.
-- No historical Question backfill or synthetic PlanItems; no foreign keys.
ALTER TABLE interview
    ADD COLUMN mode VARCHAR(32) NOT NULL DEFAULT 'COMPREHENSIVE' COMMENT 'Interview 模式；旧 Create 兼容默认综合面试',
    ADD COLUMN focus_area VARCHAR(32) NULL COMMENT '仅技术专项提供的 KnowledgeArea',
    ADD CONSTRAINT ck_interview_mode_area CHECK (
        mode IN ('COMPREHENSIVE', 'PROJECT_DEEP_DIVE', 'TECHNICAL_FOCUS') AND
        ((mode = 'TECHNICAL_FOCUS' AND focus_area IS NOT NULL) OR (mode <> 'TECHNICAL_FOCUS' AND focus_area IS NULL)) AND
        (focus_area IS NULL OR focus_area IN ('JAVA', 'CONCURRENCY', 'JVM', 'MYSQL', 'REDIS', 'MQ', 'SPRING', 'DISTRIBUTED_SYSTEMS', 'SYSTEM_DESIGN'))
    );
CREATE TABLE interview_plan (
    id BIGINT PRIMARY KEY COMMENT '正式 Plan 标识，由 Java 分配',
    interview_id BIGINT NOT NULL COMMENT '所属 Interview 标识',
    initial_plan_item_id BIGINT NOT NULL COMMENT '本 Plan 内 initial PlanItem 的真实标识',
    CONSTRAINT uq_interview_plan_interview UNIQUE (interview_id)
) COMMENT = 'Interview 正式计划，无独立状态机';
CREATE TABLE interview_plan_item (
    id BIGINT PRIMARY KEY COMMENT '正式 PlanItem 标识，由 Java 分配',
    plan_id BIGINT NOT NULL COMMENT '所属 Plan 标识',
    area VARCHAR(32) NOT NULL COMMENT 'KnowledgeArea 枚举值',
    focus TEXT NOT NULL COMMENT 'MAIN 的唯一考察焦点事实',
    reason TEXT NOT NULL COMMENT '选择该考察项的理由',
    INDEX idx_plan_item_plan (plan_id),
    CONSTRAINT ck_plan_item_area CHECK (area IN ('JAVA', 'CONCURRENCY', 'JVM', 'MYSQL', 'REDIS', 'MQ', 'SPRING', 'DISTRIBUTED_SYSTEMS', 'SYSTEM_DESIGN'))
) COMMENT = '正式计划项，不保存 AI 临时引用或状态';
ALTER TABLE question
    ADD COLUMN plan_item_id BIGINT NULL COMMENT '正式 PlanItem 标识；过渡期旧生成路径允许为空',
    ADD COLUMN main_plan_item_id BIGINT GENERATED ALWAYS AS (CASE WHEN type = 'MAIN' THEN plan_item_id ELSE NULL END) VIRTUAL COMMENT '仅 MAIN 参与 PlanItem 唯一使用约束',
    ADD CONSTRAINT uq_question_main_plan_item UNIQUE (main_plan_item_id);
