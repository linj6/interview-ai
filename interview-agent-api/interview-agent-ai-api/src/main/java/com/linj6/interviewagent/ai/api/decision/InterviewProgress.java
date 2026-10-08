package com.linj6.interviewagent.ai.api.decision;

/**
 * 应用提供的 Interview 问题数量与上限。
 *
 * @param mainQuestionCount 当前 Interview 已创建的 MAIN 数量。
 * @param followUpCount 当前所属 MAIN 问题链已创建的 FOLLOW_UP 数量，不是当前问题的直接子节点数。
 * @param maxMainQuestions MAIN 问题数量上限。
 * @param maxFollowUpsPerMainQuestion 每条 MAIN 问题链的 FOLLOW_UP 数量上限。
 */
public record InterviewProgress(
        int mainQuestionCount, int followUpCount, int maxMainQuestions, int maxFollowUpsPerMainQuestion) {}
