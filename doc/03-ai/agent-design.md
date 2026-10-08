# Interview Agent Agent / Workflow 设计

**版本：V1.1**  
**状态：[已确认]**

## 定位

Interview 是长生命周期业务对象；AI Workflow / Graph 是由业务事件触发的短生命周期执行单元。V0.1 不使用一个长时间挂起、跨越用户等待阶段的 InterviewGraph。

核心原则：

```text
Business Event
     ↓
Application
     ↓
Load Business Facts
     ↓
Build Business Input
     ↓
AI Context Builder
     ↓
AI Context / GraphState
     ↓
AI Workflow / Graph
     ↓
AI Proposal / Result
     ↓
Application + Domain Validation
     ↓
Persist Business Facts
```

Graph 不跨越 `WAITING_ANSWER`。用户回答是新的业务事件，会触发新的 Turn Workflow。

## V0.1 AI Capability 划分

```text
InterviewAiApi
├── Prepare Capability → Planner（Question Quality V1）
├── Opening Capability → MAIN Generator（基于 initial PlanItem）
└── Turn Workflow → Turn Graph
```

- Prepare Capability（Question Quality V1）：Planner 生成整场 Plan Proposal，Java 校验后由 Application 原子持久化；不新增 Graph PrepareNode。
- Opening Capability：生成第一道正式 Question。V0.1 不强制使用 Graph；若未来 Opening 演化为多步骤、分支流程，再考虑 Graph 化。
- Turn Workflow：处理一次已提交 Answer，是 V0.1 主要使用 Graph 的 Workflow。

`InterviewAiApi` 保持 Workflow 粒度 Capability。Evaluator、DecisionMaker、QuestionGenerator、Graph Node 等细粒度能力属于 AI 模块内部实现。

## Turn Workflow

Turn 从“Answer 已持久化成为业务事实”开始，Graph 不负责接收或保存用户 Answer。

```text
Answer 已持久化
      ↓
Build Turn Context
      ↓
┌────────── Turn Graph ──────────┐
│        EvaluateNode            │
│             ↓                  │
│        DecisionNode            │
│             ↓                  │
│      Conditional Edge          │
│       ┌─────┼─────┐            │
│       ↓     ↓     ↓            │
│ FOLLOW_UP  NEXT   END           │
│       └──┬──┘                  │
│          ↓                     │
│ QuestionGenerationNode         │
└───────────┬────────────────────┘
            ↓
        TurnResult
            ↓
Application + Domain
```

### EvaluateNode

只负责评价当前 Question / Answer，输出 Evaluation Proposal。LLM 负责结构化、多维评价；Java 负责校验和确定性总分计算。EvaluateNode 不决定下一步，也不生成问题。

### DecisionNode

根据当前 Evaluation 和 Interview 整体上下文决定下一步。V0.1 核心 Action：

```text
FOLLOW_UP
NEXT_QUESTION
END
```

确定性业务约束先形成 Allowed Actions / Constraints，再由 AI 在允许范围内做策略决策。Decision 仍是 Proposal，不能直接修改 Interview 状态。

Decision 是 Interview AI 提供的能力。Application 负责加载业务事实、计算 Interview
Progress 与 AllowedActions，并构造完整 `DecisionContext`；AI 模块只消费 Context，
不查询 Repository，也不修改 Interview 状态。最小链路为：

```text
DecisionContext → LLM → DecisionProposal → Java Validation → ValidatedDecisionResult
```

Decision 的 AI 内部 `DecisionProposal` 输出 `action`、`reason`、`targetRef` 与 V1 的 `planItemRef`；
该 Proposal 和 `targetRef` 不进入 AI Module Public API、Domain、持久化或 HTTP API。
每次 Decision 调用的追问目标引用只由 Evaluation 的 missingPoints 按顺序生成 `M1`、`M2`……；weaknesses
保留在 Decision 上下文中，仅辅助决策，不生成 targetRef。missingPoints 为空时不得选择
FOLLOW_UP。同一个调用内构造的 target 集合同时用于 Prompt 展示和 Java Validator/Resolver。
FOLLOW_UP 必须返回其中恰好一个存在的 M* targetRef；Java 将 targetRef 严格解析为对应的原始
description，作为 `ValidatedDecisionResult.followUpTarget`。null、blank、格式不合法或不存在
的引用均为 Validation Failure，不做纠错或多目标解析。NEXT_QUESTION 不输出 focus 或 targetRef，必须通过 `planItemRef` 选择未使用 PlanItem；Java 严格解析为真实 PlanItem ID。FOLLOW_UP 继续使用 Evaluation `targetRef`，不提供 planItemRef；END 的 reason 非 blank，两个引用均为空。AI 临时引用不得进入 Module Public API、业务 Domain、数据库或 HTTP API。

选择 FOLLOW_UP 还须满足 [Decision Information Gain / Closed Target Contract](prompt-design.md#decision-information-gain--closed-target)：当前 target 内仍有尚未澄清且候选人可能回答的信息；missingPoint 不自动构成继续追问的理由。事实范围关闭与部分关闭由 Decision Prompt 判断，不改变 targetRef 或 Java Validator/Resolver。

Interview Progress 使用独立的 `maxMainQuestions` 与
`maxFollowUpsPerMainQuestion` 限制，第一版由简单 Spring 应用配置提供，不建设数据库
Interview Configuration 模型。`followUpCount` 是当前所属 MAIN 问题链已经创建的
FOLLOW_UP 数量，不是当前 Question 的直接子节点数量。AllowedActions 规则为：

| MAIN 是否达上限 | FOLLOW_UP 是否达上限 | AllowedActions |
|---|---|---|
| 否 | 否 | `FOLLOW_UP` / `NEXT_QUESTION` |
| 否 | 是 | `NEXT_QUESTION` |
| 是 | 否 | `FOLLOW_UP` / `END` |
| 是 | 是 | `END` |

因此 Slice 3 MVP 不允许 LLM 在 MAIN 未达上限时主动返回 `END`。`FOLLOW_UP` 由 targetRef
唯一确定下一道 Question 要考察的目标。V1 的 `NEXT_QUESTION` 从未使用 PlanItem 选择，替代运行中 Focus Selector 调用；QuestionGeneration 必须围绕选中的 PlanItem 生成 MAIN。MAIN 未达上限但 PlanItem 耗尽是数据一致性异常，不能转成 END。

### Conditional Edge

Conditional Edge 只根据 Decision Action 路由，不承载第二套复杂业务或 AI 决策逻辑。

### QuestionGenerationNode

统一生成 MAIN / FOLLOW_UP Question。它按当前 PlanItem 或已解析追问目标生成问题，
不重新判断是否应该追问：

```text
FOLLOW_UP     → Question(type=FOLLOW_UP, parent=所属 MAIN Question)
NEXT_QUESTION → Question(type=MAIN)
```

独立 `FollowUpNode` 删除。

FOLLOW_UP 内容仍基于当前 Question / Answer / Evaluation 生成；`parent_question_id` 表达
所属 MAIN，而非本次被回答的题目。连续追问均直接关联同一个 MAIN，保持两层关系。

<a id="planner-与引用边界question-quality-v1已确认待实现"></a>

### Planner 与引用边界（Question Quality V1，[已确认]）

Planner 在 Prepare 中生成 `area / focus / reason` 与 initial PlanItem 选择；数量规则见 [领域模型](../02-architecture/domain-model.md#question-quality-v1plan-与准备流程已确认待实现)。P1、P2 等临时引用只在单次 AI 调用内有效，由 Java Validator 解析。持久化分配真实 PlanItem ID，并保存 initial PlanItem ID，不保存临时引用。Decision 每次调用为未使用 PlanItem 构建自己的临时引用集合，Prompt 与 Validator 必须共享同一映射；不得引用其他调用或已使用项。

Prepare 失败、并发与短事务边界见 [数据模型](../04-data/data-model.md#question-quality-v1-持久化-contract已确认待新增-migration)。Create/Prepare/Start 是独立业务用例，不是跨用户等待挂起的 Graph；Turn T1/T2、恢复与 Evaluation 确定性评分边界保持。

MAIN Generator 的输入边界见 [Context 设计](context-design.md#question-quality-v1-main-generator-context)。FOLLOW_UP 继承所属 MAIN 的同一 PlanItem，继续围绕解析后的单一 Evaluation target 生成。

Task 5 接入规则：Skip 不执行 Evaluation、Decision 或 Focus Selector；未达到 MAIN 上限时，沿当前 Plan 的既有顺序选择第一个未使用 PlanItem 生成 MAIN。现有持久化按 Java 顺序分配的真实 PlanItem ID 升序恢复 Plan 顺序，不新增排序字段或 Migration。当前 MAIN 在生成时已消耗其 PlanItem，Skip 不释放关联；跳过 FOLLOW_UP 同样不释放所属 MAIN 的 PlanItem。达到 MAIN 上限时沿用现有无 AI 完成规则；未达上限却无未使用项为数据一致性异常，不提前 END。

Turn 与 Skip 在事务外读取 Plan 和 MAIN 历史、执行 AI；对应提交短事务重新读取 Interview、Plan 聚合及 Question 关联，校验状态/version、Plan 归属、MAIN 唯一使用和 FOLLOW_UP 继承。Turn/Skip 的正常调用还比较 AI 前后的完整 Plan；校验失败不提交新的 PlanItem 使用事实。Turn T1 已保存 Answer 的事实与既有自动/手动完整重试、T2 原子性保持。


### Node 与 LLM Call

EvaluateNode 与 DecisionNode 在架构职责上保持独立。Node 边界不等于物理 LLM Call 边界；Phase 3 可根据性能、延迟和 Token 成本优化底层调用，但不得破坏职责语义。

## 删除的旧 Node

- `PrepareNode`：删除。业务数据获取属于 Application / Module API；AI Context 选择、裁剪、组织属于 Context Builder。
- `FollowUpNode`：删除。追问统一由 QuestionGenerationNode 生成。
- `EndNode`：删除。DecisionNode 仅返回 END Proposal，Application + Domain 校验并执行 Interview Completion。

## Business Input / AI Context / GraphState

必须区分：

```text
Business Input
      ↓
AI Context
      ↓
GraphState
```

- Business Input：业务层提供的稳定业务数据。
- AI Context：Context Builder 面向当前 AI Task 进行选择、裁剪、组织、摘要和 Token Budget 处理后的上下文。
- GraphState：单次短生命周期 Workflow 的运行时状态，不是业务数据库副本，也不是持久化恢复源。

AI Context Builder 可位于 AI 模块，但不直接查询 Candidate / Position / Interview Repository，而是消费业务层提供的 Business Input。

## Graph Checkpoint 与恢复

V0.1 不建设独立 Graph Checkpoint。

恢复目标不是精确恢复到某个 Node，而是：

> 根据 MySQL Business Facts 重建 Context / GraphState，安全重新执行 Workflow，并避免产生重复业务事实。

Graph Runtime、GraphState 和尚未持久化的 Evaluation / Decision / Question Proposal 都允许丢弃和重算。只有经过业务校验并成功持久化的结果才是正式 Business Fact。

T1 成功后 Answer 已是不可变正式业务事实，Interview 保持
`IN_PROGRESS + PROCESSING_TURN`，直到 T2 成功。Evaluate、Decision、
QuestionGeneration 或 T2 失败时，不回退到 `WAITING_ANSWER`；未完成 T2 的
Evaluation / Decision / Generated Question 丢弃后，从 MySQL 正式业务事实重建并重新
执行完整 Turn。Slice 3 MVP 首次失败自动完整重试一次，第二次失败则保持
`PROCESSING_TURN`，由后续前端“重试”再次触发；不设计后台恢复任务。

## Turn 事务、失败恢复与幂等

一次 Turn 采用：

```text
T1：保存 Answer
      ↓
AI Workflow / Graph
      ↓
T2：原子提交 TurnResult
```

数据库事务不跨越 LLM 调用。

T2 原子提交：

```text
NEXT / FOLLOW_UP:
Evaluation + 当前 Question=ANSWERED + Next Question + phase=WAITING_ANSWER

END:
Evaluation + 当前 Question=ANSWERED + Interview COMPLETED + phase=null
```

避免出现“Evaluation 已保存但 Next Question 未保存”等半个 Turn 的业务状态。

通过业务唯一约束、Expected State、Interview Version / Optimistic Locking 等机制保证幂等和并发安全。重复 Graph 执行可以发生，但只有一个结果能够成为正式 Business Fact；并发失败的 AI Runtime Result 直接丢弃。

## Interview status 与 current_phase

`status` 表示 Interview 生命周期：

```text
CREATED
IN_PROGRESS
PAUSED
COMPLETED
```

`current_phase` 不再表示 Graph Node 执行位置，而表示：

> Recoverable Business Execution Phase（可持久化、可恢复的业务执行阶段）。

V0.1：

```text
OPENING
WAITING_ANSWER
PROCESSING_TURN
```

典型流转：

```text
CREATED（V1 Prepare 成功后仍为 CREATED，已有 Plan）
  ↓ start
IN_PROGRESS + OPENING
  ↓ first question persisted
IN_PROGRESS + WAITING_ANSWER
  ↓ answer persisted
IN_PROGRESS + PROCESSING_TURN
  ├─ NEXT / FOLLOW_UP → IN_PROGRESS + WAITING_ANSWER
  └─ END              → COMPLETED + phase=null
```

原 `PREPARING / GENERATING_QUESTION / EVALUATING / DECIDING / COMPLETING` 不再使用，因为它们过度描述短生命周期 Graph 内部瞬时执行位置。

## Pause / Resume 与 Human-in-the-loop

V0.1 Pause / Resume 属于 Interview Domain，不属于 AI Graph，也不调用 `InterviewAiApi`。

仅允许在稳定业务边界暂停：

```text
IN_PROGRESS + WAITING_ANSWER
        ↓ pause
PAUSED + WAITING_ANSWER
        ↓ resume
IN_PROGRESS + WAITING_ANSWER
```

Resume 后继续原 Question，不重新生成 Question。

`OPENING / PROCESSING_TURN` 期间不通过 Pause 强制取消正在运行的 LLM / Graph。

V0.1 不引入 Graph Framework 层面的 HITL / Interrupt / Resume / Checkpoint。人机交互发生在两次短生命周期 Workflow 之间。未来出现必须中断并恢复同一复杂 Workflow 的场景时再评估。

## Interview Completion 与完整复盘

AI Decision 的 END 与用户主动 Finish 最终都进入 Interview Completion 语义，但用户主动 Finish 不需要 LLM / DecisionNode 批准。

V0.1 完成后不触发新的 AI Workflow。完整复盘由 Interview 模块基于已经持久化的 Interview、Question、Answer、Evaluation 业务事实动态组装，不属于 `InterviewAiApi` Capability，不使用 Graph，不生成新的 AI Proposal，也不新增异步任务、重试或恢复流程。

Interview-level AI Report 暂缓到 MVP 真实使用后再评估；只有确认整场总结能够提供逐题 Evaluation 与完整复盘之外的明确产品价值时，再重新设计其 Capability、持久化与失败恢复语义。

## Streaming Boundary

V0.1 核心 Interview Workflow 不使用 Streaming：

- Question：完整生成、校验并持久化为正式 Question 后再展示。
- Evaluation / Decision：内部 Workflow Result，面试过程中不向用户 Streaming。
- 完整复盘：读取已持久化业务事实并一次性返回，不使用 Streaming。

Spring AI Streaming / SSE 保留为技术能力，但不因技术可用而强制进入 V0.1 产品流程。

## ReactAgent

ReactAgent 不作为顶层 Interview 生命周期控制器。未来仅在复杂开放式 Node / Capability 内部需要多轮工具调用或开放式推理时局部评估使用。

## V0.1 暂不引入

独立 Graph Checkpoint、Graph HITL、Multi-Agent、MCP、Skill、Deep Research、复杂 Memory、Voice，以及 Interview-level AI Report。
