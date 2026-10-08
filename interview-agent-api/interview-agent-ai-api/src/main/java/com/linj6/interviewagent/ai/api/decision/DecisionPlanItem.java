package com.linj6.interviewagent.ai.api.decision;

/** 当前 Interview 的未使用正式计划项；不包含 AI 临时引用。 */
public record DecisionPlanItem(Long id, String area, String focus, String reason) {}
