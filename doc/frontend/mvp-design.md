# Frontend MVP 设计

**版本：V1.0 · 状态：[已确认]**

## 1. 范围与 Source of Truth

本文维护 Frontend MVP 的技术选择、页面职责、UI Projection、交互与工程结构。实施拆分见 [实施任务](implementation-tasks.md)，实施状态与验收结果仅记录在 [Phase 3 Progress](../progress/phase-3.md)。设计已确认不表示代码已实现。

系统级定义沿用以下 SoT，本文不重新定义 Domain、HTTP API 或状态机：

| 事实 | 权威依据 |
|---|---|
| 功能与复盘验收范围 | [requirements](../01-requirements/requirements.md) |
| 业务对象、状态和 Question / Answer 关系 | [domain-model](../02-architecture/domain-model.md)、[ADR-003](../adr/ADR-003-interview-state.md)、[ADR-004](../adr/ADR-004-question-answer.md) |
| Backend 事实源、事务与恢复边界 | [architecture](../02-architecture/architecture.md)、[ADR-006](../adr/ADR-006-short-lived-ai-workflow.md) |
| Maven 模块与依赖边界 | [module-structure](../02-architecture/module-structure.md) |
| HTTP 包装、错误码与 requestId | [Java / API 规范](../00-project/java-development-guide.md) |
| 当前 HTTP 路径、请求与响应字段 | [InterviewController](../../interview-agent-interview/src/main/java/com/linj6/interviewagent/interview/interfaces/controller/InterviewController.java) 及同模块 `interfaces/request`、`interfaces/response` DTO；行为对照相关自动化测试 |
| 已落地生命周期、Duration、Review 行为 | [Phase 3 Progress](../progress/phase-3.md) 的 Slice 4 / 5 记录；稳定语义仍以以上设计与 ADR 为准 |

## 2. 技术栈与工程位置

采用 Vue 3、TypeScript、Vite、Vue Router，以及 native fetch + typed API client。暂不引入 Pinia、Axios、UI Framework、TanStack Query / Vue Query。

前端位于当前 monorepo 的 `frontend/`，是独立 Node / Vite 工程，拥有自己的依赖、开发和构建命令，不加入 Maven Module。浏览器只调用 Backend HTTP API，不直接访问数据库、LLM 或 Java Module Public API。模型密钥及数据库凭据不得进入前端环境变量或构建产物。

## 3. 三个页面

| 路由 | 页面职责 |
|---|---|
| `/` | 创建 Interview，成功后使用 Backend 返回的 ID 进入 Session 页；V1 创建、Prepare 与 Start 是三次独立操作 |
| `/interviews/:id` | 读取 Session，按服务端状态展示当前题目与 Prepare / Start / Submit / Skip / Pause / Resume / Finish / Retry 入口 |
| `/interviews/:id/review` | 获取已完成 Interview 的只读复盘；展示 Overview、MAIN + FOLLOW_UP 两层问题链、Answer 和精简 Evaluation |

Question Quality V1（已确认，前端适配待 Review）创建请求提供 mode，仅 TECHNICAL_FOCUS 展示 9 个 KnowledgeArea 的单选并要求 focusArea；其他模式不展示且不得发送 focusArea，切换模式清除不适用选择。InterviewMode 与 KnowledgeArea 的唯一清单见[领域模型](../02-architecture/domain-model.md#question-quality-v1plan-与准备流程已确认待实现)。Create → Prepare → Start，不新增 Candidate / Position 配置或 Interview 列表 API。Backend Task 7 已接入必填 mode 的 Create 与详情 mode/focusArea/planPrepared（待 Review）；前端已接入 V1 JSON Create、Prepare 与详情准备状态，适配待 Review，尚未执行 V1 最终验收。直接访问 Session / Review 路由必须能独立加载；不存在的 ID、非法路由参数、未完成 Interview 的 Review 请求须给出明确提示及可用导航，不新增第四个页面。完成后提供进入 Review 的入口。复盘使用 Backend 返回的 `ANSWERED / SKIPPED / UNANSWERED`，不在浏览器重新推导题目结果或总分。

## 4. 事实源与状态归属

Backend 是业务状态唯一事实源，MySQL 的权威地位由总体架构规定。Frontend 只维护服务端快照及 UI Projection，不建立第二套业务状态机，不乐观提交生命周期、当前题目或 Evaluation。

`loading`、待执行 Command、HTTP error、刷新失败、确认交互等属于短生命周期 UI 状态。按钮禁用由最新 Session 快照与当前 pending 状态共同决定，最终合法性由 Backend 校验。请求发出不代表业务迁移成功，网络断开也不代表 Backend 未提交。

### Runtime UI 映射

下表是既有业务状态的 UI 映射；OPENING / WAITING_ANSWER / PROCESSING_TURN 是 phase，不能当作 Interview status。表中 Command 均要求当前无 pending Command，Submit / Skip 还要求有效 `currentQuestion`。

| UI 名称 | Backend 快照条件 | 展示与可用操作 |
|---|---|---|
| CREATED | `status=CREATED`，`currentPhase=null` | 尚未启动；planPrepared=false 时允许 Prepare，true 时允许 Start，不再 Prepare |
| OPENING | `IN_PROGRESS + OPENING` | 首题准备中；显示等待和刷新入口，禁止业务 Command |
| WAITING_ANSWER | `IN_PROGRESS + WAITING_ANSWER` | 展示 `currentQuestion` 与回答输入；允许 Submit、Skip、Pause、Finish |
| PROCESSING_TURN | `IN_PROGRESS + PROCESSING_TURN` | 回答已进入服务端处理阶段；禁止再次 Submit、Skip、Pause、Finish；显示刷新及手动 Retry 恢复入口 |
| PAUSED | `PAUSED + WAITING_ANSWER` | 保留原题，回答输入不可提交；允许 Resume、Finish |
| COMPLETED | `COMPLETED`，`currentPhase=null` | 显示完成提示与 Review 入口；禁止业务 Command |

未知枚举、非法组合或等待状态缺失当前题目时，显示无法识别状态并允许重新读取，禁止猜测或自行修复业务状态。`firstQuestion` 不替代 `currentQuestion`。

### Command 协调与草稿

- Prepare：仅 CREATED 且 planPrepared=false 提供；成功后重读详情，再提供 Start；失败或结果不确定时先重读，仍无 Plan 才允许重试。pending 仅是 UI 状态，不展示为 PREPARING/PREPARED 业务状态。
- Start：等待请求完成并使用返回详情；失败后重新读取，只有读到 CREATED 且 planPrepared=true 才再次提供 Start。OPENING 不调用 Turn Retry。
- Submit：提交当前题目 ID 与草稿内容，空白输入不可提交。成功返回 Answer / Evaluation 后，再 GET Interview，才能确定下一题或完成。不以 Evaluation 返回推导 Session 状态。
- Skip：提示将跳过当前题目；返回详情决定展示下一题或完成。Skip 的 pending 是本地 HTTP 状态，不伪造 PROCESSING_TURN。
- Pause / Resume：使用返回详情；暂停保留题目与当前页面草稿，恢复继续同一题，不自动 Start 或生成新题。
- Finish：用户确认后提交，返回详情为展示依据；不把未提交草稿转换为 Answer。仅在表中允许的稳定边界提供入口。
- Retry：调用现有 Turn Retry，复用 Backend 已保存的 Answer，不携带草稿、不重发 Submit、不做客户端自动重试。返回 Answer / Evaluation 后重新 GET Interview。PROCESSING_TURN 本身无法说明另一个请求是否仍执行中；UI 不宣称已失败或提供精确恢复位置，竞争结果交由 Backend 冲突控制。

`answerDraft` 保留在 Page，按 Interview / Question 身份管理。请求失败保留草稿；确认当前题已改变或 Interview 已完成后清理。Submit 成功但刷新失败时保持“已提交，状态刷新失败”提示，禁用继续提交，直到重新读取确定状态。页面刷新后的草稿持久化不属于 MVP。

### 恢复与错误 UX

进入页面、浏览器刷新或重新访问时先 GET Interview，再决定操作。Command 执行期间禁用冲突操作，允许持续显示上一份快照但标明正在处理；迟到响应不得覆盖另一个 Interview 或较新的读取结果。离开页面时清理请求相关资源；取消浏览器请求不等于撤销服务端 Command。

Command 出错或结果不确定时，展示错误并重新读取权威状态；若重读也失败，保留快照并标记可能过期，阻止继续发送 Command，提供重新加载。状态冲突和过期错误同样先重读，不自动重放写请求。读取可由用户重试；MVP 不依赖持续自动轮询或后台恢复任务。

OPENING 和 PROCESSING_TURN 可被直接访问时观察到，但当前同步 HTTP 接口不提供 Node 进度、执行中标识或百分比。等待提示不能伪造进度。expiresAt 仅作提示，倒计时不能在本地将 Interview 置为 COMPLETED；截止附近重新读取 Backend。Pause 不停止计时，PROCESSING_TURN 的截止后 Retry 仍遵循 Backend 已确认规则。

## 5. 前端结构与 Component 原则

结构原则如下，文件名及具体 Component 随当前 Task 的真实需要确定，不预建空层：

```text
frontend/src/
├── pages/        # 三个路由页面、页面布局、answerDraft
├── components/   # 已出现复用或有清晰独立职责的 UI
├── composables/  # useInterview(interviewId) 等实际所需协调逻辑
├── api/          # fetch、Result 解包、ApiError、typed API 方法
├── types/        # HTTP Contract 与必要 UI 类型
└── router/       # 路由定义
```

`useInterview(interviewId)` 负责 Session 服务端状态、加载/刷新、pending/error 与 Command 协调，以及 Submit / Retry 后的详情重读；不拥有 `answerDraft`，不实现评分或业务状态迁移。Page 负责输入、确认和导航，api 层只负责 HTTP 契约与传输，不操作 Router 或全局 UI。

Component 采用 Page-first：先在页面完成真实交互，再按真实复用或明确职责提取。例如题目展示、操作区、复盘问题链可在复杂度确实出现后提取，不要求一次性创建完整组件清单。MVP 不提前建设 Design System、通用表单引擎或通用 CRUD 页面框架。

## 6. API Client 与 TypeScript HTTP Contract

native fetch 集中在 typed API client。对外 API 方法返回 `Promise<T>`，统一解包 `Result<T>.data`；Page / composable 不重复判断 envelope，不把 `Result<T>` 当作客户端成功/失败 union。当前已实现 HTTP 路径、请求体和响应字段以 Controller 与 Web DTO 为依据；V1 预期变更以 [需求 Contract](../01-requirements/requirements.md#question-quality-v1-配置与-http-contract当前有效) 为准，实施时同步 typed client 与 UI，不复制完整 API 表。

Client 同时检查 HTTP 是否成功、JSON envelope 是否有效以及 `code === 0`。非成功 HTTP、非零业务码、网络失败、响应无法解析或关键结构不符合预期，统一转换为 `ApiError`。`ApiError` 保留错误类别、可用的 HTTP status、业务 code、面向用户的 message 和 requestId；网络错误没有 HTTP status / 业务 code，不编造服务端错误码。requestId 优先使用有效响应体值，必要时读取 `X-Request-Id`，缺失时允许为空。前端不生成或透传服务端 requestId，不展示堆栈、原始异常或非 JSON 网关页面。

`data=null` 是否合法由具体接口约定决定，不普遍强制非空。当前详情、Submit / Retry 与 Review 方法需要相应数据，缺失时作为 Contract 错误。传输层不自动重试 POST；界面区分重试读取、重新 Start 和恢复 Turn。

TypeScript Contract 对齐 **HTTP JSON**，不镜像 Java Entity、数据库列、Module Public API 或内部 AI Proposal：

- 保持 camelCase 字段名、嵌套结构、枚举值及 nullability；`currentPhase` 等可空字段显式写 `null`，不能随意改为 optional。
- Java 时间字段按实际序列化格式表示为字符串；`LocalDateTime` 不附带时区，不自行补 `Z` 或假设 UTC。
- HTTP JSON 业务 ID 为 decimal string，前端使用 string；统一规则见 [Java / API 规范](../00-project/java-development-guide.md)。路由直接保留字符串，校验规范正整数十进制表示及 Long.MAX_VALUE 上限，不使用 Number/parseInt 转换 ID。
- BigDecimal 分数按当前 JSON 表示接收，仅用于展示，不在前端重算确定性分数。
- TypeScript 类型断言不是运行时校验；Task 2 用实际响应 fixture 核对必要字段、nullable 和解包失败分支，不引入通用 Schema / 代码生成框架。
- Review DTO 与 Submit Evaluation DTO 分别建模，不能因名称相近而共用错误结构；后端 DTO 变化时，同步受影响 TypeScript Contract 和对应验证。

## 7. Non-goals

- 用户认证、Candidate / Resume / Position 管理、上传、Interview 列表或历史检索等 Slice 6 外围能力。
- 除 Question Quality V1 已确认 Contract 外的新 Backend API；前端 Task 不执行数据库 Migration，不新增业务状态、客户端业务状态机、评分计算或 Interview-level AI Report。
- Streaming、SSE、WebSocket、Graph Node 进度、客户端 Workflow、自动重放 Command 或后台恢复框架。
- Pinia、Axios、UI Framework、Query 框架、Design System、通用表单/CRUD 框架及提前抽象。
- 离线运行、跨页面持久化草稿、多标签页状态同步、复杂缓存与乐观业务更新。
- 语音/视频、RAG、Memory、Graph Checkpoint 及其他本轮之外的基础设施。

## 8. 与当前实现的一致性

旧版 Frontend MVP 已验收；Question Quality V1 的 Create/Prepare/planPrepared 已接入前端（适配待 Review），Backend Task 7 Review 状态见 Phase 3 Progress。V1 最终端到端与真实 LLM 验收尚未执行。现有其余差异继续保留：Submit / Retry 不返回 Session 详情；Skip 没有独立持久化处理中状态；OPENING / PROCESSING_TURN 不提供执行进度；Review 是完成后只读查询。这些是 UI 设计的约束，不是新增 Backend 能力的授权。

## Public Profile V0.1 — Task 4（[已确认]）

本节是用户确认的 Public Profile 前端增量，替代上文当前任务内“不新增第四个页面”和资料管理排除项；其余 Interview 页面、模式/方向与生命周期交互保持既有设计。

- 独立 `/profile` 页面通过 GET `/api/interview-profile` 加载 Current Candidate、Resume、Projects、Position，编辑姓名、简历摘要、项目名称/描述与岗位标题/描述。Projects 仅在页面本地增删改，数量 0..5，达到上限禁用新增并提示，不提供排序。
- “保存 Profile”统一 PUT 完整 replacement：已有项目保留 ID，新项目 ID 为 null，删除项目不进入请求。请求/响应以 Bootstrap [Request](../../interview-agent-bootstrap/src/main/java/com/linj6/interviewagent/profile/InterviewProfileRequest.java)、[Response](../../interview-agent-bootstrap/src/main/java/com/linj6/interviewagent/profile/InterviewProfileResponse.java) 和 [Service](../../interview-agent-bootstrap/src/main/java/com/linj6/interviewagent/profile/InterviewProfileApplicationService.java) 为准。客户端只做必填、字符长度及 TEXT UTF-8 字节上限校验；后端仍负责归属、事务与最终校验。
- GET 提供 loading/error/retry，PUT pending 禁止重复提交并禁用编辑；失败保留草稿，提供反馈及人工重试。成功直接采用 PUT 返回的 canonical Profile 和真实 Project ID，更新 dirty 基线，不额外 GET。页面状态局部保存，不增加全局 Profile Store或复杂离页保护。
- 固定提示：“面试问题和评估会基于这里的资料生成。首次使用时，请先将示例内容替换为你的真实资料。”和“资料修改会影响正在进行中的面试，建议在开始新面试前完成编辑。”不检测进行中的 Interview、不阻止编辑。
- Home 每次挂载加载 Current Profile，仅展示姓名、目标岗位、项目数量与编辑入口。空 Projects 仅禁用 PROJECT_DEEP_DIVE 并提示添加项目，COMPREHENSIVE/合法 TECHNICAL_FOCUS 仍可创建；加载中或失败时禁止创建，失败提供重试及编辑入口。后端 admission validation 为最终保障。
- Profile Project ID 遵循统一 HTTP 业务 ID 字符串 Contract，新项目 id 为 null；GET/PUT 使用通用 JSON parse/stringify，不进行专用文本改写。
- 不实现上传/解析、Project 独立 CRUD、Snapshot/Version、targetProjectId、完整度评分、全局 Store、新框架或后端业务改动。
