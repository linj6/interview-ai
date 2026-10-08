package com.linj6.interviewagent.evaluation.api.profile;

/** Evaluation 模块对外提供的评价规则查询能力。 */
public interface EvaluationProfileApi {
    EvaluationProfile findById(Long profileId);

    EvaluationProfile findByCode(String profileCode);
}
