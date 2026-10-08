# Interview Agent 功能需求

> 文档生命周期：Reference。本文维护当前需求边界与 HTTP Contract，不维护 Phase / Task / PR 进度。当前实现和验收状态见 [PROJECT_CONTEXT](../PROJECT_CONTEXT.md)，详细记录见 [Phase 3 Progress](../progress/phase-3.md)。

## 当前 v0.1 需求边界

当前代码已接入以下范围；“已接入”不等于真实模型质量或完整新流程已验收，验收状态统一见上述 Current 入口。

| 范围 | 当前需求与实现边界 |
|---|---|
| 输入资料 | 单用户个人实例通过 Profile GET/PUT 读取和编辑当前 Candidate 姓名、简历摘要、0–5 个项目及目标岗位名称和描述；首次启动包含可替换的演示资料。无登录认证或多用户数据隔离，不提供简历上传/解析、独立选择目标项目或多岗位管理，不适合直接匿名公网开放 |
| 面试配置与准备 | Create 必填 InterviewMode；仅 TECHNICAL_FOCUS 单选 KnowledgeArea；Prepare 形成 InterviewPlan，已有 Plan 才能 Start；具体规则见下文当前 HTTP Contract 与领域 SoT |
| 面试过程 | 文本回答、逐题评价、动态追问/下一题、Skip、Pause/Resume、主动 Finish、截止时间处理与 Whole-Turn Retry；数量/时长为服务端配置，不提供用户难度和时长选择 |
| 前端与复盘 | 首页创建、Profile 编辑、Session、Review 四页；完成后展示客观统计、MAIN/FOLLOW_UP、回答与逐题评价，不展示整场综合分、能力画像或整场 AI Report |
| 历史与反馈 | 业务事实持久化，可按已知 Interview ID 读取 Session / 已完成 Review；不提供历史列表、历史检索、历次能力趋势或用户评价反馈 |
| 输出与质量 | 正式业务输出为完整 JSON，不提供 Streaming/SSE；Plan、历史 MAIN 输入与 Prompt 约束用于降低重复，不保证语义去重、覆盖、grounding 或评价质量 |

Profile 修改会影响进行中的 Interview；当前没有资料快照或版本冻结，已保存的 Plan、问题和评价保留原有事实，后续生成与评价可能使用更新后的资料。

InterviewMode、KnowledgeArea 和 Plan/PlanItem 的唯一枚举与规则来源为 [领域模型](../02-architecture/domain-model.md)。PlanItem 使用事实来自已持久化 MAIN，FOLLOW_UP 继承所属 MAIN 的关联；Skip 不释放已用项，Resume 继续同题。持久化与历史 NULL 兼容边界见 [数据模型](../04-data/data-model.md)，评价与确定性评分见 [Evaluator 设计](../03-ai/evaluator-design.md)。

## 早期需求参考的阅读边界

下方保留 Phase 1 原始需求背景与愿景。除明确描述当前 v0.1 的配置 Contract、完整复盘、过程控制和恢复规则外，早期用户故事、功能架构、原始 MVP 范围/验收标准和版本演进只作为历史需求参考；其中“支持”“必须实现”等措辞不表示当前已完成。与当前边界不同的目标不自动进入本次发布范围。原始愿景另见 [project-definition](../00-project/project-definition.md)。

---

## 文档概述

### 文档目的

本文档用于定义 Interview Agent 第一阶段（MVP）的产品目标、用户需求、功能边界、核心业务流程、AI 行为规则及验收标准。

本阶段的目标不是一次性构建完整的商业化 AI 面试平台，而是优先建立一个能够真正用于个人面试训练，并具备持续迭代能力的 AI 面试训练闭环。

### 项目定位

Interview Agent 是一个面向程序员面试准备场景的 AI 面试训练系统。

系统通过候选人的：

- 基本信息
- 工作经历
- 技术栈
- 简历
- 项目经历
- 目标岗位
- 历史面试记录

构建候选人上下文，由 AI 充当面试官进行动态面试。

AI 不仅负责生成问题，还需要根据候选人的回答动态追问并形成逐题、多维、可解释的 Evaluation；面试结束后通过完整复盘查看整场问题链、回答和评价。

系统最终形成：

```text
学习 / 准备知识
        ↓
AI 模拟面试
        ↓
回答问题
        ↓
AI 动态追问
        ↓
AI 内部评估
        ↓
面试结束
        ↓
完整面试复盘
        ↓
发现薄弱点
        ↓
针对性学习
        ↓
再次模拟面试
        ↓
真实面试
        ↓
真实面试复盘
        ↓
更新题库 / Prompt / Evaluator / 训练重点
        ↓
再次训练
```

---

# 1. 产品概述

## 第一阶段目标用户

MVP 第一阶段主要服务于项目创建者本人，用于：

1. Java 后端开发岗位面试准备
2. AI 应用开发岗位面试准备
3. LLM 应用开发岗位面试准备
4. Agent 开发岗位面试准备

后续可以扩展为面向其他程序员以及其他职业的通用 AI 面试训练平台。

## 核心目标

MVP 必须能够让用户：

> 选择目标岗位和面试配置，基于自己的简历、项目经历和技术背景完成一场约 30～45 分钟以上可配置时长的 AI 模拟面试；AI 能够根据用户回答动态追问，并在面试结束后通过完整复盘查看逐题多维评价、优势、问题、遗漏点与参考答案。

## 核心产品指标

系统不应该只关注某一次面试的分数，而应该逐渐形成某个目标岗位的：

**Interview Readiness Score（面试准备度）**

例如 Java 后端岗位可以逐渐形成：

```text
Java                     82
Spring / Spring Boot     78
MySQL                    73
Redis                    70
MQ                       65
JVM                      75
分布式系统               68
系统设计                 62
项目经验                 80
沟通表达                 76
综合面试准备度           72
```

该能力画像可以随着多次模拟面试持续变化。

---

# 2. 用户角色

## 普通用户

MVP 暂时只考虑单一用户角色。

主要能力：

- 登录系统
- 管理个人候选人资料
- 管理简历
- 管理项目经历
- 管理目标岗位
- 创建面试
- 进行 AI 面试
- 查看完整面试复盘
- 查看历史面试
- 提交 AI 评价反馈

## 后续角色

后续商业化阶段可以增加：

- 管理员
- 企业招聘人员
- 面试官
- 教练 / 培训机构
- 多租户组织管理员

MVP 不实现。

---

# 3. 核心用户故事

## 候选人资料

作为用户，我希望维护自己的基本信息、工作经历和技术栈，以便 AI 在面试过程中了解我的背景。

## 简历管理

作为用户，我希望上传 PDF / Word 简历，也可以直接粘贴简历文本，以便系统将简历用于 AI 面试。

同一份简历可以关联多个目标岗位。

## 项目经历

作为用户，我希望独立维护自己的项目经历，并在一次面试中选择一个或多个项目。

## 创建面试

作为用户，我希望选择：

- 目标岗位
- 面试模式
- 面试类型
- 技术方向
- 项目经历
- 面试难度
- 面试时长

然后开始 AI 模拟面试。

## 动态面试

作为用户，我希望 AI 不只是按照固定题库逐题提问，而是能够根据我的回答继续追问。

例如：

```text
AI：
你之前提到系统 QPS 提升了 10 倍，请具体介绍一下。

用户：
我们通过 Redis 缓存把 QPS 从 1000 提升到了 10000。

AI：
原来的瓶颈是什么？

用户：
主要是数据库查询压力比较大。

AI：
为什么选择 Redis，而不是本地缓存？
缓存一致性是怎么解决的？
你如何验证 QPS 确实提升到了 10000？
```

## 完整面试复盘

作为用户，我希望面试结束后能够按 MAIN + FOLLOW_UP 的层级查看整场问题链，并看到自己的回答、逐题评分、优势、问题、遗漏点、参考答案和适用维度评分。

复盘同时提供开始时间、结束时间、实际持续时长以及 MAIN、FOLLOW_UP、已回答、跳过、未回答等客观统计。V0.1 不要求额外生成 Interview-level AI 总结或综合报告。
## 持续训练

作为用户，我希望系统能够根据历次面试结果发现长期薄弱点，并指导下一次训练。

---

# 4. 产品功能架构

```text
Interview Agent
│
├── 用户与登录
│
├── Candidate Profile
│   ├── 基本信息
│   ├── 工作经历
│   ├── 技术栈
│   ├── 简历
│   └── 项目经历
│
├── 岗位管理
│   └── 目标岗位
│
├── 面试配置
│   ├── 面试模式
│   ├── 面试类型
│   ├── 技术方向
│   ├── 项目选择
│   ├── 难度
│   └── 面试时长
│
├── AI 面试
│   ├── 问题生成
│   ├── 用户回答
│   ├── 动态追问
│   ├── 简历深挖
│   ├── 项目深挖
│   └── 面试状态管理
│
├── AI 评估
│   ├── 知识准确性
│   ├── 完整性
│   ├── 深度
│   ├── 表达能力
│   ├── 逻辑能力
│   ├── 项目经验
│   └── 追问应变能力
│
├── 完整面试复盘
│   ├── 面试客观概览
│   ├── MAIN / FOLLOW_UP 问题链
│   ├── 用户回答
│   └── 逐题 Evaluation
│
├── 面试历史
│
└── 用户反馈
```

---

# 5. 功能需求

## 用户与登录

### 简单登录

MVP 支持简单登录。

具体认证方式在 Phase 2 技术设计阶段确定。

### 要求

- 用户登录后才能使用个人数据和面试功能
- 用户数据必须与用户身份隔离
- 未登录用户不能访问个人简历、项目、面试记录等数据

---

## Candidate Profile

Candidate Profile 是 AI 面试的核心上下文。

```text
Candidate Profile
├── Basic Info
├── Work Experience
├── Tech Stack
├── Resume
└── Projects
```

支持维护：

- 基本信息
- 工作经历
- 技术栈

具体字段在详细设计阶段确定。

---

## 简历管理

### 简历输入方式

MVP 支持：

1. PDF 文件上传
2. Word 文件上传
3. 直接粘贴简历文本

### 简历管理

用户可以：

- 创建简历
- 查看简历
- 编辑简历文本
- 上传新版本
- 删除简历
- 将简历关联到一个或多个目标岗位

### 多岗位关联

一份简历可以关联多个目标岗位。

---

## 项目经历

项目经历必须作为独立资源进行管理，而不是只能随着某一次面试临时输入。

### 项目信息

至少支持：

- 项目名称
- 项目背景
- 项目职责
- 技术栈
- 业务描述
- 核心功能
- 技术难点
- 个人贡献
- 项目成果
- 性能指标 / 数据指标
- 其他补充信息

具体字段在详细设计阶段进一步确定。

### 多项目面试

一次面试可以选择多个项目。

AI 不要求平均分配时间，应根据岗位相关性、项目技术栈、回答质量、暴露出的薄弱点和剩余时间动态决定深挖重点。

---

## 目标岗位

岗位与技术主题必须明确区分。

### 岗位

例如：

- Java 后端开发
- AI 应用开发
- LLM 应用开发
- Agent 开发

### 技术主题

例如：

- Java
- Spring
- MySQL
- Redis
- MQ
- JVM
- 分布式系统
- RAG
- Agent
- MCP
- Reactor

一个岗位可以包含多个技术主题。

---

## 面试配置

当前创建配置以以下 Contract 为准；后面的早期产品配置仅为历史参考。

### 目标岗位

当前使用固定演示岗位，不提供岗位选择；“岗位必选”属于早期产品设想。

<!-- 保留旧锚点供 phase-3.md 的历史 Task 设计记录引用；“待实现”仅是旧链接标识，不代表当前实现状态。Current / Reference 请使用下方当前有效标题的锚点。 -->
<a id="question-quality-v1-配置与-http-contract已确认待实现"></a>

### Question Quality V1 配置与 HTTP Contract（当前有效）

V1 将面试模式与类型统一为 `InterviewMode`，枚举值及 9 个 `KnowledgeArea` 以[领域模型](../02-architecture/domain-model.md#question-quality-v1plan-与准备流程已确认待实现)为唯一清单；不沿用下文早期产品配置作为枚举依据。仅 `TECHNICAL_FOCUS` 必须提供一个 `focusArea`，其他模式禁止提供。使用现有 CandidateInterviewProfile 和岗位上下文；Question Quality V1 本身不新增资料管理能力，当前资料编辑由 Personal Profile GET/PUT 提供，范围见本文当前 v0.1 需求边界。

| 当前 HTTP Contract | 规则 |
|---|---|
| `POST /api/interviews` | Create 必须提供 JSON 请求体及非空 `mode`；`focusArea` 可空，仅 TECHNICAL_FOCUS 必须提供，其他模式禁止提供非空值；枚举严格使用领域模型中的值 |
| `POST /api/interviews/{id}/prepare` | 准备接口；仅 CREATED 且无 Plan 可调用；成功持久化 Plan 后拒绝再次 Prepare；失败不持久化 Plan，可重试 |
| `GET /api/interviews/{id}` | 返回 `mode / focusArea / planPrepared`；mode/focusArea 读取持久化配置，planPrepared 从正式 Plan 是否存在派生 |
| Start | 仅在已有 Plan 时启动；Opening 失败恢复 CREATED 并保留已准备 Plan |
| Answer / Retry / Skip / Pause / Resume / Finish / Review | 沿用现有外部 Contract；内部按 PlanItem 关联生成与恢复，不向外暴露 AI 临时引用 |

用户流程为 Create → Prepare → Start。Prepare 成功后仍为 CREATED，不新增 PREPARING/PREPARED；planPrepared 从正式 Plan 是否存在派生。InterviewPlan 保存初始 PlanItem，Start 使用该项生成首题，后续 MAIN 从未使用项选择；Plan 数量、失败、并发与使用规则见 [领域模型](../02-architecture/domain-model.md) 和 [数据模型](../04-data/data-model.md)。计划提供考察方向，不是候选人经历事实来源，也不保证模型遵守方向或消除重复。

接口使用 Result/error/requestId 包装。缺失/null mode 与非法组合返回 HTTP 400 / 10001；未知枚举、空字符串、不可解析 JSON 或缺失请求体返回 HTTP 400 / 10003。旧版无请求体 Create 不再是兼容入口；旧持久化 Interview 的 COMPREHENSIVE/null 默认事实保留，但不意味着旧无 Plan Interview 可继续 V1 Turn/Skip。共用详情响应的操作返回 mode/focusArea/planPrepared；不暴露 PlanItem 内容、AI 临时引用或新增计划状态。HTTP 字段与参数校验对照 [InterviewController](../../interview-agent-interview/src/main/java/com/linj6/interviewagent/interview/interfaces/controller/InterviewController.java) 及同模块 request/response DTO。

### 面试模式（早期产品设想，V1 以以上 Contract 为准）

**完全面试**

由 AI 自主决定面试方向、问题和追问。

**指定方向**

用户主动指定面试方向，例如：

```text
Java 并发
Redis
MySQL
RabbitMQ
```

### 面试类型（早期产品设想，非 V1 独立配置）

MVP 支持：

```text
综合面试
技术专项面试
项目面试
项目 + 技术综合面试
```

> 面试模式与面试类型存在一定交叉，具体 UI 和最终枚举结构在 Phase 3 详细设计阶段进一步统一，避免重复配置。

### 技术方向

Question Quality V1 仅 TECHNICAL_FOCUS 选择一个 V1 KnowledgeArea 作为 focusArea；其余模式不允许提供。上文岗位技术主题示例不等于 V1 Area 枚举。

### 项目选择（早期产品设想，非当前用户配置）

用户可以选择 0 个、1 个或多个项目。

如果是项目面试或项目 + 技术综合面试，则至少选择一个项目。

### 面试难度（早期产品设想，非当前用户配置）

MVP 支持不同难度等级：

```text
初级
中级
高级
专家
```

具体等级划分在详细设计阶段确定。

### 面试时长（早期产品设想，非当前用户配置）

建议 MVP 提供：

```text
30 分钟
60 分钟
90 分钟
120 分钟
```

用户也可以在达到配置时长之前主动结束面试。

---

## AI 面试

AI 是系统核心。

AI 面试官需要结合：

```text
Candidate Profile
+
Resume
+
Projects
+
Target Position
+
Interview Configuration
+
Interview History（后续逐步引入）
```

形成当前面试上下文。

### 问题生成

MVP 阶段允许由 LLM 动态生成问题。

AI 应考虑：

- 目标岗位
- 技术方向
- 面试类型
- 用户技术栈
- 简历内容
- 项目经历
- 当前面试进度
- 已问问题
- 用户之前的回答

避免连续生成重复或明显无关的问题。

### 后续演进

后续可以从：

```text
纯 LLM 动态生成
```

演进为：

```text
题库
+
LLM 动态生成
+
历史表现
+
Evaluator
```

提高问题质量和稳定性。

---

## 动态追问

动态追问是 MVP 的核心能力之一。

AI 应根据用户回答判断：

```text
是否回答正确？
是否完整？
是否存在逻辑漏洞？
是否存在值得深入的问题？
是否与简历中的重要信息冲突？
是否暴露知识薄弱点？
```

然后决定：

```text
继续追问
      或
切换问题
```

追问可以逐层深入：

```text
基础概念
  ↓
实现原理
  ↓
为什么这样设计
  ↓
替代方案
  ↓
优缺点
  ↓
异常情况
  ↓
生产实践
  ↓
性能 / 并发
  ↓
故障排查
```

---

## 简历 / 项目深挖

AI 可以主动识别简历中的：

- 关键技术
- 性能指标
- 项目成果
- 架构描述
- 高价值经历
- 可能存在夸大或表述不清的内容

例如：

> “通过 Redis 将系统 QPS 提升 10 倍。”

AI 可以继续询问：

```text
提升前 QPS 是多少？
提升后 QPS 是多少？
瓶颈在哪里？
为什么选择 Redis？
缓存结构是什么？
如何保证缓存一致性？
如何验证性能提升？
是否进行过压测？
压测环境是什么？
如果缓存失效怎么办？
```

因此系统需要具备：

**Resume Deep Dive / Project Deep Dive**

能力。

---

## AI 评估

### 评估时机

AI 在面试过程中进行内部评估，但：

> **面试过程中不向用户展示评分。**

面试结束后统一展示详细评分。

### 评估维度

| 维度 | 说明 |
|---|---|
| 知识准确性 | 技术事实是否正确 |
| 完整性 | 是否覆盖问题核心要点 |
| 深度 | 是否理解底层原理及设计原因 |
| 沟通表达 | 是否能够清晰表达 |
| 逻辑能力 | 回答是否结构清晰、前后连贯 |
| 项目经验 | 是否具有真实项目实践能力 |
| 追问应变 | 面对进一步追问时的表现 |

### 评分结果

当前形成逐题总分、维度评分、优势、问题、遗漏点和参考回答，完成后在 Review 中展示；不生成整场总体评分或能力画像。早期能力分析目标保留为后续产品设想，不作为当前发布验收要求。

---

## 完整面试复盘

完整复盘只允许在 Interview 进入 `COMPLETED` 后查看。V0.1 不额外调用 LLM 生成整场 Report，而是基于已持久化业务事实动态组装。

### 面试概览

包括开始时间、结束时间、实际持续时长、MAIN 数、FOLLOW_UP 数、已回答数、跳过数和未回答数。第一版不生成综合得分、能力等级或整场 AI 总结。

### 问题链与逐题评价

按 MAIN + FOLLOW_UP 两层结构展示。已回答题展示用户回答、overallScore、strengths、weaknesses、missingPoints、referenceAnswer，以及适用维度的名称、分数和解释。跳过题显示为 SKIPPED；Interview 完成时仍处于 WAITING_ANSWER 的题在复盘中派生为 UNANSWERED。
## 面试过程控制

### 暂停

用户可以暂停面试。

暂停后保存：

- 当前面试状态
- 当前问题
- 已产生的回答
- 恢复所需的 MySQL 业务事实；不持久化 AI Context 或 Graph Checkpoint

后续可以继续面试。

### 跳过

用户可以跳过当前问题。

跳过的问题：

- 不要求回答
- 不阻止继续面试
- 必须被系统记录

跳过 / 未回答本身也是训练信号，必须在完整复盘中保留。

### 主动结束

用户可以在达到配置时间之前主动结束面试。

系统进入 `COMPLETED` 后应允许查看当前已形成业务事实的完整复盘。

---

## 面试状态

生命周期与可恢复阶段以 [领域模型](../02-architecture/domain-model.md) 为准；当前不使用早期预留的 FAILED / INTERRUPTED。Pause 仅在 WAITING_ANSWER 稳定边界允许，Resume 继续同题。Turn 失败保留已提交 Answer 与 PROCESSING_TURN，从业务事实重建并重跑完整 Turn，不恢复 Graph Node 快照。

### 异常恢复

以下情况不能导致已经完成的面试内容无故丢失：

- 浏览器关闭
- 网络断开
- LLM 调用超时
- LLM 服务异常
- 后端服务异常
- 页面刷新

---

## 面试历史（早期需求，当前未提供列表）

用户可以查看历史面试。

列表至少包括：

- 面试时间
- 目标岗位
- 面试类型
- 面试时长
- 问题数量
- 总体评分
- 面试状态

点击后可以查看完整面试复盘。

---

## 用户反馈（早期需求，当前未实现）

用户可以针对 AI 的评分或评价提交反馈。

例如：

```text
AI 给我的这个问题评分过低，因为我的实际项目中采用的是另一种方案。
```

### 评分不可修改

AI 原始评分必须保留。

用户反馈不能直接覆盖原始评分。

系统保存：

```text
Original AI Evaluation
        +
User Feedback
```

未来可以形成：

```text
AI Evaluation
+
User Feedback
+
External / Manual Evaluation
```

作为 Evaluator 优化数据。

---

# 6. 核心业务流程

## 准备阶段（原始设想，当前使用演示资料）

早期设想包含登录、维护 Candidate/简历/项目和选择岗位；当前使用固定演示资料，不要求用户先完成这些未提供的操作。

## 创建面试

```text
选择 InterviewMode
 ↓
仅 TECHNICAL_FOCUS 选择一个 KnowledgeArea
 ↓
Create 保存 mode / focusArea
 ↓
Prepare 形成正式 InterviewPlan（仍为 CREATED）
 ↓
Start 使用 initial PlanItem 生成首题
```

数量和时长使用服务端配置；不提供独立面试类型、难度、项目或时长选择。

## AI 面试循环

```text
AI 生成问题
 ↓
用户回答
 ↓
AI 内部评估
 ↓
判断是否需要追问
 ├── 是 → 生成追问 → 用户回答
 │                ↓
 │              内部评估
 │                ↓
 └───────────────┘
 ↓
判断是否继续当前主题
 ├── 是 → 继续追问
 └── 否 → 生成下一问题
 ↓
判断是否达到结束条件
 ├── 否 → 继续
 └── 是 → 面试结束
 ↓
查看完整面试复盘
```

## 面试结束

```text
面试结束
 ↓
读取 Interview / Question / Answer / Evaluation
 ↓
组装面试客观概览
 ↓
按 MAIN + FOLLOW_UP 组织完整问题链
 ↓
展示回答与逐题 Evaluation
```

---

# 7. AI 行为要求

## AI 应该像真实面试官

AI 不应该：

- 每个问题都给出提示
- 用户答错后立即告诉答案
- 在面试过程中展示评分
- 机械按照固定题目执行
- 只关注知识点而忽略项目经历

AI 应该：

- 根据回答动态追问
- 主动发现问题
- 深挖简历和项目
- 根据岗位调整问题
- 根据剩余时间调整面试策略
- 根据候选人的表现动态调整难度

## AI 应保持面试目标一致

AI 的目标不是让用户“答对尽可能多的问题”。

目标是：

> 在有限面试时间内尽可能准确地评估候选人的岗位胜任能力。

因此 AI 可以主动选择：

- 深挖
- 切换主题
- 提高难度
- 降低难度
- 追问项目
- 验证简历中的关键描述

---

# 8. 数据与业务规则

## Resume 与 Position

```text
Resume N : M Position
```

同一份简历可以关联多个岗位。

## Interview 与 Project

```text
Interview N : M Project
```

一次面试可以选择多个项目。

## Position 与 Topic

二者必须独立建模：

```text
Position
   ↓
多个 Topic
```

例如：

```text
Java 后端开发
 ├── Java
 ├── Spring
 ├── MySQL
 ├── Redis
 ├── MQ
 ├── JVM
 └── 分布式系统
```

## Interview 与 Evaluation

一场面试包含多个问题。

一个问题可能存在：

```text
Question
 ├── Answer
 ├── Follow-up
 └── Evaluation
```

后续需要支持完整追问链路。

## Evaluation 不可变

AI 原始评估结果必须保留。

用户反馈作为独立数据保存。

未来优化 Evaluator 时：

```text
原始 AI 评价
      +
用户反馈
      +
人工 / 真实面试验证
      ↓
Evaluation Dataset
      ↓
优化 Evaluator
      ↓
回归测试
```

---

# 9. 非功能需求

## 性能

MVP 主要服务单个用户，不以大规模并发为目标。

要求：

- 页面操作响应正常
- 面试过程流畅
- 当前正式业务输出不使用 Streaming；流式体验是早期设想，不属于当前 v0.1 需求
- 不因单次 LLM 调用导致整个面试状态丢失

后续商业化再考虑大规模并发。

## 可用性与恢复

必须考虑 LLM 调用不稳定的问题：

```text
LLM Timeout
LLM Error
Network Error
Backend Error
Browser Refresh
```

不能导致已经完成的面试数据全部丢失。

## 安全

公开部署时需要保护：

- LLM API Key
- 数据库账号密码
- 用户简历
- 项目经历
- 面试记录
- 用户个人信息

敏感配置不能硬编码在代码仓库中。

## 可观测性

系统需要逐步具备：

- LLM 调用日志
- LLM 调用耗时
- Token 使用量
- 调用失败记录
- Prompt 版本
- Evaluator 版本
- 面试状态
- AI 生成问题记录
- AI 评价记录

这些数据既用于排障，也用于后续 AI 能力迭代。

---

# 10. 原始 MVP 范围（历史参考）

## 原始 MVP 目标清单（不表示实现完成）

### 用户

- 简单登录

### Candidate Profile

- 基本信息
- 工作经历
- 技术栈

### 简历

- PDF 上传
- Word 上传
- 文本粘贴
- 简历管理
- 多岗位关联

### 项目

- 项目经历管理
- 一次面试选择多个项目

### 岗位

- Java 后端开发
- AI 应用开发
- LLM 应用开发
- Agent 开发
- 技术主题管理

### 面试

- 完全面试
- 指定方向面试
- 综合面试
- 技术专项面试
- 项目面试
- 项目 + 技术综合面试
- 难度配置
- 时长配置
- 动态问题生成
- 动态追问
- 简历深挖
- 项目深挖
- 暂停
- 继续
- 跳过
- 主动结束

### AI 评估

- 内部实时评估
- 面试过程中隐藏评分
- 面试结束后统一展示
- 多维度评分
- 参考答案
- 答案差距分析

### 完整面试复盘

- 面试客观概览
- MAIN / FOLLOW_UP 完整问题链
- 用户回答
- 逐题多维 Evaluation
- 跳过与未回答记录

### 历史

- 面试历史列表
- 面试详情

### 反馈

- 用户评价反馈
- AI 原始评分不可覆盖

---

# 11. 非 MVP 范围

暂不实现：

- 语音面试
- 视频面试
- 复杂 RAG 题库
- Multi-Agent
- 复杂长期 Memory
- MCP
- Skill
- Deep Research
- 自动求职
- 多租户
- 企业招聘后台
- 支付 / 会员 / SaaS 商业化

这些能力在后续版本根据真实需求逐步引入。

---

# 12. 原始 MVP 验收标准（历史参考）

## 基础资料

用户能够：

```text
登录
 ↓
创建 Candidate Profile
 ↓
上传 / 粘贴简历
 ↓
创建项目经历
 ↓
选择目标岗位
```

## 创建面试

用户能够：

```text
选择岗位
 ↓
选择面试模式
 ↓
选择面试类型
 ↓
选择技术方向
 ↓
选择项目
 ↓
选择难度
 ↓
选择面试时长
 ↓
开始面试
```

## 面试

系统能够：

- AI 自动生成问题
- 用户输入文本答案
- AI 根据回答动态追问
- AI 主动深挖简历
- AI 主动深挖项目
- 支持跳过
- 支持暂停
- 支持继续
- 支持提前结束
- 面试过程中不展示评分

## 面试结束

系统能够查看完整复盘：

- 面试开始、完成与实际持续时间
- MAIN / FOLLOW_UP 数量及回答状态统计
- MAIN + FOLLOW_UP 完整问题链
- 用户回答
- 逐题评分、优势、问题、遗漏点和参考答案
- 适用维度的评分与解释

## 数据完整性

系统能够保存：

```text
Interview
 ├── Question
 ├── Answer
 ├── Follow-up
 ├── Evaluation
 └── Evaluation
```

刷新页面、短暂网络异常或 LLM 调用失败后，不能无故丢失已经持久化的数据。

## 反馈

用户能够对 AI 的评分提出异议。

系统保留：

```text
Original Evaluation
+
User Feedback
```

不能直接覆盖原始评分。

---

# 13. 后续版本演进

## V0.1 —— MVP

目标：

> 跑通 AI 面试核心闭环。

```text
Candidate Profile
+
Resume
+
Project
+
Position
+
Interview
+
Dynamic Follow-up
+
Evaluation
+
Complete Review
```

## V0.2 —— 能力画像

增加：

- Interview Readiness Score
- 技术能力画像
- 知识点掌握度
- 多次面试趋势
- 薄弱点统计

## V0.3 —— RAG / 题库

从：

```text
LLM 动态生成
```

演进为：

```text
题库
+
RAG
+
LLM
+
历史数据
```

提高问题质量和稳定性。

## V0.4 —— 真实面试复盘

增加：

```text
真实面试记录
 ↓
用户录入面试问题
 ↓
用户记录自己的回答
 ↓
AI 分析
 ↓
与模拟面试对比
```

## V0.5+ —— AI 工程能力增强

逐步引入：

- Multi-Agent
- Memory
- MCP
- Skill
- Deep Research
- Voice
- Evaluator
- Evaluation Dataset
- Prompt Engineering
- Context Engineering
- Harness Engineering
- Loop Engineering

这些技术必须服务于真实产品需求，而不是为了“堆 AI 技术”。

---

# 14. 持续迭代闭环

这是整个项目最重要的长期设计。

```text
┌───────────────────────────────┐
│        学习 / 知识准备         │
└───────────────┬───────────────┘
                ↓
        AI 模拟面试
                ↓
          用户回答问题
                ↓
          AI 动态追问
                ↓
          AI 内部评估
                ↓
          完整面试复盘
                ↓
       发现能力短板 / 知识点
                ↓
          针对性学习训练
                ↓
          再次 AI 面试
                ↓
             真实面试
                ↓
          真实面试复盘
                ↓
       ┌────────┴─────────┐
       ↓                  ↓
   用户反馈          外部/人工评价
       └────────┬─────────┘
                ↓
        Evaluation Dataset
                ↓
       Prompt / Evaluator 优化
                ↓
             回归测试
                ↓
          再次模拟面试
                │
                └──────────────→ 持续迭代
```

系统最终希望从：

> “一个 AI 面试 Demo”

演进为：

> **“能够持续收集面试数据、发现能力问题、指导训练，并通过反馈不断改进 AI 评估质量的 AI 面试训练系统。”**

---

# 15. 关键设计原则

## 产品价值优先

不要为了展示 AI 技术而加入技术。

每一个 AI 能力都应该回答：

> 它是否让面试训练更真实、更准确或更有效？

## MVP 优先

第一阶段优先跑通：

```text
用户
 ↓
资料
 ↓
面试
 ↓
回答
 ↓
追问
 ↓
评估
 ↓
完整复盘
```

而不是一开始实现复杂的 Multi-Agent、MCP、Memory 等能力。

## AI 与传统工程结合

系统应该同时体现：

```text
传统后端工程能力
+
LLM 应用开发能力
+
Agent Engineering
+
Evaluation
+
持续迭代
```

## 原始数据不可丢失

对于：

- 用户回答
- AI 原始评分
- AI 原始评价
- Prompt 版本
- Evaluator 版本
- 用户反馈

原则上都应保留。

这些数据未来可能成为：

```text
Evaluation Dataset
Training Data
Regression Test Data
```

## AI 行为可解释、可追踪

后续应尽量能够回答：

> 为什么 AI 问这个问题？

> 为什么 AI 继续追问？

> 为什么这个回答得到这个分数？

> 当时使用的 Prompt 是哪个版本？

> 当时使用的 Evaluator 是哪个版本？

这会成为后续 Evaluation Engineering 的基础。

---

# 16. Phase 1 输出（历史记录）

完成本阶段后，应形成以下明确结论：

```text
产品目标              ✅
目标用户              ✅
目标岗位              ✅
核心用户故事          ✅
功能边界              ✅
面试流程              ✅
AI 行为规则           ✅
评分维度              ✅
数据业务规则          ✅
异常恢复要求          ✅
MVP 范围              ✅
非 MVP 范围            ✅
验收标准              ✅
后续演进方向          ✅
```

下一阶段：

> **Phase 2：技术选型 + 系统架构设计**

重点解决：

- Spring Boot / Spring AI / Spring AI Alibaba 的选型
- LLM 模型选择
- 数据库设计原则
- Redis 是否需要
- 向量数据库是否在 MVP 引入
- SSE / WebSocket / HTTP Streaming 选择
- AI Agent 架构
- Interview State 管理
- Prompt 管理
- Evaluator 架构
- 会话与上下文管理
- 文件解析架构
- 异步任务架构
- 日志与可观测性
- 部署架构
- API 设计原则
- 安全设计
- 项目编码规范
- 测试策略
- ADR 决策记录
