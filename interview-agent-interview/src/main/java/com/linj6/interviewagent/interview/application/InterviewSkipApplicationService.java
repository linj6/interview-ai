package com.linj6.interviewagent.interview.application;

import com.linj6.interviewagent.ai.api.InterviewAiApi;
import com.linj6.interviewagent.ai.api.ProjectContext;
import com.linj6.interviewagent.ai.api.question.GeneratedQuestion;
import com.linj6.interviewagent.ai.api.question.MainQuestionGenerationContext;
import com.linj6.interviewagent.candidate.api.CandidateInterviewProfile;
import com.linj6.interviewagent.candidate.api.CandidateQueryApi;
import com.linj6.interviewagent.common.api.CommonErrorCode;
import com.linj6.interviewagent.common.exception.BusinessException;
import com.linj6.interviewagent.interview.domain.InterviewErrorCode;
import com.linj6.interviewagent.interview.domain.InterviewPhase;
import com.linj6.interviewagent.interview.domain.InterviewStatus;
import com.linj6.interviewagent.interview.domain.PlanItem;
import com.linj6.interviewagent.interview.domain.QuestionStatus;
import com.linj6.interviewagent.interview.domain.QuestionType;
import com.linj6.interviewagent.interview.infrastructure.persistence.repository.InterviewPlanRepository;
import com.linj6.interviewagent.interview.infrastructure.persistence.repository.InterviewRepository;
import com.linj6.interviewagent.interview.infrastructure.persistence.repository.InterviewSnapshot;
import com.linj6.interviewagent.interview.infrastructure.persistence.repository.QuestionCreateCommand;
import com.linj6.interviewagent.interview.infrastructure.persistence.repository.QuestionRepository;
import com.linj6.interviewagent.interview.infrastructure.persistence.repository.QuestionSnapshot;
import com.linj6.interviewagent.interview.interfaces.response.InterviewDetailResponse;
import com.linj6.interviewagent.position.api.PositionInterviewProfile;
import com.linj6.interviewagent.position.api.PositionQueryApi;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

/** 用户跳过当前问题；AI 调用在事务外，T2 原子提交问题与 Interview 状态。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewSkipApplicationService {
    private final InterviewRepository interviews;
    private final QuestionRepository questions;
    private final InterviewPlanRepository plans;
    private final InterviewApplicationService interviewApplicationService;
    private final InterviewDecisionLimits limits;
    private final CandidateQueryApi candidates;
    private final PositionQueryApi positions;
    private final InterviewAiApi ai;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public InterviewDetailResponse skipQuestion(Long interviewId, Long questionId) {
        interviewApplicationService.requireNotExpiredWaiting(interviewId);
        InterviewSnapshot interview = interviews.findById(interviewId);
        requireWaiting(interview, interview.version());
        QuestionSnapshot question = questions.findById(questionId);
        if (!Objects.equals(question.interviewId(), interviewId)) {
            throw new BusinessException(InterviewErrorCode.QUESTION_INTERVIEW_MISMATCH);
        }
        if (question.status() != QuestionStatus.WAITING_ANSWER) {
            throw new BusinessException(InterviewErrorCode.QUESTION_STATE_CONFLICT);
        }
        List<QuestionSnapshot> all = questions.findByInterviewId(interviewId);
        QuestionSnapshot current = questions.findLatestWaitingByInterviewId(interviewId);
        if (current == null
                || !Objects.equals(current.id(), questionId)
                || all.stream()
                                .filter(value -> value.status() == QuestionStatus.WAITING_ANSWER)
                                .count()
                        != 1) {
            throw new BusinessException(InterviewErrorCode.QUESTION_STATE_CONFLICT);
        }
        long mainCount =
                all.stream().filter(value -> value.type() == QuestionType.MAIN).count();
        boolean complete = mainCount >= limits.maxMainQuestions();
        InterviewPlanFacts facts = planFacts(interviewId, all);
        PlanItem selected = complete
                ? null
                : facts.unused().stream()
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("Interview Plan exhausted before MAIN limit"));
        QuestionProposal proposal =
                complete ? null : generate(interview, question, selected, facts.previousMainQuestions());
        transactionTemplate.executeWithoutResult(status -> {
            // T2 在任何写入前校验原始版本；最终 CAS 仍负责关闭并发窗口并回滚此前的写入。
            requireWaiting(interviews.findById(interviewId), interview.version());
            QuestionSnapshot actual = questions.findById(questionId);
            QuestionSnapshot latest = questions.findLatestWaitingByInterviewId(interviewId);
            if (!Objects.equals(actual.interviewId(), interviewId)
                    || actual.status() != QuestionStatus.WAITING_ANSWER
                    || latest == null
                    || !Objects.equals(latest.id(), questionId)) {
                throw new BusinessException(InterviewErrorCode.QUESTION_STATE_CONFLICT);
            }
            InterviewPlanFacts actualFacts = planFacts(interviewId, questions.findByInterviewId(interviewId));
            if (!facts.plan().equals(actualFacts.plan()))
                throw new BusinessException(InterviewErrorCode.INTERVIEW_STATE_CONFLICT);
            if (!complete) {
                PlanItem first = actualFacts.unused().stream()
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("Interview Plan exhausted before MAIN limit"));
                if (!first.equals(selected)) throw new BusinessException(InterviewErrorCode.INTERVIEW_STATE_CONFLICT);
                actualFacts.requireUnused(selected.id());
            }
            questions.markSkipped(questionId, interviewId);
            if (complete) {
                interviews.completeAfterSkip(interviewId, interview.version(), LocalDateTime.now(clock));
            } else {
                questions.create(QuestionCreateCommand.builder()
                        .interviewId(interviewId)
                        .sequenceNo(all.get(all.size() - 1).sequenceNo() + 1)
                        .parentQuestionId(null)
                        .type(QuestionType.MAIN)
                        .content(proposal.question().content())
                        .planItemId(selected.id())
                        .evaluationProfileId(question.evaluationProfileId())
                        .topicId(null)
                        .build());
                interviews.markWaitingAfterSkip(interviewId, interview.version());
            }
        });
        log.info("Skip 成功 interviewId={} questionId={} completed={}", interviewId, questionId, complete);
        return interviewApplicationService.getInterview(interviewId);
    }

    private QuestionProposal generate(
            InterviewSnapshot interview,
            QuestionSnapshot question,
            PlanItem selected,
            List<String> previousMainQuestions) {
        CandidateInterviewProfile candidate = candidates.getInterviewProfile(interview.candidateId());
        PositionInterviewProfile position = positions.getInterviewProfile(interview.positionId());
        if (candidate == null || position == null) {
            throw new IllegalStateException("Skip business context is incomplete");
        }
        MainQuestionGenerationContext context = MainQuestionGenerationContext.builder()
                .area(selected.area().name())
                .focus(selected.focus())
                .reason(selected.reason())
                .previousMainQuestions(previousMainQuestions)
                .candidateName(candidate.candidateName())
                .resumeSummary(candidate.resumeSummary())
                .projects(candidate.projects().stream()
                        .map(project -> new ProjectContext(project.name(), project.description()))
                        .toList())
                .positionTitle(position.title())
                .positionDescription(position.description())
                .build();
        RuntimeException firstFailure = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                GeneratedQuestion generated = ai.generateMainQuestion(context);
                if (generated == null) {
                    throw new BusinessException(CommonErrorCode.AI_RESPONSE_INVALID);
                }
                return new QuestionProposal(selected.focus(), generated);
            } catch (RuntimeException failure) {
                boolean recoverable = isRecoverableAiFailure(failure);
                log.warn(
                        "Skip QuestionGeneration 失败 interviewId={} questionId={} attempt={} recoverable={}",
                        interview.id(),
                        question.id(),
                        attempt,
                        recoverable);
                if (!recoverable || attempt == 2) {
                    RuntimeException terminal = mapAiFailure(failure);
                    if (firstFailure != null && firstFailure != terminal) {
                        terminal.addSuppressed(firstFailure);
                    }
                    throw terminal;
                }
                firstFailure = failure;
                try {
                    Thread.sleep(100);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Skip retry interrupted", interrupted);
                }
            }
        }
        throw new IllegalStateException("Skip attempt limit exceeded");
    }

    private InterviewPlanFacts planFacts(Long interviewId, List<QuestionSnapshot> all) {
        var plan = plans.findByInterviewId(interviewId);
        if (plan == null || !interviewId.equals(plan.interviewId()))
            throw new IllegalStateException("Interview Plan is missing or foreign");
        return new InterviewPlanFacts(plan, all);
    }

    private record QuestionProposal(String focus, GeneratedQuestion question) {}

    private void requireWaiting(InterviewSnapshot interview, Long version) {
        if (interview.status() != InterviewStatus.IN_PROGRESS
                || interview.currentPhase() != InterviewPhase.WAITING_ANSWER) {
            throw new BusinessException(InterviewErrorCode.INTERVIEW_STATE_CONFLICT);
        }
        if (!Objects.equals(interview.version(), version)) {
            throw new BusinessException(InterviewErrorCode.INTERVIEW_VERSION_CONFLICT);
        }
    }

    private boolean isRecoverableAiFailure(Throwable failure) {
        if (failure instanceof BusinessException business) {
            return business.getErrorCode() == CommonErrorCode.AI_RESPONSE_INVALID
                    || business.getErrorCode() == CommonErrorCode.AI_SERVICE_TIMEOUT
                    || business.getErrorCode() == CommonErrorCode.AI_SERVICE_ERROR
                    || business.getErrorCode() == CommonErrorCode.RATE_LIMIT;
        }
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof DataAccessException) {
                return false;
            }
            if (cause instanceof SocketTimeoutException
                    || cause instanceof java.util.concurrent.TimeoutException
                    || cause instanceof ConnectException
                    || cause instanceof UnknownHostException
                    || cause instanceof ResourceAccessException) {
                return true;
            }
            if (cause instanceof RestClientResponseException response) {
                int status = response.getStatusCode().value();
                return status == 429 || status >= 500;
            }
            String name = cause.getClass().getSimpleName();
            if (name.equals("QuestionGenerationValidationException")
                    || name.equals("TransientAiException")
                    || name.equals("ReadTimeoutException")
                    || name.equals("ConnectTimeoutException")) {
                return true;
            }
        }
        return false;
    }

    private RuntimeException mapAiFailure(RuntimeException failure) {
        if (failure instanceof BusinessException || !isRecoverableAiFailure(failure)) {
            return failure;
        }
        CommonErrorCode code = CommonErrorCode.AI_SERVICE_ERROR;
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            String name = cause.getClass().getSimpleName();
            if (name.equals("QuestionGenerationValidationException")) {
                code = CommonErrorCode.AI_RESPONSE_INVALID;
                break;
            }
            if (cause instanceof SocketTimeoutException
                    || cause instanceof java.util.concurrent.TimeoutException
                    || name.equals("ReadTimeoutException")
                    || name.equals("ConnectTimeoutException")) {
                code = CommonErrorCode.AI_SERVICE_TIMEOUT;
                break;
            }
            if (cause instanceof RestClientResponseException response
                    && response.getStatusCode().value() == 429) {
                code = CommonErrorCode.RATE_LIMIT;
                break;
            }
        }
        BusinessException mapped = new BusinessException(code);
        mapped.initCause(failure);
        return mapped;
    }
}
