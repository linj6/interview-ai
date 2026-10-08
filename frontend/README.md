# Interview Agent Frontend

独立的 Vue 3 + TypeScript + Vite 工程，不属于 Maven Reactor。已接入 API client、Create / Prepare / Start、Interview Runtime、Recovery 和只读 Review；旧版 Frontend MVP 验收见 Phase 3 Progress；Question Quality V1 前端适配待 Review，尚未执行 V1 最终端到端或真实 LLM 验收。

## 本地运行

Node 版本要求见 `package.json` 的 `engines`。在 `frontend/` 目录执行：

```sh
npm ci
npm run dev
```

打开 Vite 输出的地址（默认 `http://localhost:5173`）。可直接访问：

| 路由 | 页面 |
|---|---|
| `/` | Current Profile 摘要、编辑入口与 Interview 创建，成功后跳转 Session |
| `/profile` | 加载、编辑并统一保存 Current Profile，项目本地增删改（0..5） |
| `/interviews/:id` | 读取权威状态、Prepare / Start、OPENING 等待与手动刷新、当前 Question、Answer / Skip / Pause / Resume / Finish |
| `/interviews/:id/review` | 完成后只读复盘：Overview、MAIN / FOLLOW_UP、Answer、Evaluation |

所有 HTTP JSON 业务 ID 和前端 ID 使用十进制字符串；Session / Review 路由必须为规范正整数十进制表示，且不超过 Long.MAX_VALUE，直接访问分别 GET Interview / Review。所有 business command 成功或失败后都重新 GET；读取失败会禁用 business commands，直到手动刷新成功。Review 显示读取错误及可用 requestId，提供重新加载、返回 Session 和首页；题目结果、统计、时间与分数直接展示 Backend DTO。Session 完成入口通过 Vue Router 进入 Review。

开发服务器将 `/api` 原路径代理到 `http://localhost:8080`，不移除 `/api` 前缀。页面交互需要可用 Backend；真实 LLM 验收必须显式 opt-in，遵循 [开发指南](../doc/00-project/development-guide.md) 的 `llm-smoke` 约定。模型密钥只配置在 Backend。

## 验证与构建

```sh
npm run test:api
npm run test:create-start
npm run test:profile
node --test tests/long-id.test.mjs
npm run test:runtime
npm run test:recovery
npm run test:review
npm run type-check
npm run build
```

以上都是会自行退出的一次性命令。API 测试使用 fixture；Create & Start 测试编译真实 Vue 页面并使用 Vue 自定义 renderer 与 mock fetch，覆盖等待、重复操作、失败重读、直接访问和迟到响应，不调用真实 Backend / LLM，也不等同于浏览器或真实全链路 E2E。

类型检查使用 `vue-tsc`，覆盖 TypeScript 和 Vue SFC；production build 输出到 `dist/`。开发 proxy 仅用于 Vite 开发服务器。[Docker 一键部署](../doc/00-project/docker-deployment.md) 使用 Nginx 提供静态文件、同源 `/api` 转发和 history fallback，以支持直接访问及刷新。

## 源码结构

`pages/` 承载路由页面及页面级状态，`router/` 定义路由，`api/` 与 `types/` 保存 typed client 和 HTTP 契约。后续 Task 按真实需求提取组件及 composable，不预建空层。

## Public Profile V0.1 人工检查

- 访问 `/profile`，核对 GET 字段与两条固定说明；修改姓名、简历摘要、岗位标题/描述和项目名称/描述。
- 删除至 0 个项目、添加至 5 个项目，核对新增上限与提示；所有变更只在点击“保存 Profile”时统一 PUT，不调用 Project CRUD。
- 核对 dirty、保存期间禁用与成功/失败反馈；保存成功后页面直接采用 PUT 返回资料，不再 GET；再次编辑新项目时携带服务端真实 ID（HTTP JSON 与前端均使用十进制字符串）。
- 返回首页核对姓名、岗位、项目数量和编辑入口；无项目时仅项目深挖不可用，综合与合法技术专项仍可创建；GET 失败时禁止创建，重试成功恢复。

`test:profile` 使用真实 Vue SFC、自定义 renderer 与 mock fetch，不调用数据库或真实 LLM，不能替代真实浏览器人工验收。

设计和任务边界见 [MVP 设计](../doc/frontend/mvp-design.md) 与 [实施任务](../doc/frontend/implementation-tasks.md)。

Runtime 的 `useInterview` 负责权威快照、读取与 command 协调；页面保留回答草稿，Pause / Resume 不丢失当前草稿。Skip / Finish 使用轻量确认，完成后停留 Session 并提供 Review 入口。Runtime 测试覆盖 command pending、权威 GET、草稿与确认；使用 mock，不代表真实 Backend / LLM E2E。

## Frontend MVP 人工 closure 清单

- 从首页创建、Start、Answer → FOLLOW_UP / NEXT_MAIN、Skip、Pause / Resume 同题、主动 Finish、进入 Review 并返回 Session。
- Answer 自动完成以及截止时间由 Backend 完成；核对 Review 时间、时长、统计、MAIN / FOLLOW_UP 归属、三种结果与逐题 Answer / Evaluation。
- 刷新或直接访问 Session / Review；不存在、非法 ID、未完成 Review 的错误与导航。
- Start / Turn 失败、PROCESSING_TURN 手动 Retry、409 / 过期冲突、写请求网络不确定结果及重读失败；确认不自动重放 POST，草稿与按钮行为正确。
- 路由切换期间迟到响应、返回 Session、浏览器前进后退；部署时验证 history fallback 和 /api 转发。

Review 测试使用真实 Vue SFC、自定义 renderer 和 mock fetch，串联首页到 Review，并覆盖主动结束与 Answer 自动完成。它不使用真实 Router / 浏览器 / Backend / LLM，不能替代上述人工验收。

## Question Quality V1 前端适配（2026-10-04，待 Review）

创建页必须选择 InterviewMode；TECHNICAL_FOCUS 额外单选 KnowledgeArea，切换到其他模式清空 focusArea 且请求不发送该字段。Session 从 GET 读取 mode / focusArea / planPrepared；CREATED 未准备时提供 Prepare，准备后必须 GET 确认 planPrepared=true 才提供 Start。Prepare 不自动启动或重放，重复点击由 pending 阻止。

Prepare 失败或响应不确定时先 GET：已准备则提供 Start；仍未准备则显示错误并允许用户重试；读取失败则保留快照、禁用操作，只允许刷新。缺失或非法 planPrepared 作为读取 Contract 错误处理。

本轮只修改前端及前端文档，保持三页结构和既有操作风格；未修改后端或数据库，既有本地配置、截图与脚本保持。完成后停下等待 Review，未提交、推送或创建 PR，不代表 V1 整体完成。

验证结果：

- `npm run type-check`：通过，覆盖 Vue SFC 与 typed API Contract。
- `node --test tests/api.test.mjs tests/create-start.test.mjs tests/runtime.test.mjs tests/recovery.test.mjs`：28 项通过，覆盖请求路径/JSON、模式与方向选择、准备门禁、重复点击、失败/不确定结果对账，以及既有运行与恢复行为。
- `node --test tests/create-start.test.mjs tests/review.test.mjs`：当时 15 项通过，其中 Review 5 项验证首页新增必填选择后既有 mock 导航与复盘行为；随后新增 Prepare 错误/非法准备状态用例已包含在上述最终 28 项中。合计 33 项不同测试通过。
- `npm run build`：通过；`git diff --check`：通过。
- 未执行 Backend 测试、`mvn clean verify`、V1 最终端到端或真实 LLM / llm-smoke 验收：本次仅前端适配，定向 mock 测试、类型检查与构建已构成最小充分验证；真实验收按用户指令留待后续。

实现自检已核对范围、API Contract、生命周期门禁、读取失败阻断、重复请求与迟到响应保护、错误提示、测试与前端文档一致性；无 Migration 或后端事务改动，不声称独立 Review 已通过。
