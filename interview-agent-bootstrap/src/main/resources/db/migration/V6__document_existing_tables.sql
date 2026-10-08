-- 为 Slice 1 / Profile 历史表补齐业务注释；不改变字段类型、默认值或约束语义。
ALTER TABLE `user`
    COMMENT = '系统用户基础信息',
    MODIFY id BIGINT NOT NULL COMMENT 'User 标识',
    MODIFY username VARCHAR(64) NOT NULL COMMENT '用户登录名';

ALTER TABLE candidate
    COMMENT = '候选人基础信息',
    MODIFY id BIGINT NOT NULL COMMENT 'Candidate 标识',
    MODIFY user_id BIGINT NOT NULL COMMENT '所属 User 标识',
    MODIFY name VARCHAR(100) NOT NULL COMMENT '候选人姓名';

ALTER TABLE candidate_resume
    COMMENT = '候选人简历摘要',
    MODIFY id BIGINT NOT NULL COMMENT 'Resume 标识',
    MODIFY candidate_id BIGINT NOT NULL COMMENT '所属 Candidate 标识',
    MODIFY summary TEXT NOT NULL COMMENT '简历摘要正文';

ALTER TABLE candidate_project
    COMMENT = '简历中的项目经历',
    MODIFY id BIGINT NOT NULL COMMENT 'Project Experience 标识',
    MODIFY resume_id BIGINT NOT NULL COMMENT '所属 Resume 标识',
    MODIFY name VARCHAR(200) NOT NULL COMMENT '项目名称',
    MODIFY description TEXT NOT NULL COMMENT '项目经历描述';

ALTER TABLE position
    COMMENT = '面试目标岗位',
    MODIFY id BIGINT NOT NULL COMMENT 'Position 标识',
    MODIFY title VARCHAR(200) NOT NULL COMMENT '岗位标题',
    MODIFY description TEXT NOT NULL COMMENT '岗位职责与要求说明';

ALTER TABLE interview
    COMMENT = '一次可恢复的 AI 模拟面试',
    MODIFY id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Interview 标识',
    MODIFY candidate_id BIGINT NOT NULL COMMENT '参与面试的 Candidate 标识',
    MODIFY position_id BIGINT NOT NULL COMMENT '目标 Position 标识',
    MODIFY status VARCHAR(32) NOT NULL COMMENT '生命周期：CREATED、IN_PROGRESS、PAUSED、COMPLETED',
    MODIFY current_phase VARCHAR(32) NULL COMMENT '可恢复阶段：OPENING、WAITING_ANSWER、PROCESSING_TURN；无进行中阶段时为空',
    MODIFY version BIGINT NOT NULL DEFAULT 0 COMMENT '状态流转乐观锁版本',
    MODIFY created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Interview 创建时间',
    MODIFY updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Interview 最近更新时间';

ALTER TABLE evaluation_profile
    COMMENT = '不可变评价规则 Profile',
    MODIFY id BIGINT NOT NULL COMMENT 'Evaluation Profile 标识',
    MODIFY code VARCHAR(64) NOT NULL COMMENT '稳定业务代码',
    MODIFY name VARCHAR(128) NOT NULL COMMENT 'Profile 名称',
    MODIFY description TEXT NOT NULL COMMENT 'Profile 业务说明',
    MODIFY created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Profile 创建时间';

ALTER TABLE evaluation_dimension_config
    COMMENT = 'Evaluation Profile 的维度规则配置',
    MODIFY id BIGINT NOT NULL AUTO_INCREMENT COMMENT '维度配置标识',
    MODIFY profile_id BIGINT NOT NULL COMMENT '所属 Evaluation Profile 标识',
    MODIFY dimension_code VARCHAR(64) NOT NULL COMMENT '稳定维度代码',
    MODIFY dimension_name VARCHAR(128) NOT NULL COMMENT '维度名称',
    MODIFY evaluation_criteria TEXT NOT NULL COMMENT '该维度的评价标准',
    MODIFY score_guidance TEXT NOT NULL COMMENT '该维度的评分指引',
    MODIFY weight DECIMAL(6, 4) NOT NULL COMMENT 'Java 确定性评分使用的原始权重',
    MODIFY na_allowed BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否允许 NOT_APPLICABLE',
    MODIFY sort_order INT NOT NULL COMMENT 'Profile 内维度顺序';

ALTER TABLE question
    COMMENT = 'Interview 中的主问题或追问',
    MODIFY id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Question 标识',
    MODIFY interview_id BIGINT NOT NULL COMMENT '所属 Interview 标识',
    MODIFY sequence_no INT NOT NULL COMMENT 'Interview 内问题顺序',
    MODIFY parent_question_id BIGINT NULL COMMENT '父 Question 标识；主问题为空',
    MODIFY type VARCHAR(32) NOT NULL COMMENT '问题类型：MAIN 或 FOLLOW_UP',
    MODIFY status VARCHAR(32) NOT NULL COMMENT '生命周期：CREATED、WAITING_ANSWER、ANSWERED、SKIPPED',
    MODIFY question_content TEXT NOT NULL COMMENT '问题正文',
    MODIFY created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Question 创建时间',
    MODIFY topic_id BIGINT NULL COMMENT '可选 Topic 标识',
    MODIFY evaluation_profile_id BIGINT NOT NULL COMMENT '固定绑定的 Evaluation Profile 标识';
