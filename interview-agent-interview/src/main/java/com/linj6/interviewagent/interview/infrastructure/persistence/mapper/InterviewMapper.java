package com.linj6.interviewagent.interview.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.linj6.interviewagent.interview.domain.InterviewPhase;
import com.linj6.interviewagent.interview.domain.InterviewStatus;
import com.linj6.interviewagent.interview.infrastructure.persistence.entity.InterviewDO;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** Interview 的 MyBatis-Plus Mapper。 */
@Mapper
public interface InterviewMapper extends BaseMapper<InterviewDO> {
    InterviewDO selectForUpdate(@Param("id") Long id);

    int changeStatusAtWaitingAnswer(
            @Param("id") Long id,
            @Param("expectedStatus") InterviewStatus expectedStatus,
            @Param("expectedPhase") InterviewPhase expectedPhase,
            @Param("targetStatus") InterviewStatus targetStatus,
            @Param("version") Long version);

    int finish(
            @Param("id") Long id,
            @Param("expectedStatus") InterviewStatus expectedStatus,
            @Param("expectedPhase") InterviewPhase expectedPhase,
            @Param("version") Long version,
            @Param("completedAt") LocalDateTime completedAt);

    int completeExpiredWaiting(
            @Param("id") Long id,
            @Param("expectedStatus") InterviewStatus expectedStatus,
            @Param("expectedPhase") InterviewPhase expectedPhase,
            @Param("version") Long version,
            @Param("completedAt") LocalDateTime completedAt);

    int markOpening(
            @Param("id") Long id,
            @Param("expectedStatus") InterviewStatus expectedStatus,
            @Param("expectedPhase") InterviewPhase expectedPhase,
            @Param("targetStatus") InterviewStatus targetStatus,
            @Param("targetPhase") InterviewPhase targetPhase,
            @Param("version") Long version,
            @Param("startedAt") LocalDateTime startedAt,
            @Param("expiresAt") LocalDateTime expiresAt);

    int markWaitingAfterOpening(
            @Param("id") Long id,
            @Param("expectedStatus") InterviewStatus expectedStatus,
            @Param("expectedPhase") InterviewPhase expectedPhase,
            @Param("targetPhase") InterviewPhase targetPhase,
            @Param("version") Long version);

    int markProcessingTurnBeforeDeadline(
            @Param("id") Long id,
            @Param("expectedStatus") InterviewStatus expectedStatus,
            @Param("expectedPhase") InterviewPhase expectedPhase,
            @Param("targetPhase") InterviewPhase targetPhase,
            @Param("version") Long version,
            @Param("now") LocalDateTime now);

    int markWaitingAfterTurn(
            @Param("id") Long id,
            @Param("expectedStatus") InterviewStatus expectedStatus,
            @Param("expectedPhase") InterviewPhase expectedPhase,
            @Param("targetPhase") InterviewPhase targetPhase,
            @Param("version") Long version);

    int completeAfterTurn(
            @Param("id") Long id,
            @Param("expectedStatus") InterviewStatus expectedStatus,
            @Param("expectedPhase") InterviewPhase expectedPhase,
            @Param("targetStatus") InterviewStatus targetStatus,
            @Param("version") Long version,
            @Param("completedAt") LocalDateTime completedAt);

    int recoverOpening(
            @Param("id") Long id,
            @Param("expectedStatus") InterviewStatus expectedStatus,
            @Param("expectedPhase") InterviewPhase expectedPhase,
            @Param("targetStatus") InterviewStatus targetStatus,
            @Param("version") Long version);
}
