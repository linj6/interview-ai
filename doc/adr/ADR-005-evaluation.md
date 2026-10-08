# ADR-005：LLM 负责语义评价，Java 负责确定性计算

**状态：[已确认]**

## Context

Interview Agent 需要对候选人的自然语言回答进行多维评价，并提供可解释、可追踪的训练反馈。

LLM 擅长理解答案语义、识别优缺点和生成参考回答，但输出具有非确定性，可能出现格式错误、非法分数、错误 N/A 或与 Profile 不一致。Java 擅长结构校验、约束验证和确定性计算。

如果让 LLM 直接生成权重、加权分和总分，会使历史评分难以复现；如果用 Java 关键字规则替代语义评价，又无法可靠评价深度、逻辑和项目经验。

## Decision

采用：

> **LLM Semantic Evaluation Proposal + Java Validation and Deterministic Calculation。**

LLM 输出结构化 EvaluationProposal，包括：

- Dimension Applicability、Score、ScoreExplanation 与 Evidence；
- Strengths、Weaknesses、Missing Points；
- Reference Answer。

LLM 不输出或决定 Weight、Weighted Score、Overall Score 和运行版本元数据。

Java 根据 Question 已绑定的不可变 EvaluationProfile：

- 校验维度集合、Score、N/A、Evidence 和 Feedback 约束；
- 排除 NOT_APPLICABLE 维度并归一化有效权重；
- 计算 Weighted Score、overallScore10 和 overallScore100；
- 补充 profileId、promptVersion、evaluatorVersion、model 等追踪信息；
- 组装 Validated EvaluationResult。

Application / Domain 最终校验 Interview Business State，并将结果作为 TurnResult 的一部分原子提交。非法或半成品 Proposal 不得成为正式 Evaluation。

N/A 只表示客观上缺少合理评价条件。回答错误、肤浅、空回答或跳过不能使用 N/A。Profile 中维度默认必须评分，仅显式允许时才能返回 NOT_APPLICABLE。

V0.1 使用轻量可追踪方案：

- Profile 使用不可变记录，profileId 定位一套确定规则；
- Prompt 与 Evaluator 使用手动维护的轻量版本字符串；
- 不建设 Profile Version Manager、Prompt Registry、A/B Test 或 AI Trace 表。

## Consequences

正式 Evaluation 的跨模块 API 位于 `evaluation.api.evaluation`；Profile 查询位于
`evaluation.api.profile`。EvaluationApi 不声明或创建事务，而是参与 Interview
Application 的 T2 外层事务，确保 Evaluation、Question、Interview 一起提交或回滚。

### Positive

- 语义判断与确定性计算职责清晰；
- 总分计算可复现、可测试；
- 非法 LLM 输出不会直接污染正式业务数据；
- Evidence、评分说明和反馈提高可解释性；
- Profile、Prompt、Evaluator 与 Model 变化可进行最小化追踪；
- 不需要在 MVP 建设复杂版本管理与 Prompt 平台。

### Negative

- Java需要实现完整的 Proposal 校验与明确的校验失败处理；
- Structured Output 合法不代表语义评价一定正确；
- N/A、Evidence、Reference Answer 等语义质量仍依赖模型与 Prompt；
- 模型调用或校验失败时，依 ADR-006 从 MySQL 正式业务事实重新执行完整 Turn；Slice 3 MVP 仅自动完整重试一次，不建设异常分类体系或通用 Retry Framework；
- 后续仍需要 Dataset、人工标注和 Regression Test 改善评价稳定性。

## Alternatives Considered

### LLM 直接输出完整 Evaluation 和最终总分

否决。权重、计算与版本元数据不应由非确定性模型决定。

### Java 规则评价

否决。关键字和固定规则无法可靠评价自然语言回答的准确性、深度、逻辑和项目经验。

### MVP 建设完整 Profile / Prompt 版本平台

暂缓。当前只保留轻量标识和不可变 Profile 记录，待出现真实管理、对比或回滚需求后再评估。
