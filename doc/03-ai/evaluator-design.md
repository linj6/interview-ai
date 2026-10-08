# Interview Agent Evaluator 设计

**版本：V1.3**
**状态：[已确认]**

## 定位与核心原则

Evaluator 是 AI 模块内部的评价能力组件，在 Turn Workflow 的 EvaluateNode 中使用。

核心原则：

> **LLM 负责语义评价，Java 负责结构校验、确定性计算和结果组装；Application / Domain 负责将结果转化为正式业务事实。**

逻辑链路：

```text
EvaluationProfile + Question + Answer + AI Context
        ↓
Evaluator 构建 Prompt
        ↓
LLM Structured EvaluationProposal
        ↓
Java Validation
        ↓
Java Score Calculation
        ↓
Validated EvaluationResult
        ↓
Application + Domain
        ↓
Official Evaluation
```

Structured Output 只约束输出形状，不能替代 Java 的业务校验。

## EvaluationProposal

LLM 不直接输出完整持久化 Evaluation，而只输出语义评价提案：

```text
EvaluationProposal
├── DimensionEvaluations[]
│   ├── DimensionCode
│   ├── Applicability
│   ├── Score
│   ├── ScoreExplanation
│   └── Evidence[]
├── Strengths[]
├── Weaknesses[]
├── MissingPoints[]
└── ReferenceAnswer
```

LLM 不输出或决定：

- weight / normalizedWeight / weightedScore；
- overallScore10 / overallScore100；
- profileId；
- promptVersion / evaluatorVersion；
- model / tokenUsage / latency。

这些信息由 Java 从 Profile、运行配置和调用元数据中取得并组装。

具体 Java DTO、JSON Schema 和 Spring AI 实现留到 Phase 3。

## Evaluation Dimensions

V0.1 使用七个稳定维度：

```text
ACCURACY
COMPLETENESS
DEPTH
EXPRESSION
LOGIC
PROJECT_EXPERIENCE
ADAPTABILITY
```

Dimension Definition 表达维度长期稳定的基本含义；Profile Dimension Rubric 表达该评价场景下如何应用该维度。

## 当前 Question / Answer 的评分语义（[已确认]）

Evaluation 主要评价当前 Question 下当前 Answer 的实际质量。背景和岗位资料用于理解上下文，不增加题目未要求的评分知识点；Profile Rubric 在以下维度边界内应用，不改变 Profile 记录、权重和 Java 确定性计算。

- Accuracy：只评价 Answer 实际表达的技术事实、因果关系和判断是否正确；未提及知识点不能扣分。只能因已表达的事实、因果或判断错误或明显不合理扣分，必须指出具体问题；未提及、未展开或未深入讨论机制本身不能作为扣分理由，即使该机制与题目相关。
- Completeness：只评价 Question 明确要求的信息覆盖；不因题外理想知识点缺失扣分。
- Depth：评价已回答内容是否解释机制、依据、权衡、边界或结果；可以沿已回答话题纵向评价，不得横向扩展新知识点后因未提及扣分。

“不知道 / 没做 / 没发生 / 记不清”可能使题目明确要求的信息尚未获得，可影响 Completeness 或形成 MissingPoint，但须准确说明原因，不推断为技术能力不足，也不要求编造不存在事件的处理过程。信息缺失或解释不足不自动成为 Accuracy 的事实错误。评分说明、Evidence 说明和反馈关联维度遵循相同边界。

例如，题目询问本地缓存与并发消费如何缓解消息堆积，回答未展开 Canal / Redis / 缓存一致性时，不能仅因缺少这些题外知识扣 Accuracy / Completeness / Depth 或生成 Weakness / MissingPoint。题目明确询问 QPS 而候选人记不清时，可记录指标未获得及原因，不能推断其不会压测。

候选人明确“当时没做 / 没发生 / 没有专门方案”是有效的历史事实回答，不能自动转化为能力 Weakness。MissingPoint 的 description / explanation 必须保持历史事实边界：已经明确没做或没发生的事实不是仍未回答的历史实现，不得要求补出不存在的方案；仍可记录题目明确要求但尚未获得的信息或回答展开后值得澄清的信息。候选人明确“如果现在重新设计 / 现在会考虑”的方案只代表当前思考，可以澄清该思考，不得反推为历史项目应该存在的实现。

## Dimension Evaluation

每个 Profile 已配置的维度必须在 Proposal 中返回：

```text
DimensionEvaluation
├── dimensionCode
├── applicability
├── score
├── scoreExplanation
└── evidence[]
```

Applicability 仅包括：

```text
APPLICABLE
NOT_APPLICABLE
```

约束：

- APPLICABLE 必须有 Score；
- NOT_APPLICABLE 的 Score 必须为空；
- APPLICABLE 维度至少有一条 Evidence；
- NOT_APPLICABLE 必须说明原因，Evidence 为空；
- 缺少 Profile 维度、返回额外维度或重复维度均为非法输出。

Score 范围为 0～10，最小步长为 0.5。

`scoreExplanation` 是面向用户的评分说明，即根据 Rubric 综合 Evidence、错误与缺失，解释为什么得到该分数。它不是模型内部 Chain of Thought。

## N/A 语义与约束

必须区分：

- Profile 未配置某维度：该维度不进入本次评价，LLM 不返回；
- Profile 已配置但客观上缺少合理评价条件：在 Profile 允许时返回 NOT_APPLICABLE。

Profile 中已配置的维度默认必须评分；只有 Profile 显式允许 N/A 的维度，LLM 才能返回 NOT_APPLICABLE。

只要 Question 已提供评价机会，回答错误、肤浅、缺少案例、空回答或跳过，都应给低分，不能使用 N/A。N/A 不能表示“回答差”或“模型不知道如何评分”。

Java 负责验证 Applicability 是否符合 Profile 约束。

## Score Calculation

LLM 不计算权重、加权分或总分。

NOT_APPLICABLE 不按 0 分处理，也不进入分子和分母。Java 对适用维度权重重新归一化：

```text
applicableWeightSum
    = Σ applicable dimension weight

overallScore10
    = Σ(score × weight) / applicableWeightSum

overallScore100
    = overallScore10 × 10
```

若所有维度均为 N/A、有效权重为 0 或 Proposal 违反 Profile 约束，本次 Proposal 无效，不生成正式 Evaluation。

计算过程中保留足够精度，最后统一舍入；BigDecimal scale 与舍入实现留到 Phase 3。

## Evidence

Evidence 是 Dimension Score 的可追溯评分论据：

```text
Evidence
    ↓
ScoreExplanation
    ↓
Score
```

MVP 支持：

```text
ANSWER_QUOTE
OVERALL_OBSERVATION
```

调用 Evaluator 前，Java 将 Answer 按非空行切分为有序 Fragment：每行 trim 后过滤空行，并按顺序编号为 E1、E2、E3……。同一份 Fragment 集合同时提供给 Evaluator Prompt 和 Java 校验，不重复切分。此编号只属于 AI Evaluator 内部协议。

`ANSWER_QUOTE` 通过 `evidenceRef` 引用一个存在的 Fragment 编号；每条 Evidence 最多引用一个 Fragment。Java 严格校验编号及存在性，并将合法编号确定性解析为对应 Fragment 的 trim 后原文。`OVERALL_OBSERVATION` 的 `evidenceRef` 必须为 null。解析后的 Evidence 使用原文 quote 进入 Validated Evaluation，持久化模型与 Review API 不变。无效 ref 作为 Validation Failure 进入既有完整 Turn Retry 流程；不做模糊匹配、自动纠错或降级。

OVERALL_OBSERVATION 不携带伪造 Quote，用于表达、逻辑等需要观察整体回答的维度。

每个 APPLICABLE 维度至少一条 Evidence，通常控制在 1～3 条。NOT_APPLICABLE 的 Evidence 必须为空。

V0.1 Evidence Source 仅限当前 Answer；简历、知识库和外部来源以后按真实需求扩展。

## Strengths / Weaknesses / Missing Points

三者属于 Evaluation 顶层综合反馈，不嵌入每个 Dimension：

- Strength：已经回答且表现好的内容；
- Weakness：当前 Answer 已实际暴露出的回答质量或技术问题；未验证、未提及或记不清不能自动推断为能力弱点；
- Missing Point：Question 明确要求但尚未获得的信息，或 Answer 主动展开后仍存在的关键未澄清点；必须从当前 Question + Answer 得到依据，不得仅依据 Reference Answer、岗位要求或通用最佳实践生成。

Strength / Weakness 逻辑结构：

```text
description
relatedDimensionCodes[]
```

Missing Point 逻辑结构：

```text
description
importance: HIGH | MEDIUM | LOW
explanation
relatedDimensionCodes[]
```

MVP 不直接关联 Evidence ID，也不重复保存 Evidence；通过 Related Dimension 间接追踪 Dimension Evidence。

数量建议：

- Strengths：0～3；
- Weaknesses：0～3；
- Missing Points：0～5。

允许为空，不为满足数量而生成低价值内容。反馈只能关联 APPLICABLE 维度，N/A 维度不产生这些反馈。

## Reference Answer

Reference Answer 定位为“参考回答”，不是唯一正确的标准答案，也不参与当前评分。

逻辑结构：

```text
ReferenceAnswer
├── answerForm
│   ├── SAMPLE_ANSWER
│   └── ANSWER_FRAMEWORK
├── keyPoints[]
└── content
```

- 知识题、原理题和通用方案题通常使用 SAMPLE_ANSWER；
- 项目经历题、行为题等依赖真实经历的问题使用 ANSWER_FRAMEWORK；
- 每份成功的正式 Evaluation 都提供 Reference Answer，可按问题复杂度精简；
- Reference Answer 应保留正确内容、修正错误、补充重要 Missing Points 并改善表达；
- HIGH Missing Point 原则上必须覆盖，MEDIUM 尽量覆盖，LOW 可选择性覆盖；
- Reference Answer 用于复盘当前 Question，不是评分 checklist；先基于 Question + Answer 形成评价，再组织参考回答；Score / Dimension / Weakness / MissingPoint 不得通过与 Reference Answer 做知识点 diff 得出，keyPoints 不定义新的扣分项；
- Reference Answer 不能作为 Evidence，也不能反向影响 Score；
- 项目经验类内容不得虚构候选人的经历、数据、职责或效果，只能使用可信 Context 中的事实或提供待补充框架；不得补造未提供的具体实现、配置、指标或效果，通用建议保持为建议或可讨论方向。

ANSWER_FRAMEWORK 的 content 和 keyPoints 必须明确区分：

- Candidate/Profile/Answer 支持的历史事实；
- 候选人明确不知道、没做、没发生或记不清的信息；
- 当前可进一步讨论或改进的技术建议。

后两类不得写成候选人的历史项目实现。未获上述来源支持的具体实现、配置、指标、机制或效果不得补入历史项目回答；建议如需出现，明确标记为“如果现在重新设计 / 可进一步考虑”等建议语义。

## EvaluationProfile / Prompt / Evaluator

三者职责分离：

```text
EvaluationProfile = 评价规则
Prompt Template    = 如何要求 LLM 执行评价
Evaluator          = 编排、调用、校验、计算与组装
```

Profile 逻辑上包含：

- Dimension Code / Name；
- Evaluation Criteria；
- Score Guidance；
- Score Range / Step；
- Weight；
- N/A 约束；
- Sort Order。

Profile Weight 不提供给 LLM，只供 Java 计算。LLM 接收 Dimension、Criteria、Score Guidance、Score Range / Step 与 N/A 约束。

Question 正式持久化时绑定确定的、不可漂移的 Profile 记录。AI 最多从系统提供的候选 Profile 中选择或提出建议，最终由 Java 校验并绑定。Evaluator 不能自行创建、修改或临时调整 Profile。

MVP 按评价意图维护少量 Profile，不按岗位、Topic、难度建立组合式 Profile。

## Lightweight Traceability

V0.1 不建设 Profile Version Manager、Prompt Registry、在线编辑、发布、灰度、A/B Test 或自动回滚。

Profile 使用不可变记录：

- 已被正式使用的 Profile 不原地修改评分语义；
- 规则变化时创建新的 Profile 记录；
- profileId 指向一套确定的评价规则并承担版本定位作用；
- MVP 不强制单独保存 profileVersion。

Prompt 保存在代码或资源文件，使用手动维护的轻量 promptVersion；Evaluator 使用轻量 evaluatorVersion。

正式 Evaluation 保存必要追踪信息：

```text
profileId
promptVersion
evaluatorVersion
model
tokenUsage
latency
```

具体差异通过 Git 追踪。

## Validation and Failure Handling

不建设按失败类别分派重试的机制。模型调用、Structured Output、Evaluation Validation、
Decision、QuestionGeneration 或 T2 任一环节失败时，均丢弃尚未成为正式业务事实的运行时
结果；不接受局部 Proposal 补丁。

依 ADR-006，首次失败后从 MySQL 正式业务事实重建 Context 并自动重新执行完整 Turn 一次。
第二次仍失败时保留 `IN_PROGRESS + PROCESSING_TURN`，由后续前端“重试”再次执行完整 Turn。
T2 并发冲突也按此完整 Turn 恢复边界处理；幂等判断仅用于避免重复提交已形成的正式业务事实，
不形成独立的 LLM 重试分支。

Java只允许无语义变化的标准化，不自动修改分数、补造维度、Evidence 或其他语义结果。

Evaluator 最终失败时：

- 保留已经持久化的 Answer；
- 不保存非法或半成品 Evaluation；
- 不增加 Interview FAILED 状态；
- Interview 保持可根据 Business Facts 重建并重新执行 Turn Workflow 的状态；
- 不允许无 Evaluation 降级进入下一题。

空回答或跳过不是 N/A，应按未展示能力评价，可使用 OVERALL_OBSERVATION 作为 Evidence，并继续生成 Missing Points 和 Reference Answer。

## Persistence and Observability

Evaluation 创建后保持 Immutable；用户异议通过 EvaluationFeedback 表达。

V0.1 不建设独立 AI Trace / AI Execution 表。失败信息通过受控日志和指标记录必要运行元数据与 Correlation ID，不默认记录完整 Prompt、Resume、Answer 或原始模型响应。

只有经过 Java 校验、计算，并由 Application / Domain 成功提交的结果才是正式 Evaluation Business Fact。

## Future Evolution

以下能力延后：

- Profile 管理后台与显式 Revision；
- Prompt Registry / A/B Test / 自动优化；
- AI Trace 持久化；
- Evaluator Dataset；
- 人工标注；
- LLM-as-a-Judge 对比；
- Regression Test；
- 多模型评分与校准。


## Phase 3 Slice 2 实现约束（[已确认]）

Slice 2 只负责提交 Answer、生成 EvaluationProposal、Java 校验与确定性计算、持久化正式 Evaluation；不实现 Decision、下一题生成或完整 Turn Graph。

### Java Contract

EvaluationProposal 与正式 EvaluationResult 分离。Proposal 使用不可变 Java Contract，评分使用 `BigDecimal`，枚举值使用文档定义的大写英文值，Java 与 JSON 使用 camelCase。Proposal 校验失败不得自动修补分数、维度或 Evidence。

### Evidence 校验

- Answer 按非空行切分为 trim 后的有序 Fragment，编号 E1、E2、E3……；同一 Fragment 集合供 Prompt 与 Validator 共用；
- `ANSWER_QUOTE` 必须引用一个存在的 `evidenceRef`，且每条 Evidence 最多引用一个 Fragment；Validator 将 ref 确定性解析为真实 Answer 行原文；
- `OVERALL_OBSERVATION` 的 `evidenceRef` 必须为 null；
- 每个 `APPLICABLE` 维度有 1～3 条 Evidence；
- `NOT_APPLICABLE` 不允许 Evidence，且必须说明不适用原因；
- Fragment 可以被多个 Evidence 复用；
- 非法 ref 沿用 Validation Failure + Retry，不做 fuzzy match、自动纠错或自动降级；
- Fragment 与 `evidenceRef` 仅属于 AI Evaluator 内部协议，不改变持久化 Evidence.quote 和 Review API。

### 分数计算

Score 范围为 0～10，步长 0.5。N/A 不计入分子和分母；中间计算不提前舍入；`weighted_score` 最终保留 4 位，`overall_score_10` 最终保留 2 位，`overall_score_100` 等于已舍入的 `overall_score_10 × 10`，统一使用 HALF_UP。

### 工程规范

代码应增加必要注释和结构化日志，不记录 Answer 原文、API Key、Token 等敏感信息。所有枚举值、数据库字段以及 DO 类相关字段必须有必要注释。
