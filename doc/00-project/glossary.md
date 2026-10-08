# Interview Agent Glossary

> 本文档用于解释 Interview Agent 项目文档中经常出现的英文术语、架构概念和 AI 相关概念。
>
> 本文档仅用于辅助理解，不作为具体架构设计的 Source of Truth。若术语说明与正式设计文档或 ADR 冲突，以对应的 Source of Truth / ADR 为准。
>
> 维护原则：术语首次进入正式设计，且可能造成理解障碍或在项目文档中高频出现时，再补充到本文档；不追求成为通用技术词典。

## 文档与架构治理

### Source of Truth

**中文：** 权威信息源 / 唯一事实来源

指某项设计或事实的正式权威文档。项目中的同一重要概念应尽量只有一个 Source of Truth，其他文档引用它，而不是复制出多个可能逐渐不一致的版本。

### ADR

**全称：** Architecture Decision Record  
**中文：** 架构决策记录

用于记录重要架构决策及其背景、最终选择、主要理由和影响。ADR 重点保存“为什么这样决定”，而具体当前设计仍应以对应 Source of Truth 为准。

### Context（ADR）

**中文：** 背景 / 决策上下文

描述为什么需要做这个架构决策，包括当前问题、约束、目标以及促使团队做出选择的背景。

### Decision

**中文：** 决策

ADR 中最终确认采用的方案。

### Consequences

**中文：** 决策后果 / 决策影响

表示采用某个架构决策后需要接受的影响，包括收益、代价、限制以及对后续设计和实现形成的约束。Consequences 不等于“缺点”。

### Trade-off

**中文：** 权衡 / 取舍

架构方案通常无法同时获得所有优点。Trade-off 表示为了获得某些收益，明确接受另一些成本或限制。

### Source of Truth 与 ADR 的区别

- Source of Truth 回答：**系统现在正式采用什么设计？**
- ADR 回答：**为什么当时做出这个重要决定？**

## AI / Agent / Graph

### Workflow

**中文：** 工作流 / AI 工作流

围绕一个完整目标组织的一段连续处理流程。Interview Agent 中 Workflow 是架构概念，不代表每个 Workflow 都必须使用 Graph 实现。

### Graph

**中文：** 图工作流

通过 Node 和 Edge 描述多步骤 AI 执行流程的编排方式，适合存在多步骤处理、条件分支和状态传递的 AI Workflow。

### Node

**中文：** 节点

Graph 中承担某项明确职责的处理单元，例如 EvaluateNode、DecisionNode。

### Edge

**中文：** 边 / 节点连接

描述 Graph 中 Node 之间的流转关系。Conditional Edge 根据已有结果选择下一条执行路径，本身不应承载复杂业务逻辑。

### GraphState

**中文：** 图运行时状态

单次 Graph / Workflow 执行期间用于在节点之间传递数据的运行时状态。

在 Interview Agent V0.1 中，GraphState 不是核心业务状态的第二持久化来源；Workflow 需要恢复时，应根据持久化业务事实重新构建。

### Checkpoint

**中文：** 检查点 / 执行状态快照

将 Workflow / Graph 的运行状态保存下来，使长时间运行流程能够在中断后从某个执行位置继续。

Interview Agent V0.1 不建设独立 Graph Checkpoint 持久化模型，流程恢复以持久化业务事实为依据。

### Human-in-the-loop（HITL）

**中文：** 人在回路 / 人工介入

AI Workflow 在执行过程中暂停并等待人工输入、确认或审批，然后继续原来的 Workflow。

Interview Agent V0.1 的“等待用户回答”属于业务层面的 WAITING_ANSWER，不等同于 Graph 框架层面的 HITL / Interrupt / Resume。

### Structured Output

**中文：** 结构化输出

要求模型按照约定的数据结构输出结果，而不是只返回自由文本。它便于程序进行校验、计算和持久化。

### Tool Calling

**中文：** 工具调用

模型根据任务需要选择并调用外部工具或程序能力，再利用工具结果继续完成任务。

### Prompt

**中文：** 提示词

发送给模型的指令和上下文组织方式，用于描述角色、目标、约束、输入信息和期望输出格式。

### AI Context

**中文：** AI 上下文

为了某次 AI 能力调用而组织、裁剪、压缩后的模型输入上下文。它不同于完整业务数据，也不同于 GraphState。

### Proposal

**中文：** 提议 / 建议结果

AI Workflow 产生、但尚未成为正式业务事实的结果。Proposal 需要经过 Application / Domain 的合法性和并发校验，并成功持久化后，才能成为 Business Fact。

## 业务状态与可靠性

### Business Fact

**中文：** 业务事实

已经通过业务规则校验并成功持久化的正式业务数据，例如正式 Answer、Evaluation、Question，以及 Interview 的状态变化。

核心关系：

```text
AI Result
+ Domain Validation
+ Concurrency Validation
+ Successful Transaction
= Business Fact
```

### Recoverable Business Phase

**中文：** 可恢复业务执行阶段

用于表示业务流程当前处于哪个可以根据持久化事实恢复的阶段。

Interview Agent 中 `current_phase` 表达的是 Recoverable Business Execution Phase，而不是 Graph 当前执行到哪个 Node。

### Idempotency

**中文：** 幂等性

同一个业务操作因为重试等原因被执行多次时，不会产生重复或冲突的正式业务事实。

例如同一个 Question 不能因为重复提交而产生多个正式 Answer。

### Optimistic Locking

**中文：** 乐观锁

通过版本号或预期状态检测并发修改。提交时如果发现业务对象已经被其他请求更新，则当前操作失败，而不是覆盖新的状态。

Interview Agent 可利用它保证并发执行的多个 AI Workflow Result 最终只有一个能够成为正式业务事实。

### Retry

**中文：** 重试

操作失败后重新执行。在 AI Workflow 中，Retry 不意味着一定从失败 Node 精确恢复；V0.1 可以根据持久化业务事实重建上下文并重新执行完整 Workflow。

## 软件架构

### Modular Monolith

**中文：** 模块化单体

系统作为一个整体应用部署，但在代码和依赖层面划分清晰的业务模块。它保留单体应用较低的部署和运维复杂度，同时通过模块边界控制耦合。

### Module Public API

**中文：** 模块公开能力接口

模块对其他模块暴露的稳定 Capability Contract。它用于表达跨模块业务能力，而不是把模块内部的 Entity、Repository、Mapper 等实现细节暴露出去，也不是在单体内部模拟 RPC。

### Capability

**中文：** 能力

从调用方视角表达“目标模块能够完成什么”，强调业务语义和稳定边界，而不是内部具体如何实现。

### Application Layer

**中文：** 应用层

负责业务用例编排、事务边界、模块协作以及调用 Domain 和 AI Capability，本身不承载模型推理细节。

### Domain Layer

**中文：** 领域层

负责核心业务模型、确定性业务规则和合法状态变化。AI 可以提出建议，但核心业务状态是否合法最终由 Domain 规则决定。

### Infrastructure Layer

**中文：** 基础设施层

负责数据库、缓存、文件存储、模型 Provider、Repository 实现、外部系统集成等技术实现。

### Lightweight CQRS

**中文：** 轻量 CQRS 语义

使用 Command 表达改变目标模块业务状态的请求，使用 Query 表达只读查询，但不因此引入 CommandBus、QueryBus、Handler 等完整 CQRS 基础设施。


## 面试评估维度

> 以下术语用于 Interview Agent 的回答评估。当前正式评估规则、权重和评分方式以 Evaluation 相关 Source of Truth 为准，本节只解释各维度的语义。

### Accuracy

**中文：** 准确性

衡量回答中的技术概念、事实、原理和结论是否正确，是否存在明显错误、混淆或误导。

### Completeness

**中文：** 完整性

衡量回答是否覆盖问题要求的关键内容和主要要点，而不是只回答其中一部分。

### Depth

**中文：** 深度

衡量回答是否超越表面定义，能够进一步解释原理、机制、原因、边界条件、实现细节或实际影响。

### Expression

**中文：** 表达能力

衡量回答是否清晰、准确、简洁，术语使用是否恰当，以及是否能够让面试官容易理解候选人想表达的内容。

### Logic

**中文：** 逻辑性

衡量回答的组织和推理是否连贯，包括观点之间是否存在清晰关系、结论是否有合理依据，以及分析过程是否前后一致。

### Project Experience

**中文：** 项目经验

衡量回答是否能够结合真实或合理的项目实践说明问题，例如实际场景、技术选型、问题定位、解决方案、结果和经验总结，而不是停留在纯理论层面。

### Adaptability

**中文：** 应变能力

衡量面对追问、场景变化、额外约束或陌生问题时，是否能够理解新的信息并调整分析和回答，而不是机械重复已有结论。

### Evaluation Profile

**中文：** 评估配置 / 评估画像

定义一次评估采用哪些维度以及相应的评分规则、权重和范围。Interview Agent 通过 Evaluation Profile 使评估规则可以被明确管理和版本化。

### Dimension

**中文：** 维度 / 评估维度

从某一个特定角度评价回答质量的指标。例如 Accuracy、Completeness、Depth 都属于 Evaluation Dimension。

### Weight

**中文：** 权重

表示某个评估维度在综合得分中的相对重要程度。不同维度可以具有不同 Weight。

### Weighted Score

**中文：** 加权得分

某个维度的 Score 按其 Weight 计算后对综合得分产生的贡献。

### Evidence

**中文：** 评分依据 / 证据

用于说明某个维度为什么得到当前评分的具体依据，通常来自候选人的回答内容或其中表现出的事实、缺失点和问题。
