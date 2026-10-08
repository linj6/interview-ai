# Interview Agent 数据模型设计

**版本：V1.1**  
**状态：[已确认]**

## 核心表

``` text
user
candidate
candidate_resume
candidate_project
position
interview
interview_plan
interview_plan_item
interview_topic
question
answer
evaluation_profile
evaluation_dimension_config
evaluation
evaluation_dimension_score
evaluation_feedback
report
```

具体字段以实现阶段最终 DDL 为准。

表的模块归属：interview 模块负责 `interview / interview_plan / interview_plan_item / question / answer`；evaluation 模块负责
`evaluation / evaluation_profile / evaluation_dimension_config / evaluation_dimension_score`；candidate 模块负责
Candidate、Resume、Project；position 模块负责 Position；user 模块负责 User。数据库关联不改变模块归属。

## Interview

`status`：CREATED、IN_PROGRESS、PAUSED、COMPLETED。

`current_phase`：OPENING、WAITING_ANSWER、PROCESSING_TURN。

`current_phase` 表示可持久化、可恢复的业务执行阶段，不表示 Graph Node 的瞬时执行位置。Interview 进入 `COMPLETED` 后，`current_phase = null`；进入 `PAUSED` 时保留原 `current_phase`。V0.1 仅在 `WAITING_ANSWER` 稳定边界暂停。

同时保存 `created_at / started_at / expires_at / completed_at` 与 version/更新时间等字段。`started_at` 在用户 Start 成功、Interview 正式启动时写入且不再修改；`expires_at` 与 `started_at` 使用同一时间基准计算；`completed_at` 在 Interview 首次进入 `COMPLETED` 的原子状态迁移中写入且不再修改。

## Question Quality V1 持久化 Contract（[已确认]，待新增 Migration）

- Interview 保存 `mode` 与可空 `focus_area`，合法组合见 [领域模型](../02-architecture/domain-model.md#question-quality-v1plan-与准备流程已确认待实现)。`planPrepared` 是查询派生值，不新增数据库列。
- `interview_plan` 关联 Interview，以 `UNIQUE(interview_id)` 保证每场仅一个 Plan；保存 `initial_plan_item_id`，必须指向本 Plan 内的真实 PlanItem ID。
- `interview_plan_item` 保存真实 ID、Plan 归属与 `area / focus / reason`。数量和使用语义由领域规则约束；不保存 P1/P2 等 AI 临时引用，不新增 Plan/PlanItem 状态机。
- `question.plan_item_id NOT NULL` 是尚未完成的最终目标，已延期；当前数据库保留可空列以保存历史 Question。V1 新生成的每个 MAIN 仍须关联本 Interview 的一个 PlanItem，一个 PlanItem 最多生成一个 MAIN；FOLLOW_UP 与所属 MAIN 的 `plan_item_id` 必须相同。MAIN 唯一使用约束不得禁止多个 FOLLOW_UP 继承同一 ID。具体 DDL 与索引在实现 Task 确定，不创建外键。
- 删除 `question.focus`，由 `PlanItem.focus` 作为唯一事实来源。保留历史 Question 及其 NULL 关联，不回填、不伪造 PlanItem、不删除 Question；不得把旧 Question focus 当作新模型中的第二份事实。历史 NULL 不授权 V1 生成路径写入空关联，也不恢复旧 Focus Selector。
- 不得改写在持久化数据库成功应用的 Migration。本次用户明确授权在未发现持久化数据库成功应用 V12 的前提下调整该版本；不操作本地数据库，不执行 Flyway repair。

### Task 2 分阶段迁移（用户已确认）

V10 先建立 Plan / PlanItem 表、Interview.mode / focus_area，并增加可空的 question.plan_item_id；暂时保留 question.focus。旧 Create 及历史 Interview 使用 COMPREHENSIVE、focus_area=null，旧 Start / Turn / Skip 不要求 Plan，保持原 Focus Selector 调用链可运行。不删除开发数据、不伪造 PlanItem，也不回填历史 Question。

Plan 聚合使用 Java 分配的真实 ID，Repository 在同一事务保存 Plan 和全部 PlanItems，校验 initial ID、归属、ID 不重复及数量；UNIQUE(interview_id) 保证 Plan 唯一。V10 的 MAIN 专用生成列唯一索引只限制非空 PlanItem 的 MAIN 使用，不限制 FOLLOW_UP 共享，也允许旧 MAIN 的空关联。数据库不创建外键；完整 Interview/PlanItem 归属、FOLLOW_UP 继承及短事务生命周期/CAS 校验由后续业务接入实现。

Task 2 Review 修正新增 V11，删除 V10 的 `interview.ck_interview_mode_area` 与 `interview_plan_item.ck_plan_item_area`，不修改 V10。数据库不枚举 InterviewMode / KnowledgeArea 的合法值及 mode / focusArea 组合；枚举解析和组合规则由 Java 校验。数据库继续保留 NOT NULL、Plan 唯一性及 MAIN PlanItem 唯一使用等结构性约束。

Start、Turn、Skip 均接入 PlanItem 后，V12 仅删除 question.focus；plan_item_id NOT NULL 已延期至后续独立版本化 Migration，历史数据策略仍需另行确认，不以清理开发数据作为当前前置要求。V1 最终持久化 Contract 尚未完成。

Task 5 已将 Turn/Skip 接入已准备 Plan：新建 MAIN 写入 Java 解析/选择的真实 plan_item_id，旧 question.focus 暂存对应 PlanItem.focus；FOLLOW_UP 继承所属 MAIN 的 plan_item_id，旧 focus 继续为空。使用事实包含所有状态的 MAIN（包括 SKIPPED），不包含 FOLLOW_UP 的额外消耗。对应短事务校验完整 Interview/PlanItem 归属及未使用事实，并与 Question、Evaluation（仅 Turn）和 Interview 状态/version 更新原子提交。旧空 plan_item_id 仍允许存在于数据库，但不伪造 Plan 或为旧 Interview 恢复 Focus Selector；缺少正式关联的 V1 Turn/Skip 作为数据一致性异常拒绝。未改 Migration 或 Create/GET Contract，字段收紧与清理留给后续 Task。


### Task 6 删除 focus，非空收紧延期（2026-10-05，用户已确认，修正待 Review）

V12 仅执行 `ALTER TABLE question DROP COLUMN focus`，保留 V10 创建的可空 question.plan_item_id；移除原 V12 添加非空 CHECK 与收紧 NOT NULL 的逻辑。带有旧 focus 和 NULL 关联的历史 Question 可升级，除删除 focus 列外，行及其余字段保持；不会回填、伪造 PlanItem 或删除 Question。已有真实关联、MAIN 专用唯一索引及 FOLLOW_UP 共享规则不变。PlanItem.focus 继续作为新 V1 Question 的唯一方向事实。

此前 Task 6 的非空 CHECK/NOT NULL 方案已被本次决定替代；V1 最终 Contract 尚未完成，不得标记验收完成。当前 Java 新建 Question 的非空关联校验、归属与短事务规则保持，数据库可空只兼容历史事实，不表示旧无 Plan Interview 可继续 V1 Turn/Skip。后续收紧需独立确认历史数据策略并新增 Migration。本轮不操作本地开发数据库、执行 repair 或处理既有失败 Migration 记录。

Prepare 的 AI 计算在事务外。持久化时在同一短事务内重读并校验 CREATED、无 Plan，原子保存 Plan、全部 PlanItems 与 initial PlanItem ID。并发可重复 AI 计算，但通过事务与唯一约束最终仅一个 Plan 成为业务事实；失败整批回滚，不能留下半个 Plan。Question 的 PlanItem 归属与未使用校验在 Opening/Turn/Skip 对应短事务中完成，与 Question 和 Interview 更新一起提交；失败不消耗新的 PlanItem。

## Question

至少表达
interview_id、plan_item_id、topic_id、parent_question_id、type、status、question_content、evaluation_profile_id、顺序/创建时间。

type：MAIN / FOLLOW_UP。

## Answer

关联 Interview 与 Question，保存用户正式回答，不允许原地修改；使用
`UNIQUE(interview_id, question_id)` 保证同一 Interview 中同一 Question 只能提交一次。

Candidate 与简历数据关系为：`Candidate → Resume → ProjectExperience`。ProjectExperience 归属于具体 Resume，以支持同一 Candidate 针对不同岗位维护不同简历及项目呈现。

## Evaluation

保存
interview_id、question_id、answer_id、overall_score_10、overall_score_100、strengths、weaknesses、missing_points、reference_answer、profile_id、evaluator_version、prompt_version、created_at
等。

Evaluation 不可修改。MVP 一个 Answer 最多形成一份正式 Evaluation，建议 `UNIQUE(answer_id)`，避免 Workflow 重试产生重复正式评价。

## Dimension Score

保存
evaluation_id、dimension_code、dimension_name、score、weight、weighted_score、evidence、created_at。

保存历史 Weight 以保证历史结果可解释。

## Feedback

用户反馈独立保存，不直接修改 Evaluation。

## Profile

EvaluationProfile 与 EvaluationDimensionConfig 为 1:N。Question
正式持久化时绑定一个确定、不可漂移的 Profile 记录，通过 `profile_id` 定位一套确定的评价规则。MVP 不强制单独保存 `profile_version`，也不建设 Profile Version Manager。

## 完整复盘

V0.1 不新增 Report / Review 持久化模型。完整复盘按 `interview_id` 批量读取 Interview、Question、Answer、Evaluation 及 Dimension Score，并在查询层动态组装；不复制既有业务事实，不保存统计快照，不增加缓存。

## AI 可追踪性与 Graph 恢复

V0.1 不建设独立 AI Trace / AI Execution 持久化模型。具有业务追溯价值的信息继续随业务事实保存，例如 Evaluation 的 `profile_id / evaluator_version / prompt_version`；模型调用耗时、异常等工程信息 MVP 主要通过应用日志记录。

V0.1 不建设独立 Graph Checkpoint 持久化模型。GraphState 保持一次 Graph 执行过程中的运行时状态，不作为第二套业务状态来源。

流程恢复以 MySQL 中的 Interview、Question、Answer、Evaluation 等业务事实以及 `status / current_phase` 为依据，重新构建 GraphState 并继续 Workflow。关键业务写入通过唯一约束、版本控制和幂等机制避免重复业务事实。

未来当 Workflow 演化成长时间运行、多 Tool、多 Agent、Deep Research 等复杂流程后，再评估 Durable Graph Checkpoint；当出现模型对比、Prompt A/B、成本分析、回归测试等需求后，再评估独立 AI Trace / Observability。

## 事务

T1 保存不可变 Answer 并将 Interview 转为 PROCESSING_TURN；LLM 与 Proposal 校验在事务外；T2 由 Interview Application 的同一事务保存正式 Evaluation、更新 Question 和 Interview。EvaluationApi 参与外层事务且不使用 REQUIRES_NEW。InterviewStatus、InterviewPhase、QuestionStatus 的枚举名由 MyBatis 默认 EnumTypeHandler 映射为同名 VARCHAR；更新校验 status、phase、version，Answer 与 Evaluation 的唯一约束处理重复提交。

不让事务跨越 LLM 调用。采用调用前后短事务，并配合幂等和版本控制。

## 原则

数据库保存业务事实与历史追踪，不为未来能力提前建立大量复杂表。


## Slice 2 Evaluation 持久化约束（[已确认]）

Slice 2 新增并实现以下最小持久化结构：

- `answer`：保存不可变正式回答，一个 Question 最多一个 Answer；
- `evaluation`：保存正式评价及总分、追踪元数据和 JSON 形式的复杂评价内容；
- `evaluation_dimension_score`：结构化保存维度分数、历史 Weight、Weighted Score 和 Evidence JSON。

核心唯一约束：

- `UNIQUE(answer.interview_id, answer.question_id)`；
- `UNIQUE(evaluation.answer_id)`；
- `UNIQUE(evaluation_id, dimension_code)`。

Evaluation 的核心分数结构化保存；Strengths、Weaknesses、Missing Points、Reference Answer 和 Evidence 使用 JSON 保存。V0.1 不新增独立 Evidence 表、失败记录表或 AI Trace 表。

事务边界：T1 保存 Answer 并将 Interview 置为 `PROCESSING_TURN`；LLM 调用和 Java 校验不持有数据库事务。T2 以完整 TurnResult 原子提交两种出口：`FOLLOW_UP / NEXT_QUESTION` 时保存 Evaluation、Dimension Scores、将当前 Question 更新为 `ANSWERED`、保存下一道 Question，并将 Interview 置为 `IN_PROGRESS + WAITING_ANSWER`；`END` 时保存 Evaluation、Dimension Scores、将当前 Question 更新为 `ANSWERED`，并将 Interview 置为 `COMPLETED + current_phase = null`。T2 成功前，Evaluation、Decision 与 Generated Question 都不是正式业务事实。Evaluate、Decision、QuestionGeneration 或 T2 失败时保留 Answer，丢弃运行时结果并保持 `IN_PROGRESS + PROCESSING_TURN`；重新执行时依据 MySQL 正式业务事实完成整个 Turn，不新增 Checkpoint 或失败记录表。

Slice 2 当时暂缓 Topic / `coveredTopics` 设计。Question Quality V1 使用 PlanItem 与已生成 MAIN 内容降低重复，不以 `interview_topic / question.topic_id` 构造去重事实，不新增 coveredTopics 持久化模型。

插入 Answer 和 Evaluation 时不执行 exists 预检查。Repository 直接插入并将数据库唯一约束产生的
`DuplicateKeyException` 转换为重复 Answer 或重复 Evaluation 业务异常。Interview 状态更新必须同时匹配主键、
期望旧 `status`、期望旧 `current_phase` 和期望 `version`，更新 `version = version + 1` 并检查影响行数。

Migration、表、字段、约束和枚举必须增加必要注释；数据库表映射 DO 类的相关字段必须增加 JavaDoc。
