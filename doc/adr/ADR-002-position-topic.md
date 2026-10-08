# ADR-002：Position 不固定拥有 Topic

**状态：归档候选版**

## Context

Position 是面试目标，Topic 是一次 Interview 的关注方向。让 Position
固定拥有 Topic 会造成较强耦合。

## Decision

MVP：

``` text
Position ──→ Interview ←── Topic
```

Topic 属于 Interview 上下文。

## Consequences

同一 Position 可以产生不同 Topic 的 Interview。未来题库/RAG 阶段再评估
Topic 是否抽取。
