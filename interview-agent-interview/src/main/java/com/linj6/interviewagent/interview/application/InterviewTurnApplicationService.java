package com.linj6.interviewagent.interview.application;

import com.linj6.interviewagent.ai.api.InterviewAiApi;
import com.linj6.interviewagent.ai.api.ProjectContext;
import com.linj6.interviewagent.ai.api.decision.DecisionAction;
import com.linj6.interviewagent.ai.api.decision.DecisionContext;
import com.linj6.interviewagent.ai.api.decision.DecisionPlanItem;
import com.linj6.interviewagent.ai.api.decision.InterviewProgress;
import com.linj6.interviewagent.ai.api.decision.ValidatedDecisionResult;
import com.linj6.interviewagent.ai.api.evaluation.EvaluationContext;
import com.linj6.interviewagent.ai.api.evaluation.EvaluationDimensionContext;
import com.linj6.interviewagent.ai.api.evaluation.ValidatedDimensionEvaluation;
import com.linj6.interviewagent.ai.api.evaluation.ValidatedEvaluationResult;
import com.linj6.interviewagent.ai.api.question.FollowUpQuestionGenerationContext;
import com.linj6.interviewagent.ai.api.question.GeneratedQuestion;
import com.linj6.interviewagent.ai.api.question.MainQuestionGenerationContext;
import com.linj6.interviewagent.ai.evaluator.EvaluationValidationException;
import com.linj6.interviewagent.candidate.api.CandidateInterviewProfile;
import com.linj6.interviewagent.candidate.api.CandidateQueryApi;
import com.linj6.interviewagent.common.api.CommonErrorCode;
import com.linj6.interviewagent.common.exception.BusinessException;
import com.linj6.interviewagent.evaluation.api.evaluation.DimensionScore;
import com.linj6.interviewagent.evaluation.api.evaluation.EvaluationApi;
import com.linj6.interviewagent.evaluation.api.evaluation.EvaluationCommand;
import com.linj6.interviewagent.evaluation.api.evaluation.EvaluationCreated;
import com.linj6.interviewagent.evaluation.api.evaluation.Evidence;
import com.linj6.interviewagent.evaluation.api.evaluation.Feedback;
import com.linj6.interviewagent.evaluation.api.evaluation.MissingPoint;
import com.linj6.interviewagent.evaluation.api.evaluation.ReferenceAnswer;
import com.linj6.interviewagent.evaluation.api.profile.EvaluationDimension;
import com.linj6.interviewagent.evaluation.api.profile.EvaluationProfile;
import com.linj6.interviewagent.evaluation.api.profile.EvaluationProfileApi;
import com.linj6.interviewagent.interview.domain.InterviewErrorCode;
import com.linj6.interviewagent.interview.domain.InterviewPhase;
import com.linj6.interviewagent.interview.domain.InterviewPlan;
import com.linj6.interviewagent.interview.domain.InterviewStatus;
import com.linj6.interviewagent.interview.domain.PlanItem;
import com.linj6.interviewagent.interview.domain.QuestionStatus;
import com.linj6.interviewagent.interview.domain.QuestionType;
import com.linj6.interviewagent.interview.infrastructure.persistence.repository.AnswerRepository;
import com.linj6.interviewagent.interview.infrastructure.persistence.repository.AnswerSnapshot;
import com.linj6.interviewagent.interview.infrastructure.persistence.repository.InterviewPlanRepository;
import com.linj6.interviewagent.interview.infrastructure.persistence.repository.InterviewRepository;
import com.linj6.interviewagent.interview.infrastructure.persistence.repository.InterviewSnapshot;
import com.linj6.interviewagent.interview.infrastructure.persistence.repository.QuestionCreateCommand;
import com.linj6.interviewagent.interview.infrastructure.persistence.repository.QuestionRepository;
import com.linj6.interviewagent.interview.infrastructure.persistence.repository.QuestionSnapshot;
import com.linj6.interviewagent.interview.interfaces.response.AnswerSubmissionResponse;
import com.linj6.interviewagent.position.api.PositionInterviewProfile;
import com.linj6.interviewagent.position.api.PositionQueryApi;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

/** Turn 用例编排；T1/T2 持久化及事务外 AI 流程由本服务负责。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewTurnApplicationService {
    private static final int VALIDATION_REASON_LOG_LIMIT = 256;

    private final InterviewRepository interviewRepository;
    private final InterviewApplicationService interviewApplicationService;
    private final QuestionRepository questionRepository;
    private final InterviewPlanRepository planRepository;
    private final AnswerRepository answerRepository;
    private final EvaluationProfileApi evaluationProfileApi;
    private final EvaluationApi evaluationApi;
    private final TransactionTemplate transactionTemplate;
    private final CandidateQueryApi candidateQueryApi;
    private final PositionQueryApi positionQueryApi;
    private final InterviewAiApi interviewAiApi;
    private final AllowedActionsCalculator allowedActionsCalculator;
    private final InterviewDecisionLimits decisionLimits;
    private final Clock clock;

    /** 独立 Turn T2：在一个事务内提交评价、当前题、可选下一题和最终 Interview CAS。 */
    public EvaluationCreated persistProcessedTurn(
            Long interviewId,
            Long questionId,
            Long answerId,
            Long expectedVersion,
            ValidatedEvaluationResult evaluation,
            ValidatedDecisionResult decision,
            GeneratedQuestion generatedQuestion,
            String mainFocus) {
        return persistProcessedTurn(
                interviewId,
                questionId,
                answerId,
                expectedVersion,
                evaluation,
                decision,
                generatedQuestion,
                mainFocus,
                null);
    }

    private EvaluationCreated persistProcessedTurn(
            Long interviewId,
            Long questionId,
            Long answerId,
            Long expectedVersion,
            ValidatedEvaluationResult evaluation,
            ValidatedDecisionResult decision,
            GeneratedQuestion generatedQuestion,
            String mainFocus,
            InterviewPlan expectedPlan) {
        return transactionTemplate.execute(status -> {
            InterviewSnapshot interview = interviewRepository.findById(interviewId);
            QuestionSnapshot question = questionRepository.findById(questionId);
            AnswerSnapshot answer = answerRepository.findById(answerId);
            requireInterviewState(
                    interview, InterviewStatus.IN_PROGRESS, InterviewPhase.PROCESSING_TURN, expectedVersion);
            boolean expired = isExpired(interview);
            if (!Objects.equals(question.interviewId(), interviewId)
                    || question.status() != QuestionStatus.WAITING_ANSWER
                    || !Objects.equals(answer.interviewId(), interviewId)
                    || !Objects.equals(answer.questionId(), questionId)) {
                throw new BusinessException(InterviewErrorCode.INTERVIEW_STATE_CONFLICT);
            }
            if (evaluation == null || decision == null || decision.action() == null) {
                throw new IllegalArgumentException("Turn T2 需要 Evaluation 和 Decision Action");
            }
            DecisionAction action = decision.action();
            if (!expired
                    && ((action == DecisionAction.END && generatedQuestion != null)
                            || (action != DecisionAction.END && generatedQuestion == null))) {
                throw new IllegalArgumentException("GeneratedQuestion 与 Decision Action 不匹配");
            }

            if (!Objects.equals(evaluation.profileId(), question.evaluationProfileId())) {
                throw new IllegalStateException("Validated Evaluation Profile 与 Question 绑定 Profile 不一致");
            }
            var actualQuestions = questionRepository.findByInterviewId(interviewId);
            var actualById =
                    actualQuestions.stream().collect(Collectors.toMap(QuestionSnapshot::id, Function.identity()));
            mainQuestionId(question, actualById);
            InterviewPlanFacts facts = planFacts(interviewId, actualQuestions);
            if (expectedPlan != null && !expectedPlan.equals(facts.plan()))
                throw new BusinessException(InterviewErrorCode.INTERVIEW_STATE_CONFLICT);
            PlanItem nextItem = null;
            if (!expired && action == DecisionAction.NEXT_QUESTION) {
                nextItem = facts.requireUnused(decision.planItemId());
                if (!Objects.equals(mainFocus, nextItem.focus()))
                    throw new IllegalStateException("MAIN focus must match PlanItem");
            }
            Long parentQuestionId = null;
            if (!expired && action == DecisionAction.FOLLOW_UP) {
                parentQuestionId = mainQuestionId(question, actualById);
                nextItem = facts.item(actualById.get(parentQuestionId).planItemId());
            }

            EvaluationCreated created =
                    evaluationApi.create(toEvaluationCommand(interviewId, questionId, answerId, evaluation));
            questionRepository.markAnswered(questionId, interviewId);
            if (!expired && (action == DecisionAction.FOLLOW_UP || action == DecisionAction.NEXT_QUESTION)) {
                questionRepository.create(QuestionCreateCommand.builder()
                        .interviewId(interviewId)
                        .sequenceNo(question.sequenceNo() + 1)
                        .parentQuestionId(parentQuestionId)
                        .type(action == DecisionAction.FOLLOW_UP ? QuestionType.FOLLOW_UP : QuestionType.MAIN)
                        .content(generatedQuestion.content())
                        .planItemId(nextItem.id())
                        .evaluationProfileId(question.evaluationProfileId())
                        .topicId(action == DecisionAction.FOLLOW_UP ? question.topicId() : null)
                        .build());
                interviewRepository.markWaitingAfterTurn(interviewId, expectedVersion);
            } else {
                interviewRepository.completeAfterTurn(interviewId, expectedVersion, LocalDateTime.now(clock));
            }
            return created;
        });
    }

    /** 在当前 Turn 内按动作构造各自独立的 QuestionGeneration Context。 */
    private QuestionProposal generateQuestion(TurnProcessingContext turn, ValidatedDecisionResult decision) {
        if (decision.action() == DecisionAction.FOLLOW_UP) {
            FollowUpQuestionGenerationContext context = FollowUpQuestionGenerationContext.builder()
                    .followUpTarget(decision.followUpTarget())
                    .currentQuestionContent(turn.question().content())
                    .currentAnswerContent(turn.answer().content())
                    .candidateName(turn.candidate().candidateName())
                    .resumeSummary(turn.candidate().resumeSummary())
                    .projects(turn.candidate().projects().stream()
                            .map(project -> new ProjectContext(project.name(), project.description()))
                            .toList())
                    .positionTitle(turn.position().title())
                    .positionDescription(turn.position().description())
                    .build();
            return new QuestionProposal(null, interviewAiApi.generateFollowUpQuestion(context));
        }
        PlanItem selected = turn.planFacts().requireUnused(decision.planItemId());
        MainQuestionGenerationContext context = MainQuestionGenerationContext.builder()
                .area(selected.area().name())
                .focus(selected.focus())
                .reason(selected.reason())
                .previousMainQuestions(turn.planFacts().previousMainQuestions())
                .candidateName(turn.candidate().candidateName())
                .resumeSummary(turn.candidate().resumeSummary())
                .projects(turn.candidate().projects().stream()
                        .map(project -> new ProjectContext(project.name(), project.description()))
                        .toList())
                .positionTitle(turn.position().title())
                .positionDescription(turn.position().description())
                .build();
        return new QuestionProposal(selected.focus(), interviewAiApi.generateMainQuestion(context));
    }

    /** 在当前 Turn 内根据 Evaluation 和业务事实生成 Decision。 */
    private ValidatedDecisionResult decideNext(TurnProcessingContext turn, ValidatedEvaluationResult evaluation) {
        if (evaluation == null) {
            throw new IllegalArgumentException("Decision 需要 Evaluation");
        }
        Long interviewId = turn.interview().id();
        Long questionId = turn.question().id();
        Long answerId = turn.answer().id();
        QuestionSnapshot question = turn.question();
        AnswerSnapshot answer = turn.answer();
        List<QuestionSnapshot> questions = turn.questions();
        Map<Long, QuestionSnapshot> byId =
                questions.stream().collect(Collectors.toMap(QuestionSnapshot::id, Function.identity()));
        Long mainId = mainQuestionId(question, byId);
        int mainCount = (int) questions.stream()
                .filter(value -> value.type() == QuestionType.MAIN)
                .count();
        int followUpCount = (int) questions.stream()
                .filter(value -> value.type() == QuestionType.FOLLOW_UP)
                .filter(value -> mainId.equals(mainQuestionId(value, byId)))
                .count();
        InterviewProgress progress = new InterviewProgress(
                mainCount,
                followUpCount,
                decisionLimits.maxMainQuestions(),
                decisionLimits.maxFollowUpsPerMainQuestion());
        DecisionContext context = DecisionContext.builder()
                .interviewId(interviewId)
                .questionId(questionId)
                .answerId(answerId)
                .questionContent(question.content())
                .answerContent(answer.content())
                .evaluationScore10(evaluation.overallScore10())
                .evaluationStrengths(evaluation.strengths().stream()
                        .map(value -> value.description())
                        .collect(Collectors.joining("; ")))
                .evaluationWeaknesses(evaluation.weaknesses().stream()
                        .map(value -> value.description())
                        .toList())
                .evaluationMissingPoints(evaluation.missingPoints().stream()
                        .map(value -> value.description())
                        .toList())
                .candidateName(turn.candidate().candidateName())
                .resumeSummary(turn.candidate().resumeSummary())
                .projects(turn.candidate().projects().stream()
                        .map(project -> new ProjectContext(project.name(), project.description()))
                        .toList())
                .positionTitle(turn.position().title())
                .positionDescription(turn.position().description())
                .unusedPlanItems(turn.planFacts().unused().stream()
                        .map(item -> new DecisionPlanItem(item.id(), item.area().name(), item.focus(), item.reason()))
                        .toList())
                .progress(progress)
                .allowedActions(allowedActionsCalculator.calculate(progress).stream()
                        .filter(action -> action != DecisionAction.FOLLOW_UP
                                || !evaluation.missingPoints().isEmpty())
                        .collect(Collectors.toSet()))
                .build();
        return interviewAiApi.decideNext(context);
    }

    private InterviewPlanFacts planFacts(Long interviewId, List<QuestionSnapshot> questions) {
        var plan = planRepository.findByInterviewId(interviewId);
        if (plan == null || !interviewId.equals(plan.interviewId()))
            throw new IllegalStateException("Interview Plan is missing or foreign");
        return new InterviewPlanFacts(plan, questions);
    }

    private Long mainQuestionId(QuestionSnapshot question, Map<Long, QuestionSnapshot> byId) {
        if (question == null || question.id() == null) {
            throw new IllegalStateException("Question identity is missing");
        }
        if (question.type() == QuestionType.MAIN) {
            if (question.parentQuestionId() != null) {
                throw new IllegalStateException("MAIN question must not have a parent");
            }
            return question.id();
        }
        if (question.type() != QuestionType.FOLLOW_UP) {
            throw new IllegalStateException("Unsupported question type");
        }
        QuestionSnapshot parent = byId.get(question.parentQuestionId());
        if (parent == null) {
            throw new IllegalStateException("FOLLOW_UP parent question is missing");
        }
        if (!Objects.equals(parent.interviewId(), question.interviewId())) {
            throw new IllegalStateException("FOLLOW_UP parent must belong to the same Interview");
        }
        if (parent.type() != QuestionType.MAIN || parent.parentQuestionId() != null) {
            throw new IllegalStateException("FOLLOW_UP parent must be a root MAIN question");
        }
        return parent.id();
    }

    private TurnProcessingContext loadTurnContext(
            Long interviewId, Long questionId, Long answerId, Long expectedVersion) {
        InterviewSnapshot interview = interviewRepository.findById(interviewId);
        requireInterviewState(
                interview,
                InterviewStatus.IN_PROGRESS,
                InterviewPhase.PROCESSING_TURN,
                expectedVersion == null ? interview.version() : expectedVersion);
        QuestionSnapshot question = questionRepository.findById(questionId);
        AnswerSnapshot answer = answerRepository.findById(answerId);
        if (!Objects.equals(question.interviewId(), interviewId)
                || !Objects.equals(answer.interviewId(), interviewId)
                || !Objects.equals(answer.questionId(), questionId)
                || question.status() != QuestionStatus.WAITING_ANSWER) {
            throw new BusinessException(InterviewErrorCode.INTERVIEW_STATE_CONFLICT);
        }
        List<QuestionSnapshot> questions = questionRepository.findByInterviewId(interviewId);
        Map<Long, QuestionSnapshot> byId =
                questions.stream().collect(Collectors.toMap(QuestionSnapshot::id, Function.identity()));
        if (!byId.containsKey(questionId)) {
            throw new IllegalStateException("Current question is missing from Interview question list");
        }
        questions.forEach(value -> mainQuestionId(value, byId));
        InterviewPlanFacts facts = planFacts(interviewId, questions);
        long mainCount = questions.stream()
                .filter(value -> value.type() == QuestionType.MAIN)
                .count();
        if (mainCount < decisionLimits.maxMainQuestions() && facts.unused().isEmpty())
            throw new IllegalStateException("Interview Plan exhausted before MAIN limit");
        EvaluationProfile profile = evaluationProfileApi.findById(question.evaluationProfileId());
        CandidateInterviewProfile candidate = candidateQueryApi.getInterviewProfile(interview.candidateId());
        PositionInterviewProfile position = positionQueryApi.getInterviewProfile(interview.positionId());
        if (profile == null || candidate == null || position == null) {
            throw new IllegalStateException("Turn business context is incomplete");
        }
        return new TurnProcessingContext(interview, question, answer, profile, candidate, position, questions, facts);
    }

    private AnswerSubmissionResponse processTurn(
            Long interviewId, Long questionId, Long answerId, Long expectedVersion, Instant startedAt) {
        RuntimeException firstFailure = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            String stage = "CONTEXT";
            try {
                TurnProcessingContext turn = loadTurnContext(interviewId, questionId, answerId, expectedVersion);
                stage = "EVALUATE";
                ValidatedEvaluationResult evaluation = evaluateOutsideTransaction(turn);
                if (evaluation == null) {
                    throw new IllegalArgumentException("Evaluator returned no result");
                }
                stage = "DECISION";
                ValidatedDecisionResult decision = decideNext(turn, evaluation);
                if (decision == null || decision.action() == null) {
                    throw new IllegalArgumentException("Decision returned no action");
                }
                QuestionProposal proposal = null;
                if (decision.action() != DecisionAction.END && !isExpired(turn.interview())) {
                    stage = "QUESTION_GENERATION";
                    proposal = generateQuestion(turn, decision);
                    if (proposal == null || proposal.question() == null) {
                        throw new IllegalArgumentException("QuestionGeneration returned no question");
                    }
                }
                stage = "T2";
                EvaluationCreated created = persistProcessedTurn(
                        interviewId,
                        questionId,
                        answerId,
                        expectedVersion,
                        evaluation,
                        decision,
                        proposal == null ? null : proposal.question(),
                        proposal == null ? null : proposal.focus(),
                        turn.planFacts().plan());
                log.info(
                        "Turn 成功 interviewId={} questionId={} answerId={} attempt={} stage={} action={} durationMs={}",
                        interviewId,
                        questionId,
                        answerId,
                        attempt,
                        stage,
                        decision.action(),
                        Duration.between(startedAt, Instant.now()).toMillis());
                return AnswerSubmissionResponse.from(
                        answerId, turn.answer().content(), created.evaluationId(), evaluation);
            } catch (RuntimeException exception) {
                boolean retryable = isAiStage(stage) && isRecoverableAiFailure(exception);
                log.warn(
                        "Turn 失败 interviewId={} questionId={} answerId={} attempt={} stage={} failureType={} errorCode={} validationReason={} durationMs={}",
                        interviewId,
                        questionId,
                        answerId,
                        attempt,
                        stage,
                        exception.getClass().getSimpleName(),
                        failureCode(exception, retryable),
                        evaluationValidationReason(exception),
                        Duration.between(startedAt, Instant.now()).toMillis());
                if (!retryable || attempt == 2) {
                    RuntimeException terminal = retryable || isAiStage(stage) ? mapAiFailure(exception) : exception;
                    if (firstFailure != null && terminal != firstFailure) {
                        terminal.addSuppressed(firstFailure);
                    }
                    throw terminal;
                }
                firstFailure = exception;
                try {
                    Thread.sleep(100);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    IllegalStateException terminal = new IllegalStateException("Turn retry interrupted", interrupted);
                    terminal.addSuppressed(firstFailure);
                    throw terminal;
                }
            }
        }
        throw new IllegalStateException("Turn attempt limit exceeded");
    }

    private boolean isAiStage(String stage) {
        return "EVALUATE".equals(stage) || "DECISION".equals(stage) || "QUESTION_GENERATION".equals(stage);
    }

    private String evaluationValidationReason(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof EvaluationValidationException) {
                String message = current.getMessage();
                if (message == null) {
                    return null;
                }
                String singleLine = message.replace("\r", "\\r").replace("\n", "\\n");
                return singleLine.length() <= VALIDATION_REASON_LOG_LIMIT
                        ? singleLine
                        : singleLine.substring(0, VALIDATION_REASON_LOG_LIMIT) + "... [truncated]";
            }
        }
        return null;
    }

    private Object failureCode(RuntimeException failure, boolean retryable) {
        if (failure instanceof BusinessException business) {
            return business.getErrorCode();
        }
        if (failure instanceof DataAccessException) {
            return CommonErrorCode.DB_ERROR;
        }
        return retryable ? aiErrorCode(failure) : CommonErrorCode.SYSTEM_ERROR;
    }

    private boolean isRecoverableAiFailure(Throwable failure) {
        if (failure instanceof BusinessException business) {
            return business.getErrorCode() == CommonErrorCode.AI_RESPONSE_INVALID
                    || business.getErrorCode() == CommonErrorCode.AI_SERVICE_TIMEOUT
                    || business.getErrorCode() == CommonErrorCode.AI_SERVICE_ERROR
                    || business.getErrorCode() == CommonErrorCode.RATE_LIMIT;
        }
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof DataAccessException) {
                return false;
            }
            if (current instanceof SocketTimeoutException
                    || current instanceof java.util.concurrent.TimeoutException
                    || current instanceof ConnectException
                    || current instanceof UnknownHostException
                    || current instanceof ResourceAccessException) {
                return true;
            }
            if (current instanceof RestClientResponseException response) {
                int status = response.getStatusCode().value();
                return status == 429 || status >= 500;
            }
            String name = current.getClass().getSimpleName();
            if (current instanceof EvaluationValidationException
                    || name.equals("DecisionValidationException")
                    || name.equals("QuestionGenerationValidationException")
                    || name.equals("TransientAiException")
                    || name.equals("ReadTimeoutException")
                    || name.equals("ConnectTimeoutException")) {
                return true;
            }
        }
        return false;
    }

    private CommonErrorCode aiErrorCode(Throwable failure) {
        if (failure instanceof BusinessException business && business.getErrorCode() instanceof CommonErrorCode code) {
            return code;
        }
        for (Throwable current = failure; current != null; current = current.getCause()) {
            String name = current.getClass().getSimpleName();
            if (current instanceof EvaluationValidationException
                    || name.equals("DecisionValidationException")
                    || name.equals("QuestionGenerationValidationException")) {
                return CommonErrorCode.AI_RESPONSE_INVALID;
            }
            if (current instanceof SocketTimeoutException
                    || current instanceof java.util.concurrent.TimeoutException
                    || name.equals("ReadTimeoutException")
                    || name.equals("ConnectTimeoutException")) {
                return CommonErrorCode.AI_SERVICE_TIMEOUT;
            }
            if (current instanceof RestClientResponseException response
                    && response.getStatusCode().value() == 429) {
                return CommonErrorCode.RATE_LIMIT;
            }
        }
        return CommonErrorCode.AI_SERVICE_ERROR;
    }

    private RuntimeException mapAiFailure(RuntimeException failure) {
        if (failure instanceof BusinessException || !isRecoverableAiFailure(failure)) {
            return failure;
        }
        BusinessException mapped = new BusinessException(aiErrorCode(failure));
        mapped.initCause(failure);
        return mapped;
    }

    public AnswerSubmissionResponse submitAnswer(Long interviewId, Long questionId, String content) {
        if (content == null || content.isBlank()) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "参数校验失败：content 不能为空");
        }
        Instant startedAt = Instant.now();
        AnswerTurn turn;
        try {
            do {
                turn = transactionTemplate.execute(status -> persistAnswer(interviewId, questionId, content));
                if (turn.expired()) {
                    // The T1 transaction made no writes. Converge in Task 4.2's transaction,
                    // then retry admission only if a competing transition won the CAS.
                    interviewApplicationService.requireNotExpiredWaiting(interviewId);
                }
            } while (turn.expired());
        } catch (ProcessingTurnCasFailedException failure) {
            throw processingTurnCasFailure(interviewId, failure);
        }
        return processTurn(interviewId, questionId, turn.answer().id(), turn.processingVersion(), startedAt);
    }

    /** 重跑失败 Turn；Answer 和 Interview version 均固定使用本次请求开始时的持久化事实。 */
    public AnswerSubmissionResponse retryTurn(Long interviewId) {
        InterviewSnapshot interview = interviewRepository.findById(interviewId);
        requireInterviewState(
                interview, InterviewStatus.IN_PROGRESS, InterviewPhase.PROCESSING_TURN, interview.version());
        List<QuestionSnapshot> waiting = questionRepository.findWaitingByInterviewId(interviewId);
        if (waiting.isEmpty()) {
            throw new BusinessException(InterviewErrorCode.QUESTION_NOT_FOUND);
        }
        if (waiting.size() != 1) {
            throw new BusinessException(InterviewErrorCode.INTERVIEW_STATE_CONFLICT);
        }
        QuestionSnapshot question = questionRepository.findLatestWaitingByInterviewId(interviewId);
        if (question == null || !Objects.equals(question.id(), waiting.get(0).id())) {
            throw new BusinessException(InterviewErrorCode.INTERVIEW_STATE_CONFLICT);
        }
        AnswerSnapshot answer = answerRepository.findByInterviewIdAndQuestionId(interviewId, question.id());
        if (!Objects.equals(answer.interviewId(), interviewId) || !Objects.equals(answer.questionId(), question.id())) {
            throw new BusinessException(InterviewErrorCode.INTERVIEW_STATE_CONFLICT);
        }
        return processTurn(interviewId, question.id(), answer.id(), interview.version(), Instant.now());
    }

    private AnswerTurn persistAnswer(Long interviewId, Long questionId, String content) {
        InterviewSnapshot interview = interviewRepository.findById(interviewId);
        requireInterviewState(
                interview, InterviewStatus.IN_PROGRESS, InterviewPhase.WAITING_ANSWER, interview.version());
        if (isExpired(interview)) {
            return AnswerTurn.expiredTurn();
        }
        QuestionSnapshot question = questionRepository.findById(questionId);
        if (!Objects.equals(question.interviewId(), interviewId)) {
            throw new BusinessException(InterviewErrorCode.QUESTION_INTERVIEW_MISMATCH);
        }
        if (question.status() != QuestionStatus.WAITING_ANSWER) {
            throw new BusinessException(InterviewErrorCode.QUESTION_STATE_CONFLICT);
        }
        AnswerSnapshot answer = answerRepository.save(interviewId, questionId, content);
        LocalDateTime admissionTime = LocalDateTime.now(clock);
        InterviewSnapshot processing =
                interviewRepository.markProcessingTurnBeforeDeadline(interviewId, interview.version(), admissionTime);
        if (processing == null) {
            throw new ProcessingTurnCasFailedException(interview.version(), admissionTime);
        }
        return new AnswerTurn(answer, processing.version());
    }

    private BusinessException processingTurnCasFailure(Long interviewId, ProcessingTurnCasFailedException failure) {
        InterviewSnapshot actual = transactionTemplate.execute(status -> interviewRepository.findById(interviewId));
        if (isExpectedWaitingState(actual, failure.expectedVersion()) && isExpired(actual, failure.admissionTime())) {
            if (interviewApplicationService.convergeExpiredWaiting(interviewId)) {
                return new BusinessException(InterviewErrorCode.INTERVIEW_EXPIRED);
            }
            actual = transactionTemplate.execute(status -> interviewRepository.findById(interviewId));
        }
        if (isExpectedWaitingState(actual, null) && !Objects.equals(actual.version(), failure.expectedVersion())) {
            return new BusinessException(InterviewErrorCode.INTERVIEW_VERSION_CONFLICT);
        }
        return new BusinessException(InterviewErrorCode.INTERVIEW_STATE_CONFLICT);
    }

    private boolean isExpectedWaitingState(InterviewSnapshot interview, Long expectedVersion) {
        return interview.status() == InterviewStatus.IN_PROGRESS
                && interview.currentPhase() == InterviewPhase.WAITING_ANSWER
                && (expectedVersion == null || Objects.equals(interview.version(), expectedVersion));
    }

    private boolean isExpired(InterviewSnapshot interview, LocalDateTime now) {
        return interview.expiresAt() != null && !now.isBefore(interview.expiresAt());
    }

    private ValidatedEvaluationResult evaluateOutsideTransaction(TurnProcessingContext turn) {
        EvaluationContext context = EvaluationContext.builder()
                .interviewId(turn.interview().id())
                .questionId(turn.question().id())
                .answerId(turn.answer().id())
                .profileId(turn.profile().id())
                .questionContent(turn.question().content())
                .answerContent(turn.answer().content())
                .candidateName(turn.candidate().candidateName())
                .resumeSummary(turn.candidate().resumeSummary())
                .projects(turn.candidate().projects().stream()
                        .map(project -> new ProjectContext(project.name(), project.description()))
                        .toList())
                .positionTitle(turn.position().title())
                .positionDescription(turn.position().description())
                .dimensions(turn.profile().dimensions().stream()
                        .map(this::toDimensionContext)
                        .toList())
                .build();
        return interviewAiApi.evaluateAnswer(context);
    }

    private EvaluationCommand toEvaluationCommand(
            Long interviewId, Long questionId, Long answerId, ValidatedEvaluationResult result) {
        List<DimensionScore> dimensions =
                result.dimensions().stream().map(this::toDimensionScore).toList();
        return EvaluationCommand.builder()
                .interviewId(interviewId)
                .questionId(questionId)
                .answerId(answerId)
                .profileId(result.profileId())
                .overallScore10(result.overallScore10())
                .overallScore100(result.overallScore100())
                .strengths(result.strengths().stream()
                        .map(value -> new Feedback(value.description(), value.relatedDimensionCodes()))
                        .toList())
                .weaknesses(result.weaknesses().stream()
                        .map(value -> new Feedback(value.description(), value.relatedDimensionCodes()))
                        .toList())
                .missingPoints(result.missingPoints().stream()
                        .map(value -> new MissingPoint(
                                value.description(),
                                value.importance().name(),
                                value.explanation(),
                                value.relatedDimensionCodes()))
                        .toList())
                .referenceAnswer(new ReferenceAnswer(
                        result.referenceAnswer().answerForm().name(),
                        result.referenceAnswer().keyPoints(),
                        result.referenceAnswer().content()))
                .promptVersion(result.promptVersion())
                .evaluatorVersion(result.evaluatorVersion())
                .model(result.model())
                .inputTokens(result.inputTokens())
                .outputTokens(result.outputTokens())
                .totalTokens(result.totalTokens())
                .latencyMs(result.latencyMs())
                .dimensions(dimensions)
                .build();
    }

    private DimensionScore toDimensionScore(ValidatedDimensionEvaluation dimension) {
        return new DimensionScore(
                dimension.dimensionCode(),
                dimension.dimensionName(),
                dimension.applicability().name(),
                dimension.score(),
                dimension.weight(),
                dimension.weightedScore(),
                dimension.scoreExplanation(),
                dimension.evidence().stream()
                        .map(value -> new Evidence(value.type().name(), value.quote(), value.explanation()))
                        .toList());
    }

    private EvaluationDimensionContext toDimensionContext(EvaluationDimension value) {
        return new EvaluationDimensionContext(
                value.code(),
                value.name(),
                value.evaluationCriteria(),
                value.scoreGuidance(),
                value.weight(),
                Boolean.TRUE.equals(value.naAllowed()),
                value.sortOrder());
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

    private boolean isExpired(InterviewSnapshot interview) {
        return isExpired(interview, LocalDateTime.now(clock));
    }

    private record AnswerTurn(AnswerSnapshot answer, Long processingVersion, boolean expired) {
        private AnswerTurn(AnswerSnapshot answer, Long processingVersion) {
            this(answer, processingVersion, false);
        }

        private static AnswerTurn expiredTurn() {
            return new AnswerTurn(null, null, true);
        }
    }

    private static final class ProcessingTurnCasFailedException extends RuntimeException {
        private final Long expectedVersion;
        private final LocalDateTime admissionTime;

        private ProcessingTurnCasFailedException(Long expectedVersion, LocalDateTime admissionTime) {
            this.expectedVersion = expectedVersion;
            this.admissionTime = admissionTime;
        }

        private Long expectedVersion() {
            return expectedVersion;
        }

        private LocalDateTime admissionTime() {
            return admissionTime;
        }
    }

    private record TurnProcessingContext(
            InterviewSnapshot interview,
            QuestionSnapshot question,
            AnswerSnapshot answer,
            EvaluationProfile profile,
            CandidateInterviewProfile candidate,
            PositionInterviewProfile position,
            List<QuestionSnapshot> questions,
            InterviewPlanFacts planFacts) {}

    private record QuestionProposal(String focus, GeneratedQuestion question) {}
}
