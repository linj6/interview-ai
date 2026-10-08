package com.linj6.interviewagent.interview.infrastructure.persistence.repository;

/** Application 使用的不可变 Answer 持久化快照。 */
public record AnswerSnapshot(Long id, Long interviewId, Long questionId, String content) {}
