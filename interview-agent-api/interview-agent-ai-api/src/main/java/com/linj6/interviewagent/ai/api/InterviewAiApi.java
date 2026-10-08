package com.linj6.interviewagent.ai.api;

import com.linj6.interviewagent.ai.api.decision.DecisionContext;
import com.linj6.interviewagent.ai.api.decision.ValidatedDecisionResult;
import com.linj6.interviewagent.ai.api.evaluation.EvaluationContext;
import com.linj6.interviewagent.ai.api.evaluation.ValidatedEvaluationResult;
import com.linj6.interviewagent.ai.api.focus.FocusProposal;
import com.linj6.interviewagent.ai.api.focus.FocusSelectionContext;
import com.linj6.interviewagent.ai.api.question.FollowUpQuestionGenerationContext;
import com.linj6.interviewagent.ai.api.question.GeneratedQuestion;
import com.linj6.interviewagent.ai.api.question.MainQuestionGenerationContext;

/** Interview 模块调用的 AI 能力入口。输入由调用方从业务事实构造；AI 输出经校验并被业务模块接纳后才成为业务事实。 */
public interface InterviewAiApi {
    /** 准备整场计划，解析并移除单次调用的临时引用。 */
    default com.linj6.interviewagent.ai.api.planner.ValidatedPlanResult preparePlan(
            com.linj6.interviewagent.ai.api.planner.PlannerContext context) {
        throw new IllegalStateException("Planner capability is unavailable");
    }

    /** 根据候选人与岗位资料选择一个新的 MAIN 考察目标。 */
    FocusProposal selectFocus(FocusSelectionContext context);

    /** 根据当前 Answer 与其绑定的 Evaluation Profile 生成并校验评价结果。 */
    ValidatedEvaluationResult evaluateAnswer(EvaluationContext context);

    /** 根据当前 Turn 的 Answer、Evaluation 和应用提供的限制选择后续业务动作。 */
    ValidatedDecisionResult decideNext(DecisionContext context);

    /** 围绕指定 MAIN focus 生成一个 MAIN 面试问题。 */
    GeneratedQuestion generateMainQuestion(MainQuestionGenerationContext context);

    /** 围绕已解析的 Missing Point 及当前问答生成一个 FOLLOW_UP 问题。 */
    GeneratedQuestion generateFollowUpQuestion(FollowUpQuestionGenerationContext context);
}
