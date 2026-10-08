package com.linj6.interviewagent.ai;

import com.linj6.interviewagent.ai.api.InterviewAiApi;
import com.linj6.interviewagent.ai.api.decision.DecisionContext;
import com.linj6.interviewagent.ai.api.decision.ValidatedDecisionResult;
import com.linj6.interviewagent.ai.api.evaluation.EvaluationContext;
import com.linj6.interviewagent.ai.api.evaluation.EvaluationProposal;
import com.linj6.interviewagent.ai.api.evaluation.ValidatedEvaluationResult;
import com.linj6.interviewagent.ai.api.focus.FocusProposal;
import com.linj6.interviewagent.ai.api.focus.FocusSelectionContext;
import com.linj6.interviewagent.ai.api.planner.PlannerContext;
import com.linj6.interviewagent.ai.api.planner.ValidatedPlanResult;
import com.linj6.interviewagent.ai.api.question.FollowUpQuestionGenerationContext;
import com.linj6.interviewagent.ai.api.question.GeneratedQuestion;
import com.linj6.interviewagent.ai.api.question.MainQuestionGenerationContext;
import com.linj6.interviewagent.ai.decision.DecisionProposal;
import com.linj6.interviewagent.ai.decision.DecisionProposalValidator;
import com.linj6.interviewagent.ai.decision.DecisionTargets;
import com.linj6.interviewagent.ai.evaluator.AnswerFragment;
import com.linj6.interviewagent.ai.evaluator.AnswerFragments;
import com.linj6.interviewagent.ai.evaluator.EvaluationProposalValidator;
import com.linj6.interviewagent.ai.evaluator.EvaluationValidationException;
import com.linj6.interviewagent.ai.planner.PlannerProposal;
import com.linj6.interviewagent.ai.planner.PlannerProposalValidator;
import com.linj6.interviewagent.ai.prompt.ProjectPromptFormatter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.ai", name = "real-llm-enabled", havingValue = "true")
public class InterviewAiService implements InterviewAiApi {
    private static final Logger log = LoggerFactory.getLogger(InterviewAiService.class);
    private static final String PROMPT_VERSION = "evaluation-v3";
    private static final String EVALUATOR_VERSION = "slice-2-v1";
    private static final int RESPONSE_LOG_LIMIT = 2048;
    private static final int INPUT_LOG_LIMIT = 8192;
    private static final java.util.regex.Pattern CREDENTIAL_PATTERN = java.util.regex.Pattern.compile(
            "(?i)(api[_ -]?key|authorization|bearer|password|client[_ -]?secret|access[_ -]?token|refresh[_ -]?token)([\\\"']?\\s*[:=]\\s*[\\\"']?\\s*(?:bearer\\s+)?|\\s+)([^\\s,;\\\"'}]+)");
    private final ChatClient chatClient;
    private final Resource plannerPrompt;
    private final Resource focusPrompt;
    private final Resource evaluationPrompt;
    private final Resource decisionPrompt;
    private final Resource mainQuestionGenerationPrompt;
    private final Resource followUpQuestionGenerationPrompt;
    private final EvaluationProposalValidator validator = new EvaluationProposalValidator();
    private final DecisionProposalValidator decisionValidator = new DecisionProposalValidator();
    private final String model;

    public InterviewAiService(
            ChatClient.Builder builder,
            ResourceLoader resourceLoader,
            @Value("${spring.ai.dashscope.chat.options.model:qwen-plus}") String model) {
        this.chatClient = builder.build();
        this.plannerPrompt = resourceLoader.getResource("classpath:prompts/planner-system.st");
        this.focusPrompt = resourceLoader.getResource("classpath:prompts/focus-selection-system.st");
        this.evaluationPrompt = resourceLoader.getResource("classpath:prompts/evaluation-system.st");
        this.decisionPrompt = resourceLoader.getResource("classpath:prompts/decision-system.st");
        this.mainQuestionGenerationPrompt =
                resourceLoader.getResource("classpath:prompts/main-question-generation-system.st");
        this.followUpQuestionGenerationPrompt =
                resourceLoader.getResource("classpath:prompts/follow-up-question-generation-system.st");
        this.model = model;
        log.info("已注册真实 InterviewAiApi，Focus Selector 与 Evaluator 将使用 DashScope ChatClient, model : {}", model);
    }

    @Override
    public ValidatedPlanResult preparePlan(PlannerContext context) {
        var proposal = chatClient
                .prompt()
                .system(spec -> spec.text(plannerPrompt))
                .user(
                        "allowedInterviewModes=%s\nallowedKnowledgeAreas=%s\nmode=%s\nfocusArea=%s\nmaxMainQuestions=%s\ncandidateName=%s\nresumeSummary=%s\nprojects=%s\npositionTitle=%s\npositionDescription=%s"
                                .formatted(
                                        context.allowedInterviewModes(),
                                        context.allowedKnowledgeAreas(),
                                        context.mode(),
                                        context.focusArea(),
                                        context.maxMainQuestions(),
                                        context.candidateName(),
                                        context.resumeSummary(),
                                        ProjectPromptFormatter.format(context.projects()),
                                        context.positionTitle(),
                                        context.positionDescription()))
                .call()
                .entity(PlannerProposal.class);
        try {
            return new PlannerProposalValidator().validate(context, proposal);
        } catch (IllegalArgumentException exception) {
            throw new EvaluationValidationException(exception.getMessage());
        }
    }

    @Override
    public ValidatedDecisionResult decideNext(DecisionContext context) {
        DecisionTargets targets = DecisionTargets.from(context);
        log.info(
                "Decision 调用开始 interviewId={} questionId={} answerId={}",
                context.interviewId(),
                context.questionId(),
                context.answerId());
        String userPrompt =
                """
                候选人：%s
                简历摘要：%s
                项目经历：%s
                岗位：%s
                岗位描述：%s
                当前问题：%s
                当前回答：%s
                评估得分（0-10）：%s
                优点：%s
                薄弱点：%s
                缺失点：%s
                追问目标（FOLLOW_UP 只能选择一个 targetRef）：
                %s
                未使用计划项（NEXT_QUESTION 必须选择一个 planItemRef）：
                %s
                主问题数：%s/%s
                当前 MAIN 问题链中的追问数：%s/%s
                允许动作：%s
                """
                        .formatted(
                                context.candidateName(),
                                context.resumeSummary(),
                                ProjectPromptFormatter.format(context.projects()),
                                context.positionTitle(),
                                context.positionDescription(),
                                context.questionContent(),
                                context.answerContent(),
                                context.evaluationScore10(),
                                context.evaluationStrengths(),
                                context.evaluationWeaknesses(),
                                context.evaluationMissingPoints(),
                                targets.render(),
                                targets.renderPlanItems(),
                                context.progress().mainQuestionCount(),
                                context.progress().maxMainQuestions(),
                                context.progress().followUpCount(),
                                context.progress().maxFollowUpsPerMainQuestion(),
                                context.allowedActions());
        logRequestInput(
                "Decision",
                context.interviewId(),
                context.questionId(),
                context.answerId(),
                decisionPrompt,
                userPrompt);
        DecisionProposal proposal = chatClient
                .prompt()
                .system(spec -> spec.text(decisionPrompt))
                .user(spec -> spec.text(
                                """
                        候选人：{candidateName}
                        简历摘要：{resumeSummary}
                        项目经历：{projects}
                        岗位：{positionTitle}
                        岗位描述：{positionDescription}
                        当前问题：{question}
                        当前回答：{answer}
                        评估得分（0-10）：{score}
                        优点：{strengths}
                        薄弱点：{weaknesses}
                        缺失点：{missingPoints}
                        追问目标（FOLLOW_UP 只能选择一个 targetRef）：
                        {targets}
                        未使用计划项（NEXT_QUESTION 必须选择一个 planItemRef）：
                        {planItems}
                        主问题数：{mainCount}/{maxMain}
                        当前 MAIN 问题链中的追问数：{followUpCount}/{maxFollowUps}
                        允许动作：{allowedActions}
                        """)
                        .param("candidateName", context.candidateName())
                        .param("resumeSummary", context.resumeSummary())
                        .param("projects", ProjectPromptFormatter.format(context.projects()))
                        .param("positionTitle", context.positionTitle())
                        .param("positionDescription", context.positionDescription())
                        .param("question", context.questionContent())
                        .param("answer", context.answerContent())
                        .param("score", context.evaluationScore10())
                        .param("strengths", context.evaluationStrengths())
                        .param("weaknesses", context.evaluationWeaknesses())
                        .param("missingPoints", context.evaluationMissingPoints())
                        .param("targets", targets.render())
                        .param("planItems", targets.renderPlanItems())
                        .param("mainCount", context.progress().mainQuestionCount())
                        .param("maxMain", context.progress().maxMainQuestions())
                        .param("followUpCount", context.progress().followUpCount())
                        .param("maxFollowUps", context.progress().maxFollowUpsPerMainQuestion())
                        .param("allowedActions", context.allowedActions()))
                .call()
                .entity(DecisionProposal.class);
        ValidatedDecisionResult result = decisionValidator.validate(context, proposal, targets);
        log.info(
                "Decision 调用成功 interviewId={} questionId={} action={} reason={} followUpTarget={}",
                context.interviewId(),
                context.questionId(),
                result.action(),
                result.reason(),
                result.followUpTarget());
        return result;
    }

    @Override
    public FocusProposal selectFocus(FocusSelectionContext context) {
        String userPrompt =
                """
                Candidate: %s
                Resume: %s
                Projects: %s
                Position: %s
                Position description: %s
                Already selected MAIN focuses (avoid duplicates): %s
                """
                        .formatted(
                                context.candidateName(),
                                context.resumeSummary(),
                                ProjectPromptFormatter.format(context.projects()),
                                context.positionTitle(),
                                context.positionDescription(),
                                context.selectedMainFocuses());
        logRequestInput("FocusSelector", null, null, null, focusPrompt, userPrompt);
        FocusProposal proposal = chatClient
                .prompt()
                .system(spec -> spec.text(focusPrompt))
                .user(userPrompt)
                .call()
                .entity(FocusProposal.class);
        if (proposal == null) {
            throw new EvaluationValidationException("FocusProposal must not be null");
        }
        if (proposal.focus() == null || proposal.focus().isBlank()) {
            throw new EvaluationValidationException("Focus must not be blank");
        }
        String focus = proposal.focus().trim();
        int focusLength = focus.codePointCount(0, focus.length());
        if (focusLength > FocusProposal.MAX_LENGTH) {
            throw new EvaluationValidationException("Focus exceeds maximum length of " + FocusProposal.MAX_LENGTH);
        }
        log.debug("AI focus selection completed: focus={}", focus);
        return new FocusProposal(focus);
    }

    @Override
    public GeneratedQuestion generateMainQuestion(MainQuestionGenerationContext context) {
        String direction = context.hasCompletePlanItem()
                ? "输入边界：完整 PlanItem 已提供。\n当前 PlanItem area：%s\n当前 PlanItem focus：%s\n当前 PlanItem reason：%s"
                        .formatted(context.area(), context.focus(), context.reason())
                : "输入边界：仅 focus 可用，完整 PlanItem 未提供；area、reason 不可用。\n考察 focus：%s".formatted(context.focus());
        String history = context.previousMainQuestions() == null
                ? "previousMainQuestions：未提供（历史 MAIN 不可用，不能据此认定没有历史或当前为首题）。"
                : "previousMainQuestions：%s（历史 MAIN 已提供）。".formatted(context.previousMainQuestions());
        String prompt =
                """
                %s
                %s
                候选人：%s
                简历摘要：%s
                项目经历：%s
                岗位：%s
                岗位描述：%s
                围绕已提供的考察方向生成一道具体 MAIN 问题。
                """
                        .formatted(
                                direction,
                                history,
                                context.candidateName(),
                                context.resumeSummary(),
                                ProjectPromptFormatter.format(context.projects()),
                                context.positionTitle(),
                                context.positionDescription());
        logRequestInput("MainQuestionGeneration", null, null, null, mainQuestionGenerationPrompt, prompt);
        GeneratedQuestion generated = chatClient
                .prompt()
                .system(spec -> spec.text(mainQuestionGenerationPrompt))
                .user(prompt)
                .call()
                .entity(GeneratedQuestion.class);
        log.debug("AI main question generation completed: question={}", generated.content());
        return generated;
    }

    @Override
    public GeneratedQuestion generateFollowUpQuestion(FollowUpQuestionGenerationContext context) {
        String prompt =
                """
                followUpTarget（唯一追问方向，不是候选人事实）: %s
                currentQuestion（考察上下文，不是 Candidate Fact Source）: %s
                currentAnswer（候选人本轮陈述的事实来源）: %s
                Candidate: %s
                Resume: %s
                Projects: %s
                Position: %s
                Position description: %s
                围绕唯一 target 生成一道追问；归因遵循当前 Answer 的确认、否定及假设边界。
                """
                        .formatted(
                                context.followUpTarget(),
                                context.currentQuestionContent(),
                                context.currentAnswerContent(),
                                context.candidateName(),
                                context.resumeSummary(),
                                ProjectPromptFormatter.format(context.projects()),
                                context.positionTitle(),
                                context.positionDescription());
        logRequestInput("FollowUpQuestionGeneration", null, null, null, followUpQuestionGenerationPrompt, prompt);
        GeneratedQuestion generated = chatClient
                .prompt()
                .system(spec -> spec.text(followUpQuestionGenerationPrompt))
                .user(prompt)
                .call()
                .entity(GeneratedQuestion.class);
        log.debug("AI follow-up question generation completed: question={}", generated.content());
        return generated;
    }

    @Override
    public ValidatedEvaluationResult evaluateAnswer(EvaluationContext context) {
        Instant startedAt = Instant.now();
        String dimensions = formatDimensions(context);
        List<AnswerFragment> answerFragments = AnswerFragments.from(context.answerContent());
        String formattedAnswerFragments = answerFragments.stream()
                .map(fragment -> fragment.evidenceRef() + ": " + fragment.text())
                .collect(java.util.stream.Collectors.joining("\n"));
        log.info(
                "Evaluator 调用开始 interviewId={} questionId={} answerId={}",
                context.interviewId(),
                context.questionId(),
                context.answerId());
        String userPrompt =
                """
                候选人：%s
                简历摘要：%s
                项目背景：%s
                目标岗位：%s
                岗位描述：%s
                问题：%s
                回答片段（答案按非空行切分；证据只能引用下列一个编号）：
                %s
                评估维度（此处有意不提供权重）：
                %s
                """
                        .formatted(
                                context.candidateName(),
                                context.resumeSummary(),
                                ProjectPromptFormatter.format(context.projects()),
                                context.positionTitle(),
                                context.positionDescription(),
                                context.questionContent(),
                                formattedAnswerFragments,
                                dimensions);
        logRequestInput(
                "Evaluator",
                context.interviewId(),
                context.questionId(),
                context.answerId(),
                evaluationPrompt,
                userPrompt);
        ResponseEntity<ChatResponse, EvaluationProposal> response = chatClient
                .prompt()
                .system(spec -> spec.text(evaluationPrompt))
                .user(spec -> spec.text(
                                """
                                候选人：{candidateName}
                                简历摘要：{resumeSummary}
                                项目背景：{projects}
                                目标岗位：{positionTitle}
                                岗位描述：{positionDescription}
                                问题：{question}
                                回答片段（答案按非空行切分；证据只能引用下列一个编号）：
                                {answerFragments}
                                评估维度（此处有意不提供权重）：
                                {dimensions}
                                """)
                        .param("candidateName", context.candidateName())
                        .param("resumeSummary", context.resumeSummary())
                        .param("projects", ProjectPromptFormatter.format(context.projects()))
                        .param("positionTitle", context.positionTitle())
                        .param("positionDescription", context.positionDescription())
                        .param("question", context.questionContent())
                        .param("answerFragments", formattedAnswerFragments)
                        .param("dimensions", dimensions))
                .call()
                .responseEntity(EvaluationProposal.class);
        long latencyMs = Duration.between(startedAt, Instant.now()).toMillis();
        Usage usage = response.response() == null || response.response().getMetadata() == null
                ? null
                : response.response().getMetadata().getUsage();
        String responseModel =
                response.response() == null || response.response().getMetadata() == null
                        ? null
                        : response.response().getMetadata().getModel();
        String actualModel = responseModel == null || responseModel.isBlank() ? model : responseModel;
        if (log.isDebugEnabled()) {
            String rawResponse = response.response() == null
                            || response.response().getResult() == null
                            || response.response().getResult().getOutput() == null
                    ? null
                    : response.response().getResult().getOutput().getText();
            log.debug(
                    "Evaluator AI response interviewId={} questionId={} answerId={} stage=EVALUATE model={} rawResponse={} parsedProposal={}",
                    context.interviewId(),
                    context.questionId(),
                    context.answerId(),
                    actualModel,
                    responsePreview(rawResponse),
                    responsePreview(String.valueOf(response.entity())));
        }
        ValidatedEvaluationResult result = validator.validateAndCalculate(
                context,
                answerFragments,
                response.entity(),
                PROMPT_VERSION,
                EVALUATOR_VERSION,
                actualModel,
                usage == null ? null : usage.getPromptTokens(),
                usage == null ? null : usage.getCompletionTokens(),
                usage == null ? null : usage.getTotalTokens(),
                latencyMs);
        log.info(
                "Evaluator 调用成功 interviewId={} questionId={} answerId={} latencyMs={}",
                context.interviewId(),
                context.questionId(),
                context.answerId(),
                latencyMs);
        return result;
    }

    private String responsePreview(String value) {
        if (value == null) {
            return null;
        }
        String singleLine = value.replace("\r", "\\r").replace("\n", "\\n");
        return singleLine.length() <= RESPONSE_LOG_LIMIT
                ? singleLine
                : singleLine.substring(0, RESPONSE_LOG_LIMIT) + "... [truncated]";
    }

    private void logRequestInput(
            String capability,
            Long interviewId,
            Long questionId,
            Long answerId,
            Resource systemPrompt,
            String userPrompt) {
        if (!log.isDebugEnabled()) {
            return;
        }
        log.debug(
                "LLM request input capability={} interviewId={} questionId={} answerId={} systemPrompt={} userPrompt={}",
                capability,
                interviewId,
                questionId,
                answerId,
                inputPreview(readPromptForLog(systemPrompt)),
                inputPreview(userPrompt));
    }

    private String readPromptForLog(Resource prompt) {
        try {
            return prompt.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            log.debug("Unable to read LLM system prompt for debug logging resource={}", prompt.getFilename());
            return "[system prompt unavailable for debug logging]";
        }
    }

    private String inputPreview(String value) {
        if (value == null) {
            return null;
        }
        String sanitized = CREDENTIAL_PATTERN.matcher(value).replaceAll("$1$2[REDACTED]");
        if (sanitized.length() <= INPUT_LOG_LIMIT) {
            return sanitized;
        }
        return sanitized.substring(0, INPUT_LOG_LIMIT) + "... [truncated]";
    }

    private String formatDimensions(EvaluationContext context) {
        StringBuilder value = new StringBuilder();
        context.dimensions().stream()
                .sorted(java.util.Comparator.comparingInt(dimension -> dimension.sortOrder()))
                .forEach(dimension -> value.append("- code=")
                        .append(dimension.code())
                        .append(", name=")
                        .append(dimension.name())
                        .append(", criteria=")
                        .append(dimension.evaluationCriteria())
                        .append(", scoreGuidance=")
                        .append(dimension.scoreGuidance())
                        .append(", naAllowed=")
                        .append(dimension.naAllowed())
                        .append('\n'));
        return value.toString();
    }
}
