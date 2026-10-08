# Interview Agent Context 设计

**版本：V1.2**  
**状态：[已确认]**

## 定位

Context 的目标不是把全部业务数据塞给 LLM，而是为当前 AI Task 提供最小、相关、可解释、可重建的上下文。

Phase 2 只确认架构边界和 MVP 最小策略。具体字段、轮数、Token 阈值和 Java类型在 Phase 3 通过实现与测试确定。

## 三层边界

```text
Business Input
      ↓
AI Context
      ↓
GraphState
```

### Business Input

由 Interview Application 协调业务模块、Module API / Repository 获得并提供的稳定业务事实。它表达 AI Capability 可以使用的数据范围，不是 Prompt，也不是 Graph Runtime State。

### AI Context

由 AI Context Builder 针对当前 Capability / Task，从 Business Input 中选择、裁剪、组织最小必要信息后形成的 AI 输入。

### GraphState

服务一次短生命周期 Workflow / Graph 的运行时状态，不是业务数据库副本，也不是 V0.1 的持久化恢复来源。

## 构建链路

```text
Business Modules
      ↓
Interview Application
      ↓
Business Input
      ↓
InterviewAiApi
      ↓
AI Context Builder
      ↓
AI Context
      ↓
Prompt / Graph / AI Capability
```

AI Context Builder 可以位于 `interview-agent-ai`，但不直接查询 Candidate、Position、Interview、Evaluation 等业务模块 Repository。

## Capability Context

Prepare、Opening、Turn 根据各自任务构建独立 Context，不建设一个长期累积、包含全部业务数据的万能 Context。

### Opening

V1 Start 加载已准备 Plan 的 initial PlanItem，按下文 MAIN Generator Context 构建首题输入；不重新选择方向，不注入超出 CandidateInterviewProfile 的简历或项目资料。

### Turn

Slice 3 Task 1 的 `DecisionContext` 是 Turn 中已确认的最小 Context，包含：

- 当前 Question、当前 Answer、当前 Evaluation 的必要信息；
- Candidate Context：`candidateName`、`resumeSummary`、结构化 `projects`；
- Position Context：`title`、`description`；
- Interview Progress 与 AllowedActions。

它不包含完整历史 Question / Answer，也暂不包含 `coveredTopics`。Application 提供
完整的 `DecisionContext`；AI 模块及其中的 Context Builder 不查询业务 Repository。

V1 Decision 额外接收未使用 PlanItems，用 `planItemRef` 选择 NEXT_QUESTION，解析规则见 [Workflow 设计](agent-design.md#planner-与引用边界question-quality-v1已确认待实现)。不引入 Topic/coveredTopics 去重模型。

### Question Quality V1 MAIN Generator Context

MAIN Generator（Opening 与 NEXT_QUESTION 共用此边界）接收：

- 当前 PlanItem：`area / focus / reason`；
- 现有 `CandidateInterviewProfile`：`candidateName / resumeSummary / projects`；
- 现有岗位上下文：`title / description`；
- `previousMainQuestions`：当前 Interview 已生成的全部 MAIN Question content，包括已 Skip 的 MAIN；首题为空列表。

历史 MAIN 仅用于降低核心内容、场景、语义和提问形式重复，不能使 Generator 偏离当前 PlanItem。不得传 Answer、Evaluation、FOLLOW_UP 或完整历史 Turn；不能套用下文“最近有限轮次”策略而遗漏已生成 MAIN。去重为 Prompt 语义约束，不承诺确定性消除重复。

Planner 使用 Create 保存的 mode/focusArea、现有候选人和岗位上下文及 maxMainQuestions 约束生成 Plan，不调用运行中的 Focus Selector。候选人输入始终以现有 CandidateInterviewProfile 字段为准；“原始简历”措辞不授权扩展 Candidate 模型或读取全文。

### FOLLOW_UP Generator Context

Decision 选定唯一 followUpTarget 后，Generator 只接收该目标、当前 Question / Answer，以及现有候选人和岗位背景字段；不接收 strengths / weaknesses / missingPoints 或整份 Evaluation。反馈用于 Decision 选择目标，不再承担 Generator 的输入职责；目标仍由既有 targetRef 映射解析，不新增输出字段或 AI 调用。

当前 Answer 是本轮陈述的事实来源；Question 仅提供考察上下文，target 仅表示下一步追问方向。背景资料不能替代 Answer 确认本轮事实或覆盖其否定、假设边界。四类归因规则见 [Prompt 设计](prompt-design.md#question-grounding-mvp)。

### Report

关注：

- Interview 基本信息；
- 已持久化的 Question / Answer / Evaluation；
- 已确认的统计结果与必要历史摘要；
- 生成本次 Interview Report 所需信息。

具体字段和跨模块 Contract 留到 Phase 3。

## MVP 历史与 Token 策略

除已确认的 `DecisionContext` 外，其他 Capability Context 在 V0.1 采用简单、确定性的策略：

```text
稳定基础信息
+
当前 Task 必需信息
+
最近有限轮次原文
+
必要历史评价摘要
```

Context 选择与裁剪使用固定优先级：

1. 当前 Question / Answer 与当前任务约束；
2. 岗位目标、Topic 和必要候选人信息；
3. 当前相关 Project Experience；
4. 最近轮次；
5. 更早、相关性较低的历史。

超出预算时优先裁剪较早且相关性较低的信息。

V0.1 不建设：

- LLM History Compression；
- 持久化 Context Snapshot；
- 向量检索历史；
- 复杂长期 Memory；
- 动态 Context 路由或通用 Context Pipeline。

具体最近轮数 N、Token Budget、估算方式和阈值在 Phase 3 通过模型限制和测试确定。

## Context 与 Prompt

Context 负责：

- 选择什么信息；
- 如何裁剪和组织；
- 控制输入规模；
- 向 Prompt 提供结构化动态数据。

Prompt 负责：

- 定义模型角色；
- 描述任务；
- 说明规则与输出约束；
- 引用 Context 变量。

Prompt 不直接查询业务数据，也不负责 Context Window 管理。

## Context 与恢复

V0.1 不持久化 Graph Checkpoint 或 Context Snapshot。

Workflow 失败后：

```text
MySQL Business Facts
      ↓
Rebuild Business Input
      ↓
Rebuild AI Context
      ↓
Re-execute Workflow
```

未持久化的 AI Context、GraphState 和 AI Runtime Result 可以丢弃并重建。正式业务事实仍以 MySQL 为准。

## 隐私与最小必要原则

Context 只包含当前 AI Task 必需的信息。简历、项目经历和历史回答不因“可能有用”而全部注入。

MVP 不默认把完整 Prompt、Resume、Answer 和原始模型响应写入普通日志。

具体脱敏字段、日志策略和 Provider 配置在 Phase 3 确定。

## Phase 3 Implementation Decisions

以下内容不再作为 Phase 2 前置设计：

- 具体 Context DTO 字段；
- Builder 类与方法签名；
- 最近历史轮数 N；
- Token 估算实现；
- 各 Context Section 的精确预算；
- 裁剪算法代码；
- History Summary 是否在真实数据压力出现后引入。

实现应优先选择最简单、可测试的方案；只有真实 Token 或效果问题出现时，再引入更复杂的 Compression / Snapshot / Memory。

## Public Profile V0.1 — Task 1（[已确认]）

本 Task 按用户确认的范围将 CandidateInterviewProfile 改为 List<ProjectProfile>，每项保留 name、description。Candidate/Resume 与 Position 语义不变；Repository 读取当前 Resume 的全部 Projects，允许空列表并按 project id 升序，不使用 LIMIT 1 或五条截断。

Interview Application 将项目逐项映射到 AI API 的结构化 ProjectContext，保持既有模块依赖，不在 Repository/Application 拼接摘要。Planner、Opening/MAIN、FOLLOW_UP、Evaluation、Decision、Skip 及保留的 Focus Context 均透传完整列表；只在 Prompt formatting 边界逐项分隔，并禁止无依据混合不同项目的行为、场景与结果。无项目时不推断项目经历。

不包含写 API、前端、Snapshot/Version、sort_order、Migration 或后续 Task。测试覆盖 0/1/多（超过五个）项目、稳定排序及 Context/Prompt 传播。
