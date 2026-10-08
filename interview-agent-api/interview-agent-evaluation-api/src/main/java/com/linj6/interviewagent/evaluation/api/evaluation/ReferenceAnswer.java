package com.linj6.interviewagent.evaluation.api.evaluation;

import java.util.List;

/** 正式 Evaluation 的参考回答快照。 */
public record ReferenceAnswer(String answerForm, List<String> keyPoints, String content) {}
