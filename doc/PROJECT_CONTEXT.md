# Interview Agent — Project Context

> 本文件是新会话使用的项目导航页，不是完整设计或进度日志。详细事实以对应 Source of Truth 为准。
>
> Last Updated: 2026-10-08

## 1. 项目定位

Interview Agent 是一个面向程序员的 AI 面试训练系统，通过模拟面试、动态追问、回答评价和完整复盘，支持持续训练。项目当前也用于实践 AI 应用与 Agent 工程。

当前功能范围见 [`requirements.md`](01-requirements/requirements.md)；项目启动阶段的原始愿景保留在 [`project-definition.md`](00-project/project-definition.md)，不作为当前已实现能力清单。

## 2. 当前状态

- 当前仍处于 **Phase 3 — MVP 实现**，正在准备 Public Release；尚未将整个 Phase 或原始 MVP 目标标记为完成。
- 文本面试主链路、完整生命周期与只读复盘，以及 Frontend MVP 已实现；旧版 Core Backend / Frontend MVP 已完成 E2E 验收。
- **Question Quality V1 后端与前端实现已合并**，使用 Create → Prepare → Start 和 InterviewPlan；Docker Compose 独立 Demo 已由用户确认完成 Create → Prepare → Start → Submit → FOLLOW_UP → Finish → Review 的真实业务验证，记录见 [Phase 3](progress/phase-3.md#public-release-task-1demo-截图与-readme-集成2026-10-08)。该 Demo 验证不代表问题去重、覆盖、事实归因或评价质量具有确定性保证，也不等同于全部 V1 场景验收完成。
- Personal Profile 已支持编辑当前实例的候选人、简历摘要、项目经历与目标岗位。当前为单用户个人实例，无登录认证或多用户数据隔离，不适合直接匿名公网开放；上传/解析、历史列表及用户反馈等外围能力未实现。V1 数据库非空关联收紧仍延期，历史数据兼容边界见对应数据设计。

实现与验收记录见 [`phase-3.md`](progress/phase-3.md)，当前功能边界见 [`requirements.md`](01-requirements/requirements.md)，已知质量问题见 [`technical-debt.md`](00-project/technical-debt.md)。原始路线规划见历史 [`roadmap.md`](00-project/roadmap.md)。

## 3. V0.1 目标

完成一次可运行、可验证的文本模拟面试闭环：创建并启动面试、提问、回答、动态追问、评价、结束面试并查看完整复盘。具体验收范围以需求文档和当前 Slice 设计为准。

V0.1 不以引入新 AI 框架为目标。语音/视频、多 Agent、MCP、Skill、RAG 和复杂长期记忆等能力，只有在产品需求支持时再纳入后续规划。

## 4. 长期架构约束

- 系统采用模块化单体；模块通过明确的 Public API 协作，不依赖其他模块的内部实现。
- MySQL 保存权威业务事实；AI 输出是待校验的 Proposal，不直接成为业务事实。
- LLM 调用期间不持有长数据库事务；业务持久化使用短事务。
- AI Workflow 是短生命周期执行过程；恢复依赖已持久化的业务事实，不依赖长期挂起的 Graph。
- Database Migration 不创建外键；索引按实际查询需求逐步添加。
- 实现先满足当前产品闭环，避免为展示技术或未来假设提前建设通用框架。

详细定义分别见 [`architecture.md`](02-architecture/architecture.md)、[`domain-model.md`](02-architecture/domain-model.md)、[`module-structure.md`](02-architecture/module-structure.md)、[`agent-design.md`](03-ai/agent-design.md)、[`evaluator-design.md`](03-ai/evaluator-design.md) 和对应 ADR。

## 5. 文档导航

| 主题 | 权威文档 |
|---|---|
| 原始项目愿景（Historical） | [`project-definition.md`](00-project/project-definition.md) |
| 原始路线图（Historical） | [`roadmap.md`](00-project/roadmap.md) |
| 功能需求与验收 | [`requirements.md`](01-requirements/requirements.md) |
| 总体架构 | [`architecture.md`](02-architecture/architecture.md) |
| 领域对象与状态 | [`domain-model.md`](02-architecture/domain-model.md) |
| 模块边界 | [`module-structure.md`](02-architecture/module-structure.md) |
| Agent / Workflow | [`agent-design.md`](03-ai/agent-design.md) |
| Context / Prompt / Evaluation | [`context-design.md`](03-ai/context-design.md)、[`prompt-design.md`](03-ai/prompt-design.md)、[`evaluator-design.md`](03-ai/evaluator-design.md) |
| 数据结构 | [`data-model.md`](04-data/data-model.md) |
| 架构决策原因 | [`doc/adr/`](adr/) |
| Phase 与 Slice 进度 | [`phase-3.md`](progress/phase-3.md) |
| Frontend MVP 设计与任务边界 | [`mvp-design.md`](frontend/mvp-design.md)、[`implementation-tasks.md`](frontend/implementation-tasks.md) |
| 开发与验收操作 | [`development-guide.md`](00-project/development-guide.md) |
| Java / API 规范 | [`java-development-guide.md`](00-project/java-development-guide.md) |
| 文档维护规则 | [`documentation-guide.md`](00-project/documentation-guide.md) |
| AI 协作规则 | 仓库根目录 [`AGENTS.md`](../AGENTS.md) |

## 6. AI 协作

ChatGPT 用于需求和设计讨论、文档整理及一致性 Review；Codex CLI 根据已确认的设计执行代码、测试和构建任务。Git 仓库中的正式文档与实现是项目依据，聊天记录只用于讨论。

新会话按任务读取 `AGENTS.md`、本文件、当前 Phase Progress、相关 Source of Truth 和 ADR；不要默认读取全部历史文档。具体治理规则见 [`documentation-guide.md`](00-project/documentation-guide.md)。

## 7. 下一步

完成 Public Release 文档与 Demo 展示整理，并继续验证 Question Quality V1 的场景覆盖与 AI 质量。后续功能优先级根据验证和实际使用结果确定；原始愿景和路线图不作为已完成范围或固定实施承诺。详细实施与验收记录见 [`phase-3.md`](progress/phase-3.md)。
