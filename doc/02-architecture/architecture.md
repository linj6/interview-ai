# Interview Agent 总体架构设计

**版本：V1.1**  
**状态：[已确认]**

## 总体架构

采用：**模块化单体 + 分层架构 + 独立 AI Orchestration**。

``` text
Presentation
      ↓
Application
      ↓
Domain + AI Orchestration
      ↓
Infrastructure
```

## Presentation

负责 Controller、DTO、SSE、HTTP 输入输出。保持薄，不直接调用 LLM、Graph
或承担面试决策。

## Application

负责 Application Service、用例编排、事务边界、跨领域协调、调用 AI
Orchestration、驱动状态变化。

MVP 不建立 `StartInterviewUseCase` 等独立 UseCase 类层级。Use Case
是业务概念，Application Service 是代码承载者，例如：

``` text
InterviewApplicationService
├── createInterview()
├── prepareInterview()  # Question Quality V1
├── startInterview()
├── submitAnswer()
├── pauseInterview()
├── resumeInterview()
└── finishInterview()
```

Application Service 不实现 Agent 内部 Workflow。

## Domain

负责 Entity、Value Object、Aggregate、Domain Rule、Repository
Port、确定性业务约束和状态合法性。Domain 不依赖 Spring AI，不负责 AI
决策。

## AI Orchestration

负责 Interview Graph、Node、Graph
State、Context、Prompt、Evaluator、Model、Tool、动态追问和 AI 流程决策。

核心边界：AI 决定"如何驱动动态流程"，Domain 决定"业务上什么是合法的"。

## Infrastructure

负责 MySQL、Redis、文件存储、LLM Provider、Repository
实现、外部服务、日志、Metrics 与监控。

### 业务持久层边界

业务模块的 MyBatis-Plus 持久层统一组织为：

```text
infrastructure/persistence
├── entity
├── mapper
└── repository
```

调用方向固定为 `Application → Repository → Mapper → Entity → Database`。Application 不直接依赖
Mapper、DO 或 MyBatis-Plus Wrapper；Mapper 只由 Repository 调用。MVP 暂不为简单 CRUD 机械增加
`domain.repository` Port。标准 CRUD 和简单 Lambda 查询使用 `BaseMapper`，XML 仅保留多表 Join、状态条件更新、
乐观锁更新等复杂 SQL，Java 代码不编写原始 SQL 字符串。

## 核心红线

1.  Controller 不直接调用 LLM。
2.  Domain 不依赖 Spring AI。
3.  Domain 不执行 AI 决策。
4.  Application Service 不实现 Agent 内部 Workflow。
5.  AI 模块不绕过 Domain 修改核心业务状态。
6.  LLM 输出是 AI Proposal/Decision，不天然等于业务事实。
7.  最终业务状态转换经过确定性业务规则校验。

## Interview State、AI Context 与 Graph State

MySQL 是业务状态权威来源。V0.1 明确区分：

``` text
Business Facts / Business Input
      ↓
AI Context Builder
      ↓
AI Context
      ↓
GraphState / AI Workflow Runtime
```

GraphState 只服务一次短生命周期 Workflow，不复制全部业务数据，也不作为第二持久化状态来源。V0.1 不建设独立 Graph Checkpoint；恢复以 MySQL Business Facts、`status/current_phase`、版本和幂等机制为依据，重建 Context / GraphState 并安全重执行。

## 状态与事务

Slice 2 使用两段短事务：T1 保存不可变 Answer 并把 Interview 转为
PROCESSING_TURN；LLM、EvaluationProposal 校验和确定性计算在事务外；T2 由
InterviewApplicationService 的同一本地事务创建正式 Evaluation、更新 Question
和 Interview。EvaluationApi 参与该外层事务，不使用 REQUIRES_NEW。状态、阶段和
版本以 Java 枚举传给 Mapper，并由 MyBatis 映射为同名 VARCHAR。

Interview 同时维护 `status` 与 `current_phase`。`status` 表示生命周期；`current_phase` 表示可持久化、可恢复的业务执行阶段：`OPENING / WAITING_ANSWER / PROCESSING_TURN`，不表示 Graph Node 当前执行位置。

LLM 调用不包在长事务中。一次 Turn 采用 `T1 保存 Answer → AI Workflow → T2 原子提交 TurnResult`；T2 原子提交 Evaluation + Next Question，或 Evaluation + Interview Completion。使用 Expected State、Version / Optimistic Locking、业务唯一约束和幂等机制处理并发与重复执行。

T1 成功后 Answer 是正式业务事实，Interview 保持 `IN_PROGRESS + PROCESSING_TURN`，直至
T2 成功。其间任一 Turn 环节失败都不回退 `WAITING_ANSWER`；运行时结果丢弃后从 MySQL
业务事实重新执行完整 Turn。Slice 3 MVP 自动完整重试一次，仍失败时等待后续前端重试，
不建设后台恢复任务或 Graph Checkpoint。

## 技术栈

MVP：Spring Boot 3.5.x、Spring AI 1.1.x、Spring AI Alibaba 1.1.2.2、JDK 17、Maven、MySQL；Redis、文件存储、SSE 仅在实际需求出现时使用。

Spring AI 负责模型抽象、ChatClient、Tool、Structured Output、Streaming 能力；Spring AI Alibaba Graph/Workflow 用于需要多步骤和条件分支的 AI Workflow。V0.1 不要求每个 AI Capability 都物理实现为 Graph。

ReactAgent 可在未来复杂开放式 Node 内部使用，但不作为顶层 Interview Controller。

Question Quality V1 的 Prepare 是独立短生命周期 Planner 调用，AI 计算在事务外；Plan 与全部 PlanItems 在同一短事务原子保存，仅一个 Plan 成为正式事实。不新增 Interview 状态或 Graph Checkpoint，详细边界见 [Workflow 设计](../03-ai/agent-design.md) 与 [数据模型](../04-data/data-model.md)。

## AI Workflow

Interview 是长生命周期业务对象，Graph / AI Workflow 是业务事件触发的短生命周期执行。Graph 不跨越 `WAITING_ANSWER`。

``` text
InterviewAiApi
├── Prepare Capability → Planner（Question Quality V1）
├── Opening Capability → MAIN Generator（基于 initial PlanItem）
└── Turn Workflow → Turn Graph
```

Turn Graph：

``` text
EvaluateNode
    ↓
DecisionNode
    ↓
Conditional Edge
 ┌──┼──────────┐
 ↓  ↓          ↓
FOLLOW_UP  NEXT_QUESTION  END
 └──┬──────────┘
    ↓
QuestionGenerationNode
```

Evaluate 只评价；Decision 决定 `FOLLOW_UP / NEXT_QUESTION / END`；Edge 只路由；QuestionGeneration 根据 Decision Intent 统一生成 MAIN / FOLLOW_UP。PrepareNode、FollowUpNode、EndNode 不再使用。

Pause / Resume 属于 Interview Domain。V0.1 只在 `WAITING_ANSWER` 稳定业务边界暂停，不引入 Graph HITL / Interrupt / Resume。

V0.1 不生成 Interview-level AI Report。Interview 完成后的完整复盘由 interview 模块基于 MySQL 已持久化业务事实动态组装，是只读查询视图，不属于 AI Workflow。

V0.1 正式业务输出不使用 Streaming：Question 完整生成并持久化后展示；Evaluation / Decision 为内部结果；完整复盘一次性读取并返回。
