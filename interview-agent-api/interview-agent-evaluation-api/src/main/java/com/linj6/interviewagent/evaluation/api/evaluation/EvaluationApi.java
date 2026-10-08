package com.linj6.interviewagent.evaluation.api.evaluation;

import java.util.List;

/** Evaluation 模块对外提供的正式评价创建与查询能力。 */
public interface EvaluationApi {
    EvaluationCreated create(EvaluationCommand command);

    List<EvaluationDTO> findByInterviewId(Long interviewId);
}
