package com.linj6.interviewagent.ai.api.evaluation;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.Optional;

/** AI 输出的单条评分证据；evidenceRef 仅是 Evaluator 内部引用，Validated Result 中会解析为 Answer 原文或 null。 */
public record EvidenceProposal(
        @JsonPropertyDescription("ANSWER_QUOTE 表示引用当前回答中的一个非空行片段；OVERALL_OBSERVATION 表示对当前回答整体表现的观察，不引用原文。")
                EvidenceType type,
        // Spring AI 的运行时 Schema 需允许 null；类型与引用有效性仍由 EvaluationProposalValidator 校验。
        @JsonPropertyDescription(
                        "ANSWER_QUOTE 必须引用回答片段列表中存在的一个 evidenceRef（如 E1）；每条证据最多引用一个片段。OVERALL_OBSERVATION 的 evidenceRef 必须为 null。")
                Optional<String> evidenceRef,
        @JsonPropertyDescription("必须非空，面向用户解释该证据如何支持当前维度评分；不得包含模型内部推理过程。") String explanation) {
    public EvidenceProposal {
        evidenceRef = evidenceRef == null ? Optional.empty() : evidenceRef;
    }
}
