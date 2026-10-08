# ADR-001：采用模块化单体 + Maven 多模块

**状态：[已确认]**

## Context

项目需要体现 Java 后端与 AI
工程能力。微服务会增加部署、治理、网络与开发复杂度，而 MVP 不需要。

## Decision

采用模块化单体 + Maven 多模块 + 分层架构 + 独立 AI
Orchestration；模块通过 Public API 协作。

## Consequences

保留模块边界并降低部署复杂度；未来拆微服务时需要重新处理通信、事务等边界。

## Alternative

MVP 不直接微服务化，不为了展示分布式而增加服务。
