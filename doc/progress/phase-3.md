# Phase 3：MVP 实现进度

**版本：V1.1**  
**状态：进行中**

当前 Public Release 状态：Personal Profile 与 Create → Prepare → Start 流程已可用；Docker Compose 独立 Demo 的真实业务链路已由用户确认通过，见本文末尾 Public Release Task 1 记录。历史 Task 的“待 Review / 验收阻塞”描述保留为当时事实；本次 Demo 通过不代表全部 V1 场景或整个 Phase 已验收完成。

## Phase 2 复盘约束

Phase 3 的开发节奏与复杂度控制应遵循：

~~~text
doc/retrospectives/phase-2-retrospective.md
~~~

每个 Slice 开始和完成时，应使用其中的 Slice Checkpoint 检查是否重新出现过度设计、提前抽象、范围扩张或文档投入超过可运行成果等问题。

## Phase 3 目标

Phase 3 的目标是：

> **通过渐进式纵向切片，尽快打通一场完整、可运行、可测试、可复盘的 AI 面试。**

本阶段不先完成全系统详细设计，也不按 Maven Module 或技术层横向开发。

第一条完整主链路：

~~~text
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
查看完整复盘
~~~

## 开发模式

采用：

> **当前 Slice 最小详细设计 → 开发 → 测试 → 验收 → PR → 下一 Slice。**

每次只详细设计当前 Slice 所需内容，不提前完成整个系统的 Java Interface、DTO、DDL、Prompt、异常类型、前端接口或通用 AI Framework。

### 当前 Slice 开始前

至少确认：

1. 业务目标与验收标准；
2. 用户操作与调用链；
3. 状态变化；
4. 事务边界；
5. 涉及模块与最小 Public API；
6. 最小数据模型与 Migration；
7. 失败、并发和幂等边界；
8. 关键测试场景。

### 当前 Slice 完成后

至少完成：

~~~text
最小设计已确认
+
代码完成
+
Migration 完成（如需要）
+
关键单元测试通过
+
关键集成测试通过
+
真实链路可运行
+
用户验收
+
PR 合并
+
Phase 3 Progress 更新
~~~

## Slice 规划

Slice 顺序是当前实施方向，可以根据真实依赖和实现反馈调整。调整普通 Slice 顺序不属于 Architecture Change。

### Slice 0：工程骨架与最小运行环境

目标：

- 建立 Maven 多模块工程骨架；
- 配置模块依赖；
- Spring Boot 应用可启动；
- MySQL 可连接；
- 数据库 Migration 可运行；
- 基础测试环境可运行；
- LLM Provider 可以完成最小调用。

验收：应用、数据库、Migration、测试和模型调用均可独立验证。

当前状态：**已完成**

### Slice 1：创建并启动 Interview

~~~text
使用固定测试数据
    ↓
创建 Interview
    ↓
启动 Interview
    ↓
Opening 生成第一题
    ↓
持久化 Question
    ↓
查询并展示第一题
~~~

优先使用固定测试用户、Candidate、Position、Resume / Project 数据，不先被完整登录、文件上传和管理功能阻塞。

#### Slice 1 最小设计（[已确认]）

- 使用 V2 Migration 创建 Slice 1 所需的最小业务表，并写入一组固定演示数据；不实现 User、Candidate、Resume、Project、Position 的 CRUD；
- 仅创建 Slice 1 真实需要的 Interview / Question 持久化与读取能力；
- Interview 模块通过最小 Module Public API 调用 InterviewAiApi 的 Opening Capability；AI 模块不直接查询业务模块 Repository；
- 启动过程先以短事务将 Interview 置为 IN_PROGRESS / OPENING，LLM 调用不持有事务；成功后以短事务持久化首题并置为 IN_PROGRESS / WAITING_ANSWER；
- 自动化集成测试使用可控的测试 AI 实现；真实 DashScope Opening 调用仅作为具备安全环境变量时的人工验收；
- 不实现登录、资料 CRUD、Answer、Evaluation、Turn Graph、Report、前端或新增基础设施。

验收：固定演示数据可创建并启动 Interview；首题作为 MAIN / WAITING_ANSWER 持久化，查询可读取该题；默认构建无需 API Key，真实 DashScope Profile 可完成一次 Opening 调用。

### Slice 2：提交 Answer 与 Evaluation

~~~text
提交 Answer
    ↓
T1 持久化 Answer
    ↓
Evaluator 生成 EvaluationProposal
    ↓
Java Validation / Score Calculation
    ↓
形成 Validated EvaluationResult
~~~

优先单独验证 Evaluator 的真实模型表现，再接入完整 Turn Graph。

### Slice 3：Turn Graph 与下一步决策

~~~text
Evaluate
    ↓
Decision
    ↓
FOLLOW_UP / NEXT_QUESTION / END
    ↓
QuestionGeneration
    ↓
T2 原子提交 TurnResult
~~~

验收 Turn Graph、T2 原子性、幂等和重复执行处理。

#### Slice 3 / Task 1：Decision 最小设计（历史已实现设计）

以下 Focus Selector、NEXT_QUESTION 无引用及 Topic/coveredTopics 设想是旧版记录；Question Quality V1 已确认替换为 PlanItem 选择，现行目标设计见 [Workflow SoT](../03-ai/agent-design.md)。旧版实现/验收事实保留，不代表 V1 已实现。

Decision 是 Interview AI 能力。Application 加载业务事实，计算 Interview Progress 与
AllowedActions，并构造 `DecisionContext`；AI 模块只消费完整 Context，不查询
Repository、不修改 Interview 状态。最小链路为：

```text
DecisionContext → LLM → DecisionProposal → Java Validation → ValidatedDecisionResult
```

Decision 的 AI 内部 `DecisionProposal` 输出 `action`、`reason`、`targetRef`；
targetRef 不进入 Module Public API、Domain、持久化或 HTTP API。Decision 每次调用只按 Evaluation
missingPoints 顺序生成 M1、M2……；weaknesses 保留在上下文中辅助决策，不生成 targetRef。
missingPoints 为空时不允许 FOLLOW_UP。Prompt 与 Validator/Resolver 共用同一 target 集合。
FOLLOW_UP 必须只返回一个存在的 M* targetRef；Java 严格解析为目标原始 description，作为
`ValidatedDecisionResult.followUpTarget`。null、blank、格式非法、不存在或多目标引用均校验失败。
NEXT_QUESTION 不输出 focus 或 targetRef；Application 后续调用 Focus Selector 获取新的 MAIN
focus。END 保持 reason 非 blank、targetRef 为空的约束。Action 为
`FOLLOW_UP`、`NEXT_QUESTION`、`END`。DecisionContext 包含当前 Question / Answer / Evaluation 的必要信息、
Candidate Context（candidateName / resumeSummary / projectSummary）、Position Context
（title / description）、Interview Progress 和 AllowedActions；不包含完整历史
Question / Answer，也暂不包含 `coveredTopics`。

Progress 使用 Spring 应用配置中的 `maxMainQuestions` 与
`maxFollowUpsPerMainQuestion` 两个独立限制，不建设数据库 Interview Configuration。
`followUpCount` 统计当前所属 MAIN 问题链已创建的 FOLLOW_UP 数量。AllowedActions 为：

| MAIN 是否达上限 | FOLLOW_UP 是否达上限 | 允许 Action |
|---|---|---|
| 否 | 否 | FOLLOW_UP / NEXT_QUESTION |
| 否 | 是 | NEXT_QUESTION |
| 是 | 否 | FOLLOW_UP / END |
| 是 | 是 | END |

因此 MAIN 未达上限时，MVP 不允许 LLM 主动 END。FOLLOW_UP 的解析后目标表示当前
问题的单一深挖方向；NEXT_QUESTION 后由 Focus Selector 根据候选人、岗位与已选 MAIN focus
获取新的考察目标，再由 QuestionGeneration 生成具体 Question。QuestionGeneration 不重新做 Decision。

Topic / coveredTopics 延后统一设计。未来可基于 `interview_topic + question.topic_id`
构造轻量 coveredTopics，帮助 NEXT_QUESTION 避免重复主题；FOLLOW_UP 不单独增加
covered topic。Task 1 不改现有 Topic 模型、不新增 coveredTopics 持久化模型，当前允许
NEXT_QUESTION 存在一定程度主题重复。

#### Slice 3 / Task 3.1 + 3.2：Repository 与独立 T2 原子提交（已确认）

QuestionRepository 增加统一创建能力，初始状态固定为 `WAITING_ANSWER`；保留 Opening 创建能力。
InterviewRepository 增加从 `IN_PROGRESS + PROCESSING_TURN + expectedVersion` 到
`IN_PROGRESS + WAITING_ANSWER` 或 `COMPLETED + current_phase = null` 的 CAS 迁移，版本加一。

Interview Application 的独立 T2 在单个事务内重读 Interview、Question、Answer，校验状态、
调用方版本、归属和 Evaluation / Decision / GeneratedQuestion 组合，然后依次保存 Evaluation、
将当前 Question 置为 `ANSWERED`、按 FOLLOW_UP 或 NEXT_QUESTION 创建新题，最后执行 Interview
CAS。FOLLOW_UP 继承当前题的 Evaluation Profile 与 Topic，父题为当前题所属 MAIN；追问内容仍
基于当前 Question / Answer / Evaluation 生成，连续追问不增加关系层级。NEXT_QUESTION
继承 Evaluation Profile，父题和 Topic 为空。两者的 sequence 均为当前题加一。END 不创建新题。
任一步失败由事务整体回滚。此 Task 不接入 `submitAnswer()`，不实现 Turn orchestration、retry
或 Task 3.3 的真实数据库事务与并发集成测试。

#### Slice 3 / Task 4：完整 Turn 接入（已合并）

`submitAnswer()` 的 T1 只保存正式 Answer 并进入 `PROCESSING_TURN`，固定该次状态版本。
Application 随后从 MySQL 重建 Interview、Question、Answer、Profile、Candidate、Position 与全部
Question 的轻量 Context，依次执行 Evaluate、Decision、条件 QuestionGeneration 和独立 T2。
Decision 的 MAIN 与 FOLLOW_UP 计数依据已创建 Question 和 parent 链计算；END 不生成下一题。
T2 返回正式 Evaluation 标识，成功响应沿用 Answer 与 Evaluation 结构。

AI 输出校验、超时、临时连接错误、5xx、限流只允许一次完整 Turn 重试；重试前固定退避
100ms，重新读取正式业务事实，T1 和 expectedVersion 均不重做。第二次失败保留 Answer 与
`PROCESSING_TURN`，首次异常作为 suppressed；状态、归属、版本、Question 链、数据库及未知
异常不重试。旧 Evaluation 单独提交、失败回退与 Evaluator 内部重试已移除。

`getInterview()` 保留 `firstQuestion`，增加 `currentQuestion`：只有 `WAITING_ANSWER` 返回最新
待答题，其余阶段返回 null。默认测试使用受控 AI，不触发真实模型费用。

Task 4 已合并至 `main`（PR #30）。此前本文件仍显示 Task 4 待 Review，是本次 Task 5 文档同步中修正的过期状态。

#### Slice 3 / Task 5：手动 Whole-Turn Retry（实现与验收）

新增 `POST /api/interviews/{interviewId}/retry`，无请求体、questionId 或 answerId，成功返回与
`submitAnswer()` 相同的 `AnswerSubmissionResponse`。仅允许 `IN_PROGRESS + PROCESSING_TURN`；
不存在的 Interview、Question、Answer 复用现有错误码，其他状态及多个待答 Question 使用
`INTERVIEW_STATE_CONFLICT`。确定性错误在调用 AI 前返回。

Application 固定本次读取的 Interview version，以唯一的 `WAITING_ANSWER` Question 与按
interviewId/questionId 查询到的既有 Answer 调用完整 `processTurn()`。不重复执行 T1 或新建 Answer；
每次 Attempt 从 MySQL 重载 Turn Context，沿用 Task 4 的 AI retry predicate / 错误映射，最多两次
Attempt，版本不在 Attempt 间刷新。T2 仍在事务内 authoritative re-read 并原子写入；T2 不自动重试，
竞争者由状态/version/唯一约束拒绝。并发手动 Retry 测试验证仅一个 T2 成功、另一个沿用重复 Evaluation 错误；手动 Retry 与自动 Retry 并发测试验证手动成功后，自动 Attempt 以固定版本重新加载并被现有状态校验拒绝，不覆盖已完成状态。最终 AI 失败时 Interview 保持 `IN_PROGRESS + PROCESSING_TURN`。

不新增状态、锁、Migration、后台恢复任务或前端能力。

验收结果：Retry 成功响应包含原 Answer 与正式 Evaluation；测试验证复用 Answer、AI 首次失败后第二次成功、两次失败后状态保持、固定 version、缺失业务记录错误码、非法状态不调用 AI、Answer / Evaluation / Question 不重复。默认测试 AI 不触发模型费用。

验证：`mvn spotless:apply`、`mvn spotless:check`、`mvn -pl interview-agent-bootstrap -am test` 与 `mvn clean verify` 均通过；Testcontainers MySQL 8.4 执行成功，项目级测试合计 61 个通过、0 失败、0 跳过。T2 集成测试覆盖事务回滚和并发状态冲突。未执行真实 LLM Smoke Test；本 Task 不包含前端人工验收。

Task 5 实现自检：**完成**。已检查范围、模块边界、状态与固定版本、T1/T2 事务边界、并发与幂等、无 Migration、错误映射和日志、测试及文档一致性；Blocker：0，Major：0。以上为实现阶段自检，不代表独立 PR Review 已完成或 PR 已合并。

Task 5 已完成并合并；Slice 3 已收尾。该记录为实现与验收结果。

#### Slice 3 已确认的 Turn Failure Recovery 演进

这项演进取代 Slice 2 的临时 `recoverEvaluation(): PROCESSING_TURN → WAITING_ANSWER`
语义。T1 成功后 Answer 已是不可变正式业务事实，Interview 进入
`IN_PROGRESS + PROCESSING_TURN`。Evaluate、Decision、QuestionGeneration 或 T2 任一
环节失败时，不回退 `WAITING_ANSWER`；T2 成功前的 Evaluation、Decision、Generated
Question 都是可丢弃、可重算的运行时结果。

恢复时不保存 Graph / Node Checkpoint，而是从 MySQL 中的正式业务事实重新执行完整 Turn。
Slice 3 MVP 首次失败后自动完整重试一次，第二次仍失败则保持 `PROCESSING_TURN`；后续由
前端“重试”入口触发完整 Turn。后台恢复任务不在本次设计与实现范围内。

### Slice 4：完整 Interview 生命周期

- 多轮 Interview；
- Pause / Resume；
- Skip；
- 用户主动 Finish；
- 时长约束；
- Workflow 失败后的重新执行；
- 重复提交与并发保护。

#### Task 1：Interview Pause / Resume 最小设计 [已确认]

- `POST /api/interviews/{interviewId}/pause`：仅允许 `IN_PROGRESS + WAITING_ANSWER` 转为 `PAUSED + WAITING_ANSWER`；`POST /api/interviews/{interviewId}/resume`：仅允许反向流转。均无需请求体，复用 Interview 详情响应。
- `current_phase` 保持 `WAITING_ANSWER`；Question、Answer、Evaluation 均不变，不生成新题，不调用 AI / Graph。Resume 后继续原 Question，不增加暂停原因、历史或自动暂停。
- `currentQuestion` 表示仍需用户回答的问题：`IN_PROGRESS` 或 `PAUSED` 且阶段为 `WAITING_ANSWER` 时返回原待回答 Question；`OPENING`、`PROCESSING_TURN`、`COMPLETED` 不返回。
- 使用现有 Interview version、期望 status / phase 的 CAS 和短事务；CAS 失败不重试。非法状态及 CAS 失败均返回 `INTERVIEW_STATE_CONFLICT`。Pause 与 Submit Answer 并发时 first-success-wins。
- 本 Task 不包含 Skip、Finish、Duration 等后续能力。

#### Task 2：用户主动结束 Interview（User Finish）最小设计 [已确认]

- 新增 `POST /api/interviews/{interviewId}/finish`，无请求体，成功后复用 `InterviewDetailResponse`，不新增 Finish 响应 DTO。
- 仅允许 `IN_PROGRESS + WAITING_ANSWER` 或 `PAUSED + WAITING_ANSWER` 转为 `COMPLETED + current_phase = null`；`OPENING`、`PROCESSING_TURN`、`COMPLETED` 及其他状态组合均返回现有 `INTERVIEW_STATE_CONFLICT`。
- Finish 是严格状态转换：重复 Finish 与 CAS 竞争失败均返回状态冲突，不做幂等成功或自动重试；并发时 first-success-wins。
- 在短数据库事务中读取 Interview、校验状态，并使用读取到的 `expectedStatus`、`WAITING_ANSWER` 和 `version` 执行 CAS；成功时仅更新 Interview 的 `status`、`current_phase` 和 `version`。
- 不修改当前 `WAITING_ANSWER` Question，不创建 Answer 或 Evaluation，不调用 AI、Graph、Decision 或 QuestionGeneration；完成后的详情响应 `currentQuestion = null`。

#### Task 3：Skip Question 最小设计 [已确认]

- `POST /api/interviews/{interviewId}/questions/{questionId}/skip` 无请求体，返回 Interview 详情。仅允许 `IN_PROGRESS + WAITING_ANSWER` 的当前待回答 Question；旧题、已回答或已跳过题均返回现有冲突错误。
- 所有已生成 MAIN 题均计入上限。达到上限时不调用 AI，原题转 `SKIPPED`，Interview 完成；否则以 `USER_SKIP + NEXT_QUESTION` 生成新的 MAIN 题，沿用当前题的 EvaluationProfile、递增序号，parent 与 topic 均为空。Skip 不创建 Answer、Evaluation，也不执行 Evaluation 或 Decision。
- QuestionGeneration 在事务外执行，只对可恢复 AI 异常最多重试一次，间隔 100ms。成功后在单个 T2 事务内条件更新原题、按需插入新题，最后以请求开始时的 Interview version、`IN_PROGRESS + WAITING_ANSWER` 执行 CAS。CAS 失败回滚全部写入；不增加持久化恢复状态或 Question version。

#### Task 4：Interview Duration 硬截止语义（已完成）

- V7 新增可空 `interview.expires_at`；启动成功进入 Opening 时设置固定截止时间。Pause / Resume 保留同一截止值，不暂停或重算计时；`now >= expires_at` 视为过期。
- 过期的 `IN_PROGRESS / PAUSED + WAITING_ANSWER` 在 GET 时通过独立短事务和状态/version CAS 收敛为 `COMPLETED + current_phase = null`，然后正常返回 completed detail。Pause、Resume、Skip、Submit 不执行原操作，先完成收敛后返回 `INTERVIEW_EXPIRED`；Finish 仍允许显式完成。
- Submit 的 T1 将 Answer 插入与 `WAITING_ANSWER → PROCESSING_TURN` admission 放在同一事务。最终 CAS 条件包含 `(expires_at IS NULL OR expires_at > now)`；CAS 失败使 Answer 一并回滚。进入正式 `PROCESSING_TURN` 后跨过 deadline 不撤销 Answer。
- Turn 的 Evaluation / Decision 可以在 deadline 后继续；QuestionGeneration 不再产生有效下一题。T2 重新读取权威 Interview 状态和时间，在同一事务内保存 Evaluation、当前 Question=`ANSWERED` 并完成 Interview，不创建下一题。T2 Evaluation、Question 与 Interview 更新原子提交。
- deadline 后允许对已有 `PROCESSING_TURN` 手动 Retry：复用正式 Answer，不重复 T1；每次 Attempt 仍遵循现有完整 Turn 流程，最终由 T2 authoritative deadline 规则完成。
- 过期收敛以单独提交的事务完成，不在同一事务更新状态后再抛异常；CAS 失败只重读最新状态，不覆盖并发转换。未增加生命周期状态或后台调度设施。

#### Task 5：Slice 4 收口（已完成）

覆盖盘点后，仅发现 Submit vs Finish 缺少直接集成测试，新增 `DatabaseMigrationTest.concurrentSubmitAndFinishKeepOnlyTheWinningBusinessFacts`，验证 first-success-wins 及双方对应的业务事实。之前临时添加的 `manualRetryAfterDeadlineReusesAnswerAndCompletesAtT2` 与 Task 4.4 已有覆盖重复，按 Review 删除；deadline 后、精确 deadline 和 deadline 前 Retry 均由 Task 4.4 的既有测试覆盖。其余矩阵由现有测试覆盖或由相同条件 CAS / 事务回滚测试直接证明，没有重复添加测试。未发现需要修改生产代码的 Blocker / Major。

**现有测试覆盖矩阵**

| 场景 | 现有覆盖 |
| --- | --- |
| Pause / Resume、Finish 合法状态与终态 | `DatabaseMigrationTest` HTTP 与 repository 集成测试 |
| Submit vs Pause | `DatabaseMigrationTest.pauseAndSubmitAnswerRaceAllowsOnlyOneSuccess` |
| Submit vs Skip | `SkipQuestionTest.concurrentSkipAndSubmitAllowOnlyOneConsistentBusinessPath` |
| Submit vs Finish | 本 Task 新增 `concurrentSubmitAndFinishKeepOnlyTheWinningBusinessFacts` |
| Skip vs Finish | Skip T2 最终 CAS 失败回滚测试 + Finish 状态/version CAS 测试；无单独配对 race 测试 |
| Pause vs Finish | Finish 同类竞争成功仅一次测试 + Pause/Resume CAS 冲突测试；无单独配对 race 测试 |
| Retry vs Retry | `DatabaseMigrationTest.concurrentManualRetriesCommitOnlyOneEvaluation` |
| Automatic Turn Retry vs Manual Retry | `DatabaseMigrationTest.manualRetryWinningAgainstAutomaticRetryPreventsStaleTurnCommit` |
| Retry after / at / before deadline | Task 4.4 的 `manualRetryAfterDeadlineCompletesExistingTurnWithoutGeneratingQuestion`、`manualRetryAtExactDeadlineCompletesWithoutGeneratingQuestion`、`manualRetryBeforeDeadlineStillGeneratesFollowUp` |
| Duration 边界 / 收敛 / T1 rollback / T2 deadline | `DatabaseMigrationTest` 边界与 MySQL 事务测试、`InterviewExpirationConvergenceTest` |
| T2 原子性 / CAS 回滚 | `ProcessedTurnTransactionTest` 与 `SkipQuestionTest.finalCasConflictRollsBackQuestionAndInsert` |

Skip vs Finish 与 Pause vs Finish 没有独立组合竞态测试；现有条件更新分别校验 phase/status/version，Skip 事务回滚和 Finish 并发状态转换已覆盖同一失效保护，不构成需要复制集成测试的缺口。

**最终 Review：通过**

- Blocker：0；Major：0；Minor：0。
- 范围 / 架构：只完成生命周期、Duration 和恢复收口；无新状态、基础设施或 Slice 5 能力。ADR-003 / ADR-006 与实现一致。
- 生命周期 / 并发 / Retry：状态转换与 `PROCESSING_TURN` 恢复一致；状态 CAS、version 和数据库约束保证 first-success-wins，输方事务不留业务事实。
- Duration / 事务：硬截止与上文一致；LLM 位于事务外；过期收敛事务独立提交；条件状态 CAS 不覆盖其他转换。
- Error mapping：稳定待答操作过期映射 `INTERVIEW_EXPIRED`，状态与 version 竞争映射现有冲突错误；Turn AI 错误使用既有映射。
- Migration：仅 V7 新增 `expires_at`，可空且有说明，无修改既有 Migration。
- Tests / Documentation：Testcontainers MySQL 8.4 覆盖关键回滚与 CAS；文档已按实现同步。默认验证不调用真实 LLM。

Slice 4 实现、自动化验收和最终 Review 已完成，并已合并到 `main`。

**Task Gate 验证（2026-09-26）**

- 新增测试定向验证：Submit vs Finish MySQL 8.4 Testcontainers 测试 1/1 通过；deadline 后 Retry 由 Task 4.4 的既有测试覆盖。
- `mvn clean verify`：BUILD SUCCESS；全 Reactor Spotless `validate` 检查通过；Bootstrap 93 项测试通过，0 失败、0 错误、0 跳过；V1–V7 Migration 在 MySQL 8.4 执行通过。默认 profile，未执行 `llm-smoke`，未调用真实模型。
- `git diff --check`：通过。
- 未重复执行 root `spotless:check`，因为本次 `mvn clean verify` 的 `validate` 已执行等价完整 Spotless 检查。

### Slice 5：完整面试复盘

目标：Interview 完成后，用户能够查看整场面试的客观概览、MAIN / FOLLOW_UP 问题链、自己的回答以及每次回答的 Evaluation，形成“面试 → 回答 → 评估 → 复盘”的第一版 MVP 闭环。

#### Slice 5 最小设计（[已确认]）

- V0.1 不生成 Interview-level AI Report。原 Report Capability、异步生成、Report 状态/重试/持久化均暂缓，待 MVP 真实使用后根据产品价值重新评估。
- 完整复盘不是新的领域对象或业务事实，而是 `COMPLETED` Interview 的只读查询视图；不新增 Review / Report 表、Repository、缓存、快照、MQ、Scheduler、Graph 或 LLM 调用。
- Review 归属 `interview` 模块，接口为 `GET /api/interviews/{interviewId}/review`；不扩充现有 Interview 详情接口。仅 `COMPLETED` Interview 允许查询。
- `interview` 模块批量读取整场 Question / Answer，并通过 `evaluation-api` 按 `interviewId` 批量读取 Evaluation 业务快照；不得直接访问 evaluation 模块的 Repository / Mapper / DO。所有数据在内存中关联和组装，避免 N+1。
- Overview 仅返回用户有价值的客观信息：`startedAt`、`completedAt`、`durationSeconds`、MAIN 数、FOLLOW_UP 数、已回答数、跳过数、未回答数。统计查询时动态计算，不持久化综合分、能力等级或 AI 总结。
- Question Review 只表达两层结构：MAIN + 其 FOLLOW_UP 列表，不建设通用递归 Tree。用户侧结果为 `ANSWERED / SKIPPED / UNANSWERED`；其中 `UNANSWERED` 由 `COMPLETED + WAITING_ANSWER` 派生，不新增 QuestionStatus、不持久化，也不区分具体结束原因。
- 已回答题展示 Answer 内容和精简 Evaluation：overallScore、strengths、weaknesses、missingPoints、referenceAnswer，以及适用维度的 name / score / explanation。不向用户暴露 prompt/model/token/latency、weight/weightedScore、evidence、dimensionCode 等内部评估与追踪字段。
- Review 组装时校验必要业务不变量。`ANSWERED` 缺少 Answer / Evaluation、`SKIPPED` 存在正式 Answer、已完成 Interview 缺少必要生命周期时间等非法组合视为数据一致性异常，不返回残缺 Review。
- Interview 新增不可变生命周期事实 `started_at`、`completed_at`。`started_at` 表示用户 Start 成功、Interview 正式启动的时间，并与 `expires_at` 使用同一时间基准；`completed_at` 表示首次进入 `COMPLETED` 的时间，所有完成路径必须与状态迁移原子写入。`durationSeconds = completedAt - startedAt`。
- Review 每次从 MySQL 权威业务事实实时查询并组装；MVP 不做缓存。

#### Slice 5 / Task 4：集成验收与 Slice 收口（已完成，2026-09-27）

Slice 5 已完成。Review 是 `COMPLETED` Interview 的实时只读视图，按批次读取 Question / Answer / Evaluation 并在内存组装；Overview 提供生命周期时间、持续秒数和问题状态计数；问题按 MAIN + FOLLOW_UP 两层展示，结果状态为 `ANSWERED / SKIPPED / UNANSWERED`。已回答题展示 Answer 和约定的精简 Evaluation 字段，关联缺失或非法组合按数据一致性异常处理。

生命周期 `started_at / completed_at` 写入语义与已确认设计一致；未增加 Report 持久化、LLM Report、Review 缓存或其他 Slice 外能力。检查现有测试后未发现 Slice 级验收缺口，Task 4 未新增或修改生产代码及测试。

最终 Gate：`mvn clean verify` BUILD SUCCESS；全 Reactor Spotless 检查通过；Bootstrap 104 项测试通过，0 失败、0 错误、0 跳过；Testcontainers MySQL 8.4 成功，Flyway V1～V8 校验并执行通过。`git diff --check` 通过。使用默认 profile，未运行 `llm-smoke`，未调用真实模型。
### Slice 6：外围能力补齐

根据主链路真实需要逐步补齐：

- 简单登录；
- Candidate 管理；
- Resume 上传、粘贴与解析；
- Position 管理；
- Project Experience；
- 历史 Interview 列表；
- Evaluation Feedback；
- 必要的前端体验。

如某项外围能力成为前面 Slice 的真实阻塞项，可以提前实现其最小版本。

## 实现原则

- 只实现当前 Slice 真实需要的 Module Public API；
- 不机械地为每个模块套完整四层结构；
- 只有 Turn 使用 Graph，Opening 使用普通 Service；完整复盘不调用 AI；
- 数据加载、Context 构建、校验、计算、持久化和日志不建设为 Graph Node；
- Context 先采用最近有限轮次和确定性优先级裁剪；
- Prompt、Profile、重试和异步任务采用最小静态实现；
- 没有真实需求时不引入 Redis、MQ、RAG、Memory、Fallback Model 或通用框架；
- 架构概念用于划清职责，不要求每个概念都扩展为多层接口与工厂体系。

## 设计变更规则

### 实现细节

DTO 字段、类名、包结构、Prompt 变量、最近历史轮数、Token 阈值、Resource 目录和不影响架构语义的内部重构，可在当前 Slice 内根据测试调整并记录。已确认的 Slice 3 Turn Failure Recovery（首次失败自动完整重试一次）不属于可调整的重试次数。

### Architecture Change

修改模块边界、核心领域模型、生命周期、Graph 职责、事务与恢复边界、Evaluation 规则，或引入新的状态源和重大基础设施时，必须暂停实现并遵循：

~~~text
发现问题
    ↓
提出 Architecture Change
    ↓
影响分析与确认
    ↓
更新 Source of Truth / ADR
    ↓
继续实现
~~~

## MVP 复杂度红线

V0.1 默认不引入：

- 微服务；
- MQ；
- 无真实需求的 Redis；
- Graph Checkpoint / HITL；
- Multi-Agent；
- MCP / Skill；
- RAG / 向量数据库；
- 复杂 Memory；
- LLM History Compression；
- 持久化 Context Snapshot；
- Prompt Registry；
- Profile 管理后台；
- AI Trace 表；
- A/B Test；
- Fallback Model；
- 通用 Workflow / AI Task Framework。

准备增加新能力时先回答：

> 如果不加入它，当前 Slice 或第一场完整面试是否无法运行？

如果答案是否定的，原则上延后。

## 当前进度

### 当前 Slice

> **Core Backend E2E Acceptance 与 Frontend MVP 均已完成；Frontend MVP E2E Gate 已通过。Phase 3 仍进行中，Slice 6 外围能力待开始；下一阶段优先级待基于真实使用结果重新确定。**

Frontend MVP 最小设计 **[已确认]**，技术选择、三个页面、Runtime UI Projection 与交互边界见 [Frontend MVP 设计](../frontend/mvp-design.md)。[Task 1～6](../frontend/implementation-tasks.md) 已完成并合并，真实 Full UI E2E 已通过；详细事实见本文 [Frontend MVP Closure](#frontend-mvp-closure-与真实-e2e-验收已完成2026-10-03)。

### Slice 0 实施进展

已完成：

- Maven 多模块工程骨架与最小依赖方向；
- Spring Boot 唯一启动模块；
- MySQL DataSource、Flyway 和 V1 基线 Migration；
- Testcontainers MySQL 集成测试；
- Spring Boot Web 启动与 Actuator Health 验证；
- DashScope opt-in 连通测试及安全环境变量配置；
- GitHub Actions 默认构建验证。

验证结果：

- `mvn clean verify` 通过；
- MySQL 8.4 Testcontainer 启动成功；
- `SELECT 1` 与 Flyway V1 验证通过；
- Spring Boot Web 应用启动成功；
- `/actuator/health` 返回 `UP`；
- 默认构建不需要 API Key，不产生模型调用费用；
- 在安全提供 `AI_DASHSCOPE_API_KEY` 后完成真实 DashScope 最小调用，并在阿里云百炼工作台确认调用日志；
- 用户验收完成，Slice 0 PR 已合并。

### Slice 1 设计确认

已确认：

- 固定演示数据 + 最小 Schema / Seed，而不是资料 CRUD；
- 自动化测试与真实 DashScope Opening 验收分离；
- 开发由 Codex CLI 完成；ChatGPT 负责方案讨论、文档维护与设计一致性检查。

### Slice 1 实施进展

已完成：

- V2 Migration 创建 Slice 1 最小表与固定演示数据；
- 创建、启动和查询 Interview 的 HTTP 能力；
- Opening Capability、Resource Prompt 与 DashScope opt-in 实现；
- 使用测试 AI 的 MySQL 集成测试，覆盖状态流转、首题持久化、查询和重复启动。
- Opening Context、模型调用或首题持久化失败后，在无正式 Question 的条件下恢复 Interview 至 `CREATED / null`，并保留原始异常；
- V3 Migration 为 Question 增加可空 `topic_id`、非空 `evaluation_profile_id`，并写入固定默认 EvaluationProfile 与七项最小维度配置；
- 集成测试覆盖 V1、V2、V3 Migration、失败恢复、再次启动以及首题 Profile 绑定。
- Opening AI Bean 按 `app.ai.real-llm-enabled` 配置开关互斥装配；默认配置使用安全兜底，`local` / `prod` 使用同一个真实 DashScope 实现；Maven `llm-smoke` 仅用于显式 Smoke Test。
- Slice 1 PR 已完成审核并合并。

已完成：

- 真实 DashScope Opening 人工验收；使用 Maven `llm-smoke` profile 构建 Fat JAR，并以 Spring `local` profile 启动，接口成功生成并持久化 Opening 首题；

### 当前状态

~~~text
Slice 0  工程骨架与最小运行环境        [已完成]
Slice 1  创建并启动 Interview          [已完成]
Slice 2  提交 Answer 与 Evaluation     [已完成并合并]
Slice 3  Turn Graph 与下一步决策       [已完成并合并]
Slice 4  完整 Interview 生命周期       [已完成并合并]
Slice 5  完整面试复盘                    [已完成]
Slice 6  外围能力补齐                  [待开始]
Frontend MVP / Full UI E2E             [已完成，Task 1～6 已合并，E2E Gate 已通过]
~~~

### Slice 2 设计确认结果

Slice 2 的最小设计已逐项确认，并已完成代码实现、自动化测试、真实验收和合并。以下记录同时标明已被 Slice 3 正式恢复语义取代的临时实现：

- Answer 先持久化，Answer 为不可变历史事实，一个 Question 最多一个 Answer；
- T1 保存 Answer 并将 Interview 置为 `PROCESSING_TURN`；LLM 调用和 Java 校验在事务外执行；
- T2 以完整 TurnResult 原子提交：`FOLLOW_UP / NEXT_QUESTION` 时保存 Evaluation、Dimension Scores、将当前 Question 更新为 `ANSWERED`、保存下一道 Question 并进入 `WAITING_ANSWER`；`END` 时保存 Evaluation、Dimension Scores、将当前 Question 更新为 `ANSWERED` 并进入 `COMPLETED + current_phase = null`；
- Slice 2 的失败回退语义已由 Slice 3 正式演进取代：Answer 保留，T2 成功前不保存正式 Evaluation；失败后 Interview 保持 `IN_PROGRESS + PROCESSING_TURN`，从 MySQL 正式业务事实重新执行完整 Turn；
- Slice 3 MVP 的 Turn Processing 首次失败后自动完整重试一次；第二次仍失败保持 `PROCESSING_TURN`，由后续前端“重试”触发完整 Turn；重复提交仍返回 `409 Conflict`，不设计独立重新评价或后台恢复能力；
- EvaluationProposal 与正式 EvaluationResult 分离，Proposal 使用不可变 Java Contract 和 `BigDecimal`；
- Evidence 支持 `ANSWER_QUOTE` / `OVERALL_OBSERVATION`，Quote 必须是 Answer 中经基础空白标准化后的连续原文；
- Score 范围为 0～10，步长 0.5；使用 `BigDecimal`，最终统一 `HALF_UP` 舍入；
- Evaluation 核心分数结构化保存，复杂评价内容使用 JSON 保存，不新增独立 AI Trace / 失败记录表；
- 代码增加必要注释和结构化日志；枚举值、数据库表字段、DO 类相关字段增加必要注释。

### Slice 2 完成标准与 Slice 3 前置条件（已达成）

Slice 2 完成前必须具备：

- 文档和 Migration 完成；
- Answer 提交和 Evaluation 持久化完成；
- Java Validation / Deterministic Calculation 完成；
- 关键单元测试和 MySQL 集成测试通过；
- 真实 DashScope Evaluator 人工验收完成；
- PR 合并；
- `phase-3.md` 和 `PROJECT_CONTEXT.md` 更新。

Slice 2、Slice 3 和 Slice 4 均已完成并合并到 `main`。Slice 5 已完成集成验收；Core Backend E2E Acceptance 与 Frontend MVP 均已完成，Frontend MVP E2E Gate 已通过；Slice 6 外围能力待开始，下一阶段优先级待基于真实使用结果重新确定。

### Slice 2 实施进展

已完成：

- 将 Candidate、Position、Interview 和 Evaluation 持久层统一为 MyBatis-Plus 的 `entity / mapper / repository` 分层；Application 不再直接依赖 Mapper、DO 或 Wrapper；
- Evaluation DO、Mapper、Repository 已迁入 `interview-agent-evaluation`，Interview 通过最小 `interview-agent-evaluation-api` 查询 Profile 并创建正式 Evaluation；
- 简单 CRUD 与单表查询使用 `BaseMapper` / Lambda Wrapper；仅保留 Candidate 多表 Join、Interview 乐观锁状态更新和 Question 条件更新 XML；
- 删除 Answer / Evaluation exists 预检查，Repository 直接插入并将重复键转换为明确业务异常；
- V5 在显式历史重复检查后将 Answer 唯一约束调整为 `UNIQUE(interview_id, question_id)`；V6 为历史业务表和字段补齐 SQL COMMENT；
- 所有 Interview 状态更新均校验主键、期望旧状态、期望旧阶段和版本，递增版本并检查影响行数；
- 引入 Lombok `@Getter` / `@Setter`、`@RequiredArgsConstructor`、`@Slf4j`，未使用 `@Data` 或字段注入；

- V4 Migration 新增 `answer`、`evaluation`、`evaluation_dimension_score`，包含三项业务唯一约束、完整表/字段注释且不创建外键；
- 新增 `POST /api/interviews/{interviewId}/questions/{questionId}/answer`；
- 实现 T1 保存不可变 Answer 并进入 `PROCESSING_TURN`、事务外 Evaluator/Java 校验与计算、T2 原子保存正式 Evaluation/Dimension Scores 并将 Question 更新为 `ANSWERED`；
- Slice 2 当时的临时实现：Evaluation 失败时保留 Answer、回滚 Evaluation、保持 Question `WAITING_ANSWER`，并按 Expected State/Version 恢复 Interview 至 `IN_PROGRESS + WAITING_ANSWER`；该语义已被 Slice 3 正式 Turn Failure Recovery 取代，当前失败保持 `IN_PROGRESS + PROCESSING_TURN` 并重跑完整 Turn；
- 实现 `EvaluationProposal → ValidatedEvaluationResult → Official Evaluation` 分层，Evidence 连续原文、N/A、反馈数量和 Profile 维度集合校验；
- 全部分数使用 `BigDecimal`，`weighted_score` 保留四位，`overall_score_10` 保留两位，`overall_score_100` 从已舍入十分制总分计算，统一 `HALF_UP`；
- Slice 2 当时的临时实现使用最多三次的请求内有限同步重试；该规则已被 Slice 3 的“首次失败后自动完整重试一次、第二次失败保持 `PROCESSING_TURN`”正式语义取代；
- 自动化测试使用可控 AI，覆盖正常流程、参数/状态、Proposal、分数、Slice 2 临时重试与失败恢复、T2 回滚、Version 冲突和 Migration；默认构建不需要 API Key。

验证结果：

- 持久层重构定向 Testcontainers 测试：15 个测试通过，覆盖 BaseMapper、XML、Migration、记录不存在与重复键转换、事务边界和两组并发竞争；
- MySQL 8.4 Testcontainers：V1～V6 完整执行通过；
- Review 修复后执行 `mvn clean verify` 通过；
- `mvn -Pllm-smoke -pl interview-agent-bootstrap -am verify` 通过：4 个集成测试中 2 个 Bean/Profile 验证通过，`LlmConnectivityIT` 与 `RealEvaluatorPersistenceIT` 因自动化执行环境未设置 `AI_DASHSCOPE_API_KEY` 条件跳过，未产生模型调用费用；
- 真实 DashScope Evaluator 人工验收已由用户确认通过，Answer、正式 Evaluation、维度分数和状态流转均完成验收；
- Slice 2 PR 已完成审核并合并。

独立 Review 记录（2026-09-22）：

- Blocker：0；
- Major：3，分别为 Answer 不存在仍使用通用异常、Interview 模块残留重复的 Evaluation Profile 错误码、Evaluation 主记录与维度记录的原子保存过度依赖上游事务；均已修复并复核；
- Minor：0；
- Informational：Flyway 当前版本对 MySQL 8.4 输出“最高已测试到 8.1”的升级建议，不影响本次 V1～V6 执行结果。

## Slice 1 LLM 运行配置调整（[已完成]）

当前实现曾将 Maven 的 `llm-smoke` profile 与 Spring Boot 的 `llm-smoke` runtime profile 同时用于真实 DashScope 人工验收，导致构建、依赖装配和本地运行配置耦合，增加了人工验收排查成本。

待调整方向：

- `llm-smoke` Maven profile 仅用于真实 LLM 自动化 Smoke Test；
- 本地人工验收使用 Spring Boot `local` profile，云服务器使用 `prod` profile；
- `local` 和 `prod` 均使用同一个 `OpeningInterviewAiService`，由 `app.ai.real-llm-enabled` 配置开关控制真实 LLM Bean 装配；
- API Key、模型名等敏感或环境相关参数继续通过环境变量注入；
- 默认 profile 仍可在无 API Key 时启动，并使用安全兜底实现；
- 人工验收的运行配置不依赖测试 profile 的语义。

该调整属于 Slice 1 的配置实现修正，不改变模块边界、AI Capability Contract 或业务生命周期。


## Slice Review 门禁

### MyBatis-Plus 重构后修复（2026-09-22）

- Evaluation API 已按 profile/evaluation 拆包并清理 Official 类名前缀；
- Interview、Question 状态使用枚举映射到数据库 VARCHAR，状态 XML 参数化并校验 status、phase、version；
- T1/T2 边界保持不跨 LLM，EvaluationApi 参与 T2 外层事务；
- Testcontainers 覆盖枚举映射、唯一约束、并发、LLM 失败恢复及 T2 Question/Interview 写入失败回滚；
- 剩余风险：Flyway 对 MySQL 8.4 提示最高测试版本为 8.1，但 V1–V6 已在容器中成功执行。

## Java API 基础规范落地（[实现中]）

已按 `doc/00-project/java-development-guide.md` 完成基础 Web 契约实现：

- `interview-agent-common` 提供 `Result<T>`、`ErrorCode`、`CommonErrorCode` 和 `BusinessException`；
- bootstrap 提供 requestId 生成、MDC 写入/清理、响应 Header 和 `@RestControllerAdvice` 全局异常处理；
- Interview Controller 统一包装 `Result<T>`，ID 参数和 Answer DTO 使用包装类型/Bean Validation；
- Interview 业务错误码保留在 Interview 模块，错误码与 HTTP Status 分离；
- 集成测试同步校验 `Result.data`、成功码和 requestId。

该改动不改变 Interview 生命周期、事务边界、模块依赖方向或 Evaluation 架构。

从 Slice 2 开始，Review 是 Slice 完成标准的一部分。代码实现、自测和真实人工验收完成后，必须在 PR 创建或合并前进行 Review。

Review 至少覆盖：

- Slice 范围和架构一致性；
- 状态、事务、幂等和并发；
- Migration、约束和注释；
- 日志和敏感信息；
- 单元测试、集成测试和失败场景；
- 文档与实现一致性。

Review 结论必须明确为：

```text
通过
有条件通过
不通过
```

存在 Blocker 或 Major 问题时，不得将 Slice 标记为完成；修复后必须重新 Review。

## Core Backend E2E — E2E-04 与完整生命周期真实验收（已完成）

Task 4 完成自动化 Gate、必要测试 fixture cleanup、真实 DashScope E2E 与文档收口；未扩展 Focus Selector、Decision 或 Question Generation 职责，也未新增基础设施。

自动化验证结果：

- 聚焦验证覆盖 `DatabaseMigrationTest`、`ProcessedTurnPersistenceTest`、`ProcessedTurnTransactionTest`、`SkipQuestionTest`、`TurnRepositoryTest`、`DecisionRulesTest` 与 `FocusSelectorValidationTest`。验证中发现并修正旧测试 fixture/断言对 MAIN focus 可缺省的假设，以及手动重试 FOLLOW_UP 用错 MAIN Generator 的 stub。
- 根目录 `mvn clean verify`：BUILD SUCCESS；全 Reactor Spotless 检查通过；Bootstrap 118 项测试通过，0 失败、0 错误、0 跳过；Testcontainers MySQL 8.4 校验并执行 V1～V9 Migration。
- `git diff --check` 通过。

真实 DashScope E2E 验收记录（Interview 19，脱敏摘要）：

Public 隐私说明：以下案例已删除/泛化真实业务领域、实体、故障链路和原始生成文本，仅保留原验收的流程与结论；不是原始 Question / Answer / Reference Answer，也不代表重新执行过虚构案例。

- MAIN 1 使用首个考察目标 F1；具体内容已脱敏。
- MAIN 1 后生成 2 个 FOLLOW_UP；两题的 `focus` 均为 `null`。
- 第二个 FOLLOW_UP 回答后，`followUpCount=2/2`、`allowedActions=[NEXT_QUESTION]`，Decision 为 `NEXT_QUESTION`。
- Focus Selector 输入中的 `selectedMainFocuses` 正确包含 F1。
- Focus Selector 选择与 F1 不同的考察目标 F2；具体内容已脱敏。
- 新 MAIN（Q29）围绕 F2 生成，并持久化 `focus=F2`。本次观察中，F1 与 F2 属于不同核心考察目标，Q29 与 F2 语义一致。

结论：E2E-04 在 Interview 19 这一真实 LLM 场景中通过验收；同一 Interview 的 NEXT_MAIN 切换到不同考察目标，不再重复原 MAIN focus。此结论只代表本次场景验证通过，不构成语义重复已被确定性消除的保证；后续真实使用继续观察。跨 Interview 重复不属于本次 E2E-04 验收范围。

Interview 20 完整生命周期真实验收：

- Skip：PASS。
- Pause：PASS。
- Resume：PASS；恢复后保持同一 `currentQuestion`，且没有 AI 调用。
- Answer → Evaluation → Decision → FOLLOW_UP：PASS。
- Active Finish：PASS；Interview 为 `COMPLETED`，`currentPhase=null`、`currentQuestion=null`，没有新建 Question，也没有 AI 调用。
- Review 首次真实调用发现 P0 bug：对 immutable empty list 调用 `sort()`，抛出 `UnsupportedOperationException`。commit `6e1973e8f528ff8a512dcdafccc06708de63eedd` 已修复；修复后使用同一个 Interview 20 回归 Review：PASS。
- Review 聚合正确：Q30 MAIN=`SKIPPED`；Q31 MAIN=`ANSWERED` 且包含 Evaluation；Q32 FOLLOW_UP=`UNANSWERED`。Overview：MAIN 2、FOLLOW_UP 1、ANSWERED 1、SKIPPED 1、UNANSWERED 1。

最终 Closure Gate（2026-10-02，`main` 提交 `26668ef`）：根目录 `mvn clean verify` BUILD SUCCESS，16 个 Reactor 模块全部成功；全 Reactor Spotless 检查通过；Bootstrap 141 项测试通过，0 失败、0 错误、0 跳过。Testcontainers MySQL 8.4 启动成功，Flyway V1～V9 Migration 执行通过。使用默认测试配置，未运行 `llm-smoke`，未调用真实 LLM，也未创建新的 Interview。

结论：Core Backend 真实业务生命周期 E2E 验证与最终 Closure Gate 均已通过，**Core Backend E2E Acceptance 已完成**，不存在 Backend Closure Blocker。Evaluator 剩余 P2 语义问题由 [技术债清单](../00-project/technical-debt.md) TD-009 跟踪，可重复语义回归验证能力由 TD-010 跟踪，均不阻塞本次 Backend Closure。Backend Closure 当时的后续方向为 Frontend MVP / Full UI E2E，其现已完成，见下文 Frontend MVP Closure；Slice 6 外围能力仍待开始。验收边界见 [Core Backend E2E 阶段复盘](../retrospectives/core-backend-e2e-retrospective.md)。

## Frontend MVP Task 6 — Review 与 closure 准备（历史记录，2026-10-03）

以下保留 Task 6 准备阶段的验证记录。当时实现与前端自动化已完成，真实 Full UI E2E 尚待人工验收，尚未创建 PR 或合并；这些待验收、待合并状态已由下文 Closure 结果取代，不代表当前状态。

- Review 路由独立调用现有 GET Review，展示 Overview 时间、时长与统计，以及 MAIN / FOLLOW_UP 两层问题链、Backend 返回的 ANSWERED / SKIPPED / UNANSWERED、Answer 和现有精简 Evaluation。没有前端评分计算或业务状态推导。
- Session 完成后的复盘入口改为 Vue Router 导航；Review 提供返回 Session / 首页、loading、读取错误、requestId 和重新加载。非法 ID 不请求；切换 Interview / 离开页面取消读取并忽略迟到响应。
- Task 1–5 代码与设计复核：未发现当前合法 Backend 契约下阻塞闭环的其他明显问题。未修改 Backend、Migration、事务或恢复边界，未新增依赖。
- 前端一次性全测试首次 27 项通过；随后只增加 mock closure 测试，定向 Review 5 项通过（合计 28 项）。覆盖 API 错误、Create / Start、Runtime、失败恢复、Review 直访及主动 / 自动完成后的页面串联。type-check、production build 通过。
- 自动化使用真实 Vue SFC、自定义 renderer 与 mock fetch；Router 导航目标用 mock 验证，不代表真实浏览器、Backend 或 LLM E2E。
- 人工验收清单见 [Frontend README](../../frontend/README.md)：完整生命周期、真实追问与评价、自动完成、失败恢复、直访 / 刷新、错误与导航、部署 history fallback / API 转发。当时未把 Full UI E2E 标记为完成。
- 非阻塞观察：既有 API client 只校验 envelope，未完整校验 DTO 嵌套结构；Review 增加最小顶层检查，不在本 Task 引入通用 schema。部署转发与 history fallback 仍需部署环境验证。
- Task 6 文档要求的项目级 Closure Gate：根目录 `mvn clean verify` BUILD SUCCESS，16 个 Reactor 模块成功，全 Reactor Spotless 检查通过，Testcontainers MySQL 8.4 执行 V1～V9 Migration 通过。默认配置，未启用 `llm-smoke`，未调用真实 LLM。所有 Node 验证均为 one-shot，未启动 dev / watch server；进程检查无本 Task 的 Node / Vite 残留。
- 代码与设计 Review 结论：自动化范围通过，Blocker / Major 为 0；当时人工 Full UI E2E 验收仍待执行。检查了范围、事实源、状态与恢复、请求幂等边界、Migration（无改动）、错误反馈、测试和文档一致性。

## Frontend MVP Closure 与真实 E2E 验收（已完成，2026-10-03）

### 完成状态与范围

Frontend MVP Task 1～6 已完成并合并，**Frontend MVP 已完成，Frontend MVP E2E Gate 已通过**。技术方案为 Vue 3 + TypeScript + Vite + Vue Router + native fetch typed API client；技术选择与交互边界以 [MVP 设计](../frontend/mvp-design.md) 为准，任务范围见 [实施任务](../frontend/implementation-tasks.md)。

本节记录用户确认的真实 E2E 验收结果；准备阶段的 mock 自动化验证单独保留在上节。

### 真实 Full UI E2E 结果

- 完整链路已打通并通过真实验收：Create → Start → Answer → FOLLOW_UP / NEXT MAIN → Pause / Resume → Skip → Finish → Review。
- Review 已真实验证 `ANSWERED / SKIPPED / UNANSWERED` 三种结果。
- Recovery E2E 已真实验证：Answer 已持久化 → `PROCESSING_TURN` → HTTP 失败 → 后端重启 → Refresh 仍恢复 `PROCESSING_TURN` → Retry → `WAITING_ANSWER`。
- 恢复事实与 ADR-003 / ADR-006 一致：HTTP 失败与后端重启不撤销已保存的 Answer；刷新读取 Backend 权威状态，Retry 基于已持久化业务事实重新执行 Turn。

结论：真实完整生命周期、Review 与跨后端重启的 Turn 恢复均通过，Frontend MVP E2E Gate 已通过。准备阶段的自动化与项目级 Closure Gate 结果保留在上节；本轮仅同步文档，未重新执行测试或真实 LLM 调用。

### 真实使用发现（暂不处理）

- Submit / Retry 的 Turn processing 延迟明显，实际可达数十秒，需要后续 profiling；目前未确定耗时来源或优化方案。
- 面试问题存在重复、过度集中项目经验、题型覆盖不足的问题。Backend E2E-04 的单场景通过不代表语义重复被确定性消除，本轮观察与其验收边界一致。
- Review 当前信息完整，但信息密度高，UI 仍属于 MVP 验证形态。
- 尚未支持语音回答；语音仍在本轮 MVP 范围之外。

以上记录真实使用观察与当前能力边界，本轮暂不处理，不构成已确认的下一阶段实现方案，也不撤销本次 Frontend MVP Closure。

### 后续状态

Phase 3 仍为进行中，Slice 6 外围能力待开始。下一阶段应基于真实使用结果重新确定优先级；Question Quality V1 最小设计现已确认，优先同步 SoT / Data Contract；其他真实使用问题仍未确认处理方案。


## Question Quality V1 — Task 1 SoT / Data Contract（2026-10-03）

设计状态：[已确认]；Task 1 SoT / Data Contract 文档同步已完成，未实施代码、测试、配置或 Migration。完整 InterviewMode 与 9 个 KnowledgeArea 清单已补充至领域模型；后续 Task 按既定拆分顺序执行。

- 业务配置、Create/Prepare/Start 与 HTTP V1 预期变更：[需求 Contract](../01-requirements/requirements.md#question-quality-v1-配置与-http-contract已确认待实现)。
- Plan/PlanItem、数量、使用与 Skip、生命周期：[领域模型](../02-architecture/domain-model.md#question-quality-v1plan-与准备流程已确认待实现)。
- Planner/Decision 临时引用、生成与短生命周期流程：[Workflow 设计](../03-ai/agent-design.md#planner-与引用边界question-quality-v1已确认待实现)。
- MAIN Generator 与 previousMainQuestions 的输入边界：[Context 设计](../03-ai/context-design.md#question-quality-v1-main-generator-context)。
- 原子 Prepare、NOT NULL plan_item_id、删除 question.focus 与开发数据重置：[数据模型](../04-data/data-model.md#question-quality-v1-持久化-contract已确认待新增-migration)。
- Create → Prepare → Start 与 mode/focusArea UI：[Frontend 设计](../frontend/mvp-design.md)。

旧版 Slice 和 E2E 中的 Focus Selector、question.focus、Create → Start 记录仅描述当时已实现及验收事实；V1 不沿用这些流程，替换规则以以上 SoT 为准。V1 尚未实现、验收或合并，不将本次文档同步记为 Slice 完成。


## Question Quality V1 — Task 2 Domain + Persistence Foundation（2026-10-03）

Task 2 基础模型与最小充分验证已完成，基础实现 `4f9100f` 与 V11 修正 `8efdeaa` 已提交并推送至 `feat/question-quality-v1`，未创建 PR 或合并。Task 3 已实现并提交，当前处理 Task 3 Review 小修正；Question Quality V1 整体尚未完成。

- 新增完整 InterviewMode / KnowledgeArea，Interview DO、Repository 与快照保存 mode / focusArea 并校验合法组合；旧 Create 保留 COMPREHENSIVE 兼容入口。
- 新增无状态 InterviewPlan / PlanItem、DO、Mapper 和聚合 Repository。真实 ID 在 Java 分配，initialPlanItemId 必须属于聚合；校验数量、归属、重复 ID 与非空 focus / reason，Plan 与全部 PlanItems 原子保存，插入失败整批回滚。
- V10 创建 Plan / PlanItem 表、增加 Interview.mode / focus_area 与合法组合 CHECK，增加可空 question.plan_item_id，保留 question.focus。Plan 的 UNIQUE(interview_id) 与 MAIN 专用生成列唯一索引保护唯一性，允许多个 FOLLOW_UP 共享 PlanItem；不创建外键，不删除本地开发数据、不伪造或回填历史 PlanItem。
- Question DO、创建命令、快照和 Repository 增加可空 planItemId，保留旧构造入口与 Focus Selector 的持久化查询。Start / Turn / Skip、Planner、HTTP Create / Prepare 均未接入新计划模型，业务生命周期和事务边界不变。后续接入需在对应短事务校验 Interview/PlanItem 归属、FOLLOW_UP 继承与未使用项，以及 Prepare 的 CREATED/CAS 条件；全部路径接入后再新增 Migration 收紧 NOT NULL 并删除 question.focus。过渡约定见 [数据模型](../04-data/data-model.md#task-2-分阶段迁移用户已确认)。

验证：

- `mvn -pl interview-agent-bootstrap -am test '-Dtest=QuestionQualityDomainTest,TurnRepositoryTest,DatabaseMigrationTest,QuestionQualityMigrationUpgradeTest,SkipQuestionTest' '-Dsurefire.failIfNoSpecifiedTests=false'`：编译通过，执行 72 项；首次 71 通过，1 项因 Spring 对 MySQL CHECK 错误 3819 映射为 UncategorizedSQLException 而断言失败，数据库约束实际生效。
- 修正断言后 `mvn -pl interview-agent-bootstrap -am test '-Dtest=QuestionQualityDomainTest,DatabaseMigrationTest#mapsPlanFoundationAndEnforcesTransitionConstraints' '-Dsurefire.failIfNoSpecifiedTests=false'`：BUILD SUCCESS，3 项通过；仅重跑受修正影响的测试，未重复已通过的重型测试。已通过集覆盖领域规则、映射、Plan 唯一性、MAIN 唯一使用/FOLLOW_UP 共享、NOT NULL、聚合回滚及旧 Start / Turn / Skip 路径。
- MySQL 8.4 Testcontainers 执行 V1～V10 通过；独立 V9→V10 升级测试通过，保留历史 Question 正文、focus 与空 planItemId，Plan / PlanItem 表保持为空。
- 受影响模块执行 `mvn -pl interview-agent-interview,interview-agent-bootstrap spotless:apply`；断言修正后仅对 bootstrap 再次 apply。最终测试命令 validate 阶段执行等价 Spotless check，受影响模块检查通过。`git diff --check` 通过。
- 未执行项目级 `mvn clean verify`：本轮是基础模型小 Task，定向测试已覆盖变化和旧业务路径，Slice 收尾再执行全量 Gate。未启用 llm-smoke、未调用真实 LLM；Migration 仅在隔离测试容器执行，未迁移本地开发数据库。


## Question Quality V1 — Task 3 Planner AI Contract + Prepare（2026-10-03）

本 Task 仅接入 Prepare，基于 Task 2 聚合与 V11 Java 枚举校验边界；Question Quality V1 整体仍待后续 Task 接入和验收。原 Task 3 实现 `4984319` 已提交并推送至 `feat/question-quality-v1`，未创建 PR 或合并。当前处理 Task 3 Review 小修正，修正尚未提交或推送；保留原工作区改动，暂不进入 Task 4。

- AI API 新增 PlannerContext、ValidatedPlanResult 和 preparePlan capability；PlannerProposal 保留在 AI 内部，Validator 校验 mode/focusArea、9 个 KnowledgeArea、数量上下界、P* 引用格式及唯一性、initial 引用归属，解析为列表位置后移除临时引用。中文 Planner Prompt 约束模式、覆盖、差异化与候选人事实来源；不扩展 CandidateInterviewProfile。
- 新增 `POST /api/interviews/{interviewId}/prepare`，沿用 Result/error/requestId。成功返回 interviewId、planId、initialPlanItemId，均为正式 ID；重复/状态/持久化唯一键冲突使用现有 INTERVIEW_STATE_CONFLICT（HTTP 409），无效 AI 响应使用 AI_RESPONSE_INVALID（HTTP 502）。未修改 Create/GET Contract。
- AI 调用、Java 校验与真实 ID 分配在事务外；短事务锁读并重新检查 CREATED、currentPhase=null、无 Plan 和观察版本，原子保存 Plan、全部 PlanItems、initialPlanItemId。并发允许重复计算，仅一个 Plan 成功；失败回滚且可重试，不增加 Interview 生命周期状态，不修改 V10/V11 或新增 Migration。
- 现有 Create 保留 COMPREHENSIVE/null 默认行为，Start/Turn/Skip 不改造。后续 Task 4 需使用已准备 Plan 的 initial PlanItem 接入 Opening；后续 Turn/Skip 接入未使用 PlanItem 与归属校验；Task 7 再修改 Create mode/focusArea 与 GET planPrepared。当前仍处于分阶段过渡，不能视为 V1 完整用户流程已完成。

验证与自查：

- 首轮 `mvn -pl interview-agent-bootstrap -am test '-Dtest=PlannerProposalValidatorTest,InterviewPrepareTest,InterviewReviewControllerTest' '-Dsurefire.failIfNoSpecifiedTests=false'`：12 项全部通过，包含 2 项原 Controller 回归。
- 补充 Planner ChatClient/Prompt 静态契约与真实唯一键冲突测试后，定向运行 `PlannerProposalValidatorTest,InterviewPrepareTest`：12 项中 11 项通过；唯一失败为测试夹具对 MyBatis 抽象 Mapper 调用 callRealMethod，不涉及生产实现失败。
- 将故障注入移至具体 Repository 后，仅重跑 `InterviewPrepareTest#databaseUniqueConflictIsBusinessConflictAndRollsBack`：1 项通过、BUILD SUCCESS。合计 14 个不同测试已通过，覆盖 Validator 上下界/非法枚举/组合/引用、AI 调用在事务外、Prepare HTTP 成功/重复 409/无效 AI 502、失败重试、非 CREATED 拒绝、计算期间状态变化、并发仅保存一个聚合、真实唯一键冲突映射及中途插入失败原子回滚。未机械重复已通过重型测试。
- 四个受影响模块（ai-api、ai、interview、bootstrap）已执行 Spotless apply/check；测试夹具修复后 bootstrap 再次 apply，单项测试 validate 阶段的等价 Spotless check 通过。最终仅补充事务说明注释后检查 interview 格式；`git diff --check` 通过。
- MySQL 8.4 隔离 Testcontainers 应用 V1～V11；不修改本地数据库，不启用 llm-smoke，不调用真实 LLM。未执行项目级 `mvn clean verify`：当前为独立 Task，最小充分集已覆盖本次变更，Slice 收尾再执行全量 Gate。
- 范围、模块依赖、状态、事务/并发、Migration、日志与文档一致性自查未发现 Blocker/Major。真实 Planner 的语义质量与完整 Create→Prepare→Start 人工验收留待后续路径接入后显式 opt-in；本 Task 不宣称 V1 整体完成，停止在 Task 3。

### Task 3 Review 小修正（2026-10-03，待 Review）

- 删除 Planner Validator 的 InterviewMode / KnowledgeArea 固定清单及 Prompt 的 KnowledgeArea 固定清单。Interview 应用层从领域枚举 values() 构造 allowedInterviewModes / allowedKnowledgeAreas，经不可变 PlannerContext 传入；Validator 与 Prompt 消费同一份列表。TECHNICAL_FOCUS 的必须提供 focusArea、所有项匹配 focusArea 规则保持。
- 未修改 Maven 依赖，AI 不依赖 Interview 实现模块；未改变 HTTP、生命周期、事务、持久化或 Migration。只修正上述 Task 2 / Task 3 当前提交状态，保留原历史验收记录。
- `mvn -pl interview-agent-bootstrap -am test '-Dtest=PlannerProposalValidatorTest' '-Dsurefire.failIfNoSpecifiedTests=false'`：BUILD SUCCESS，9 项通过。覆盖应用层列表来自领域枚举、调用方扩展/收窄允许列表、未知值拒绝、空列表拒绝、列表不可变快照、TECHNICAL_FOCUS 约束、Prompt 实际传递相同列表，以及原 Validator 边界。
- 四个受影响模块（ai-api、ai、interview、bootstrap）执行 Spotless apply，随后测试命令 validate 阶段执行等价 Spotless check，全部通过；`git diff --check` 通过。未重复 Prepare Testcontainers 或执行 `mvn clean verify`：本轮只调整 AI 输入、校验与 Prompt，不涉及事务/数据库行为，定向单元测试与受影响模块编译已构成最小充分验证。未调用真实 LLM。
- 本轮修正留在工作区，未提交、推送或创建 PR；保留既有本地配置、截图和脚本。停在 Task 3，等待 Review，不进入 Task 4。

补充状态（2026-10-03）：上述 Task 3 Review 小修正随后已提交并推送至 `feat/question-quality-v1`，提交为 `cf19b7a065c9ecfe6c382e4ca4c34e0fd70bf281`；原“待 Review、未提交”的记录保留为当时事实，未创建 PR 或合并。

## Question Quality V1 — Task 4 Start / Opening 接入 Plan（2026-10-03）

实现范围：按 [Domain](../02-architecture/domain-model.md#question-quality-v1plan-与准备流程已确认待实现)、[Opening / MAIN Context](../03-ai/context-design.md#opening) 与 [短事务持久化 Contract](../04-data/data-model.md#task-2-分阶段迁移用户已确认)，仅将 Start / Opening 接入已 Prepare 的 Plan 和 initialPlanItem；不进入 Task 5。

- Start 的首个短事务锁读 Interview，校验 CREATED/currentPhase=null、存在本 Interview 的 Plan、initialPlanItem 属于该 Plan 且未被 MAIN 使用，然后使用既有 CAS 进入 IN_PROGRESS/OPENING。
- 事务外加载现有 CandidateInterviewProfile 和岗位资料，使用 initialPlanItem 的 area/focus/reason 与空 previousMainQuestions 生成首题，不再调用 Focus Selector。MAIN Context 增加上述输入；保留旧构造与 builder 入口供尚未接入的 Turn/Skip 过渡使用。中文 MAIN Prompt 说明 PlanItem 的方向/事实边界及历史 MAIN 去重用途。
- 首题提交短事务重读状态/版本、Plan 聚合与 initial 使用事实；Question.planItemId 保存真实 initialPlanItem ID，并与 WAITING_ANSWER 状态更新原子提交。失败按原 Opening 恢复规则回到 CREATED，保留 Plan，重试使用同一 initial 项；不增加生命周期状态。
- 暂时继续将该项 focus 写入旧 question.focus 列，以兼容现有 Turn/Skip 的 Focus Selector 查询；正式方向仍来自 PlanItem，不引入独立方向选择。字段删除和 plan_item_id NOT NULL 留给全部路径接入后的 Migration。本轮未修改 Repository 的旧创建入口、Turn/Skip 的 PlanItem 规则、Create/GET HTTP Contract 或 Migration。
- 当前验证目标是准备后的首题正确关联、非法状态/缺失 Plan 和错误归属被拒绝、AI 在事务外、失败保留 Plan 可重试、提交失败无部分 Question；不宣称 V1 整体或完整用户流程已验收。

验证记录：

- `mvn -pl interview-agent-bootstrap -am test '-Dtest=DatabaseMigrationTest#createsStartsAndReadsInterviewWithPersistedOpeningQuestion+recoversCreatedStateAfterOpeningFailureAndAllowsRestart+startRequiresPreparedPlanAndRejectsIllegalState+opening*,StructuredOutputSchemaTest,InterviewExpirationConvergenceTest,AiRequestLoggingTest' '-Dsurefire.failIfNoSpecifiedTests=false'`：BUILD SUCCESS，26 项通过。9 项 MySQL 8.4 Testcontainers 测试覆盖正式初始项（非列表首项）关联、HTTP 成功与重复 Start、缺失 Plan/非法状态、跨 Plan 初始引用/归属、已使用初始项、AI 期间 Plan 变化、事务中途失败回滚、AI 事务外执行及保留 Plan 重试；其余覆盖 Context/Prompt 边界、既有 AI 日志与过期收敛。
- 首轮通过后按 Prompt SoT 将本次 MAIN User Prompt 改为中文，并补充模型实际收到 area/reason/空 previousMainQuestions 的断言；仅重跑 `AiRequestLoggingTest#mainGenerationLogsFocusAndProfileWithoutTurnLevelInput`：1 项通过、BUILD SUCCESS，未重复已通过的重型测试。
- 四个受影响模块（ai-api、ai、interview、bootstrap）执行 Spotless apply，定向测试 validate 阶段执行等价 Spotless check；Prompt 调整后对 ai/bootstrap 再次 apply 与检查。`git diff --check` 通过。
- 未执行 `mvn clean verify` 或全模块测试：本轮为 Start/Opening 小 Task，定向单元和真实数据库事务验证覆盖本次改动；Slice 收尾执行全量 Gate。未启用 llm-smoke、未调用真实 LLM，真实生成语义质量及完整前端流程仍待后续显式 opt-in 验收；未迁移本地数据库。

工作区：保持 `feat/question-quality-v1`，既有 application-local.yml、截图与本地脚本未改动；本 Task 未提交、推送、创建 PR 或合并，停在 Task 4，等待 Review，不进入 Task 5。

### Task 4 Review：MAIN Prompt 兼容修正（2026-10-03，待 Review）

原 Task 4 实现已提交为 `74291dc4799db13750d0641598c3de8b57c0a33d`，尚未推送；上述未提交记录保留为当时事实。本轮为该实现的 Review 修正，留在工作区，不提交或推送，不进入 Task 5。

- MAIN Context 不再把缺失 previousMainQuestions 转为 List.of()：null 明确表示历史未提供，显式空列表表示已提供且没有历史 MAIN；已提供列表仍保存不可变副本。旧构造器与未设置新字段的旧 builder 均保留缺失语义。
- User Prompt 按完整 PlanItem / 仅 focus 分别组织方向信息，旧输入明确说明 area、reason 不可用，不打印缺失字段的 null，不称其为完整 PlanItem；历史未提供不能被理解为没有历史或当前为首题。System Prompt 同步说明两类输入边界。
- Opening 继续传入完整 area/focus/reason 和显式空历史列表，行为保持；未改动 Interview Application、Turn/Skip 的选择与持久化、HTTP、Migration 或事务。
- 新增实际 ChatClient 用户 Prompt 测试：旧构造器、旧 builder、Opening 完整输入、完整项但缺失历史，以及提供历史的不可变副本；静态 Prompt Contract 同步锁定缺失/显式空列表语义。
- `mvn -pl interview-agent-bootstrap -am test '-Dtest=MainQuestionPromptCompatibilityTest,StructuredOutputSchemaTest,AiRequestLoggingTest' '-Dsurefire.failIfNoSpecifiedTests=false'`：BUILD SUCCESS，21 项全部通过。三个受影响模块（ai-api、ai、bootstrap）执行 Spotless apply，随后测试 validate 阶段的等价 Spotless check 全部通过；`git diff --check` 通过。未运行 Testcontainers 或项目级 `mvn clean verify`：本轮仅修正 Context/Prompt，实际用户 Prompt 与 Contract 单元测试及受影响模块编译构成最小充分验证，不涉及数据库或事务行为。未调用真实 LLM；保留原本地配置、截图和脚本。


## Question Quality V1 — Task 5 Turn / Skip 接入 PlanItem（2026-10-04）

实现依据：用户明确授权本 Task 采用当前 `feat/question-quality-v1` 分支中已确认的 V1 SoT；main 中旧 Focus Selector 运行流程不适用于本 Task。其他仓库规范保持。任务边界见 [Workflow](../03-ai/agent-design.md#planner-与引用边界question-quality-v1已确认待实现)、[MAIN Context](../03-ai/context-design.md#question-quality-v1-main-generator-context) 与 [分阶段数据 Contract](../04-data/data-model.md#task-2-分阶段迁移用户已确认)。

- Decision 输入当前 Interview Plan 的未使用项；本次调用生成 P* 引用，Prompt 与 Validator 共用同一映射。NEXT_QUESTION 严格解析为真实 planItemId，非法、缺失、已用或集合外引用拒绝；FOLLOW_UP/END 禁止携带计划引用。
- Turn MAIN 与 Skip MAIN 使用 PlanItem 的 area/focus/reason 和当前 Interview 全部历史 MAIN 正文（含 SKIPPED），不传 Answer、Evaluation 或 FOLLOW_UP。FOLLOW_UP 持续继承所属 MAIN 的 planItemId，原追问目标与上下文边界保持。
- Skip 从 Plan 原有顺序取第一个未使用项，不调用 Focus Selector；当前 MAIN 的 SKIPPED 状态不释放关联，FOLLOW_UP 不额外消耗项。未达 MAIN 上限但 Plan 耗尽时抛出数据一致性异常。
- T2 重读 Interview 状态/version、完整 Plan 和 MAIN 使用事实，校验归属与 FOLLOW_UP 继承；Question 创建/当前题转换、Evaluation（仅 Turn）及 Interview CAS 原子提交。AI 保持在事务外；失败不消耗新项，Turn 保留 T1 Answer / PROCESSING_TURN，自动/手动重试与并发语义保持。
- 暂存 MAIN focus 为 PlanItem.focus，FOLLOW_UP focus 保持为空；保留可空 plan_item_id。未改 Migration、Create/GET HTTP Contract，未删除旧 Focus Selector API 或进入后续 Task。

最小充分验证：

- `mvn -pl interview-agent-bootstrap -am test`，指定 `DecisionRulesTest`（7）、`ProcessedTurnPersistenceTest`（15）、`ProcessedTurnTransactionTest`（5）、`SkipQuestionTest`（11）及 `DatabaseMigrationTest` 中直接相关的 12 个方法：**BUILD SUCCESS，50 项通过，0 失败/错误/跳过**。后者选择 NEXT/连续 FOLLOW_UP、全部 MAIN 历史（含 SKIPPED）、Plan 耗尽、AI 期间归属变化、跨过期边界、自动/手动重试及重试并发方法，没有运行整个 DatabaseMigrationTest。
- 单元验证覆盖临时引用格式/本次可用集合、真实已用/越权/空 ID 拒绝、MAIN focus 一致性和 FOLLOW_UP 继承；MySQL 8.4 Testcontainers 验证正常提交、最终 CAS 失败整批回滚、并发 NEXT/Skip/Submit 仅单一业务路径、失败保留 T1、Skip 按原序消费及不释放 SKIPPED 项。
- 受影响模块 `interview-agent-api/interview-agent-ai-api,interview-agent-ai,interview-agent-interview,interview-agent-bootstrap` 已执行 `spotless:apply`；最后仅同步过渡期注释，按注释变更规则补做相关模块格式化，不重复已通过业务测试。上述四个受影响模块的 `spotless:check`：**BUILD SUCCESS**；`git diff --check`：**通过**。
- 本 Task 不是 Slice 收尾，定向 Gate 已覆盖修改行为，未运行整个模块测试或 `mvn clean verify`；未启用 `llm-smoke`，未调用真实 LLM，未进行真实模型人工验收。本次自动化证明引用、归属、事务和上下文契约，不承诺模型语义去重效果。

实现阶段自检覆盖范围、模块边界、状态流转、T1/T2 与幂等并发、Migration（无改动）、日志、测试和文档一致性。本 Task 实现与定向 Gate 已完成，停下等待用户 Review；不代表独立 Review 通过或 V1 整体完成。保持当前分支，既有 application-local.yml、截图与本地脚本未改动；未提交、推送或创建 PR，不进入后续 Task。


### Task 5 提交与 Review 状态同步（2026-10-04）

Task 5 已提交并推送至 `feat/question-quality-v1`，提交为 `68956aaa7abf264896c1de66cce46b600cb9f040`；用户确认 Review 已通过。上文“未提交、推送、等待 Review”保留为提交前的历史事实，不再代表当前状态。未创建 PR 或合并；下一项为 Task 6 最终 Schema Migration，Create/GET Contract 留待 Task 7。

## Question Quality V1 — Task 6 最终 Schema Migration（历史方案，2026-10-04，已被延期决定替代）

范围依据为当前分支已确认的 [数据 Contract](../04-data/data-model.md#task-2-分阶段迁移用户已确认)。Start/Opening、Turn MAIN/FOLLOW_UP 与 Skip MAIN 已接入真实 PlanItem，符合进入最终字段收紧的前置条件；本 Task 不修改 Create/GET Contract，不进入 Task 7，也不代表 V1 整体完成。

- 新增 V12：先显式检查历史空关联，发现 `question.plan_item_id IS NULL` 则拒绝继续；随后以同一 ALTER 收紧 NOT NULL 并删除 question.focus。无回填、伪造 PlanItem、业务数据删除或外键；V1～V11 不修改。已有空关联的开发数据库不会被静默转换，后续数据处理仍需独立明确授权。
- 移除 Question DO/创建命令/快照中的 focus、旧 focus 查询及未写入 planItemId 的 createOpening/过渡构造入口；Repository 拒绝缺失 PlanItem ID。首题使用 initial 正式 ID，Turn MAIN 使用本次解析的真实 ID，FOLLOW_UP 继承所属 MAIN ID，Skip 使用正式未使用项；既有短事务归属、未使用检查及 CAS 边界保持。
- 测试夹具改为先建立真实 Plan，再插入关联 Question，删除插入后补写关联的过渡助手。MySQL 8.4 Testcontainers 增加 V11→V12 合法历史保留、空关联拒绝且不伪造事实、最终 NOT NULL/删除 focus、MAIN 唯一使用与 FOLLOW_UP 共享验证；既有 V9→V10、V10→V11 测试固定到对应历史版本。

验证过程：首轮数据库回归暴露 QuestionSnapshot 去除 focus 后的参数错位，读取的 PlanItem ID 不正确；修正 Repository 映射并为 EvaluationProfile/Topic/PlanItem 三个 ID 增加独立断言后，`TurnRepositoryTest#mapsStoredQuestionTypeStringBackToEnum` 定向验证通过（1 项、BUILD SUCCESS）。已停止原失败批次，随后集中重跑受影响数据库 Gate；最终定向 Gate 已通过，详见下文。仅在隔离 Testcontainers 执行 Migration；未迁移、清理或重置本地开发数据库，未调用真实 LLM。本 Task 保持工作区未提交、未推送、未创建 PR，完成验证后停下等待 Review。


Task 6 最小充分验证与停止状态：

- 修复后执行 `mvn -pl interview-agent-bootstrap -am test '-Dtest=QuestionQualityMigrationUpgradeTest,DatabaseMigrationTest,TurnRepositoryTest,ProcessedTurnTransactionTest,SkipQuestionTest' '-Dsurefire.failIfNoSpecifiedTests=false'`：**BUILD SUCCESS，89 项通过，0 失败/错误/跳过**。其中数据库与首题/Turn/读取回归 64 项、T2 回滚/并发 5 项、分阶段与最终 Migration 4 项、Skip 11 项、Repository 单元 5 项。
- 首轮已通过的 `ProcessedTurnPersistenceTest` 15 项保持有效；后续修复只涉及真实 Repository 快照转换，该组使用 mock Repository，测试与应用代码未再变化，不重复运行。与最终 Gate 合计 **104 项不同测试通过**；修复点单项验证随后也包含在最终 Repository 5 项中，不重复计数。
- MySQL 8.4 Testcontainers 完整应用 V1～V12，通过 V9→V10 历史保留、V10→V11 枚举检查移除、V11→V12 正式关联保留/最终约束，以及 V12 拒绝空关联并保留旧事实。生产路径验证覆盖 initial ID、NEXT MAIN 正式 ID、FOLLOW_UP 继承、Skip 使用/不释放、CAS 失败回滚及并发仅一次提交。
- `interview-agent-interview,interview-agent-bootstrap` 已执行 `spotless:apply`；修复后对涉及模块再 apply，最终显式 `mvn -pl interview-agent-interview,interview-agent-bootstrap spotless:check`：**BUILD SUCCESS**。`git diff --check`：**通过**。
- 未执行全模块测试、项目级 `mvn clean verify` 或真实 LLM/llm-smoke：本次为 Schema 小 Task，定向 Gate 已覆盖受影响 Migration、持久化、事务与业务生成路径，V1 收尾再执行完整 Gate。仅同步构造参数的 Review 单元夹具完成编译；llm-smoke 夹具只调整真实关联准备，未运行真实模型测试。
- 实现自检核对范围、模块与生命周期边界、Question 创建路径、事务/CAS/幂等、Migration、日志、测试及文档一致性；本次未新增人工模型验收，不声称独立 Review 已通过。Task 6 实现与定向验证已完成，**停下等待用户 Review**，V1 整体尚未完成。
- 保持 `feat/question-quality-v1`；未迁移、清理或重置本地开发数据库，未改 Create/GET Contract，既有 application-local.yml、截图与本地脚本未改动。未提交、推送、创建 PR 或合并，不进入 Task 7。


### Task 6 Review 修正：V12 并发写入窗口（历史方案，2026-10-04，已被延期决定替代）

原 Task 6 实现已提交并推送，提交为 `7be291e777c2d8fd4e5b2c0ef27e532efed6049c`；上文未提交/待提交状态保留为当时事实。Review 发现原 V12 的临时表计数检查与最终 ALTER 之间仍允许旧应用写入 NULL；上文原方案及 104 项验证属于修正前记录，不能证明该并发窗口已关闭。本轮按用户明确指令修正 V12，留在工作区，不提交或推送。

- 删除临时表计数预检查。第一条 ALTER 在独占锁下添加 `ck_v12_no_null_question_plan_item CHECK (plan_item_id IS NOT NULL) ENFORCED`，由 MySQL 原子校验现有行并建立正式写入约束；历史 NULL 会使该 DDL 失败，不回填、不伪造、不删除事实。
- CHECK 在两条 DDL 之间持续阻止 INSERT/UPDATE NULL；第二条 ALTER 原子执行 DROP CHECK、plan_item_id NOT NULL 与 DROP focus，没有先单独解除 CHECK 的窗口。若最终 ALTER 失败，已建立的 CHECK 仍保护非空规则；不自动 repair 或处理历史数据。
- 增加 MySQL 8.4 双连接验证：直接读取实际 V12 SQL，在第一条执行成功后暂停，确认列此时仍可空；另一条连接在宽松 SQL 模式下尝试省略关联、显式 INSERT NULL 和 UPDATE NULL，均须被 CHECK 拒绝，真实关联 FOLLOW_UP 仍允许写入；执行第二条后确认最终 NOT NULL、focus 删除、过渡 CHECK 移除及合法数据保留。该测试确定性暴露两条语句间隔，不依赖 sleep 或随机竞争。
- 历史 NULL 失败测试增加 question 全行快照比较，核对失败前后所有字段与数量不变，原 focus、空关联均保留，Plan/PlanItem 仍为空；既有 Flyway 成功升级验证继续覆盖最终 Schema、MAIN 唯一性与 FOLLOW_UP 共享。

本轮最小充分 Gate：

- `mvn -pl interview-agent-bootstrap -am test '-Dtest=QuestionQualityMigrationUpgradeTest#final*+enforcedGuard*' '-Dsurefire.failIfNoSpecifiedTests=false'`：**BUILD SUCCESS，3 项通过，0 失败/错误/跳过**。覆盖实际 Flyway V11→V12 成功升级、历史 NULL 失败及全行不变、两条 ALTER 之间另一连接的非法/合法写入与最终 Schema。
- bootstrap 已执行 `spotless:apply`，随后显式 `mvn -pl interview-agent-bootstrap spotless:check`：**BUILD SUCCESS**；`git diff --check`：**通过**。
- 未重复全部业务持久化测试、全模块测试或 `mvn clean verify`：本轮仅修复 V12 的 DDL 顺序和保护机制，三个实际 MySQL 测试已覆盖变更；Java 业务代码和 HTTP Contract 未变，完整 Gate 留待 V1 收尾。未启用 llm-smoke 或调用真实 LLM。
- 实现自检核对历史数据拒绝、过渡 CHECK 的连续保护、原子 DDL、失败后约束保留、最终字段与索引、测试和文档一致性；**修正与最小充分验证完成，停下等待 Review**，不宣称独立 Review 已通过。

范围保持 Task 6；未改 Java 业务实现、Create/GET Contract 或 V1～V11，不进入 Task 7。仅操作隔离 Testcontainers，不操作本地开发数据库，保留既有本地配置、截图和脚本。完成必要 Gate 后停下等待 Review，未创建 PR 或合并。


## Question Quality V1 — Task 7 Create / GET API Contract（2026-10-04，待 Review）

本轮在 `feat/question-quality-v1` 核对分支、工作区及 `origin/main` 后，按用户明确指定的当前分支已确认 V1 设计推进。V1 设计仍位于功能分支 doc/，未合并到 main；依据为 [需求 HTTP Contract](../01-requirements/requirements.md#question-quality-v1-配置与-http-contract已确认待实现)、[领域枚举与 Plan 规则](../02-architecture/domain-model.md#question-quality-v1plan-与准备流程已确认待实现)、数据 Contract 与 ADR-003。Task 6 Review 修正已进入当前分支提交；上文工作区未提交记录保留为当时事实。本记录不代表 V1 整体完成。

- Create 新增 InterviewCreateRequest，要求 JSON 请求体和非空 mode；focusArea 仅 TECHNICAL_FOCUS 必须提供，其他模式不允许非空值。HTTP 按领域枚举名称解析，拒绝未知值、空串及数字序号，不复制枚举清单。组合规则复用 InterviewMode.validateFocusArea，在创建事务前转换为参数业务异常；Repository 保存实际 mode/focusArea，仍使用固定演示 Candidate/Position。
- GET / 共用 InterviewDetailResponse 增加 mode、focusArea、planPrepared。前两项来自持久化配置，planPrepared 从正式 Plan 是否存在派生；未新增列或计划状态，不暴露 PlanItem 内容或 AI 临时引用。现有详情字段、Result/error/requestId 包装保留；使用同一详情 DTO 的 Create/Start/Skip/Pause/Resume/Finish 响应也增加三个字段，Answer/Retry/Review Contract 保持。
- 必填/null mode 及不合法组合使用 HTTP 400 / 10001；不可解析请求、未知枚举及缺失请求体沿用现有 HTTP 400 / 10003。旧无请求体 HTTP Create 被 V1 请求替代；旧数据库 COMPREHENSIVE/null 默认事实与 Repository 兼容入口保留。Start 仍要求已准备 Plan，Opening 失败仍保留 Plan 并恢复 CREATED；未调整状态流转、恢复或事务边界。
- 更新 Create HTTP 回归夹具，增加三种模式及九个 KnowledgeArea 的保存/GET 验证、非法 Create 不落库、不调用 AI、requestId、缺失请求体与不存在 GET；Prepare 成功、失败和重试测试核对 planPrepared 与 CREATED/null phase。新增 DTO JSON 测试，验证枚举名称与数字、大小写、空白拒绝。同步需求 Contract、开发示例及 Frontend 文档的 Backend 实现事实，未修改前端代码。旧前端无请求体 Create 待后续 Frontend Task 接入。

验证与自检：

- 首轮编译发现响应参数替换误触过期判断，已修正；未执行到数据库测试。修正后 `mvn -pl interview-agent-bootstrap -am test '-Dtest=InterviewPrepareTest,DatabaseMigrationTest#createsStartsAndReadsInterviewWithPersistedOpeningQuestion+recoversCreatedStateAfterOpeningFailureAndAllowsRestart' '-Dsurefire.failIfNoSpecifiedTests=false'`：**BUILD SUCCESS，31 项通过，0 失败/错误/跳过**。覆盖 Create/GET/Prepare 的数据库与 HTTP 路径、完整 Plan 事务回归、Start 和 Opening 失败恢复。
- 后续枚举名称解析修正后，执行 `mvn -pl interview-agent-bootstrap -am test '-Dtest=InterviewCreateRequestTest,InterviewPrepareTest#createAndGetPersistModeAndUnpreparedProjection+technicalFocusAcceptsEveryConfirmedArea+rejectsInvalidCreateWithoutPersisting+rejectsMissingCreateBodyAndMissingGet' '-Dsurefire.failIfNoSpecifiedTests=false'`：**BUILD SUCCESS，31 项通过，0 失败/错误/跳过**（DTO JSON 9 项，受影响 Create/GET HTTP 22 项）。此前 Prepare 7 项与 Start/恢复 2 项代码未再改变，其验证保持有效；合计 **40 项不同测试通过**，不重复计数。
- 两个涉及模块均执行 spotless:apply；最终显式 `mvn -pl interview-agent-interview,interview-agent-bootstrap spotless:check`：**BUILD SUCCESS**。`git diff --check`：**通过**。
- 未执行全模块测试或项目级 mvn clean verify：本 Task 的 HTTP Contract 与持久化读取变化由定向 Gate 覆盖，完整 Gate 留待 V1 收尾。只使用隔离 MySQL 8.4 Testcontainers 和 mock AI，未操作本地数据库，未启用 llm-smoke 或进行真实 LLM 验收。
- 实现自检覆盖范围、模块边界、状态、事务与幂等、Migration、日志、测试、人工验收范围和文档一致性；未新增 Migration 或日志敏感内容，不声称独立 Review 已通过。无需更新 PROJECT_CONTEXT：本轮是普通 Task 进度，不变更低频导航。

保持工作区未提交、未推送、未创建 PR 或合并；保留既有 application-local.yml、截图和本地脚本。完成最小充分验证后停下等待用户 Review，不进入前端改造、真实 LLM 验收或 V1 最终收尾。

用户随后明确授权提交并推送本次 Task 7 改动；上文未提交、未推送状态保留为验证完成时的历史事实。Task 7 仍待 Review，未创建 PR 或合并，不进入后续 Task。

## Question Quality V1 — 最终验收（2026-10-04，真实浏览器 Gate 阻塞）

本轮按用户要求在 `feat/question-quality-v1`（HEAD `8eaa28f`）执行最终验收，依据当前分支的 V1 需求、领域、Workflow、Context、数据与 Frontend SoT、Task 进度及 `frontend/README.md` 人工验收说明。**V1 验收尚未完成**；自动化结果不能替代真实浏览器与真实 LLM Gate。

### 自动化命令与结果

- 仓库根目录 `mvn clean verify`：**BUILD SUCCESS**，16 个 Reactor 模块全部成功；218 项测试通过，0 失败/错误/跳过（汇总本轮生成的 Surefire XML），全项目 Spotless check 通过；耗时 6 分 39 秒。覆盖业务、AI Proposal/Prompt、HTTP、持久化、事务/并发、Review 及 MySQL 8.4 Testcontainers 的 V1～V12 Migration，包括合法历史升级、历史空关联拒绝与两条 ALTER 之间持续非空保护。使用默认配置，没有启用 `llm-smoke`、没有真实模型调用；没有省略本任务要求的重型自动化验证，无需重复同状态的根 Spotless Gate。
- `frontend/`：`node --test tests/*.test.mjs`：33 项通过，0 失败/取消/跳过；覆盖全部现有 api、create-start、runtime、recovery、review 测试文件。验证 Create 模式/方向、Prepare 门禁及失败对账、运行/恢复和 Review mock Contract，不是实际模型生成验证。
- `frontend/`：`npm run type-check`、`npm run build`：均通过；覆盖 Vue SFC/TypeScript 契约及 production bundle。
- `git diff --check`：验收前及文档更新后均通过；Git 仅提示工作副本 LF 将转 CRLF，无空白错误。

### 隔离数据库与真实验收限制

- 新建专用容器命令：`docker run -d --name interview-agent-v1-acceptance-20261004 --label purpose=question-quality-v1-acceptance -p 127.0.0.1:13316:3306 -e MYSQL_ALLOW_EMPTY_PASSWORD=yes -e MYSQL_DATABASE=v1_acceptance mysql:8.4`。仅绑定 loopback，不挂载现有开发库或目录；无真实凭据。
- 启动应用前只读检查：`docker exec interview-agent-v1-acceptance-20261004 mysql -uroot --batch --skip-column-names -e "SELECT SCHEMA_NAME FROM information_schema.schemata WHERE SCHEMA_NAME='v1_acceptance'; SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='v1_acceptance'; SELECT @@port, @@version;"`。初始化完成后确认 schema 为 `v1_acceptance`、表数 0、端口 3306、MySQL 8.4.11；首次初始化期间尚未建立目标 schema，未据此启动应用。
- 浏览器工具 inventory 返回 `apps=[] / browsers=[]`；创建 Chrome 与内置浏览器均返回 `Browser is not available`。按 computer-use 技能初始化原生控制后，`sky.list_apps()` 返回 `Computer Use native pipe is unavailable`（系统找不到指定文件）。没有可用的真实浏览器控制通道，因此停止本项，不用直接 API 或 mock 冒充浏览器验收。
- 未启动人工验收 Backend/Frontend，未在该隔离库执行 Flyway，未调用已配置真实 LLM，未产生实际 Plan/Question/Interview 或模型流程结果。未接入、迁移、清理、重置或回填现有 `interview-agent-mysql` 开发数据库；不存在本轮人工启动触发的 V12 历史空关联失败或处理。
- `docker stop interview-agent-v1-acceptance-20261004`：成功；保留已停止的空验收容器供追溯，没有遗留本轮人工应用进程。

以下真实场景全部**未执行、未通过 Gate**：三种创建模式 `COMPREHENSIVE / PROJECT_DEEP_DIVE / TECHNICAL_FOCUS`；技术专项方向选择及实际生成与方向一致性；Create → Prepare → Start → Answer → FOLLOW_UP / NEXT MAIN → Pause / Resume（同题）→ Skip → Finish → Review；Review 中 ANSWERED / SKIPPED / UNANSWERED 与实际操作逐题核对。后续须在恢复真实浏览器通道后于隔离数据库补验，并记录实际生成正文与流程结果；即使单次通过，也不构成语义去重、覆盖或模型质量的确定性保证。

### 工作区与停止状态

本轮仅同步本进度文档，无生产代码修复或无关重构；没有提交、推送、创建 PR 或合并，保持工作分支。既有 `application-local.yml` 和 `scripts/local-llm.local.example.ps1` 未编辑、未覆盖，未打印密钥。初始 `git status` 中存在的 `localhost_5173_interviews_29_review.png` 在后续检查中消失（`Test-Path=False`）；本轮未对该截图执行删除或移动，无法确认外部变化原因。所有必需 Gate 未齐，禁止记录 V1 验收完成。


## Question Quality V1 — V12 删除 focus，NOT NULL 延期（2026-10-05，待 Review）

用户明确确认本次调整；当前分支 `feat/question-quality-v1`，起始 HEAD `af5f644`，工作区仅有既有未跟踪本地脚本。本次 V12 仅删除 question.focus，保留 question.plan_item_id 可空，移除非空 CHECK 和 NOT NULL DDL。无回填、伪造 PlanItem 或 Question 删除，V1～V11 及生产 Java 行为不变。上述 Task 6 非空约束和并发保护方案及对应 Gate 均为历史记录，不能证明本轮新契约已收口。

修改前应用状态核查：用户提供本地开发库 V12 为失败状态、Testcontainers 为临时库；本轮未连接本地数据库。检查仓库进度/部署与配置记录、数据库环境变量名称及 Docker 容器 inventory，未发现其他持久化库成功应用 V12 的证据。上轮专用持久化验收容器停在空库，未执行 Flyway；其他可见 MySQL 容器仅核对 inventory，未启动或查询。仓库未提供远程/共享库地址及其 Migration 记录，无法独立证明未提供环境的全局状态；已询问用户是否有其他持久化应用环境，按当前已知范围推进。如果发现持久化库已成功应用旧 V12，必须停止，不改写其 Migration。不得通过 repair 或清理本地数据解决失败记录。

迁移测试更新：移除旧非空 CHECK 双连接测试；通过实际 Flyway V11→V12 验证带 focus 且 NULL 关联的历史 Question 成功升级、focus 列删除、除该列外全部行字段与数量不变、NULL 保留、Plan/PlanItem 仍为空且 Flyway V12 success=true。另保留真实关联、PlanItem.focus、MAIN 唯一性及 FOLLOW_UP 共享回归，基础 Migration 测试的可空断言同步为 YES。

本轮最小充分验证：

- `mvn -pl interview-agent-bootstrap -am test '-Dtest=QuestionQualityMigrationUpgradeTest,DatabaseMigrationTest#mapsPlanFoundationAndEnforcesTransitionConstraints+connectsToMySqlAndAppliesBaselineMigration' '-Dsurefire.failIfNoSpecifiedTests=false'`：**BUILD SUCCESS，6 项通过，0 失败/错误/跳过**。四项分阶段/历史升级测试及两项基础 Migration 测试覆盖本次 SQL、历史 NULL 保留、focus 删除、合法关联与结构约束；只在隔离 MySQL 8.4 Testcontainers 执行。
- `mvn -pl interview-agent-bootstrap spotless:apply` 与 `mvn -pl interview-agent-bootstrap spotless:check`：**BUILD SUCCESS**；`git diff --check`：**通过**。
- 未运行全模块测试或 `mvn clean verify`：本次仅调整 V12 SQL 和对应 Migration 断言，定向真实 MySQL 验证已覆盖改动，不涉及业务 Java/HTTP/事务变化，非 V1 收尾。未运行前端或真实 LLM/llm-smoke，未重复上轮全量 Gate。

本轮没有测试失败或需额外修复的问题；实现自查核对 DDL 范围、历史行保留、NULL/唯一索引、生成路径校验不变及文档一致性，不声称独立 Review 已通过。不操作本地库、不执行 repair、不提交或推送。plan_item_id NOT NULL 已延期，V1 最终契约尚未完成，真实浏览器验收仍未通过；**V1 不得标记为验收完成**。定向验证和格式/diff 检查已完成，停下等待 Review。

## Public Profile V0.1 — Task 3 Interview Integration（2026-10-07，待 Review）

- 按用户确认的创建准入规则实现，规则记录于 [领域模型](../02-architecture/domain-model.md)。`InterviewApplicationService.createInterview()` 先执行原 mode/focusArea 校验，仅 PROJECT_DEEP_DIVE 通过现有 CandidateQueryApi 读取 Current Candidate 的完整 Projects，空列表抛出 PARAM_ERROR(10001)，在创建事务与 Interview 持久化之前拒绝。
- 未改其他模式、Prepare/Start/Resume/Retry/Turn、Profile 写入、Interview/Plan 模型、Prompt/Planner、Migration 或模块依赖；接受 Mutable Profile background drift，不新增项目选择、Snapshot/Version 或编辑阻断。
- `mvn -pl interview-agent-bootstrap -am test '-Dtest=InterviewCreateRequestTest,InterviewPrepareTest#currentProjectsOnlyRestrictDeepDiveCreation+createAndGetPersistModeAndUnpreparedProjection+technicalFocusAcceptsEveryConfirmedArea+rejectsInvalidCreateWithoutPersisting+rejectsMissingCreateBodyAndMissingGet' '-Dsurefire.failIfNoSpecifiedTests=false'`：BUILD SUCCESS，36 项通过，0 失败/错误/跳过。新增 5 个 HTTP/MySQL 场景覆盖 0/1/2 Projects 的项目深挖、空项目综合/合法技术专项，以及失败时 Interview 数量不变；其余回归全部合法 KnowledgeArea、非法 mode/focusArea 和 JSON/HTTP Contract。测试恢复原 Projects，仅操作隔离 MySQL 8.4 Testcontainers，AI 为 mock。
- 两个受影响模块执行 spotless:apply；`mvn -pl interview-agent-interview,interview-agent-bootstrap spotless:check`：BUILD SUCCESS；`git diff --check` 通过。未运行全模块测试或 mvn clean verify：本次为小 Task，定向数据库/HTTP 与 DTO 测试已构成最小充分验证。未启用 llm-smoke，未调用真实 LLM 或操作本地业务数据库。
- 实现自查覆盖范围、Public API 边界、状态、事务与幂等、Migration（无变更）、日志（无新增）、测试与文档一致性；未发现 Blocker/Major，不声称独立 Review 或人工验收已通过。保持 feat/public-profile-v1，不切换分支、不提交、推送或创建 PR；本 Task 实现与定向 Gate 已完成，待用户 Review，不标记整个 Public Profile Slice 完成。

## Public Profile V0.1 — Task 4 Profile Frontend（2026-10-07，待 Review）

- 按用户确认范围新增 `/profile` 和 typed Profile GET/PUT client，设计记录于 [Frontend 设计增量](../frontend/mvp-design.md#public-profile-v01--task-4已确认)。本地编辑 Candidate/Resume/Position 与 0..5 Projects，新增 ID=null、已有 ID 保留、删除项目不进入完整 replacement；不排序、不调用 Project CRUD。提供读取等待/失败/重试、dirty、基础 Contract 校验、保存 pending 与成功/失败反馈；成功直接采用 PUT canonical Profile，不额外 GET。
- 固定显示首次替换示例资料与资料影响进行中面试的说明，不检测或阻止 active Interview。Home 挂载时读取 Profile，仅显示姓名、岗位、项目数量和编辑入口；0 Projects 仅禁用项目深挖，其他合法模式可创建；读取中/失败禁止创建，失败提供重试及编辑入口。保留 mode/focusArea、Create 导航与 Session/Review 行为，后端准入校验仍为最终保障。
- Task 4 当时的 Project ID 传输处理已被后续 Long ID HTTP Contract Fix 替代；当前统一规则见 [Java / API 规范](../00-project/java-development-guide.md)。原测试及验收记录保留为当时事实。
- `npm run type-check`、`npm run build`：通过。新增 `node --test tests/profile.test.mjs` 7 项通过，覆盖 GET/重试、0..5 项目、本地增删改、完整 PUT/null/删除/保留 ID、canonical 新 ID 与精确 Long、校验、保存成功/失败/重复提交，以及 Home 摘要和准入门禁。
- 前端回归先执行 `node --test tests/*.test.mjs`：当时 39 项中 38 通过，原有 mock closure 缺少 Profile GET fixture 导致 1 项失败；补齐 fixture 后 `node --test tests/review.test.mjs tests/profile.test.mjs` 11 项通过。其后仅增加 ID/文本精度测试并执行最终 Profile 7 项，未再改变生产代码。累计 40 项不同用例均有通过记录，不重复运行未受影响的 Runtime/Recovery/API。全部使用 mock fetch，不调用真实 Backend/数据库/LLM。
- `git diff --check`：通过。仅修改前端和相关文档，无 Java/XML/POM、Migration 或后端业务改动，无需 Spotless、Backend/Testcontainers 或 `mvn clean verify`；这是普通 Task，前端测试/类型/构建已构成最小充分验证。真实浏览器人工验收未执行，检查步骤已补充至 `frontend/README.md`，不声称真实端到端 Gate 已通过。
- 实现自查覆盖范围、架构边界、状态、写入重复提交、Migration（无变更）、日志（无新增）、测试与文档一致性；未发现 Blocker/Major，不声称独立 Review 已通过。保持 `feat/public-profile-v1`，不切换分支、不提交、推送或创建 PR；保留工作区供用户 Review，不标记整个 Public Profile Slice 完成。

## Long ID HTTP Contract Fix（2026-10-07，待 Review）

- 按用户确认的审计结论，HTTP JSON 业务 ID 统一为规范正整数十进制 string；规则记录于 [Java / API 规范](../00-project/java-development-guide.md)，前端设计与 README 同步，删除旧 Profile 专用传输方案说明。
- 6 个响应 DTO 文件中的 12 个业务 ID 字段显式使用 Jackson `@JsonFormat(shape = STRING)`，包括 Interview、Question、Answer、Evaluation、EvaluationProfile、Plan、PlanItem、Project。Java DTO 仍为 Long；Profile Request 的 Project ID 同样字符串化，并使用局部 `DecimalIdDeserializer` 接收规范字符串及新增 null，拒绝数字、非规范格式和溢出，沿用 HTTP 400 / PARAM_TYPE_ERROR(10003)。路径仍绑定 Long，URL 不变。没有全局 Mapper 规则、字段名猜测、模块依赖调整、数据库 Migration 或 ID 生成变化；durationSeconds、数量、错误码、分数保持 JSON Number。
- 前端 HTTP 类型、API 参数、useInterview、Session/Review 路由全部使用 string；共享 `isBusinessId` 校验十进制正整数及 Long.MAX_VALUE，路由不转换 Number。Project 保持 string|null；删除特殊传输选项及双向 JSON 正则，http.ts 恢复通用 response.json / JSON.stringify。
- `mvn -pl interview-agent-bootstrap -am test '-Dtest=LongIdHttpContractTest,InterviewReviewControllerTest,InterviewCreateRequestTest' '-Dsurefire.failIfNoSpecifiedTests=false'`：BUILD SUCCESS，27 项通过。新增 16 项真实 MVC/Jackson 边界测试使用 mock Application，验证超安全整数的全部响应 ID、路径还原 Long、Profile string/null 输入，以及数字/非规范/溢出输入拒绝；同时验证时长、计数、错误码、分数和未标记 Long 仍为数字。
- `mvn -pl interview-agent-bootstrap -am test '-Dtest=InterviewProfileTest#getReturnsCompleteProfileAndIds+replacesScalarsAndReconcilesByIdWithoutNameMatching,InterviewPrepareTest#createAndGetPersistModeAndUnpreparedProjection,DatabaseMigrationTest#createsStartsAndReadsInterviewWithPersistedOpeningQuestion+consecutiveFollowUpsKeepMainParentRespectLimitAndAppearInReview+manualRetryUsesExistingAnswerAndReturnsSubmissionResponse' '-Dsurefire.failIfNoSpecifiedTests=false'`：BUILD SUCCESS，8 项通过。覆盖真实 Spring Boot HTTP 配置、创建/启动/详情、FollowUp 与 Review、Retry 以及 Profile GET/PUT 持久化往返；仅使用隔离 MySQL 8.4 Testcontainers 和 mock AI。后端合计 35 项通过，无失败/错误/跳过。
- `node --test frontend/tests/*.test.mjs`：迁移后的既有 40 项通过。随后增加 3 项大 ID / 路由边界测试，`node --test tests/long-id.test.mjs tests/api.test.mjs`（frontend 目录）：8 项通过，其中 5 项重叠回归；合计 43 项不同用例通过，生产代码状态相同。覆盖 Create→route→Prepare→Session→Answer/Retry/Skip→Review、Plan/PlanItem、Profile GET→edit→PUT、大 ID 精确保留，以及 Long.MAX_VALUE 接受和溢出/非法路由拒绝。
- `npm run type-check`、`npm run build`：通过。两个受影响 Java 模块执行 spotless:apply，最终 `mvn -pl interview-agent-interview,interview-agent-bootstrap spotless:check`：BUILD SUCCESS；`git diff --check`：通过。源码检查无 number 类型业务 ID、ID 的 Number/parseInt 转换或专用 JSON 文本改写。
- 本次改动限制在 Web/HTTP Contract、前端及其测试/文档；Domain/Application/Repository 和 BIGINT 数据、业务状态/事务/准入保持。定向边界与真实 HTTP 集成验证已构成最小充分 Task Gate，不执行全模块测试或 mvn clean verify；不是 Slice 收尾。未进行真实浏览器人工验收、llm-smoke 或真实 LLM 调用，不操作本地业务数据库。
- 实现自查覆盖范围、模块边界、状态/事务（未改）、Migration（无变更）、日志（无新增生产日志）、测试、人工验收边界与文档一致性；未发现 Blocker/Major，不声称独立 Review 或 Slice 验收通过。保持 feat/public-profile-v1，未创建/切换分支、提交、推送或创建 PR，保留工作区供用户审核。

## Public Release Task 1：Demo 截图与 README 集成（2026-10-08）

- 用户确认 Docker Compose 独立 Demo 已完成真实业务验证：Create → Prepare → Start → Submit → FOLLOW_UP → Finish → Review。本轮负责文档与截图集成，未重新运行真实 LLM 或独立执行业务验收；该结论仅覆盖已确认 Demo 链路，不将 Question Quality V1 全部场景或整个 Phase 标记为完成。
- README 按 Profile 编辑、首页创建、Session 准备/启动/提交、结束后 Review 展示现有四张截图，每张只展示一次；Profile 与 Review 使用折叠展示，Review 宽度为 480 像素并链接原图。请求中的 `doc/images/interview-review.png` 不存在，实际提供的是 `doc/images/interviews-review.png`，引用实际文件，未生成、替换或重命名截图。截图内容为演示资料。
- 核对 README 快速启动与 `compose.yaml`、`.env.example`，补充停止后保留数据、再次启动及已有 volume 的凭据行为；部署文档明确 Compose 固定使用 `prod`。同步 PROJECT_CONTEXT 的 Profile 与 Demo 状态，保留历史验收记录；明确无登录认证、无多用户数据隔离、不适合直接匿名公网开放，并保留 AI 问题、追问与评价的不确定性边界。
- 文档 Gate：README、PROJECT_CONTEXT 与部署指南的本地链接/截图路径检查，四张 PNG 文件有效性及唯一展示/Review 宽度检查通过；使用 `.env.example` 与临时非真实占位值执行 `docker compose --env-file .env.example config --quiet` 通过；`git diff --check` 与暂存区 diff 检查通过。静态核对必填密码/Key、默认模型/端口、服务名、内部数据库连接及仅 Frontend 绑定 localhost，与现有配置一致。
- 仅修改 Markdown；未修改 Java、Vue、Compose、环境配置、数据库或现有截图，未加入真实凭据或个人资料。无需 Spotless、Unit/Integration Test、Testcontainers 或 `mvn clean verify`；本次未启动/停止服务，不产生模型费用。按用户要求保持当前分支，未 commit、push 或创建 PR。
