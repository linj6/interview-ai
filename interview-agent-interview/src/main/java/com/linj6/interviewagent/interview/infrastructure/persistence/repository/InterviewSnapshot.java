package com.linj6.interviewagent.interview.infrastructure.persistence.repository;

import com.linj6.interviewagent.interview.domain.InterviewMode;
import com.linj6.interviewagent.interview.domain.InterviewPhase;
import com.linj6.interviewagent.interview.domain.InterviewStatus;
import com.linj6.interviewagent.interview.domain.KnowledgeArea;
import java.time.LocalDateTime;

/** Application 使用的 Interview 持久化快照，不暴露数据库 DO。 */
public record InterviewSnapshot(
        Long id,
        Long candidateId,
        Long positionId,
        InterviewStatus status,
        InterviewPhase currentPhase,
        Long version,
        LocalDateTime expiresAt,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        InterviewMode mode,
        KnowledgeArea focusArea) {
    public InterviewSnapshot {
        java.util.Objects.requireNonNull(mode, "mode").validateFocusArea(focusArea);
    }

    public InterviewSnapshot(
            Long id,
            Long candidateId,
            Long positionId,
            InterviewStatus status,
            InterviewPhase currentPhase,
            Long version,
            LocalDateTime expiresAt,
            LocalDateTime startedAt,
            LocalDateTime completedAt) {
        this(
                id,
                candidateId,
                positionId,
                status,
                currentPhase,
                version,
                expiresAt,
                startedAt,
                completedAt,
                InterviewMode.COMPREHENSIVE,
                null);
    }

    public InterviewSnapshot(
            Long id,
            Long candidateId,
            Long positionId,
            InterviewStatus status,
            InterviewPhase currentPhase,
            Long version,
            LocalDateTime expiresAt) {
        this(id, candidateId, positionId, status, currentPhase, version, expiresAt, null, null);
    }
}
