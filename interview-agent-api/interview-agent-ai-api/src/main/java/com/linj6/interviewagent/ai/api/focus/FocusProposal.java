package com.linj6.interviewagent.ai.api.focus;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * Focus Selector 提出的 MAIN 问题链考察目标。
 * focus 只表示考察方向，不是候选人经历摘要，也不构成候选人事实来源；不得重新组合、补全或建立资料未明确支持的事实关系。
 *
 * @param focus 单一、具体且可转化为一道 MAIN 问题的目标；真实 AI 实现要求非空且最多 500 个 Unicode 码点。
 */
public record FocusProposal(
        @JsonPropertyDescription(
                        "非空、最多 500 个 Unicode 码点的单一 MAIN 问题链考察目标，可转化为一道 MAIN 问题；不得输出多个并列目标。focus 只表示考察方向，不是候选人经历摘要，也不构成候选人事实来源；不得重新组合、补全或建立资料未明确支持的事实关系。")
                String focus) {
    public static final int MAX_LENGTH = 500;
}
