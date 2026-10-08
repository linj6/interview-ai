# Interview Agent 文档治理规范

**文档版本：V1.5 · 文档状态：[已确认]**

## 1. 目的与原则

Git 仓库 `main` 分支中的 `doc/` 是项目正式文档的权威载体。聊天记录用于讨论，不作为最终设计依据；ChatGPT Library 仅是可选镜像。

文档治理遵循以下原则：

- 一个当前事实只维护一个主要 Source of Truth；其他文档优先引用，不重复维护。
- 讨论方案经用户确认后，才写入正式设计。
- 只同步本次变化实际影响的文档；不为满足流程而更新无关文档。
- Progress 记录阶段状态和实施结果；设计细节留在对应设计文档。
- `PROJECT_CONTEXT.md` 只作项目导航和低频上下文，不作超级摘要。

## 2. 设计状态

重要设计使用以下状态：

| 状态 | 含义 |
|---|---|
| `[讨论中]` | 尚未确定，不作为实现依据 |
| `[已确认]` | 已确认，可作为正式设计依据 |
| `[暂缓]` | 当前不处理，未来可以重新评估 |
| `[否决]` | 已明确不采用 |

聊天中讨论充分不等于已确认。只有确认后的方案才进入正式 Source of Truth。

## 3. 文档目录与职责

当前文档结构：

```text
doc/
├── PROJECT_CONTEXT.md
├── 00-project/
│   ├── development-guide.md
│   ├── documentation-guide.md
│   ├── glossary.md
│   ├── java-development-guide.md
│   ├── project-definition.md
│   ├── roadmap.md
│   └── technical-debt.md
├── 01-requirements/requirements.md
├── 02-architecture/
│   ├── architecture.md
│   ├── domain-model.md
│   └── module-structure.md
├── 03-ai/
│   ├── agent-design.md
│   ├── context-design.md
│   ├── evaluator-design.md
│   └── prompt-design.md
├── 04-data/data-model.md
├── frontend/
│   ├── mvp-design.md
│   └── implementation-tasks.md
├── adr/                 # ADR-001 至 ADR-007
├── progress/            # 当前维护的 Phase 2、Phase 3 进度
└── retrospectives/      # 阶段复盘与 AI Coding 实践总结
```

| 文档 | 职责 |
|---|---|
| `PROJECT_CONTEXT.md` | 项目定位、当前大阶段、下一步、重要约束和 SoT 导航；不保存实现快照 |
| `project-definition.md` | 项目背景、目标用户、产品定位和成功目标 |
| `roadmap.md` | 产品与技术演进方向，不承诺详细实现方案 |
| `requirements.md` | 功能、非功能、业务约束和验收需求 |
| `architecture.md` | 总体架构、分层和稳定架构边界 |
| `domain-model.md` | 领域对象、关系与业务状态语义 |
| `module-structure.md` | 模块职责、依赖方向和 Public API 边界 |
| `agent-design.md` | Agent / Workflow 的职责、流程与恢复边界 |
| `context-design.md` | AI Context 的来源、构建和裁剪规则 |
| `evaluator-design.md` | Evaluation 契约、校验和确定性评分规则 |
| `prompt-design.md` | Prompt 的组织、语言、版本与测试原则 |
| `data-model.md` | 持久化实体、字段、约束和数据规则 |
| `frontend/` | Frontend 专属技术选择、页面、UI Projection、交互与实施任务边界；引用系统级 SoT，阶段进度仍归 `progress/` |
| `ADR` | 记录重要架构决策的背景、选择理由和后果 |
| `progress/phase-N.md` | 阶段目标、Slice 状态、实施结果和验收记录 |
| `development-guide.md` | 本地环境、启动、测试和人工验收操作 |
| `java-development-guide.md` | Java、API、错误码、异常、日志与注释规范 |
| `technical-debt.md` | 尚待评估或处理的工程问题；状态须对照当前代码更新 |
| `retrospectives/` | 记录阶段经验；不替代当前设计规范 |
| `glossary.md` | 解释项目中高频且容易歧义的术语 |

### 文档生命周期

按文档职责区分生命周期，不新增目录或管理流程：

- **Current**：描述当前有效事实或操作规则，随相关实现变化维护，例如项目导航、开发指南和技术债状态；同一动态事实只在其主要 SoT 维护，其他入口引用。
- **Reference**：描述当前有效的需求、领域和架构设计，随已确认的设计变化维护；不承担项目进度管理，避免散落项目阶段、Task 状态和下一步等动态信息。
- **历史记录**：ADR、Progress、Retrospective 和 Original Vision 保存当时的决策、实施、验收与目标。完成后原则上冻结；历史与当前实现不同不代表错误，不为匹配当前代码改写历史。Original Vision 的未实现目标不应改写成已实现能力。

Progress 在阶段进行中维护唯一的当前进度入口，已完成的 Task / 验收记录冻结；阶段完成后整体作为历史记录。ADR 被替代时通过状态与 `Superseded by` / `Supersedes` 链接表达，保留原始决策背景。必要的事实勘误、隐私脱敏和历史状态说明可以补充，但应明确说明，不改变原验收结果或虚构新证据。

如需新增文档，先确定它负责维护的事实、生命周期，以及它和已有 SoT 的边界。

## 4. Source of Truth 导航

| 事实 | 主要依据 |
|---|---|
| 项目定位与目标 | `00-project/project-definition.md` |
| 产品路线 | `00-project/roadmap.md` |
| 功能需求 | `01-requirements/requirements.md` |
| 总体架构 | `02-architecture/architecture.md` |
| 领域状态与对象 | `02-architecture/domain-model.md`；重要决策原因见对应 ADR |
| 模块边界 | `02-architecture/module-structure.md` |
| Frontend MVP 设计与任务边界 | [`frontend/mvp-design.md`](../frontend/mvp-design.md)、[`frontend/implementation-tasks.md`](../frontend/implementation-tasks.md) |
| AI Workflow、Context、Prompt、Evaluation | `03-ai/` 下对应设计文档 |
| 持久化模型 | `04-data/data-model.md` 与数据库 Migration |
| 当前阶段和实现结果 | `progress/phase-N.md` |
| 编码规范与执行规则 | `AGENTS.md`、`00-project/java-development-guide.md` |
| 实际实现行为 | 当前代码与自动化测试 |

出现冲突时，先确定该事实所属的 SoT，再核对代码和测试是否已经实现；不要依据聊天记忆或 Context 摘要覆盖正式定义。

## 5. 文档变更流程

设计或需求发生变化时：

1. 描述问题和受影响范围。
2. 比较候选方案，说明关键取舍。
3. 用户确认后，更新该事实对应的 SoT。
4. 决策原因重要且未来可能复查时，新增 ADR；替代已有决策时维护状态与 Superseded 关系，不重写原始背景。
5. 只同步被该事实实际影响的文档、代码和测试。
6. Phase / Slice 状态或验收结果变化时，更新进行中的 Progress 入口；已完成记录保留当时事实，新结果追加记录。
7. 按 `AGENTS.md` 执行与代码变化相匹配的验证。

`PROJECT_CONTEXT.md` 只在它负责的导航信息发生变化时更新，例如项目目标、当前 Phase、当前主要 Slice、长期约束或文档入口变化。普通 Task 完成、测试数量、Migration 版本、分支或 PR 状态不触发 Context 更新。

实现中发现已确认设计无法满足需求时，先记录问题并回到设计讨论；不要默默让实现反向改变架构决策。

## 6. Progress 与历史记录

Progress 负责回答“现在进行到哪里”和“该阶段实际完成了什么”，不负责重新定义架构。稳定设计的正文应留在对应 SoT，Progress 可引用该文档。

历史记录保留当时的实现或验收结果；已被替代的决定可增加状态说明与当前 SoT 链接，正文不追随当前实现改写。进行中阶段的当前状态应有明确且唯一的位置，历史 Task 状态不作为当前状态入口。

复盘文档记录经验和当时的背景。若复盘中的建议后来被正式规范取代，应链接到当前规范，避免读者误把历史建议当作现行规则。

## 7. AI 协作与新会话

ChatGPT 负责需求与方案讨论、设计一致性检查和文档整理；Codex CLI 按已确认的目标和边界实现、测试与调试。重大设计决策由用户确认。双方都应主动指出遗漏、范围扩张和与已确认规则的冲突。

新会话按当前任务读取：

1. 仓库根目录 `AGENTS.md`；
2. `PROJECT_CONTEXT.md`；
3. 当前 Phase Progress；
4. 与任务直接相关的 Source of Truth 和 ADR；
5. 必要时再查历史 Progress 或复盘。

不要默认读取所有历史文档。执行 Prompt 应引用仓库中的规范和设计，只写本次目标、范围、特殊约束与验收条件，不复制整份项目规则。

## 8. 维护检查

文档变更前后检查：

- 是否修改了正确的 SoT，是否产生新的重复定义？
- 当前状态、设计状态和代码实现是否被清楚区分？
- 受影响文档的链接和交叉引用是否有效？
- 是否把临时测试数字、分支状态或任务日志写进低频导航文档？
- Current / Reference 是否有过期事实，历史记录是否被误读为当前状态或规则？

纯文档变更按 `AGENTS.md` 检查 diff 和链接；不因文档修改运行代码测试。
