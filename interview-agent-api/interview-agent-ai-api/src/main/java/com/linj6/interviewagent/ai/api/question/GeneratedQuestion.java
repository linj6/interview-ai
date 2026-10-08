package com.linj6.interviewagent.ai.api.question;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * Question Generator 输出的问题正文；真实 AI 实现要求非空，并在构造时去除首尾空白。
 * 候选人经历前提及事实关系必须由允许的事实来源明确支持：MAIN 使用 Candidate/Profile，FOLLOW_UP 还可使用当前 Answer 的候选人本轮自述。
 * 不得跨场景组合问题、措施或结果；未提供的实现细节应开放询问，不得作为既定事实预设。
 *
 * @param content MAIN 应围绕一个考察目标；FOLLOW_UP 应围绕一个追问目标，由调用方按对应流程持久化。
 */
public record GeneratedQuestion(
        @JsonPropertyDescription(
                        "MAIN 只聚焦一个核心考察目标，最多附带一个紧密相关的澄清或展开问题；不得罗列多个可独立回答的子问题，也不得预先穷举适合后续 FOLLOW_UP 深挖的问题。FOLLOW_UP 每次只深入当前最值得追问的一个点。候选人经历前提及事实关系必须由允许的事实来源明确支持：MAIN 使用 Candidate/Profile，FOLLOW_UP 还可使用当前 Answer 的候选人本轮自述。不得跨场景组合问题、措施或结果；未提供的实现细节应开放询问，不得作为既定事实预设。")
                String content) {
    public GeneratedQuestion {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("GeneratedQuestion content 不能为空");
        }
        content = content.trim();
    }
}
