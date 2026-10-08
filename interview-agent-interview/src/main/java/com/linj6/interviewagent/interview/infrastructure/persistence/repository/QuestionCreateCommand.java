package com.linj6.interviewagent.interview.infrastructure.persistence.repository;

import com.linj6.interviewagent.interview.domain.QuestionType;
import lombok.Builder;

/** 创建正式 Question 所需的持久化字段；初始状态由 Repository 决定。 */
@Builder
public record QuestionCreateCommand(
        Long interviewId,
        Integer sequenceNo,
        Long parentQuestionId,
        QuestionType type,
        String content,
        Long evaluationProfileId,
        Long topicId,
        Long planItemId) {}
