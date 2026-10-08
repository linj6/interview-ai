# Frontend MVP 实施任务

**版本：V1.0 · 设计状态：[已确认]**

任务范围以 [MVP 设计](mvp-design.md) 为准，按 Task 1～6 顺序形成可审核的小 Diff。本文维护任务边界和验收目标；实际实施状态、验证与验收结果记录在 [Phase 3 Progress](../progress/phase-3.md)。Task 1～6 已完成并合并，Frontend MVP E2E Gate 已通过；详细 Closure 与真实验收事实见 [Phase 3 Closure 记录](../progress/phase-3.md#frontend-mvp-closure-与真实-e2e-验收已完成2026-10-03)。下表保留旧版任务边界与验收目标，不重复维护验收历史；其中无请求体 Create → Start 是已完成旧版范围，Question Quality V1 的 Create → Prepare → Start 调整以 [MVP 设计](mvp-design.md) 和 [需求 Contract](../01-requirements/requirements.md) 为准；前端适配已实现待 Review，V1 最终验收尚未执行，不重写旧版验收结果。

| Task | 范围与交付 | 最小验收目标 |
|---|---|---|
| Task 1 Frontend Skeleton | 建立独立 `frontend/` Node / Vite 工程；Vue 3、TypeScript、Vue Router；三条路由与页面入口；按需建立结构 | 工程可启动、类型检查与构建通过；三条路由可访问；不修改 Maven 模块，不接入预排除的库 |
| Task 2 API Contract & Client | 对照现有 Controller / Web DTO 建立 HTTP 类型；native fetch client、`Result<T>` 解包与 `ApiError`；提供后续页面所需 API 方法 | 用无真实 LLM 的 fixture 验证成功解包、业务错误、HTTP 错误、网络/非 JSON 错误、必要字段与 nullability；类型检查与构建通过 |
| Task 3 Create & Start | 首页调用无请求体创建接口并导航；Session 页加载 CREATED，执行 Start 并显示 Backend 返回首题 | 创建成功使用真实 ID；Start 失败保留可恢复入口并重读；直接访问 Session 可加载；重复点击受 pending 限制 |
| Task 4 Interview Runtime | `useInterview(interviewId)` 协调 Session / Command；Page 保留草稿；六种 Runtime UI 映射与 Submit / Skip / Pause / Resume / Finish / Retry | 覆盖按钮合法性、Submit / Retry 后详情重读、当前题切换、暂停同题恢复、Finish 与完成入口；Skip pending 不伪造业务阶段；不乐观迁移 |
| Task 5 Recovery & Error UX | 刷新/重入恢复、状态冲突、过期与网络不确定结果；错误展示及 requestId；迟到响应保护与请求清理 | 覆盖 Turn 失败保留 PROCESSING_TURN 并手动 Retry、Start 失败重读、写请求超时先读取、读取失败阻止继续 Command、草稿保留；不自动重放 POST |
| Task 6 Review & Frontend E2E Closure | 完成 Review 页面：Overview、MAIN + FOLLOW_UP、三种题目结果及精简 Evaluation；收口 Full UI E2E、Review 和验收记录 | 从首页创建到 Start、Answer、Skip、Pause / Resume、Finish 和 Review 的闭环可运行；覆盖自动完成与失败恢复关键分支、直接访问与错误提示；文档与实际行为一致 |

Task 3 / 4 应包含其交互必需的基础 pending/error 处理，Task 5 集中完善跨请求恢复与错误体验。Task 4 的 Retry 入口与 Task 5 的恢复验收共同形成完整能力，不为后续任务留下可重复 Submit 的错误窗口。

## 验证与收口原则

每项任务按实际改动选择最小充分验证：类型检查、构建，以及直接相关 client / composable / UI 测试；不因每个小 Task 创建 PR 重复运行 Backend 全量测试。具体 Node 命令与测试工具在对应 Task 中按真实需求落地，本设计不预先引入额外框架。

默认自动化验证使用 fixture / mock，不要求 LLM Key、不产生模型费用。真实 Backend + LLM 的 UI 验收必须显式 opt-in，并遵循 [开发与验收指南](../00-project/development-guide.md) 的 `llm-smoke` 约定，不能把 mock UI E2E 写成真实全链路验收。

Task 6 收口执行前端类型检查、构建、关键 E2E，并按 [AGENTS.md](../../AGENTS.md) 的 Slice / 高风险 Gate 执行项目级 `mvn clean verify`；同一代码状态已通过的 Gate 不机械重跑。完成设计与实现 Review、人工验收并合并后，在 Phase 3 Progress 记录实际结果；Frontend 未完成前不将 Full UI E2E 标记为完成。
