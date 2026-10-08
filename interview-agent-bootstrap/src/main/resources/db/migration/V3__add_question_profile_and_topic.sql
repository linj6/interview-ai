CREATE TABLE evaluation_profile (
    id BIGINT PRIMARY KEY,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    description TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_evaluation_profile_code UNIQUE (code)
);

CREATE TABLE evaluation_dimension_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    profile_id BIGINT NOT NULL,
    dimension_code VARCHAR(64) NOT NULL,
    dimension_name VARCHAR(128) NOT NULL,
    evaluation_criteria TEXT NOT NULL,
    score_guidance TEXT NOT NULL,
    weight DECIMAL(6, 4) NOT NULL,
    na_allowed BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order INT NOT NULL,
    CONSTRAINT uq_dimension_config_profile_dimension UNIQUE (profile_id, dimension_code),
    CONSTRAINT uq_dimension_config_profile_order UNIQUE (profile_id, sort_order)
);

INSERT INTO evaluation_profile (id, code, name, description)
VALUES (1, 'DEFAULT_GENERAL_INTERVIEW', '默认通用面试 Profile', 'Slice 1 固定演示数据使用的默认评价规则。');

INSERT INTO evaluation_dimension_config
    (profile_id, dimension_code, dimension_name, evaluation_criteria, score_guidance, weight, na_allowed, sort_order)
VALUES
    (1, 'ACCURACY', '准确性', '回答中的技术事实与结论是否正确。', '0 到 10 分，步长 0.5。', 1.0000, FALSE, 1),
    (1, 'COMPLETENESS', '完整性', '回答是否覆盖问题要求的关键内容。', '0 到 10 分，步长 0.5。', 1.0000, FALSE, 2),
    (1, 'DEPTH', '深度', '回答是否说明原理、取舍或边界。', '0 到 10 分，步长 0.5。', 1.0000, FALSE, 3),
    (1, 'EXPRESSION', '表达', '回答是否清晰、结构化且易于理解。', '0 到 10 分，步长 0.5。', 1.0000, FALSE, 4),
    (1, 'LOGIC', '逻辑', '回答的推理和论述是否连贯。', '0 到 10 分，步长 0.5。', 1.0000, FALSE, 5),
    (1, 'PROJECT_EXPERIENCE', '项目经验', '回答是否能结合真实项目经历说明实践。', '0 到 10 分，步长 0.5。', 1.0000, FALSE, 6),
    (1, 'ADAPTABILITY', '适应性', '回答是否能结合场景调整方案。', '0 到 10 分，步长 0.5。', 1.0000, FALSE, 7);

ALTER TABLE question
    ADD COLUMN topic_id BIGINT NULL,
    ADD COLUMN evaluation_profile_id BIGINT NULL;

UPDATE question SET evaluation_profile_id = 1 WHERE evaluation_profile_id IS NULL;

ALTER TABLE question
    MODIFY COLUMN evaluation_profile_id BIGINT NOT NULL;
