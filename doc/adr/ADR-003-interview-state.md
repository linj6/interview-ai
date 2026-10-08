# ADR-003：Interview 使用 status + current_phase

**状态：[已确认]**

## Context

Interview 同时存在整体业务生命周期和可恢复的业务执行阶段，一个字段无法清晰表达两者。

早期设计曾将 `current_phase` 定义为 AI Workflow / Graph 的执行位置，并使用 `PREPARING / GENERATING_QUESTION / WAITING_ANSWER / EVALUATING / DECIDING / COMPLETING`。

随着 V0.1 确认采用“短生命周期 Graph + 不持久化 Graph Checkpoint + 基于 Business Facts 重建 Workflow”的架构，Graph 内部 Node 位置不再具有业务恢复价值，因此需要收敛 `current_phase` 语义。

## Decision

`status` 表示 Interview Lifecycle：

```text
CREATED
IN_PROGRESS
PAUSED
COMPLETED
```

`current_phase` 表示 Recoverable Business Execution Phase：

```text
OPENING
WAITING_ANSWER
PROCESSING_TURN
```

典型流转：

```text
CREATED
  ↓ start
IN_PROGRESS + OPENING
  ↓ first question persisted
IN_PROGRESS + WAITING_ANSWER
  ↓ answer persisted
IN_PROGRESS + PROCESSING_TURN
  ├─ NEXT / FOLLOW_UP → IN_PROGRESS + WAITING_ANSWER
  └─ END              → COMPLETED + phase=null
```

T1 已保存 Answer 后，`PROCESSING_TURN` 持续到 T2 成功。Evaluate、Decision、
QuestionGeneration 或 T2 失败不会把它恢复为 `WAITING_ANSWER`；恢复时基于 MySQL
正式业务事实重新执行完整 Turn。

`PAUSED` 保留原 `current_phase`。V0.1 仅允许在 `WAITING_ANSWER` 稳定业务边界暂停；Resume 后继续原 Question。

`COMPLETED` 为终态，`current_phase = null`。V0.1 完整复盘是已完成 Interview 的只读查询视图，不引入 `REPORTING` 或其他复盘生命周期状态。

Interview 同时记录不可变生命周期时间事实：用户 Start 成功时写入 `started_at`；首次进入 `COMPLETED` 时在同一原子状态迁移中写入 `completed_at`。

原 `PREPARING / GENERATING_QUESTION / EVALUATING / DECIDING / COMPLETING` 被本次决策替代，不再作为 V0.1 正式阶段。

MVP 不使用 `INTERRUPTED / FAILED / ASKING`。

Question Quality V1 补充（已确认，待实现）：Create → Prepare → Start 中 Prepare 成功仍是 `CREATED + current_phase=null`；准备事实由 Plan 是否存在表达，不引入 `PREPARING/PREPARED`。Start 要求已有 Plan；原生命周期与 Opening/Turn 恢复边界不变，详见 [领域模型](../02-architecture/domain-model.md)。

## Consequences

实现使用 InterviewStatus、InterviewPhase 和 QuestionStatus；枚举名是数据库 VARCHAR
的持久化值。所有状态更新校验期望 status、phase 与 version，并检查影响行数。

- `current_phase` 不再充当 Graph Node Checkpoint。
- 服务恢复依据 MySQL Business Facts、`status/current_phase`、版本和幂等机制重新执行 Opening / Turn Workflow。
- Graph 内部 Evaluate / Decision / QuestionGeneration 的瞬时位置无需持久化。
- Pause / Resume 保持为 Interview Domain 能力，不引入 Graph HITL / Interrupt / Resume。
- 使用乐观锁 / Version / Expected State 处理并发，不跨 LLM 调用持有数据库事务。
