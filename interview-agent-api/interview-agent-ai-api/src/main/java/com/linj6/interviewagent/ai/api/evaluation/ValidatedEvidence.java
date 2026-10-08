package com.linj6.interviewagent.ai.api.evaluation;

/**
 * Java 将 Evaluator 内部片段引用解析后的证据。
 *
 * @param quote ANSWER_QUOTE 时为当前 Answer 对应非空行片段的 trim 后原文；OVERALL_OBSERVATION 时为 null。
 */
public record ValidatedEvidence(EvidenceType type, String quote, String explanation) {}
