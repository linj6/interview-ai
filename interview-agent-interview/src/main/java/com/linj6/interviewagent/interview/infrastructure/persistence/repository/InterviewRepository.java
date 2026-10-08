package com.linj6.interviewagent.interview.infrastructure.persistence.repository;

import com.linj6.interviewagent.common.exception.BusinessException;
import com.linj6.interviewagent.interview.domain.InterviewErrorCode;
import com.linj6.interviewagent.interview.domain.InterviewMode;
import com.linj6.interviewagent.interview.domain.InterviewPhase;
import com.linj6.interviewagent.interview.domain.InterviewStatus;
import com.linj6.interviewagent.interview.domain.KnowledgeArea;
import com.linj6.interviewagent.interview.infrastructure.persistence.entity.InterviewDO;
import com.linj6.interviewagent.interview.infrastructure.persistence.mapper.InterviewMapper;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

/** Interview 持久化仓储，负责状态更新结果校验和冲突分类。 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class InterviewRepository {

    private final InterviewMapper interviewMapper;

    public InterviewSnapshot create(Long candidateId, Long positionId) {
        return create(candidateId, positionId, InterviewMode.COMPREHENSIVE, null);
    }

    public InterviewSnapshot create(Long candidateId, Long positionId, InterviewMode mode, KnowledgeArea focusArea) {
        Objects.requireNonNull(mode, "mode").validateFocusArea(focusArea);
        InterviewDO value = new InterviewDO();
        value.setMode(mode);
        value.setFocusArea(focusArea);
        value.setCandidateId(candidateId);
        value.setPositionId(positionId);
        value.setStatus(InterviewStatus.CREATED);
        interviewMapper.insert(value);
        return findById(value.getId());
    }

    /** Prepare 短事务内锁读，与已有状态更新串行化。 */
    public InterviewSnapshot findByIdForUpdate(Long id) {
        InterviewDO value = interviewMapper.selectForUpdate(id);
        if (value == null) throw new BusinessException(InterviewErrorCode.INTERVIEW_NOT_FOUND);
        return toSnapshot(value);
    }

    public InterviewSnapshot findById(Long id) {
        InterviewDO value = interviewMapper.selectById(id);
        if (value == null) {
            throw new BusinessException(InterviewErrorCode.INTERVIEW_NOT_FOUND);
        }
        return toSnapshot(value);
    }

    public InterviewSnapshot pause(Long id, Long expectedVersion) {
        return changeStatusAtWaitingAnswer(id, InterviewStatus.IN_PROGRESS, InterviewStatus.PAUSED, expectedVersion);
    }

    public InterviewSnapshot resume(Long id, Long expectedVersion) {
        return changeStatusAtWaitingAnswer(id, InterviewStatus.PAUSED, InterviewStatus.IN_PROGRESS, expectedVersion);
    }

    public InterviewSnapshot markWaitingAfterSkip(Long id, Long expectedVersion) {
        return transition(
                id,
                InterviewStatus.IN_PROGRESS,
                InterviewPhase.WAITING_ANSWER,
                InterviewStatus.IN_PROGRESS,
                InterviewPhase.WAITING_ANSWER,
                expectedVersion,
                interviewMapper.changeStatusAtWaitingAnswer(
                        id,
                        InterviewStatus.IN_PROGRESS,
                        InterviewPhase.WAITING_ANSWER,
                        InterviewStatus.IN_PROGRESS,
                        expectedVersion));
    }

    public InterviewSnapshot completeAfterSkip(Long id, Long expectedVersion, LocalDateTime completedAt) {
        return transition(
                id,
                InterviewStatus.IN_PROGRESS,
                InterviewPhase.WAITING_ANSWER,
                InterviewStatus.COMPLETED,
                null,
                expectedVersion,
                interviewMapper.finish(
                        id, InterviewStatus.IN_PROGRESS, InterviewPhase.WAITING_ANSWER, expectedVersion, completedAt));
    }

    public InterviewSnapshot finish(
            Long id, InterviewStatus expectedStatus, Long expectedVersion, LocalDateTime completedAt) {
        int updated =
                interviewMapper.finish(id, expectedStatus, InterviewPhase.WAITING_ANSWER, expectedVersion, completedAt);
        if (updated != 1) {
            throw new BusinessException(InterviewErrorCode.INTERVIEW_STATE_CONFLICT);
        }
        return findById(id);
    }

    public boolean completeExpiredWaiting(
            Long id, InterviewStatus expectedStatus, Long expectedVersion, LocalDateTime completedAt) {
        return interviewMapper.completeExpiredWaiting(
                        id, expectedStatus, InterviewPhase.WAITING_ANSWER, expectedVersion, completedAt)
                == 1;
    }

    private InterviewSnapshot changeStatusAtWaitingAnswer(
            Long id, InterviewStatus expectedStatus, InterviewStatus targetStatus, Long expectedVersion) {
        int updated = interviewMapper.changeStatusAtWaitingAnswer(
                id, expectedStatus, InterviewPhase.WAITING_ANSWER, targetStatus, expectedVersion);
        if (updated != 1) {
            throw new BusinessException(InterviewErrorCode.INTERVIEW_STATE_CONFLICT);
        }
        return findById(id);
    }

    public InterviewSnapshot markOpening(
            Long id, Long expectedVersion, LocalDateTime startedAt, LocalDateTime expiresAt) {
        return transition(
                id,
                InterviewStatus.CREATED,
                null,
                InterviewStatus.IN_PROGRESS,
                InterviewPhase.OPENING,
                expectedVersion,
                interviewMapper.markOpening(
                        id,
                        InterviewStatus.CREATED,
                        null,
                        InterviewStatus.IN_PROGRESS,
                        InterviewPhase.OPENING,
                        expectedVersion,
                        startedAt,
                        expiresAt));
    }

    public InterviewSnapshot markWaitingAfterOpening(Long id, Long expectedVersion) {
        return transition(
                id,
                InterviewStatus.IN_PROGRESS,
                InterviewPhase.OPENING,
                InterviewStatus.IN_PROGRESS,
                InterviewPhase.WAITING_ANSWER,
                expectedVersion,
                interviewMapper.markWaitingAfterOpening(
                        id,
                        InterviewStatus.IN_PROGRESS,
                        InterviewPhase.OPENING,
                        InterviewPhase.WAITING_ANSWER,
                        expectedVersion));
    }

    public InterviewSnapshot markProcessingTurnBeforeDeadline(Long id, Long expectedVersion, LocalDateTime now) {
        int updated = interviewMapper.markProcessingTurnBeforeDeadline(
                id,
                InterviewStatus.IN_PROGRESS,
                InterviewPhase.WAITING_ANSWER,
                InterviewPhase.PROCESSING_TURN,
                expectedVersion,
                now);
        return updated == 1 ? findById(id) : null;
    }

    public InterviewSnapshot markWaitingAfterTurn(Long id, Long expectedVersion) {
        return transition(
                id,
                InterviewStatus.IN_PROGRESS,
                InterviewPhase.PROCESSING_TURN,
                InterviewStatus.IN_PROGRESS,
                InterviewPhase.WAITING_ANSWER,
                expectedVersion,
                interviewMapper.markWaitingAfterTurn(
                        id,
                        InterviewStatus.IN_PROGRESS,
                        InterviewPhase.PROCESSING_TURN,
                        InterviewPhase.WAITING_ANSWER,
                        expectedVersion));
    }

    public InterviewSnapshot completeAfterTurn(Long id, Long expectedVersion, LocalDateTime completedAt) {
        return transition(
                id,
                InterviewStatus.IN_PROGRESS,
                InterviewPhase.PROCESSING_TURN,
                InterviewStatus.COMPLETED,
                null,
                expectedVersion,
                interviewMapper.completeAfterTurn(
                        id,
                        InterviewStatus.IN_PROGRESS,
                        InterviewPhase.PROCESSING_TURN,
                        InterviewStatus.COMPLETED,
                        expectedVersion,
                        completedAt));
    }

    public InterviewSnapshot recoverOpening(Long id, Long expectedVersion) {
        return transition(
                id,
                InterviewStatus.IN_PROGRESS,
                InterviewPhase.OPENING,
                InterviewStatus.CREATED,
                null,
                expectedVersion,
                interviewMapper.recoverOpening(
                        id,
                        InterviewStatus.IN_PROGRESS,
                        InterviewPhase.OPENING,
                        InterviewStatus.CREATED,
                        expectedVersion));
    }

    private InterviewSnapshot transition(
            Long id,
            InterviewStatus expectedStatus,
            InterviewPhase expectedPhase,
            InterviewStatus targetStatus,
            InterviewPhase targetPhase,
            Long expectedVersion,
            int updated) {
        if (updated == 1) {
            return findById(id);
        }
        InterviewSnapshot actual = findById(id);
        log.warn("Interview 状态流转冲突 interviewId={} failureType=STATE_CONFLICT", id);
        if (Objects.equals(actual.status(), expectedStatus)
                && Objects.equals(actual.currentPhase(), expectedPhase)
                && !Objects.equals(actual.version(), expectedVersion)) {
            throw new BusinessException(InterviewErrorCode.INTERVIEW_VERSION_CONFLICT);
        }
        throw new BusinessException(InterviewErrorCode.INTERVIEW_STATE_CONFLICT);
    }

    private InterviewSnapshot toSnapshot(InterviewDO value) {
        return new InterviewSnapshot(
                value.getId(),
                value.getCandidateId(),
                value.getPositionId(),
                value.getStatus(),
                value.getCurrentPhase(),
                value.getVersion(),
                value.getExpiresAt(),
                value.getStartedAt(),
                value.getCompletedAt(),
                value.getMode(),
                value.getFocusArea());
    }
}
