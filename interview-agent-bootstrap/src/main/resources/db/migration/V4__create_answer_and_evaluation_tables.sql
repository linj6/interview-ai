CREATE TABLE answer (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '正式回答标识',
    interview_id BIGINT NOT NULL COMMENT '回答所属 Interview 标识，应用层校验归属关系',
    question_id BIGINT NOT NULL COMMENT '回答对应 Question 标识；一个 Question 仅允许一份正式回答',
    content MEDIUMTEXT NOT NULL COMMENT '候选人提交的不可变回答原文，不允许空内容或原地覆盖',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '正式回答创建时间',
    -- 业务幂等约束：禁止同一 Question 产生第二份正式 Answer。
    CONSTRAINT uq_answer_question UNIQUE (question_id)
) COMMENT='候选人正式回答历史事实；Slice 2 不提供修改和覆盖能力';

CREATE TABLE evaluation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '正式评价标识',
    interview_id BIGINT NOT NULL COMMENT '评价所属 Interview 标识',
    question_id BIGINT NOT NULL COMMENT '评价对应 Question 标识',
    answer_id BIGINT NOT NULL COMMENT '评价对应的不可变正式 Answer；一个 Answer 最多一份正式评价',
    profile_id BIGINT NOT NULL COMMENT 'Question 绑定的不可变 EvaluationProfile 标识',
    overall_score_10 DECIMAL(5, 2) NOT NULL COMMENT 'Java 按适用维度权重确定性计算并 HALF_UP 保留两位的十分制总分',
    overall_score_100 DECIMAL(6, 2) NOT NULL COMMENT '已舍入 overall_score_10 乘以 10 得到的百分制总分',
    strengths JSON NOT NULL COMMENT '最多三条优势反馈及其适用维度关联的 JSON 快照',
    weaknesses JSON NOT NULL COMMENT '最多三条不足反馈及其适用维度关联的 JSON 快照',
    missing_points JSON NOT NULL COMMENT '最多五条缺失要点、重要性及适用维度关联的 JSON 快照',
    reference_answer JSON NOT NULL COMMENT '参考回答形式、关键点和正文的 JSON 快照；成功评价必须存在',
    prompt_version VARCHAR(64) NOT NULL COMMENT '生成 Proposal 所用 Prompt 的轻量版本标识',
    evaluator_version VARCHAR(64) NOT NULL COMMENT 'Java Evaluator 校验与计算实现版本标识',
    model VARCHAR(128) NOT NULL COMMENT '生成 EvaluationProposal 的模型名称',
    input_tokens INT NULL COMMENT '模型调用输入 Token 数；Provider 未返回时为空',
    output_tokens INT NULL COMMENT '模型调用输出 Token 数；Provider 未返回时为空',
    total_tokens INT NULL COMMENT '模型调用总 Token 数；Provider 未返回时为空',
    latency_ms BIGINT NOT NULL COMMENT '本次成功 Evaluator 调用与校验耗时，单位毫秒',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '正式评价原子提交时间',
    -- Workflow 重试幂等约束：同一 Answer 只有一个结果能成为正式 Evaluation。
    CONSTRAINT uq_evaluation_answer UNIQUE (answer_id)
) COMMENT='通过 Java 校验并确定性计算后形成的不可变正式评价';

CREATE TABLE evaluation_dimension_score (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '正式维度评分标识',
    evaluation_id BIGINT NOT NULL COMMENT '所属正式 Evaluation 标识',
    dimension_code VARCHAR(64) NOT NULL COMMENT 'Profile 维度稳定代码快照',
    dimension_name VARCHAR(128) NOT NULL COMMENT '评价发生时的维度名称快照',
    applicability VARCHAR(32) NOT NULL COMMENT '适用性：APPLICABLE=必须评分；NOT_APPLICABLE=Profile 允许且客观无评价条件',
    score DECIMAL(4, 1) NULL COMMENT '0 至 10 且步长 0.5 的维度分；NOT_APPLICABLE 时为空',
    weight DECIMAL(6, 4) NOT NULL COMMENT '评价发生时的 Profile 原始权重快照',
    weighted_score DECIMAL(10, 4) NULL COMMENT 'score 乘原始 weight 后 HALF_UP 保留四位；NOT_APPLICABLE 时为空',
    score_explanation TEXT NOT NULL COMMENT '面向用户的维度评分说明；NOT_APPLICABLE 时说明客观原因',
    evidence JSON NOT NULL COMMENT '1 至 3 条 ANSWER_QUOTE 或 OVERALL_OBSERVATION 证据 JSON；NOT_APPLICABLE 时为空数组',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '维度评分随正式 Evaluation 原子创建的时间',
    -- 防止一次 Evaluation 重复保存同一 Profile 维度。
    CONSTRAINT uq_evaluation_dimension UNIQUE (evaluation_id, dimension_code)
) COMMENT='正式 Evaluation 的维度分、历史权重和证据快照';
