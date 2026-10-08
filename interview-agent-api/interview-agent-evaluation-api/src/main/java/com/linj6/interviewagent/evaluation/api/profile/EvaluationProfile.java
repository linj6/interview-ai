package com.linj6.interviewagent.evaluation.api.profile;

import java.util.List;

/** 用于面试编排的不可变评价规则快照。 */
public record EvaluationProfile(Long id, String code, List<EvaluationDimension> dimensions) {}
