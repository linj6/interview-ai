# Interview Agent Prompt 设计

**版本：V1.5**

**状态：[已确认]**

## 目标

Prompt 需要可读、可追踪、可测试，并与 Context、EvaluationProfile、Java确定性规则和业务状态保持清晰边界。

Phase 2 只确认最小架构原则。Prompt 正文、模板语法和变量名称在 Phase 3 实现时确定。

## Prompt 类型

V0.1 主要包括：

- Opening Prompt；
- Question Generation Prompt；
- Evaluation Prompt；
- Decision Prompt；
- Report Prompt。

每类 Prompt 服务一个明确 AI Task，不建设通用 Prompt 平台。

## System / User 边界

### System Prompt

负责相对稳定的内容：

- 模型角色；
- 任务原则；
- 安全与禁止事项；
- 输出语义；
- Structured Output 等稳定约束。

### User Prompt

负责当前执行的动态内容：

- 当前 Task；
- AI Context；
- Question / Answer；
- Evaluation Rubric；
- 当前允许的 Decision Actions 等动态输入。

逻辑上可以分段组织，但 V0.1 使用 System / User 两层即可，不额外建设复杂 Prompt 继承或组合框架。

## Prompt 语言

新增或修改 LLM System Prompt 和 User Prompt 时，默认优先使用中文；只有明确需要英文，或协议、模型有特殊要求时才例外。代码标识符、协议字段及模型要求的固定输出格式保持原样。历史英文 Prompt 的整理单独记录在 [`technical-debt.md`](../00-project/technical-debt.md)，不要求在当前功能改动中顺带改写。

## Context Boundary

```text
Business Input
      ↓
AI Context Builder
      ↓
AI Context
      ↓
Prompt Template
      ↓
LLM
```

Prompt 消费已经选择、裁剪和组织后的 AI Context，不直接查询业务模块，也不承担 Context Window 管理。

## Question Grounding MVP

Question Quality V1 采用 Prepare Planner → PlanItem 选择 → MAIN / FOLLOW_UP 生成流程，替代运行中 Focus Selection。设计已确认、实现待完成；以 Contract 和 System Prompt 明确输入的事实权限：

| 输入 | 候选人经历事实权限 |
|---|---|
| Candidate/Profile 中的简历与项目资料 | MAIN 和 FOLLOW_UP 的候选人经历事实来源；事实关系仍须由资料明确支持 |
| 当前 Answer | 仅 FOLLOW_UP 可作为候选人本轮自述事实，不代表外部核实 |
| PlanItem 的 area / focus / reason | 只决定考察方向，不是候选人经历摘要，不构成事实来源 |
| 岗位资料 | 提供岗位要求，不证明候选人经历 |
| 当前 Question、Evaluation feedback、missingPoint、followUpTarget | 提供提问上下文、评价或追问方向，不能自动视为候选人已实施事实 |

Public Profile V0.1 的 Projects 在 Prompt formatting 边界按项目编号、name、description 和明确结束分隔逐项展示，不提前合并为摘要。不同项目的经历、行为、场景、技术措施和结果不得无依据混合归因；空列表表示未提供项目经历，不推断候选人做过项目。此规则用于 Planner、MAIN、FOLLOW_UP、Evaluation、Decision 及保留的 Focus Prompt。

Fact Association Error：同项目、相邻文本或相关技术不代表属于同一场景；不得跨场景重新组合问题、措施和结果，也不得补全或建立资料未明确支持的事实关系。候选人经历前提及事实关系必须由允许的事实来源明确支持。

Over-specific Question：未提供的实现细节使用开放问题，不作为既定事实预设。FOLLOW_UP 若发现当前问题有未经支持的前提，应开放澄清，不继续强化该前提。

FOLLOW_UP 事实权限：当前 Answer 是候选人本轮陈述的事实来源；当前 Question 仅提供考察上下文，不是 Candidate Fact Source；followUpTarget 仅表示追问方向，不代表候选人确认过该事实。Candidate/Profile 保留经历背景职责，不能替代 Answer 确认本轮事实、覆盖明确否定或将假设/建议写成历史实现。“你提到 / 你做过”等本轮归因必须由当前 Answer 明确支持。

- 未提及：在当前 target 内开放澄清是否存在及如果存在如何处理，不预设已实施。
- 明确否定：保留否定，不询问被否定事实的实际实施过程，不重复确认已否定事实；只开放询问 target 内仍未澄清的信息或明确标记的假设思考。
- 假设方案：保留假设/建议语气，不反推为历史实现或结果。
- 明确确认：可归因，但不超出 Answer 支持的行为、场景与结果；未确认细节仍开放询问。
- 不知道、记不清或无法确认：保留无法确认的范围，不改写为已确认事实，也不要求再次回忆同一细节；可开放追问同一 target 内仍独立可回答的信息，或提出不依赖该历史细节回忆的假设问题。“如果”不能包装重复回忆要求。

“你刚才提到 / 根据你刚才说明”等归因同样只允许引用当前 Answer 明确支持的事实。target 比 Answer 更强的措辞、Question 前提或模型推断不能补足事实依据；也不能省略归因词而暗中预设事实。例如 Answer 为“用过 MAT，但不记得 Dominator Tree 的具体信息”时，不能因 target 提到“通过 Dominator Tree 发现泄漏对象及引用链”而归因该发现，也不能再次要求回忆当时的对象、指标或引用链；可在同一方向提出重新排查时如何验证对象持续持有的假设问题。

FOLLOW_UP 单 target：followUpTarget 是本次唯一允许考察的目标；Decision 已完成目标选择后，Generator 不再接收 strengths / weaknesses / missingPoints 或整份 Evaluation，不选择、补充或合并其他目标，即使技术相关。保留当前问答与现有候选人/岗位背景，Structured Output Contract 不变。不得自行补充未支持的业务行为、数据流、写操作、组件、配置、机制或实施结果。

当前仍是 Prompt/Contract 级防护，不具备确定性 grounding 保证。Schema / Prompt 静态测试只验证核心约束存在、字段结构和 MAIN/FOLLOW_UP 输入边界；不通过生成文本关键词断言语义正确性。本轮不引入 Scenario Domain Model、Fact Graph、Grounding Validator 或正式 QuestionGroundingSmokeIT。

## Decision Information Gain / Closed Target

Decision 在选择 FOLLOW_UP 前，结合当前 Question / Answer 判断候选 target 是否仍有尚未澄清、候选人可能回答的有效信息。Evaluation missingPoints 只提供候选目标；某事实仍被列为 missingPoint，不构成继续追问的充分理由。

候选人明确“没有做 / 没发生 / 不知道 / 记不清”时，关闭其明确否定或无法回答的事实范围，不得换一种说法重复逼问。关闭按事实范围判断：“不知道 / 记不清”不自动关闭整个 target；同一 target 内独立、尚未澄清且可能回答的信息仍可 FOLLOW_UP。例如“没有发生过并发问题”关闭对该问题实际处理过程的追问；“记不清具体 QPS，但参与过瓶颈定位”只关闭具体 QPS，仍可澄清尚未说明的瓶颈定位过程。“做过压测”但未说明如何定位瓶颈且未表示无法回答，属于可继续澄清的信息。

所有 target 已关闭或没有有效信息可获取时，在 AllowedActions 内优先 NEXT_QUESTION；NEXT_QUESTION 不被允许时选择允许的 END，不为用完追问次数选择 FOLLOW_UP。reason 说明动作依据；FOLLOW_UP 说明单一 target 内仍可澄清的具体信息，部分关闭时明确排除已关闭范围，不生成问题。

本规则属于 Decision Prompt/Contract 的语义约束，FOLLOW_UP 保留 action / reason / targetRef 结构；V1 NEXT_QUESTION 增加 planItemRef，见 [Workflow 设计](agent-design.md)；保留 missingPoints 到 M* 的映射与 Java Validator/Resolver。Closed Target 不新增字段或持久化状态，也不新增 AI 调用或 Information Gain Validator；Evaluator 与 Follow-up Question Generator 保持现状。静态测试锁定 Prompt 核心约束，不证明模型实际遵守或提供确定性信息增益保证。

## Evaluation Prompt

```text
EvaluationProfile
      ↓
LLM-facing Rubric
      ↓
Evaluation Prompt
      ↓
Question + Answer + AI Context
      ↓
Structured EvaluationProposal
```

EvaluationProfile 定义“按什么规则评价”；Prompt 定义“如何要求 LLM 执行评价”；Evaluator 负责组合输入、调用模型、校验、计算和结果组装。

注入 LLM 的 Profile 内容包括 Dimension、Evaluation Criteria、Score Guidance、Score Range / Step 与 N/A 约束。

Profile Weight、Overall Score、Profile ID 和业务状态变化不提供给 LLM，也不由 Prompt 决定。

## Deterministic Boundary

Prompt 不承担：

- Interview 状态流转；
- Profile Weight 与总分计算；
- Proposal 是否通过 Java校验；
- Evaluation 是否成为业务事实；
- Decision Proposal 是否可以改变业务状态；
- 事务、并发和幂等规则。

LLM 输出始终是 Proposal，最终由 Java、Application 与 Domain 处理。

## Storage and Lightweight Versioning

V0.1：

- Prompt 保存在代码或 Resource 文件；
- 通过 Git 管理变更；
- 使用手动维护的轻量 `promptVersion`；
- 每类 Capability 先维护一套主模板。

不建设：

- Prompt 数据库表；
- Prompt Registry；
- 在线编辑；
- 模板发布 / 灰度 / 自动回滚；
- A/B Test；
- 自动 Prompt 优化；
- 根据模型动态切换多套 Prompt。

## Minimum Testing

Phase 3 为关键 Prompt 准备少量固定回归样例，覆盖正常回答、错误回答、空回答、项目题和非法 Structured Output 等典型情况。

V0.1 不建设完整 Prompt Evaluation 平台或大规模 Dataset。

## Phase 3 Implementation Decisions

以下内容不再作为 Phase 2 前置设计：

- Prompt 正文；
- 文件目录的最终组织；
- 模板引擎与语法；
- 变量名称和 Java类型；
- 各 Prompt 的精确 Token 配额；
- 回归样例的最终数量；
- Provider / Model 特定微调。

Phase 3 应先使用简单 Resource Template 和 System / User 消息组合跑通完整面试，再根据真实效果迭代。

## Evaluator 评分语义边界

Evaluation Prompt 与 Structured Output Contract 同时声明 [Evaluator 设计](evaluator-design.md) 中的当前 Question / Answer 评分边界：Accuracy 不因未提及知识扣分，Completeness 只覆盖题目明确要求，Depth 只沿已回答话题纵向评价。Weakness 和 MissingPoint 必须有当前回答或题目依据；明确不知道、没做、没发生或记不清不自动表示能力不足。Reference Answer 是复盘材料，不是评分 checklist，不允许知识点 diff 反向生成评分或反馈。Accuracy 扣分必须指出已表达内容的具体错误或明显不合理之处，不能以未展开机制为理由；反馈保留“当时没做”等有效历史事实，不能将当前重新设计思考反推为历史实现。ANSWER_FRAMEWORK 明确区分来源支持的历史事实、明确不知道或未做的信息和当前建议，后两类不得写成历史实现。

本次使用 `evaluation-v3` 追踪 Prompt/Contract 修正，不改变 Evaluation DTO、Profile 记录、数据库、HTTP API、确定性计算或 AI 调用数量。静态 Prompt / Schema 测试验证约束进入模型输入，不证明真实模型每次都正确遵循语义；Interview 25 类场景的真实验收需显式 opt-in，不属于默认测试。
