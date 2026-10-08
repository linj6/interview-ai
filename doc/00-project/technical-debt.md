# 技术债清单

本清单记录 Review 中发现、当前不阻塞合并但后续需要评估或处理的工程改进项。登记不代表已确定具体实现方案；处理时再结合当时的需求与测试决定。完成后更新状态。

| ID | 问题与后续动作 | 类型 | 优先级 | 处理时机 | 状态 |
| --- | --- | --- | --- | --- | --- |
| TD-001 | `Question.type` 目前使用 `String`；后续评估是否引入 `QuestionType` enum，避免类型值散落。 | 类型安全 | 低 | 后续 cleanup | 已完成（PR #33 已合并） |
| TD-002 | `interview.interfaces` 下混放 Controller、Request、Response；后续按职责评估分包。 | 代码结构 | 低 | 后续 cleanup | 已完成（已合并到 main） |
| TD-003 | `decision-system.st` 等历史英文 Prompt 后续按统一语言规范中文化；保留协议字段、代码标识符及确有英文要求的内容。 | Prompt 一致性 | 中 | 近期 cleanup | 已完成（PR #34 已合并） |
| TD-004 | 当前 `main` 中 `DecisionProposalValidator` 为普通无状态类，不标注 `@Component`；组件注册与手动创建不再冲突。 | Bean 管理 | 低 | — | 已完成 |
| TD-005 | `DecisionAction` 等业务枚举缺少业务语义注释；后续检查同类枚举并补齐必要说明。 | 可维护性 | 低 | 后续 cleanup | 已完成（PR #35 已合并） |
| TD-006 | MySQL 默认 `REPEATABLE READ` 下，`InterviewRepository.transition()` 最终 CAS 失败后在同一事务中以普通一致性读重新读取 Interview，可能仍看到事务快照中的旧 version，把实际 `VERSION_CONFLICT` 误分类为 `STATE_CONFLICT`。这不影响 Interview CAS 正确性、T2 整体 rollback 或数据一致性，当前仅影响并发冲突诊断和错误分类精度；Slice 3 Task 3.3 暂不修复。后续统一评估锁读、独立事务查询或调整冲突分类策略，不预定具体方案。 | 冲突诊断 | 中 | 后续并发处理评估 | 待处理（暂缓） |
| TD-007 | Turn orchestration 已从 `InterviewApplicationService` 拆至 `InterviewTurnApplicationService`；前者保留 Interview 创建、Opening、生命周期和查询职责。若这些职责后续继续增长，再依据具体用例评估是否继续拆分。 | 代码结构 | 中 | 按实际需要评估 | 已完成（Turn orchestration 已拆分） |
| TD-008 | `InterviewSkipApplicationService` 与 `InterviewTurnApplicationService` 重复实现可恢复 AI failure 判断、AI error mapping，以及最多两次尝试 / 100ms retry 策略。当前重复不影响正确性；暂不为两个调用方提前抽象。出现第三个调用方，或 AI retry / error mapping 策略需要统一调整时，再评估提取最小公共组件；避免演变为通用 AI Workflow / Retry Framework。 | 代码复用 | 低 | 第三个调用方出现或策略需要统一调整时 | 待处理（暂缓） |
| TD-009 | Evaluator 核心语义边界已通过真实回归，保留 P2 residual issues。PR #68 修正 Prompt / Contract 后，Interview 27 / Q49 使用 `evaluation-v3` + `qwen3-max` 完成真实 LLM 回归：Accuracy 不再因 omission（未提及 / 未展开）自动扣分；历史事实与当前建议得到区分；“当时没做 / 记不清”不自动转为能力 Weakness；ANSWER_FRAMEWORK 区分来源支持的历史事实、明确不知道 / 未做 / 记不清的信息和当前建议，核心边界 PASS。仍保留 P2 残留问题：未说明与明确未实施的区分、Weakness ideal-answer bias、Reference Answer factual grounding、MissingPoint 事实状态与追问价值。后续按已确认的 [Evaluator 设计](../03-ai/evaluator-design.md) 跟踪这些残留问题；本次 PASS 仅代表该场景通过，不保证模型每次稳定遵守，也不代表全部语义问题已解决。 | AI 语义质量 | 中 | 后续真实语义回归与使用反馈时 | 核心边界 PASS（Interview 27 / Q49）；P2 residual 待处理 |
| TD-010 | 当前 Prompt / Schema、Proposal 校验和确定性评分测试不能证明真实模型的语义质量，也缺少可重复对比的语义回归验证。后续评估如何保留典型 Question / Answer 与人工判定依据，覆盖题外知识、记不清指标、没做 / 没发生、当前重新设计建议和 Reference Answer 事实边界，并对 Prompt / Model 变化进行对比。TD-009 追踪具体质量问题，本项追踪验证能力缺口；现有真实 E2E 记录可作为依据。仅登记需求，不展开 AI Quality Eval System 设计；真实模型验证必须显式 opt-in，遵循仓库 `llm-smoke` 约定，默认测试不得产生模型费用。 | AI 质量验证 | 中 | 后续需要持续比较 Prompt / Model 语义效果时评估 | 待处理（暂缓） |

新增 LLM Prompt 的默认语言要求见 [`doc/03-ai/prompt-design.md`](../03-ai/prompt-design.md)，不作为待处理技术债；TD-003 只追踪历史内容的整理。
