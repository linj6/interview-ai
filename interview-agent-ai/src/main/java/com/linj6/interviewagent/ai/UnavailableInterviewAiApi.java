package com.linj6.interviewagent.ai;

import com.linj6.interviewagent.ai.api.InterviewAiApi;
import com.linj6.interviewagent.ai.api.decision.DecisionContext;
import com.linj6.interviewagent.ai.api.decision.ValidatedDecisionResult;
import com.linj6.interviewagent.ai.api.evaluation.EvaluationContext;
import com.linj6.interviewagent.ai.api.evaluation.ValidatedEvaluationResult;
import com.linj6.interviewagent.ai.api.focus.FocusProposal;
import com.linj6.interviewagent.ai.api.focus.FocusSelectionContext;
import com.linj6.interviewagent.ai.api.question.FollowUpQuestionGenerationContext;
import com.linj6.interviewagent.ai.api.question.GeneratedQuestion;
import com.linj6.interviewagent.ai.api.question.MainQuestionGenerationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.ai", name = "real-llm-enabled", havingValue = "false", matchIfMissing = true)
public class UnavailableInterviewAiApi implements InterviewAiApi {
    private static final Logger log = LoggerFactory.getLogger(UnavailableInterviewAiApi.class);

    public UnavailableInterviewAiApi() {
        log.info("已注册安全兜底 InterviewAiApi，当前未启用真实 LLM");
    }

    @Override
    public FocusProposal selectFocus(FocusSelectionContext context) {
        throw unavailable();
    }

    @Override
    public ValidatedEvaluationResult evaluateAnswer(EvaluationContext context) {
        throw unavailable();
    }

    @Override
    public ValidatedDecisionResult decideNext(DecisionContext context) {
        throw unavailable();
    }

    @Override
    public GeneratedQuestion generateMainQuestion(MainQuestionGenerationContext context) {
        throw unavailable();
    }

    @Override
    public GeneratedQuestion generateFollowUpQuestion(FollowUpQuestionGenerationContext context) {
        throw unavailable();
    }

    private IllegalStateException unavailable() {
        return new IllegalStateException("未配置可用的 LLM；请启用 app.ai.real-llm-enabled 并提供 AI_DASHSCOPE_API_KEY 进行人工验收。");
    }
}
