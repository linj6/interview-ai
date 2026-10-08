package com.linj6.interviewagent.ai.api;

/** AI 输入中的单个项目事实，不合并不同项目的经历。 */
public record ProjectContext(String name, String description) {}
