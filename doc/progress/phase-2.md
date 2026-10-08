# Phase 2：技术选型与系统设计进度

**版本：V1.3**  
**状态：已完成**

## Phase 2 目标

Phase 2 的目标是确认足以支撑 V0.1 开发的架构边界、核心模型、状态与一致性原则，而不是在编码前穷尽全部实现细节。

完成标准：

- 核心业务主链路可实现；
- 模块与分层职责清晰；
- Domain 与 AI 边界清晰；
- Interview / Question / Answer / Evaluation / Report 核心模型明确；
- 状态、事务、并发、幂等与失败恢复原则明确；
- AI Workflow、Evaluator、Context、Prompt 具备架构级最小设计；
- 具体 Java Contract、DTO、DDL、Prompt 正文、Token 参数和重试参数允许在 Phase 3 通过实现与测试确定；
- 不存在阻止 MVP 开发的重大未决架构问题。

当前设计已满足以上条件。

## [已确认]

### Architecture and Modules

- 模块化单体 + Maven 多模块；
- 分层架构 + 独立 AI Orchestration；
- Spring Boot 3.5.x / Spring AI 1.1.x / Spring AI Alibaba 1.1.2.2 / JDK 17；
- user、candidate、position、interview、evaluation、report、ai 模块；
- 独立 API Modules；
- Module Public API 使用最小化 Capability Contract 与轻量 Command / Query 语义；
- `InterviewAiApi` 使用 Workflow 粒度 Capability；
- 具体 Java Contract、DTO 和方法签名留到 Phase 3。

### Domain and Persistence

- Position → Interview ← Topic，MVP 不独立 Topic Module；
- Candidate → Resume → ProjectExperience；
- Interview：`CREATED / IN_PROGRESS / PAUSED / COMPLETED`；
- `current_phase`：`OPENING / WAITING_ANSWER / PROCESSING_TURN`；
- Question：`MAIN / FOLLOW_UP`；早期追问树表述已由 V0.1 两层关系替代，`parent_question_id` 指向所属 MAIN，见 [领域模型](../02-architecture/domain-model.md)；
- Answer Immutable；
- 一个 Answer 最多一份正式 Evaluation，一个 Interview 最多一份正式 Report；
- MySQL 是核心业务事实与恢复状态的权威来源；
- Expected State、Version / Optimistic Locking、业务唯一约束用于并发与幂等；
- 不在 LLM 调用期间持有长事务。

### Agent / Graph

- Interview 是长生命周期业务过程，AI Workflow 是业务事件触发的短生命周期执行；
- Opening Capability、Turn Workflow、Report Capability 分离；
- V0.1 仅 Turn 明确使用 Graph；
- Turn Graph：`Evaluate → Decision → Conditional Edge → QuestionGeneration`；
- Decision Action：`FOLLOW_UP / NEXT_QUESTION / END`；
- Graph 不跨越 `WAITING_ANSWER`；
- `Business Input → AI Context → GraphState`；
- V0.1 不建设 Graph Checkpoint / HITL；
- Turn：`T1 保存 Answer → AI Workflow → T2 原子提交 TurnResult`；
- Pause / Resume 属于 Interview Domain；
- Report 与 Interview Completion 解耦，V0.1 不为 Report 强制引入 MQ；
- Question / Evaluation / Decision / Report 不以 Streaming 作为正式交付方式。

### Evaluator

- LLM 输出 Structured `EvaluationProposal`，Java负责校验、N/A 归一化和确定性总分计算；
- EvaluationProposal 包含 Dimension Evaluation、Evidence、Strengths、Weaknesses、Missing Points 与 Reference Answer；
- Applicability 使用 `APPLICABLE / NOT_APPLICABLE`；
- N/A 受 Profile 约束，不把回答差、空回答或跳过视为 N/A；
- Dimension Score 为 0～10，最小步长 0.5；
- Evidence 使用 `ANSWER_QUOTE / OVERALL_OBSERVATION`；
- `scoreExplanation` 是面向用户的评分说明；
- Reference Answer 使用 `SAMPLE_ANSWER / ANSWER_FRAMEWORK`，不参与评分且不得虚构经历；
- EvaluationProfile、Prompt Template、Evaluator 职责分离；
- Profile Weight 只供 Java计算；
- Question 绑定不可变 Profile；MVP 以 profileId 定位规则；
- Prompt / Evaluator 使用轻量版本字符串；
- Evaluation 成功是 Turn 完成的必要条件，非法 Proposal 不持久化；
- 对可恢复错误执行有上限的分类重试，具体参数留到 Phase 3。

### Minimum Context Baseline

- Business Input、AI Context、GraphState 三层边界保持分离；
- Interview Application 协调业务模块并准备 Business Input；
- AI Context Builder 按当前 Capability / Task 选择、裁剪和组织最小必要上下文；
- AI Context Builder 不直接查询其他业务模块 Repository；
- Opening、Turn、Report 使用各自任务所需的 Context，不建设万能长期上下文；
- 当前 Question / Answer、岗位目标和必要候选人信息优先保留；
- 历史在 MVP 中采用“最近有限轮次 + 确定性优先级裁剪”的简单策略；
- V0.1 不建设 LLM History Compression、持久化 Context Snapshot、向量化历史或复杂 Memory；
- Workflow 失败后根据 MySQL Business Facts 重建 Context；
- 具体 N、Token Budget、裁剪阈值和 Context DTO 留到 Phase 3。

### Minimum Prompt Baseline

- Opening、Question Generation、Evaluation、Decision、Report 使用职责清晰的 Prompt；
- Prompt 消费 AI Context，不负责加载业务数据或管理 Context Window；
- System Prompt 表达角色、稳定规则和输出约束；
- User Prompt 表达当前 Task、Context 与动态输入；
- Prompt 不承担状态流转、权重、总分等确定性业务规则；
- Prompt 保存在代码或 Resource 中，通过 Git 管理；
- 使用轻量 promptVersion；
- V0.1 不建设 Prompt Registry、在线编辑、发布、灰度、自动回滚或 A/B Test；
- Prompt 模板正文、变量名称、目录结构和最小回归样例在 Phase 3 实现时确定。

## [转入 Phase 3]

以下内容不再作为 Phase 2 完成前置条件：

- Java Interface、Command / Query / Result、DTO 字段；
- DDL、索引和约束的最终语法；
- Context 具体字段和 Builder 类设计；
- 最近历史轮数 N；
- 精确 Token Budget 与裁剪阈值；
- Prompt 正文、模板语法、变量名和 Resource 目录；
- Profile 初始化数据；
- Structured Output JSON Schema；
- 重试次数、退避算法和异常类；
- Report 异步执行器与恢复触发方式；
- 日志、Metrics 和测试代码的具体实现。

这些内容遵循已确认架构边界，在 Phase 3 通过最小实现和测试迭代确定。

## [暂缓]

Multi-Agent、MCP、Skill、Deep Research、Voice、复杂 Memory、RAG / 向量化历史、持久化 Context Snapshot、LLM History Compression、多用户商业化、移动端、支付/SaaS、Durable Graph Checkpoint、Graph HITL、完整 Profile Version Manager、Prompt Registry、Prompt A/B Test、AI Trace 持久化。

## [否决 / 已替代]

- MVP 微服务化；
- INTERRUPTED / FAILED Interview 状态；
- ASKING / EVALUATING Question 状态；
- 独立 FollowUp 实体；
- 修改历史 Answer；
- LLM 直接计算最终总分；
- LLM 直接输出完整持久化 Evaluation；
- 将 N/A 按 0 分处理；
- 回答差、空回答或跳过使用 N/A；
- Java 自动修补非法 LLM 语义结果；
- Evaluation 失败后无评价降级进入下一题；
- 长生命周期 Graph 跨越用户 `WAITING_ANSWER`；
- 为 Opening / Report 强制使用 Graph；
- 为数据加载、Context 构建、校验、持久化等普通工程职责增加 Graph Node；
- 将 Module Public API 当作本地 RPC Service。

## Phase 2 退出结论

Phase 2 核心架构、领域模型、AI Workflow、Evaluator 以及最小 Context / Prompt 边界已经足以支撑 V0.1 开发。

> **Phase 2 完成，项目进入 Phase 3 — MVP 实现。**

Phase 3 第一目标不是继续建设通用框架，而是打通：

```text
创建 Interview
    ↓
生成第一题
    ↓
提交 Answer
    ↓
Evaluate
    ↓
Decision
    ↓
生成下一题或结束
    ↓
生成 Report
    ↓
查看完整复盘
```
