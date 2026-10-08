# AGENTS.md

## 目的

本文件规定在本仓库工作的 AI 编码代理执行规则。它不替代项目正式文档。

## Source of Truth

- GitHub `main` 分支的 `doc/` 是项目文档唯一的 Source of Truth。
- 修改前必须阅读 `doc/PROJECT_CONTEXT.md`、`doc/00-project/documentation-guide.md`、当前 Phase 进度文档，以及与任务直接相关的架构文档和 ADR。
- 不得以聊天历史或 ChatGPT Library 替代仓库正式文档。
- 不在本文件重复维护架构、领域模型或 API 定义；必须以引用的 `doc/` 正式文档为准。

## 变更流程

- 禁止直接提交或修改 `main`。
- 必须在独立分支完成修改并创建 PR 供用户审核；除非用户明确要求，不得合并 PR。
- 新 Slice 的最小设计未经用户明确确认并记录到 `doc/` 前，不得开始实现。
- 修改必须限制在当前 Slice 范围内，不得顺带提前实现后续 Slice 功能。
- Slice 实现、测试、验收并合并后，更新 `doc/progress/phase-3.md`；项目状态快照变化时，更新 `doc/PROJECT_CONTEXT.md`。

## 架构红线

以下已确认内容如需调整，必须停止实现，提出 Architecture Change 并等待用户确认：

- Maven 模块边界或依赖方向；
- Interview 生命周期，或 `status / current_phase` 的语义；
- MySQL 作为业务事实的权威来源；
- Graph / Workflow 生命周期、事务边界或恢复边界；
- Evaluation 架构或确定性分数计算；
- 引入重大基础设施。

除非用户明确确认，不得引入 Redis、MQ、RAG、向量数据库、Memory、Graph Checkpoint、Prompt Registry、AI Trace 平台、通用 Workflow / AI Task Framework 或其他超出当前 Slice 的基础设施。

避免提前抽象。除非当前 Slice 存在真实需求，不要创建接口、Factory、Manager、Provider、Strategy 或完整分层包结构。Module Public API 仅在存在真实跨模块协作时创建。

## 数据库与配置

- 数据库变更必须新增版本化 Flyway Migration；不得修改已经执行过的 Migration。
- Migration 只包含当前 Slice 所需内容。
- 禁止提交 API Key、数据库密码、Token 或本地环境配置文件。
- 已提交的示例配置必须使用环境变量和安全占位符。
- 默认构建和自动化测试不得要求 LLM API Key，也不得产生模型调用费用。
- 真实 LLM 连通验证必须显式 opt-in，并使用仓库约定的 `llm-smoke` Profile。

## 验证要求

验证遵循“最小充分验证集（minimum sufficient verification）”原则：目标是证明当前改动正确，Task 完成时执行覆盖本次变更的最小充分测试集，不默认要求整个模块的全部测试。按改动风险选择以下三级验证：

### Level 1：开发反馈循环

- 优先运行直接相关的单个 Unit Test、指定 test method；必要时编译受影响模块。
- 不要求每次编辑后立即 compile；完成一个小的逻辑修改单元后集中验证。若直接相关测试已包含编译，不先单独 compile。
- 若一次轻量验证已覆盖改动，不继续扩大范围。不得仅为“保险起见”在每次修改后重复运行 `@SpringBootTest`、Testcontainers、整个模块测试或 `mvn clean verify`。

### Level 2：Task 完成验证

代码基本稳定后执行一次最小充分 Task Gate，通常包括：

- 直接相关的 Unit Tests；涉及数据库、Spring Bean、事务、CAS、Migration 等行为时，运行覆盖该行为的直接相关 Integration Test / Testcontainers Test。
- 涉及 Java、XML 或 POM 时，对受影响模块执行 Spotless 检查；执行 `git diff --check`。
- 重型 Spring Boot / Testcontainers 测试原则上在代码稳定后集中运行，不作为反复开发反馈循环。普通 Task 或小 PR 不默认要求整个模块全部测试或 `mvn clean verify`；只有影响范围确实较大、较小验证集不足以建立信心时才扩大范围，并说明原因。

### Level 3：Slice / 高风险 Gate

以下情况执行项目级完整验证：Slice 收尾、大范围跨模块修改、公共基础设施重大修改、无法可靠判断影响范围，或用户明确要求：

```text
mvn clean verify
```

普通小 Task 创建 PR 前不强制执行项目级完整验证。同一 Slice 拆成多个小 Task / PR 时，不因每次创建 PR 重复运行；在 Slice 收尾统一执行。

### 避免重复验证

- 同一代码状态已成功完成的验证，不因汇报、Review 或创建 PR 而机械重跑；后续修改只重跑可能受影响的验证。
- 重型测试失败并修复后，先运行覆盖修复点的最小测试；代码稳定后再运行仍有必要的重型测试。
- 不得无依据地层层扩大验证范围。运行明显耗时的验证前，先判断它是否为当前 Task 的必要验证。
- 默认测试不得调用真实 LLM；`llm-smoke` 必须显式 opt-in，任何默认或完整验证均不得产生模型调用费用。

Task 完成汇报需列出执行的验证及结果，说明其如何覆盖本次改动，并说明未执行的重型验证及理由。最小充分验证已通过时，不将未执行 `mvn clean verify` 描述为风险或未完成项。纯文档、注释或格式调整按对应规则检查 diff / 格式，无需运行单元测试。

目标是形成以下节奏：**小 Task → 小 Diff → 小范围验证 → Task 完成最小充分验证 → Slice 收尾全量验证**。

## 提交与 PR 语言

- Git Commit Message 默认使用中文，简明描述本次变更；代码标识符、模块名、命令和固定技术术语可保留英文。
- PR 标题与 PR 说明默认使用中文；必要的代码、配置和技术名词保留原文。
- 推荐格式：`类型：中文变更说明`，例如 `feat：实现 Slice 1 面试创建与启动`、`docs：补充 Slice 1 验收记录`。

## 实现权限边界

类名、包结构、DTO 字段、配置键和测试组织等实现细节，只要不改变已确认架构，可在当前 Slice 内结合测试合理决定。

如果实现发现需要改变已确认架构，不得静默在代码中适配。必须说明问题、影响、候选方案和推荐的 Architecture Change，并等待用户确认。


## Review 门禁

从 Slice 2 开始，创建实现 PR 前必须完成一次代码与设计 Review。Review 至少覆盖范围、架构边界、状态流转、事务与幂等、Migration、日志、测试、人工验收和文档一致性。存在 Blocker 或 Major 问题时，不得将 Slice 标记为完成；修复后必须重新 Review。

## 统一格式化要求

- 所有 Java、XML 和 Maven POM 文件必须遵循项目统一格式。
- 根据改动范围选模块；开发过程中无需每改一次文件就运行 Spotless。代码稳定后，对涉及 Java、XML 或 POM 的模块各执行一次 `mvn -pl <模块列表> spotless:apply`，随后执行 `mvn -pl <模块列表> spotless:check`。修改根 `pom.xml` 时，额外检查根聚合模块；跨模块或无法判断影响范围时扩大检查范围。
- 多模块用逗号分隔，例如 `mvn -pl interview-agent-interview,interview-agent-ai spotless:apply`；仅格式化当前模块不加 `-am`，避免把依赖模块也纳入格式化。确有依赖或构建需要时再使用 `-am`。
- 只改 Markdown 等 Spotless 未覆盖的文件时，检查 diff 即可，不运行 Spotless 或测试；只改注释时执行对应模块格式检查，无需运行测试。
- Slice 收尾执行根目录的 `mvn spotless:check`，确保未漏掉模块；若同一代码状态的 `mvn clean verify` 已通过 `validate` 阶段执行等价 Spotless check，不重复运行。普通小 PR 不额外要求根目录全量 Spotless；按 Level 2 检查受影响模块即可。
- 所要求的 `spotless:check` 失败时不得宣称任务完成。
- 格式化不能改变业务逻辑、接口语义或数据库行为。
- 纯格式化变更与功能变更分开提交。
