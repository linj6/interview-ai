package com.linj6.interviewagent.ai.api.evaluation;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

public record MissingPointProposal(
        @JsonPropertyDescription(
                        "Question 明确要求但尚未获得的信息，或 Answer 主动展开后的关键未澄清点；必须有当前 Question + Answer 依据；明确没做或没发生的事实不是仍未回答的历史实现，不得要求补出不存在的方案。")
                String description,
        @JsonPropertyDescription("该缺失信息对回答当前 Question 或澄清 Answer 的重要程度：HIGH 必须覆盖，MEDIUM 应尽量覆盖，LOW 可选择性补充。")
                Importance importance,
        @JsonPropertyDescription(
                        "指出 Question 要求或 Answer 展开的依据及信息缺失原因；不知道、没做、没发生或记不清不能推断为能力不足；description / explanation 保持历史事实边界，如果现在重新设计或现在会考虑的方案只代表当前思考，不得反推为历史项目应该存在的实现。")
                String explanation,
        @JsonPropertyDescription("至少一个不重复的 APPLICABLE Profile dimension code。") List<String> relatedDimensionCodes) {}
