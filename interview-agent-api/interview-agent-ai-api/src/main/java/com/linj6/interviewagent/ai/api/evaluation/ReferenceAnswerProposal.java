package com.linj6.interviewagent.ai.api.evaluation;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/**
 * 供用户学习的参考答案；不是唯一正确答案，也不参与当前评分。
 *
 * @param content 项目经历类答案只能使用可信候选人资料或给出待补充框架，不得虚构经历、数据、职责或效果。
 */
public record ReferenceAnswerProposal(
        @JsonPropertyDescription("知识、原理或通用方案题使用 SAMPLE_ANSWER；依赖候选人真实经历的问题使用 ANSWER_FRAMEWORK。") AnswerForm answerForm,
        @JsonPropertyDescription("必须提供，可为空列表；列出当前 Question 的复盘要点，并优先包含有 Question + Answer 依据的重要缺失信息；不定义评分 checklist。")
                List<String> keyPoints,
        @JsonPropertyDescription(
                        "供当前 Question 复盘的答案或回答框架；不得补造候选人未提供的具体实现、配置、指标或效果，通用建议保持为建议或可讨论方向；不得反向影响评价。ANSWER_FRAMEWORK 的 content 和 keyPoints 必须明确区分 Candidate/Profile/Answer 支持的历史事实、明确不知道/没做/没发生/记不清的信息、当前可讨论或改进的建议；后两类不得写成历史项目实现。未获上述来源支持的实现、配置、指标、机制或效果不得补入历史项目回答；建议明确标记为如果现在重新设计或可进一步考虑。")
                String content) {}
