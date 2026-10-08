package com.linj6.interviewagent.ai.api.evaluation;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/** AI 输出的 Evaluation 提案；只有通过 Java 校验和确定性计算后才形成 ValidatedEvaluationResult。 */
public record EvaluationProposal(
        @JsonPropertyDescription(
                        "返回评价 Profile 中全部且仅这些维度，每个维度恰好一次；仅评价当前 Question 下当前 Answer 的实际质量，不与 Reference Answer 做知识点 diff。")
                List<DimensionEvaluationProposal> dimensionEvaluations,
        @JsonPropertyDescription("必须返回；已经回答且表现较好的内容，最多 3 条，可为空列表。每条只关联 APPLICABLE 评价维度。")
                List<FeedbackProposal> strengths,
        @JsonPropertyDescription(
                        "必须返回；当前 Answer 已实际暴露出的回答质量或技术问题；未验证、未提及或记不清不能自动推断为能力弱点；当时没做、没发生或没有专门方案是有效的历史事实回答，不能自动转化为能力 Weakness，最多 3 条，可为空列表。每条只关联 APPLICABLE 评价维度。")
                List<FeedbackProposal> weaknesses,
        @JsonPropertyDescription(
                        "必须返回；从当前 Question + Answer 得到依据：Question 明确要求但尚未获得的信息，或 Answer 主动展开后的关键未澄清点；不得仅依据参考回答、岗位要求或通用最佳实践生成，最多 5 条，可为空列表。每条只关联 APPLICABLE 评价维度。")
                List<MissingPointProposal> missingPoints,
        @JsonPropertyDescription(
                        "必须提供用于复盘当前 Question 的答案或回答框架；不是评分 checklist，Score / Dimension / Weakness / MissingPoint 不得通过与其做知识点 diff 得出。")
                ReferenceAnswerProposal referenceAnswer) {}
