# Interview Agent 模块结构设计

**版本：V1.1**  
**状态：[已确认]**

## 模块化

采用 Maven 多模块 + 模块化单体。模块是第一维边界，分层是第二维边界。

业务模块可按复杂度采用：
`interfaces / application / domain / infrastructure`，不要求机械完整四层。

## Maven 模块

``` text
interview-agent
├── interview-agent-bootstrap
├── interview-agent-common
├── interview-agent-api
│   ├── interview-agent-candidate-api
│   ├── interview-agent-position-api
│   ├── interview-agent-interview-api
│   ├── interview-agent-evaluation-api
│   └── interview-agent-ai-api
├── interview-agent-user
├── interview-agent-candidate
├── interview-agent-position
├── interview-agent-interview
├── interview-agent-evaluation
├── interview-agent-report
└── interview-agent-ai
```

## 职责

-   user：登录与基础用户能力
-   candidate：候选人、简历、项目经历
-   position：职位/面试目标
-   interview：Interview、InterviewPlan、PlanItem、Topic、Question、Answer、生命周期
-   evaluation：Evaluation、Profile、Dimension、Feedback
-   report：预留模块；V0.1 完整复盘不使用，Interview-level AI Report 暂缓后再评估
-   ai：AI Orchestration 与 AI 能力

表归属与持久化职责：

- interview：`interview`、`interview_plan`、`interview_plan_item`、`question`、`answer`；
- evaluation：`evaluation`、`evaluation_profile`、`evaluation_dimension_config`、`evaluation_dimension_score`；
- candidate：Candidate、Resume、Project；position：Position；user：User。

Interview 不访问 Evaluation 的 DO、Mapper、Repository、XML 或 Infrastructure。当前真实跨模块需要通过
`interview-agent-evaluation-api` 暴露最小 Profile 查询和正式 Evaluation 创建能力。

各业务模块的持久层使用 `infrastructure/persistence/entity|mapper|repository`。Application 只依赖 Repository；
当前 MVP 不增加 `domain.repository` 接口。

## AI 模块

``` text
interview-agent-ai
├── orchestration
│   ├── graph
│   ├── node
│   └── state
├── context
├── prompt
├── evaluator
├── question
├── model
└── infrastructure
    ├── llm
    └── tool
```

## Public API

Slice 2 的 Evaluation API 按业务能力拆为 `evaluation.api.profile` 和
`evaluation.api.evaluation`：前者提供规则 Profile 查询，后者提供正式
Evaluation 创建契约。API 只包含 record 和接口，不依赖 Spring、MyBatis、数据库驱动或实现模块；`Official*` 前缀已删除。

模块禁止访问其他模块内部实现，采用独立 API Module。

API Module 是 Maven 聚合模块，不承载业务实现。

## Capability Contract

Module Public API 不是本地 RPC Service
的简单替代，而是严格的模块能力契约。

API 允许：Public API
Interface、Command、Query、Result、Summary、Profile、Context、必要
Enum/Value Object。

API 禁止暴露：Domain
Entity、Aggregate、DO、PO、Repository、Mapper、Infrastructure、Web
DTO、Spring AI 内部对象、Graph、Node、Prompt 实现、ChatClient。

## 依赖

典型：

``` text
interview
   ├──→ candidate-api
   ├──→ position-api
   └──→ ai-api
```

AI 实现原则上只依赖 ai-api，不直接依赖业务实现模块；AI 所需业务上下文由
Interview Application 层准备。

## 循环依赖

出现 A→B、B→A 时，优先提升到上层 Application Service
或重新审视模块边界，而不是继续增加 API。

## Common

Common 保持小而稳定，不放业务 Entity、Service、Repository、业务 DTO。

## Command / Query / Result 语义

Module Public API 采用轻量 CQRS 语义，但 MVP 不引入 CommandBus、QueryBus、Handler Framework 等完整 CQRS 基础设施。

- `Command`：请求目标模块执行由该模块负责、会改变其业务状态的能力。
- `Query`：读取目标模块信息，不改变业务状态。
- `Result / Summary / Profile`：稳定的跨模块数据契约，不直接暴露 Domain Entity。
- `Result` 表示通用操作结果；`Summary` 表示轻量资源视图；`Profile` 表示围绕特定业务用途组织的相对完整视图。
- 不要求每个 Query 机械返回 `XxxResult`，命名优先表达业务语义。

Public API 遵循最小暴露原则：只有存在真实跨模块需求的能力才进入 API Module，不镜像 Application Service 的全部 CRUD / List / Page 方法，避免演化成本地 Dubbo/RPC Service Facade。

## InterviewAiApi 能力粒度

`interview-agent-ai-api` 按业务能力组织 Public API：根包只保留 `InterviewAiApi`；Opening 契约位于
`com.linj6.interviewagent.ai.api.interview`，Evaluation 的 Context、Proposal、Validated Result 及其枚举位于
`com.linj6.interviewagent.ai.api.evaluation`。该分包仅改善契约可读性，不改变 `InterviewAiApi` 的 Workflow
粒度或模块依赖方向。

`InterviewAiApi` 以 Workflow 粒度 AI Capability 作为主要跨模块契约。

``` text
Interview Application
        ↓
业务 Use Case 编排 / 构建 AI Context
        ↓
InterviewAiApi
        ↓
AI Workflow / Graph
        ↓
Evaluation / Decision / Question Generation
        ↓
AI Proposal / Result
        ↓
Interview Application + Domain Validation
        ↓
Business Fact / Persistence
```

Evaluator、QuestionGenerator、DecisionMaker、Graph Node 等细粒度 AI 能力保持在 `interview-agent-ai` 内部，不直接作为业务模块的 Public API。

Application 负责业务流程编排，Graph 负责 AI 推理流程编排；AI Proposal 不直接成为业务事实。

## Phase 2 / Phase 3 设计边界

Phase 2 确认 Public API 的架构方向、职责边界、依赖方向和语义。

具体 Java Interface、方法名、Command / Query / Result 字段、Package、Mapper / Assembler 等详细设计放到 Phase 3 实现阶段确定，避免 Phase 2 过度设计。

## 当前状态

[已确认]：独立 API Module、Capability Contract、最小暴露、不暴露 Domain Entity、单向依赖、轻量 Command / Query 语义、Result / Summary / Profile 语义、InterviewAiApi Workflow 粒度。

API Common 与具体 Context 对象边界在相关架构主题中按实际需要继续确认；具体 API DTO / 方法签名属于 Phase 3 详细设计。

## Public Profile V0.1 — Task 2（[已确认]）

按用户确认的 Task 2 范围，Bootstrap 承载单用户 Current Interview Profile 聚合 GET/PUT 编排，调用 Candidate / Position 独立 Public API，不新增 Maven 模块或业务模块间依赖。固定 Candidate / Position identity 集中在 Common，由 Interview 创建与 Profile 共用；Current Resume 按 Candidate 关系定位。

PUT 是完整 replacement：独立 Request/Response DTO，0..5 projects；非空 ID 必须唯一且属于 Current Resume，按 ID 更新，空 ID 由 Candidate 使用 IdWorker.getId() 新增，请求缺失的项目删除。所有参数、TEXT 字节长度、归属及资料存在性校验在写入前完成。聚合 Service 的一个本地事务原子更新 Candidate、Resume、Projects 和 Position，返回持久化后完整资料；不使用 REQUIRES_NEW、锁、version 或 snapshot。管理项目模型包含 ID，AI CandidateInterviewProfile 继续不包含 ID。

不包含前端、PROJECT_DEEP_DIVE 限制、多用户、多 Resume/Position、独立 Project CRUD、Migration 或真实 LLM 测试。
