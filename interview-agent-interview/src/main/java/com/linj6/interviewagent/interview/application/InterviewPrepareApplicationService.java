package com.linj6.interviewagent.interview.application;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.linj6.interviewagent.ai.api.InterviewAiApi;
import com.linj6.interviewagent.ai.api.ProjectContext;
import com.linj6.interviewagent.ai.api.planner.PlannerContext;
import com.linj6.interviewagent.ai.api.planner.ValidatedPlanResult;
import com.linj6.interviewagent.candidate.api.CandidateQueryApi;
import com.linj6.interviewagent.common.api.CommonErrorCode;
import com.linj6.interviewagent.common.exception.BusinessException;
import com.linj6.interviewagent.interview.domain.InterviewErrorCode;
import com.linj6.interviewagent.interview.domain.InterviewMode;
import com.linj6.interviewagent.interview.domain.InterviewPlan;
import com.linj6.interviewagent.interview.domain.InterviewStatus;
import com.linj6.interviewagent.interview.domain.KnowledgeArea;
import com.linj6.interviewagent.interview.domain.PlanItem;
import com.linj6.interviewagent.interview.infrastructure.persistence.repository.InterviewPlanRepository;
import com.linj6.interviewagent.interview.infrastructure.persistence.repository.InterviewRepository;
import com.linj6.interviewagent.interview.infrastructure.persistence.repository.InterviewSnapshot;
import com.linj6.interviewagent.interview.interfaces.response.InterviewPrepareResponse;
import com.linj6.interviewagent.position.api.PositionQueryApi;
import java.util.ArrayList;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** 独立 Prepare 用例；计划成功后仍保持 CREATED，不占用新的生命周期状态。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InterviewPrepareApplicationService {
    private final InterviewRepository interviewRepository;
    private final InterviewPlanRepository planRepository;
    private final CandidateQueryApi candidateQueryApi;
    private final PositionQueryApi positionQueryApi;
    private final InterviewAiApi interviewAiApi;
    private final InterviewDecisionLimits limits;
    private final TransactionTemplate transactionTemplate;

    public InterviewPrepareResponse prepareInterview(Long id) {
        var observed = interviewRepository.findById(id);
        requireUnprepared(observed);
        observed.mode().validateFocusArea(observed.focusArea());
        var candidate = candidateQueryApi.getInterviewProfile(observed.candidateId());
        var position = positionQueryApi.getInterviewProfile(observed.positionId());
        var context = new PlannerContext(
                id,
                observed.mode().name(),
                observed.focusArea() == null ? null : observed.focusArea().name(),
                limits.maxMainQuestions(),
                candidate.candidateName(),
                candidate.resumeSummary(),
                candidate.projects().stream()
                        .map(project -> new ProjectContext(project.name(), project.description()))
                        .toList(),
                position.title(),
                position.description(),
                java.util.Arrays.stream(InterviewMode.values()).map(Enum::name).toList(),
                java.util.Arrays.stream(KnowledgeArea.values()).map(Enum::name).toList());
        ValidatedPlanResult result;
        try {
            result = interviewAiApi.preparePlan(context);
        } catch (RuntimeException exception) {
            if (exception instanceof BusinessException) throw exception;
            var code = CommonErrorCode.AI_SERVICE_ERROR;
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof com.linj6.interviewagent.ai.evaluator.EvaluationValidationException
                        || cause instanceof IllegalArgumentException) {
                    code = CommonErrorCode.AI_RESPONSE_INVALID;
                    break;
                }
                if (cause instanceof java.net.SocketTimeoutException
                        || cause instanceof java.util.concurrent.TimeoutException)
                    code = CommonErrorCode.AI_SERVICE_TIMEOUT;
            }
            var mapped = new BusinessException(code);
            mapped.initCause(exception);
            throw mapped;
        }
        // AI 校验与真实 ID 分配先完成，不在数据库事务中等待模型。
        InterviewPlan plan = toPlan(id, result, observed.focusArea());
        try {
            transactionTemplate.executeWithoutResult(status -> {
                // 锁读与 Start 等状态更新串行化；重读并校验期间发生的状态/版本变化。
                var current = interviewRepository.findByIdForUpdate(id);
                requireUnprepared(current);
                if (!current.version().equals(observed.version()))
                    throw new BusinessException(InterviewErrorCode.INTERVIEW_VERSION_CONFLICT);
                planRepository.save(plan, limits.maxMainQuestions());
            });
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(InterviewErrorCode.INTERVIEW_STATE_CONFLICT);
        }
        log.info(
                "Prepare 成功 interviewId={} planId={} itemCount={}",
                id,
                plan.id(),
                plan.items().size());
        return new InterviewPrepareResponse(id, plan.id(), plan.initialPlanItemId());
    }

    private void requireUnprepared(InterviewSnapshot interview) {
        if (interview.status() != InterviewStatus.CREATED
                || interview.currentPhase() != null
                || planRepository.findByInterviewId(interview.id()) != null)
            throw new BusinessException(InterviewErrorCode.INTERVIEW_STATE_CONFLICT);
    }

    private InterviewPlan toPlan(Long id, ValidatedPlanResult result, KnowledgeArea focusArea) {
        try {
            if (result == null
                    || result.initialItemIndex() < 0
                    || result.initialItemIndex() >= result.items().size()) throw new IllegalArgumentException();
            long planId = IdWorker.getId();
            var items = new ArrayList<PlanItem>();
            for (var item : result.items()) {
                var area = KnowledgeArea.valueOf(item.area());
                if (focusArea != null && focusArea != area) throw new IllegalArgumentException();
                items.add(new PlanItem(IdWorker.getId(), planId, area, item.focus(), item.reason()));
            }
            var plan = new InterviewPlan(
                    planId, id, items.get(result.initialItemIndex()).id(), items);
            plan.validateItemCount(limits.maxMainQuestions());
            return plan;
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException(CommonErrorCode.AI_RESPONSE_INVALID);
        }
    }
}
