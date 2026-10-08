# ADR-006：Interview 采用短生命周期 AI Workflow

**状态：[已确认]**

## Context

Interview 是可能持续较长时间、包含多轮人机交互的业务过程。如果让一个 Graph 跨越 `WAITING_ANSWER` 长时间挂起，需要引入 Graph Checkpoint、Interrupt / Resume、运行时状态恢复，并容易与 MySQL 业务状态形成双重状态来源。

V0.1 的目标是以业务事实为权威来源，控制 Agent 工程复杂度，同时保留动态评价、决策和追问能力。

## Decision

Interview 是长生命周期 Business Process；Graph / AI Workflow 是由业务事件触发的短生命周期执行单元。

V0.1 AI Capability 分为：

```text
Prepare Capability（Question Quality V1，待实现）
Opening Capability
Turn Workflow
```

Prepare 以事务外 Planner Proposal 和短事务原子持久化生成整场 Plan，不恢复旧 PrepareNode、不挂起 Graph。并发可以重复计算，但只有一个 Plan 成为正式事实，失败不持久化 Plan。Opening 基于已准备 Plan 的 initial PlanItem 负责第一题，V0.1 不强制 Graph 化；Turn Workflow 使用 Graph 执行 `Evaluate → Decision → Conditional Edge → QuestionGeneration`。V0.1 不生成 Interview-level AI Report；完整复盘基于已持久化业务事实动态组装，不属于 AI Workflow。

Graph 不跨越 `WAITING_ANSWER`。用户 Answer 持久化后触发新的 Turn Workflow。

V0.1 不建设独立 Graph Checkpoint。GraphState 仅是单次 Workflow Runtime State；恢复依据 MySQL Business Facts、`status/current_phase`、Version 与幂等机制重建 Context 并重新执行。

一次 Turn 使用：

```text
T1 Persist Answer
    ↓
AI Workflow
    ↓
T2 Atomic Commit TurnResult
```

T2 原子提交 Evaluation + Next Question，或 Evaluation + Interview Completion。

Answer 在 T1 成功后即为不可变正式业务事实，Interview 处于
`IN_PROGRESS + PROCESSING_TURN`。此后的 Evaluate、Decision、
QuestionGeneration 或 T2 任一环节失败，均不回退为 `WAITING_ANSWER`；
在 T2 成功前形成的 Evaluation、Decision 和 Generated Question 仅为运行时结果，
允许丢弃，并从 MySQL 中的正式业务事实重新执行完整 Turn。

Slice 3 MVP 在首次失败后自动完整重试一次；第二次仍失败则保留
`PROCESSING_TURN`，后续由前端提供的“重试”入口再次触发完整 Turn。MVP 不设计
后台恢复任务、Node Checkpoint 或通用 Retry Framework。

Pause / Resume 属于 Interview Domain；V0.1 不引入 Graph HITL / Interrupt / Resume。Question、Evaluation、Decision 在 V0.1 均不以 Streaming 作为正式业务交付方式。完整复盘同样一次性读取并返回，不使用 Streaming。

## Consequences

- MySQL Business Facts 保持唯一业务恢复来源，避免与 Graph Checkpoint 形成双状态源。
- Graph Runtime 和未持久化 AI Result 可以丢弃并重算。
- 需要业务唯一约束、Expected State、Version / Optimistic Locking 保证重复执行时只有一个结果成为正式业务事实。
- LLM 调用不处于长数据库事务中。
- T2 成功是 Turn 回到 `WAITING_ANSWER` 或进入 `COMPLETED` 的唯一稳定出口；失败不撤销
  已持久化的 Answer，也不把 Interview 伪装为可再次提交同一 Answer 的等待状态。
- 不支持精确恢复到某个 Graph Node；V0.1 的可靠性目标是从业务事实安全重执行。
- 未来当 Workflow 演化为长时间运行、多 Tool、多 Agent、Deep Research 或真正需要中断并恢复同一 Workflow 时，再重新评估 Durable Graph Checkpoint / HITL。
