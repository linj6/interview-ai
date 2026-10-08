package com.linj6.interviewagent.interview.application;

import com.linj6.interviewagent.ai.api.InterviewAiApi;
import com.linj6.interviewagent.ai.api.ProjectContext;
import com.linj6.interviewagent.ai.api.question.GeneratedQuestion;
import com.linj6.interviewagent.ai.api.question.MainQuestionGenerationContext;
import com.linj6.interviewagent.candidate.api.CandidateInterviewProfile;
import com.linj6.interviewagent.candidate.api.CandidateQueryApi;
import com.linj6.interviewagent.common.PersonalInstanceIdentity;
import com.linj6.interviewagent.common.api.CommonErrorCode;
import com.linj6.interviewagent.common.exception.BusinessException;
import com.linj6.interviewagent.evaluation.api.profile.EvaluationProfile;
import com.linj6.interviewagent.evaluation.api.profile.EvaluationProfileApi;
import com.linj6.interviewagent.interview.domain.InterviewErrorCode;
import com.linj6.interviewagent.interview.domain.InterviewMode;
import com.linj6.interviewagent.interview.domain.InterviewPhase;
import com.linj6.interviewagent.interview.domain.InterviewPlan;
import com.linj6.interviewagent.interview.domain.InterviewStatus;
import com.linj6.interviewagent.interview.domain.KnowledgeArea;
import com.linj6.interviewagent.interview.domain.PlanItem;
import com.linj6.interviewagent.interview.domain.QuestionType;
import com.linj6.interviewagent.interview.infrastructure.persistence.repository.InterviewPlanRepository;
import com.linj6.interviewagent.interview.infrastructure.persistence.repository.InterviewRepository;
import com.linj6.interviewagent.interview.infrastructure.persistence.repository.InterviewSnapshot;
import com.linj6.interviewagent.interview.infrastructure.persistence.repository.QuestionCreateCommand;
import com.linj6.interviewagent.interview.infrastructure.persistence.repository.QuestionRepository;
import com.linj6.interviewagent.interview.infrastructure.persistence.repository.QuestionSnapshot;
import com.linj6.interviewagent.interview.interfaces.response.InterviewDetailResponse;
import com.linj6.interviewagent.interview.interfaces.response.QuestionResponse;
import com.linj6.interviewagent.position.api.PositionInterviewProfile;
import com.linj6.interviewagent.position.api.PositionQueryApi;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Interview 创建、Opening 启动和查询用例编排。 */
@Service
@RequiredArgsConstructor
public class InterviewApplicationService {

    private static final String DEFAULT_EVALUATION_PROFILE_CODE = "DEFAULT_GENERAL_INTERVIEW";

    private final InterviewRepository interviewRepository;
    private final QuestionRepository questionRepository;
    private final InterviewPlanRepository planRepository;
    private final EvaluationProfileApi evaluationProfileApi;
    private final TransactionTemplate transactionTemplate;
    private final CandidateQueryApi candidateQueryApi;
    private final PositionQueryApi positionQueryApi;
    private final InterviewAiApi interviewAiApi;
    private final Clock clock;
    private final InterviewDurationSettings durationSettings;

    public InterviewDetailResponse createInterview(InterviewMode mode, KnowledgeArea focusArea) {
        if (mode == null) throw new BusinessException(CommonErrorCode.PARAM_ERROR);
        try {
            mode.validateFocusArea(focusArea);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR);
        }
        // 仅创建准入时检查当前资料；后续生命周期允许 Mutable Profile 的背景漂移。
        if (mode == InterviewMode.PROJECT_DEEP_DIVE
                && candidateQueryApi
                        .getInterviewProfile(PersonalInstanceIdentity.CANDIDATE_ID)
                        .projects()
                        .isEmpty()) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR);
        }
        InterviewSnapshot created = transactionTemplate.execute(status -> interviewRepository.create(
                PersonalInstanceIdentity.CANDIDATE_ID, PersonalInstanceIdentity.POSITION_ID, mode, focusArea));
        return getInterview(created.id());
    }

    public InterviewDetailResponse startInterview(Long interviewId) {
        LocalDateTime startedAt = LocalDateTime.now(clock);
        LocalDateTime expiresAt = startedAt.plusMinutes(durationSettings.durationMinutes());
        OpeningPreparation prepared = transactionTemplate.execute(status -> {
            // 锁读和 Prepare 持久化串行化；只有合法 CREATED 和已准备计划才能启动。
            InterviewSnapshot current = interviewRepository.findByIdForUpdate(interviewId);
            if (current.status() != InterviewStatus.CREATED || current.currentPhase() != null)
                throw new BusinessException(InterviewErrorCode.INTERVIEW_STATE_CONFLICT);
            InterviewPlan plan = requireOpeningPlan(interviewId);
            PlanItem initial = initialItem(plan);
            requireUnusedInitial(interviewId, initial.id());
            return new OpeningPreparation(
                    interviewRepository.markOpening(interviewId, current.version(), startedAt, expiresAt),
                    plan,
                    initial);
        });
        InterviewSnapshot opening = prepared.interview();
        try {
            CandidateInterviewProfile candidate = candidateQueryApi.getInterviewProfile(opening.candidateId());
            PositionInterviewProfile position = positionQueryApi.getInterviewProfile(opening.positionId());
            GeneratedQuestion question = interviewAiApi.generateMainQuestion(MainQuestionGenerationContext.builder()
                    .focus(prepared.initial().focus())
                    .area(prepared.initial().area().name())
                    .reason(prepared.initial().reason())
                    .previousMainQuestions(java.util.List.of())
                    .candidateName(candidate.candidateName())
                    .resumeSummary(candidate.resumeSummary())
                    .projects(candidate.projects().stream()
                            .map(project -> new ProjectContext(project.name(), project.description()))
                            .toList())
                    .positionTitle(position.title())
                    .positionDescription(position.description())
                    .build());
            EvaluationProfile profile = evaluationProfileApi.findByCode(DEFAULT_EVALUATION_PROFILE_CODE);
            transactionTemplate.executeWithoutResult(
                    status -> persistOpeningQuestion(prepared, question, profile.id()));
        } catch (RuntimeException exception) {
            recoverOpeningFailure(opening, exception);
            throw exception;
        }
        return getInterview(interviewId);
    }

    public InterviewDetailResponse pauseInterview(Long interviewId) {
        requireNotExpiredWaiting(interviewId);
        transactionTemplate.executeWithoutResult(status -> {
            InterviewSnapshot interview = interviewRepository.findById(interviewId);
            interviewRepository.pause(interviewId, interview.version());
        });
        return getInterview(interviewId);
    }

    public InterviewDetailResponse resumeInterview(Long interviewId) {
        requireNotExpiredWaiting(interviewId);
        transactionTemplate.executeWithoutResult(status -> {
            InterviewSnapshot interview = interviewRepository.findById(interviewId);
            interviewRepository.resume(interviewId, interview.version());
        });
        return getInterview(interviewId);
    }

    public InterviewDetailResponse finishInterview(Long interviewId) {
        transactionTemplate.executeWithoutResult(status -> {
            InterviewSnapshot interview = interviewRepository.findById(interviewId);
            if ((interview.status() != InterviewStatus.IN_PROGRESS && interview.status() != InterviewStatus.PAUSED)
                    || interview.currentPhase() != InterviewPhase.WAITING_ANSWER) {
                throw new BusinessException(InterviewErrorCode.INTERVIEW_STATE_CONFLICT);
            }
            interviewRepository.finish(interviewId, interview.status(), interview.version(), LocalDateTime.now(clock));
        });
        return getInterview(interviewId);
    }

    public InterviewDetailResponse getInterview(Long interviewId) {
        convergeExpiredWaiting(interviewId);
        InterviewSnapshot interview = interviewRepository.findById(interviewId);
        QuestionSnapshot question = questionRepository.findFirstByInterviewId(interviewId);
        QuestionResponse response = question == null
                ? null
                : new QuestionResponse(question.id(), question.type().name(), question.status(), question.content());
        QuestionSnapshot current =
                (interview.status() == InterviewStatus.IN_PROGRESS || interview.status() == InterviewStatus.PAUSED)
                                && interview.currentPhase() == InterviewPhase.WAITING_ANSWER
                        ? questionRepository.findLatestWaitingByInterviewId(interviewId)
                        : null;
        QuestionResponse currentResponse = current == null
                ? null
                : new QuestionResponse(current.id(), current.type().name(), current.status(), current.content());
        return new InterviewDetailResponse(
                interview.id(),
                interview.status(),
                interview.currentPhase(),
                response,
                currentResponse,
                interview.expiresAt(),
                interview.mode(),
                interview.focusArea(),
                planRepository.findByInterviewId(interviewId) != null);
    }

    /** 在独立短事务中收敛稳定待答状态，确保调用方抛出业务异常时收敛不会回滚。 */
    public void requireNotExpiredWaiting(Long interviewId) {
        if (convergeExpiredWaiting(interviewId)) {
            throw new BusinessException(InterviewErrorCode.INTERVIEW_EXPIRED);
        }
    }

    boolean convergeExpiredWaiting(Long interviewId) {
        InterviewSnapshot observed = interviewRepository.findById(interviewId);
        if (!isExpiredWaiting(observed)) {
            return false;
        }
        InterviewSnapshot current = observed;
        while (isExpiredWaiting(current)) {
            boolean updated = Boolean.TRUE.equals(transactionTemplate.execute(status -> {
                InterviewSnapshot transactional = interviewRepository.findById(interviewId);
                if (!isExpiredWaiting(transactional)) {
                    return false;
                }
                return interviewRepository.completeExpiredWaiting(
                        interviewId, transactional.status(), transactional.version(), LocalDateTime.now(clock));
            }));
            if (updated) {
                return true;
            }
            // CAS 失败后重读；只在最新状态仍满足条件时再次尝试，绝不覆盖其他状态转换。
            current = interviewRepository.findById(interviewId);
        }
        return false;
    }

    private boolean isExpiredWaiting(InterviewSnapshot interview) {
        return (interview.status() == InterviewStatus.IN_PROGRESS || interview.status() == InterviewStatus.PAUSED)
                && interview.currentPhase() == InterviewPhase.WAITING_ANSWER
                && interview.expiresAt() != null
                && !LocalDateTime.now(clock).isBefore(interview.expiresAt());
    }

    private record OpeningPreparation(InterviewSnapshot interview, InterviewPlan plan, PlanItem initial) {}

    private InterviewPlan requireOpeningPlan(Long interviewId) {
        try {
            InterviewPlan plan = planRepository.findByInterviewId(interviewId);
            if (plan == null || !interviewId.equals(plan.interviewId()))
                throw new BusinessException(InterviewErrorCode.INTERVIEW_STATE_CONFLICT);
            initialItem(plan);
            return plan;
        } catch (IllegalArgumentException exception) {
            // 聚合加载发现 initial 引用或归属损坏，不允许生成正式首题。
            throw new BusinessException(InterviewErrorCode.INTERVIEW_STATE_CONFLICT);
        }
    }

    private PlanItem initialItem(InterviewPlan plan) {
        return plan.items().stream()
                .filter(item -> item.id().equals(plan.initialPlanItemId())
                        && item.planId().equals(plan.id()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(InterviewErrorCode.INTERVIEW_STATE_CONFLICT));
    }

    private void requireUnusedInitial(Long interviewId, Long planItemId) {
        if (questionRepository.findByInterviewId(interviewId).stream()
                .anyMatch(question -> question.type() == QuestionType.MAIN && planItemId.equals(question.planItemId())))
            throw new BusinessException(InterviewErrorCode.INTERVIEW_STATE_CONFLICT);
    }

    private void persistOpeningQuestion(OpeningPreparation prepared, GeneratedQuestion question, Long profileId) {
        var opening = prepared.interview();
        // T2 重读状态、版本和完整 Plan，确保 AI 期间的事实变化不会被覆盖。
        InterviewSnapshot current = interviewRepository.findByIdForUpdate(opening.id());
        requireInterviewState(current, InterviewStatus.IN_PROGRESS, InterviewPhase.OPENING, opening.version());
        InterviewPlan plan = requireOpeningPlan(opening.id());
        if (!plan.equals(prepared.plan())) throw new BusinessException(InterviewErrorCode.INTERVIEW_STATE_CONFLICT);
        requireUnusedInitial(opening.id(), prepared.initial().id());
        if (question == null || question.content() == null || question.content().isBlank())
            throw new BusinessException(com.linj6.interviewagent.common.api.CommonErrorCode.AI_RESPONSE_INVALID);
        // question.focus 暂存同一 PlanItem 的 focus；全部路径接入后的后续 Migration 删除该兼容列。
        questionRepository.create(QuestionCreateCommand.builder()
                .interviewId(opening.id())
                .sequenceNo(1)
                .type(QuestionType.MAIN)
                .content(question.content())
                .evaluationProfileId(profileId)
                .planItemId(prepared.initial().id())
                .build());
        interviewRepository.markWaitingAfterOpening(opening.id(), current.version());
    }

    private void recoverOpeningFailure(InterviewSnapshot opening, RuntimeException originalException) {
        try {
            transactionTemplate.executeWithoutResult(
                    status -> interviewRepository.recoverOpening(opening.id(), opening.version()));
        } catch (RuntimeException recoveryException) {
            originalException.addSuppressed(recoveryException);
        }
    }

    private void requireInterviewState(
            InterviewSnapshot interview,
            InterviewStatus expectedStatus,
            InterviewPhase expectedPhase,
            Long expectedVersion) {
        if (!Objects.equals(interview.status(), expectedStatus)
                || !Objects.equals(interview.currentPhase(), expectedPhase)) {
            throw new BusinessException(InterviewErrorCode.INTERVIEW_STATE_CONFLICT);
        }
        if (!Objects.equals(interview.version(), expectedVersion)) {
            throw new BusinessException(InterviewErrorCode.INTERVIEW_VERSION_CONFLICT);
        }
    }
}
