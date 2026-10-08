CREATE TABLE `user` (
    id BIGINT PRIMARY KEY,
    username VARCHAR(64) NOT NULL
);

CREATE TABLE candidate (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL
);

CREATE TABLE candidate_resume (
    id BIGINT PRIMARY KEY,
    candidate_id BIGINT NOT NULL,
    summary TEXT NOT NULL
);

CREATE TABLE candidate_project (
    id BIGINT PRIMARY KEY,
    resume_id BIGINT NOT NULL,
    name VARCHAR(200) NOT NULL,
    description TEXT NOT NULL
);

CREATE TABLE position (
    id BIGINT PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    description TEXT NOT NULL
);

CREATE TABLE interview (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    candidate_id BIGINT NOT NULL,
    position_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    current_phase VARCHAR(32) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE question (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    interview_id BIGINT NOT NULL,
    sequence_no INT NOT NULL,
    parent_question_id BIGINT NULL,
    type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    question_content TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_question_interview_sequence UNIQUE (interview_id, sequence_no)
);

INSERT INTO `user` (id, username) VALUES (1, 'demo-user');
INSERT INTO candidate (id, user_id, name) VALUES (1, 1, '演示候选人');
INSERT INTO candidate_resume (id, candidate_id, summary)
VALUES (1, 1, '五年 Java 后端开发经验，熟悉 Spring Boot、MySQL 与分布式系统。');
INSERT INTO candidate_project (id, resume_id, name, description)
VALUES (1, 1, '订单平台', '负责订单核心链路，优化数据库查询并建设异步事件处理能力。');
INSERT INTO position (id, title, description)
VALUES (1, 'Java 后端工程师', '负责高并发服务设计、Spring Boot 应用开发、MySQL 性能优化与系统稳定性。');
