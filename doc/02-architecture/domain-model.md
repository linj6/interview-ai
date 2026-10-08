# Interview Agent 领域模型

**版本：V1.1**  
**状态：[已确认]**

> 本文描述核心领域对象及关系。状态语义以 ADR-003 为准，Question / Answer 规则以 ADR-004 为准，持久化细节以 `doc/04-data/data-model.md` 为准，Evaluation 语义以 `doc/03-ai/evaluator-design.md` 与 ADR-005 为准。

## 核心对象

User、Candidate、Resume、ProjectExperience、Position、Interview、InterviewPlan、PlanItem、Topic、Question、Answer、Evaluation、EvaluationProfile、EvaluationDimensionConfig、EvaluationDimensionScore、EvaluationFeedback。

## 核心关系

~~~text
Candidate → Resume → ProjectExperience
                    ↓
Position ───────→ Interview ←──── Topic
                      ↓
                   Question
                      ↓
                    Answer
                      ↓
                 Evaluation
~~~

- Position 是面试目标；Topic 是一次具体 Interview 的方向，MVP 不独立成模块；
- 一次 Interview 可以选择多个 ProjectExperience；
- Follow-up 不是独立实体，而是 `type=FOLLOW_UP` 的 Question，通过 `parent_question_id` 关联所属 MAIN Question；V0.1 复盘只表达 `MAIN + FOLLOW_UP 列表` 两层关系，不建设通用递归问题树。

## Interview

Interview 维护两个互补字段：

| 字段 | 语义 |
|---|---|
| `status` | 业务生命周期 |
| `current_phase` | 可持久化、可恢复的业务执行阶段，不是 Graph Node 位置 |

`status`：

~~~text
CREATED → IN_PROGRESS ⇄ PAUSED → COMPLETED
~~~

`current_phase`：

~~~text
OPENING
WAITING_ANSWER
PROCESSING_TURN
~~~

### `current_phase` 阶段语义

| 阶段 | 含义 | 典型 `status` | 进入条件 | 正常退出 |
|---|---|---|---|---|
| `OPENING` | 正在执行 Opening 能力，为本次 Interview 生成第一道题。此阶段可能包含加载候选人和岗位上下文、调用 LLM 以及准备首题结果。 | `IN_PROGRESS` | Interview 从 `CREATED` 执行启动操作后进入。 | 首题成功持久化后进入 `WAITING_ANSWER`；如果执行失败且尚未形成正式 Question，则恢复为 `CREATED + current_phase = null`。 |
| `WAITING_ANSWER` | 当前 Question 已经生成并持久化，系统等待用户提交答案。这是一个稳定的业务边界。 | `IN_PROGRESS` 或 `PAUSED` | 首题或下一道题持久化成功后进入；恢复暂停的 Interview 后继续停留在该阶段。 | 用户提交 Answer 后进入 `PROCESSING_TURN`；用户暂停时保留该阶段。 |
| `PROCESSING_TURN` | 已收到用户 Answer，正在执行本轮评价、下一步决策以及必要的下一题生成。该阶段对应一次短生命周期 Turn Workflow，不表示某个具体 Graph Node。 | `IN_PROGRESS` | Answer 持久化成功后进入。 | 仅 T2 成功后：生成下一题或追问回到 `WAITING_ANSWER`；决定结束进入 `COMPLETED + current_phase = null`。任一 Turn 环节失败时保持本阶段，并依据正式业务事实重新执行完整 Turn。 |

补充约束：

- `current_phase = null` 表示当前没有可恢复的进行中业务阶段，适用于尚未启动的 `CREATED` 和已结束的 `COMPLETED`；
- `PAUSED` 只允许在 `WAITING_ANSWER` 稳定边界产生，并保留 `current_phase = WAITING_ANSWER`；
- `current_phase` 是可持久化、可恢复的业务执行阶段，不是 Graph Checkpoint，也不记录 Graph Node 位置；
- MVP 不使用 `INTERRUPTED`、`FAILED`、`ASKING` 或 `EVALUATING` 作为 Interview 状态或 `current_phase` 值。

典型流转：

~~~text
CREATED（V1 Create 后无 Plan，Prepare 成功后有 Plan；状态不变）
  ↓ start（V1 要求已有 Plan）
IN_PROGRESS + OPENING
  ↓ first question persisted
IN_PROGRESS + WAITING_ANSWER
  ↓ answer persisted
IN_PROGRESS + PROCESSING_TURN
  ├─ NEXT / FOLLOW_UP → IN_PROGRESS + WAITING_ANSWER
  └─ END              → COMPLETED + current_phase = null
~~~

`COMPLETED` 是终态；MVP 不使用 `INTERRUPTED`、`FAILED`、`ASKING`。Pause 仅允许发生在 `WAITING_ANSWER` 稳定边界，并保留原 `current_phase`。

## Question Quality V1：Plan 与准备流程（[已确认]，待实现）

用户流程为 Create → Prepare → Start。Create 保存 `mode` 与合法的 `focusArea`；仅 `TECHNICAL_FOCUS` 必须选择一个 V1 KnowledgeArea，其他模式禁止提供 `focusArea`。V1 的完整枚举如下，作为需求、AI Context 与前端选项的唯一依据。

### InterviewMode

| 枚举值 | 含义 |
|---|---|
| `COMPREHENSIVE` | 综合面试。面向 Java 后端岗位进行较全面的能力评估，结合岗位要求与候选人经历，在多个 KnowledgeArea 之间进行覆盖。 |
| `PROJECT_DEEP_DIVE` | 项目深挖。以候选人的项目和工作经历为主要依据，深入考察实际参与、技术决策、故障处理、性能优化和技术取舍等。 |
| `TECHNICAL_FOCUS` | 技术专项。聚焦用户指定的一个 KnowledgeArea 进行专项面试。 |

Public Profile V0.1 — Task 3（[已确认]）：`PROJECT_DEEP_DIVE` 在 Create 持久化之前，通过 Candidate Public API 检查 Current Profile，Projects 为空时返回 `PARAM_ERROR(10001)`，至少一个项目时允许创建。Projects 为空不限制 `COMPREHENSIVE` 或具有合法 focusArea 的 `TECHNICAL_FOCUS`；既有 mode/focusArea 校验语义保持。创建后 Prepare、Start、Resume、Retry 和后续 Turn 不重复执行 Projects 准入校验。V0.1 接受 Mutable Profile 导致的 background drift，不增加 Snapshot、Version、资料编辑阻断或 targetProjectId，不修改 Interview/Plan 模型；多个 Projects 继续完整传入现有 InterviewPlan/LLM 规划。

### KnowledgeArea

| 枚举值 | 含义 |
|---|---|
| `JAVA` | Java 基础与核心机制。 |
| `CONCURRENCY` | Java 并发编程。 |
| `JVM` | JVM。 |
| `MYSQL` | MySQL。 |
| `REDIS` | Redis。 |
| `MQ` | 消息队列，不绑定 RabbitMQ / RocketMQ 等具体产品。 |
| `SPRING` | Spring 生态及相关核心机制。 |
| `DISTRIBUTED_SYSTEMS` | 分布式系统，包括 RPC、分布式一致性、服务治理等分布式机制与约束。 |
| `SYSTEM_DESIGN` | 系统设计，关注多个组件组合后的架构设计、容量、可用性、扩展性和技术取舍。 |

Interview 与 InterviewPlan 为 1:0..1，Plan 包含多个 PlanItem，并保存 initial PlanItem 的真实 ID。PlanItem 保存 `area / focus / reason`；Topic 不替代 PlanItem 的考察方向事实。

Prepare 仅允许 `CREATED` 且尚无 Plan：失败不持久化 Plan，可重试；成功后拒绝再次 Prepare。成功时 Interview 仍为 `CREATED + current_phase=null`，不新增 `PREPARING/PREPARED`。`planPrepared` 从 Plan 是否存在派生，不是新的状态或独立持久化标志。Start 必须已有 Plan，使用 initial PlanItem 生成首道 MAIN；Opening 失败仍按原规则恢复 CREATED，保留已成功准备的 Plan。

PlanItem 数量必须满足 `maxMainQuestions <= itemCount <= maxMainQuestions + 3`。`maxMainQuestions` 是 MAIN 数量上限；额外 PlanItem 只提供选择空间，不要求 Planner 凑满 itemCount 上限，不能为数量生成相似项。MAIN 未达上限却没有未使用 PlanItem 时，属于 Plan/Question 数据一致性异常，不能提前 END 掩盖。

PlanItem 是否已使用由已持久化的 MAIN Question 关联确定，不给 Plan/PlanItem 增加第二套状态机。Skip 消耗当前 PlanItem；后续从未使用 PlanItem 选择，不重新为已 Skip 的 PlanItem 生成 MAIN。FOLLOW_UP 不额外消耗 PlanItem。Finish、Expiration、Pause/Resume、Turn Retry 的已有状态、事务与恢复语义保持；Resume 继续同题，Retry 依据正式事实重算，不产生重复 MAIN 或 Plan。

## Question 与 Answer

Question：

- `type`：`MAIN / FOLLOW_UP`；
- `status`：`CREATED → WAITING_ANSWER → ANSWERED / SKIPPED`；
- V1 新生成的每个 MAIN 必须关联一个属于当前 Interview Plan 的 PlanItem；FOLLOW_UP 继承所属 MAIN 的同一 PlanItem；PlanItem 是 MAIN 考察方向的唯一事实来源，不在 Question 保存独立 focus；
- 同时关联 Interview、可选 Topic、可选 parent Question 和确定的 EvaluationProfile。

历史 Question 可保留 NULL plan_item_id；V12 仅删除 question.focus，数据库 NOT NULL 收紧已延期。新生成路径的关联要求不变，不回填或伪造历史 PlanItem。V1 最终持久化契约尚未完成，详见 [数据 Contract](../04-data/data-model.md)。

Answer 是不可变历史事实；一个 Interview 中一个 Question 在 MVP 中最多有一个正式 Answer，使用
`UNIQUE(interview_id, question_id)` 作为并发一致性的最终保障。

Evaluation 是 Turn Workflow 的结果，不是 Question 生命周期状态；因此不设置 `Question.EVALUATING`。

## Evaluation 与完整复盘

EvaluationProposal 仅是 AI 生成、尚未落库的提案；ValidatedEvaluationResult 是 Java
校验与确定性计算后的内部结果；Evaluation 是 T2 成功提交后的不可变业务事实。
跨模块创建使用 EvaluationCommand / EvaluationCreated，不再使用 Official 类名前缀。

Evaluation 由 LLM 提供语义 Proposal，再由 Java 完成校验、N/A 归一化与确定性分数计算。Question 正式持久化时绑定不可漂移的 `profile_id`；MVP 不建设 Profile Version Manager。

Evaluation 不可变；一个 Answer 最多有一份正式 Evaluation。用户异议通过独立 EvaluationFeedback 保存，不覆盖原评价。

V0.1 不生成或持久化 Interview-level Report。完整复盘不是新的领域对象或业务事实，而是 `COMPLETED` Interview 基于 Interview、Question、Answer、Evaluation 动态组装的只读查询视图。

复盘只表达用户有价值的信息：面试客观概览、MAIN / FOLLOW_UP 问题链、Answer，以及精简后的逐题 Evaluation。`SKIPPED` 直接来源于 Question 状态；`UNANSWERED` 仅是 Review 查询层根据 `COMPLETED + WAITING_ANSWER` 派生的展示语义，不新增 Question 状态、不持久化。

Interview 补充不可变生命周期事实：`started_at` 表示用户 Start 成功、Interview 正式启动的时间；`completed_at` 表示首次正式进入 `COMPLETED` 的时间。Start 时 `started_at` 与 `expires_at` 使用同一时间基准；所有进入 `COMPLETED` 的原子状态迁移同时写入 `completed_at`。

## 领域边界

- Domain 负责确定性业务规则、生命周期和业务事实；
- AI 负责首题、追问、评价和决策 Proposal；
- LLM 输出未经 Application / Domain 校验不得成为正式业务事实；
- MySQL 是业务状态权威来源；GraphState 只服务一次短生命周期 Workflow，不作为第二状态源。
