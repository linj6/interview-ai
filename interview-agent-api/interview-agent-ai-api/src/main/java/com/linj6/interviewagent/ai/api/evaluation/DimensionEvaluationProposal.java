package com.linj6.interviewagent.ai.api.evaluation;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.math.BigDecimal;
import java.util.List;

public record DimensionEvaluationProposal(
        @JsonPropertyDescription("必须对应本次输入 Profile 中的一个 dimension code；全部 Profile 维度各返回一次，不得重复或遗漏。")
                String dimensionCode,
        @JsonPropertyDescription("APPLICABLE 表示具备客观评分条件；NOT_APPLICABLE 仅在 Profile 允许且确实无评分条件时使用，不能表示回答质量差。")
                Applicability applicability,
        @JsonPropertyDescription("APPLICABLE 时按 Rubric 给出 0 到 10、步长 0.5 的分数；NOT_APPLICABLE 时留空。") BigDecimal score,
        @JsonPropertyDescription(
                        "APPLICABLE 时说明当前 Question + Answer 的评分依据：ACCURACY 只评价已表达事实、因果与判断的正确性，未提及不能扣分；Accuracy 只能因已表达内容错误或明显不合理而扣分，必须指出具体问题，未提及、未展开或未深入讨论机制本身不能扣分；COMPLETENESS 只评价题目明确要求的信息覆盖；DEPTH 只沿已回答话题评价机制、依据、权衡、边界或结果，不横向扩展知识点；NOT_APPLICABLE 时说明客观上不适用的原因。必须非空。")
                String scoreExplanation,
        @JsonPropertyDescription("APPLICABLE 时提供 1 到 3 条仅基于当前回答的评分证据；NOT_APPLICABLE 时为空。")
                List<EvidenceProposal> evidence) {}
