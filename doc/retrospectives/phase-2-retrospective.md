# Phase 2 Retrospective：技术选型与系统设计复盘

**版本：V1.0**  
**状态：[已完成]**  
**复盘阶段：Phase 2 — 技术选型与系统设计**

## 文档目的

本文记录 Interview Agent 在 Phase 2 中暴露出的过程问题、产生的影响和后续改进规则。

本文不是架构 Source of Truth。正式架构仍以 architecture、domain-model、module-structure、AI Design、data-model 和 ADR 为准。

复盘目标不是否定 Phase 2 的设计价值，而是：

> **保留高价值架构判断，同时避免后续阶段重复出现设计范围失控、实现反馈过晚和文档投入过大的问题。**

## 总体结论

Phase 2 正确解决了高成本问题：

- 模块与分层边界；
- Interview / Question / Answer 生命周期；
- MySQL 业务事实权威性；
- LLM 调用与数据库事务边界；
- 短生命周期 AI Workflow；
- GraphState 与 Domain State 分离；
- Evaluation Architecture；
- LLM 与 Java 的职责边界；
- 并发、幂等和失败恢复原则。

主要问题不在设计质量，而在设计节奏：

> **试图在实现前消除过多不确定性，较晚才明确 MVP 设计的停止条件。**

Phase 3 应从“尽量提前设计完整”调整为：

> **只提前消除高成本不确定性，其余通过小步实现、真实模型调用和测试消除。**

## 主要问题与改进

### 太晚定义 Phase 退出标准

#### 表现

Phase 2 前期缺少明确的“设计到什么程度即可进入开发”标准。只要还能继续细化，就容易进入下一层讨论。

#### 影响

- Phase 2 持续时间拉长；
- Context、Prompt、Evaluator 等主题不断展开；
- 很难判断某个主题何时真正完成；
- 可运行代码和真实反馈出现得较晚。

#### 后续规则

每个 Phase 和 Slice 开始前必须先明确：

- 目标；
- 非目标；
- 验收标准；
- 退出条件。

达到退出条件后，不因为“还有内容可以讨论”而继续扩大范围。

---

### 把“可以设计”误当成“必须现在设计”

#### 表现

提前讨论了 Profile Version、Prompt Version、Context Snapshot、History Compression、重试参数和精确 Token Budget 等内容。

#### 影响

- 部分设计后来又被简化或延后；
- 时间投入与 MVP 当前价值不匹配；
- 对缺少运行数据的问题形成了虚假的确定感。

#### 后续规则

面对设计问题先回答：

1. 不决定它，当前 Slice 是否无法编码？
2. 决策错误后，修改成本是否很高？
3. 是否必须依靠真实运行数据才能合理决定？

如果不阻塞当前 Slice、修改成本低且真实测试更可靠，则转入实现阶段决定。

---

### 没有尽早区分架构决策与实现决策

#### 架构决策

需要提前确认并沉淀：

- 模块边界；
- 核心领域模型；
- 生命周期；
- 状态权威来源；
- Workflow 生命周期；
- 事务边界；
- Evaluation 业务语义与计算规则。

#### 实现决策

通常应在当前 Slice 内决定：

- DTO 字段；
- Java Interface 和方法签名；
- Prompt 变量；
- Resource 目录；
- 最近历史轮数；
- Token 阈值；
- 重试次数；
- 异常类；
- BigDecimal 具体精度。

#### 后续规则

| 风险 | 判断标准 | 处理方式 |
|---|---|---|
| 高 | 影响多个模块、业务历史、状态或兼容性 | 设计确认，必要时 ADR |
| 中 | 影响当前 Slice 多个组件但容易迁移 | 简短设计后实现 |
| 低 | 局部、可替换、可由测试验证 | 直接实现并 Code Review |

---

### 核心能力设计接近配套平台化

#### 表现

Evaluator 的语义设计合理，但一度接近同时设计：

- Profile 版本管理；
- Prompt Registry；
- AI Trace；
- A/B Test；
- 复杂重试；
- 完整评价演进平台。

#### 调整结果

Phase 2 最终收敛为：

- Profile 使用不可变记录；
- profileId 定位规则；
- Prompt / Evaluator 使用轻量版本字符串；
- 不建设 Profile Version Manager；
- 不建设 Prompt Registry；
- 不建设 AI Trace 表；
- 具体 Schema 与重试参数转入 Phase 3。

#### 后续规则

> 可以保留核心能力的清晰边界，但不要同步建设尚无真实需求的管理平台。

---

### 过早讨论缺少真实运行数据的问题

#### 表现

提前试图确定：

- 最近保留多少轮历史；
- Context Token 如何分配；
- Resume / Project 注入多少；
- Prompt 长度；
- Structured Output 稳定性；
- Reference Answer 篇幅；
- 修复重试次数。

#### 影响

这些问题无法仅靠架构推演得到可靠答案，第一次真实模型调用后仍可能改变。

#### 后续规则

采用最小默认值：

- 最近有限轮次；
- 固定 Token 上限；
- 一套主 Prompt；
- 少量 Profile；
- 一次有上限的修复重试。

先运行固定样例，再根据延迟、Token、输出质量和失败率调整。

---

### 容易将架构概念扩展为过多代码结构

Phase 2 形成了必要概念：

~~~text
Business Input
AI Context
GraphState
EvaluationProposal
EvaluationResult
Evaluation
TurnResult
EvaluationProfile
~~~

这些概念用于划清职责，但不代表每个概念都需要：

~~~text
Interface
Implementation
Factory
Assembler
Converter
Manager
Strategy
Registry
~~~

#### 后续规则

只有满足以下条件之一才增加抽象：

- 已经存在两个真实实现；
- 变化点已经在代码中出现；
- 能消除已经存在的重复；
- 测试替换确实需要边界。

不因为“未来可能”创建接口、工厂或通用框架。

---

### 太晚转向纵向切片

#### 表现

Phase 2 长时间按全局主题推进：

~~~text
Architecture
→ Modules
→ Data
→ Agent
→ Evaluator
→ Context
→ Prompt
~~~

这适合建立基础架构，但不适合持续到所有细节都完成。

#### 影响

- 很长时间没有端到端运行成果；
- 无法验证 Spring AI、Graph 和 Structured Output 的真实行为；
- 设计问题不能通过代码及时暴露。

#### 后续规则

Phase 3 统一采用纵向切片：

~~~text
当前 Slice 最小详细设计
→ 开发
→ 测试
→ 验收
→ PR
→ 下一 Slice
~~~

每个 Slice 必须产生可运行增量。

---

### 对不同风险的问题投入了相近讨论强度

高风险问题值得深入讨论：

- 状态机；
- 事务边界；
- Workflow 生命周期；
- Evaluation 规则；
- 幂等与恢复。

低风险问题不应投入相同强度：

- Prompt 文件目录；
- 版本字符串格式；
- Evidence 数量；
- 具体重试次数；
- DTO 命名。

#### 后续规则

设计深度必须与返工风险匹配，不按“问题是否有趣”决定讨论深度。

---

### 文档状态出现过轻微不同步

曾出现：

- Progress 已确认，但 Source of Truth / ADR 仍为候选状态；
- Profile Version 简化后，PROJECT_CONTEXT 仍残留旧表达；
- Source of Truth 目录未及时列出新增 ADR；
- 已部分确认内容仍出现在未决列表。

#### 后续规则

每个 Slice 完成后只同步：

1. 当前 Phase Progress；
2. 实际受影响的 Source of Truth；
3. 必要 ADR；
4. PROJECT_CONTEXT 状态摘要。

避免多个文档重复维护完整定义。

---

### 文档产出一度超过可运行成果

项目的最终价值来自：

~~~text
能运行
能面试
能评价
能复盘
~~~

而不是 Markdown 数量。

#### 后续规则

Phase 3 的主要产出必须是：

- 可运行代码；
- Migration；
- 测试结果；
- 真实模型调用结果；
- 用户可验收功能。

普通实现细节不单独创建长篇设计文档。

## Phase 3 执行规则

后续 Slice 遵循以下规则：

1. 每个 Slice 开始前先明确验收标准；
2. 详细设计只覆盖当前 Slice；
3. 能由代码和测试回答的问题，不长时间推演；
4. 默认选择最简单的可运行方案；
5. 不为单实现组件提前创建接口；
6. 不为未来能力建设平台或通用框架；
7. 每个 Slice 必须产生端到端可运行增量；
8. 实现细节在当前 Slice 调整，架构变化才回到设计层；
9. Slice 完成后只更新必要文档；
10. 连续讨论多轮仍无代码产出时，立即检查是否重新陷入 Phase 2。

## 两个停止问题

### 停止设计

每次准备继续讨论时询问：

> **如果现在停止讨论并开始编码，最可能造成的严重返工是什么？**

如果无法指出明确、高成本的返工风险，应开始编码。

### 停止增加能力

每次准备增加组件、抽象或基础设施时询问：

> **如果没有它，当前 Slice 是否无法通过验收？**

如果答案是否定的，原则上延后。

## Slice Checkpoint

每个 Slice 开始前检查：

- [ ] 目标与非目标是否清楚？
- [ ] 验收标准是否可执行？
- [ ] 是否只设计当前 Slice？
- [ ] 是否存在能通过代码或测试回答的问题？
- [ ] 是否引入了尚无第二个实现的抽象？
- [ ] 是否加入了当前 Slice 不需要的平台或基础设施？

每个 Slice 完成前检查：

- [ ] 是否形成可运行增量？
- [ ] 关键测试是否通过？
- [ ] 是否完成真实链路验证？
- [ ] 是否只更新必要文档？
- [ ] 是否记录了实现中发现的设计偏差？
- [ ] 下一 Slice 是否可以基于当前结果继续？

## 最核心教训

Phase 2 的核心设计是有价值的，它避免了模块、状态、事务、Graph 和 Evaluation Architecture 的重大返工。

需要改进的是决策节奏：

> **从“尽量在设计阶段消除不确定性”，调整为“只提前消除高成本不确定性，其余通过小步实现和测试消除”。**
