package com.linj6.interviewagent.interview.infrastructure.persistence.repository;

import com.linj6.interviewagent.interview.domain.QuestionStatus;
import com.linj6.interviewagent.interview.domain.QuestionType;

/** Application 使用的 Question 持久化快照，不暴露数据库 DO。 */
public record QuestionSnapshot(
        Long id,
        Long interviewId,
        Integer sequenceNo,
        Long parentQuestionId,
        QuestionType type,
        QuestionStatus status,
        String content,
        Long evaluationProfileId,
        Long topicId,
        Long planItemId) {}
