# Core Backend E2E 阶段复盘

> 日期：2026-09-30  
> 范围：Phase 3 内部 Core Backend E2E Acceptance 第一阶段  
> 状态：Core Backend E2E Acceptance 已完成（最终 Closure Gate PASS）
>
> Public 隐私说明：本文案例已脱敏/泛化，删除了真实业务背景、领域实体、故障链路及原始问答。保留的是原 E2E 的工程问题、结论和验收边界；摘要不是原始生成文本，也不代表重新执行过虚构案例。

## 1. 目标与原则

本轮 E2E 不是 Phase 4 正式验收，目标是在进入 Slice 6 和前端之前，使用真实 DashScope、真实候选人/项目/岗位上下文验证核心面试主链：

```text
创建 Interview
→ Start / Opening
→ Answer
→ Evaluation
→ Decision
→ FOLLOW_UP / NEXT_QUESTION
→ Skip
→ Pause / Resume
→ Finish
→ Review
```

执行原则：

- 正常回答，不刻意诱导模型。
- 阻塞主链、明显正确性问题或核心体验问题立即处理。
- 理论风险先观察，不提前过度设计。
- Prompt 明显无法稳定保证的业务约束，优先考虑程序化约束。
- 修复后必须重新进行真实 LLM E2E，而不能只依赖 Mock / Contract Test。

## 2. 本轮发现与处理

### 2.1 Question Single Focus 不稳定

#### 现象

Opening / Question Generation 曾将多个独立问题合并到一道 MAIN，将背景澄清、实现讨论和效果验证等多个考察目标同时纳入。

最初只调整 `question-generation-system.st`，但 E2E 发现 Opening 走独立链路：

```text
startInterview()
→ generateOpeningQuestion()
→ opening-system.st
```

因此普通 Question Generation 的约束无法覆盖 Opening。

#### 处理

先统一 Opening / Question Generation / Skip 的 Question Contract，随后为 Opening 引入结构化输出：

```text
OpeningQuestionProposal {
    focus,
    question
}
```

并将 focus 从宽泛的“能力标签”进一步定义为一个具体、可直接回答的技术目标。

#### 效果

问题质量有改善，部分真实调用能生成较聚焦的问题，但仍出现回退。Structured Output 能约束 JSON 结构，不能可靠保证自然语言语义上的“原子 focus”。

#### 结论

**状态：🟡 改善，但未完全结构性解决。**

不要继续无限叠加 Prompt。Prompt Contract 不等于 Business Contract。

---

### 2.2 Evaluation Evidence 无法稳定逐字引用 Answer

#### 现象

原 Evaluation 要求 `ANSWER_QUOTE.quote` 必须逐字来自 Answer。真实模型会发生：

- 改写原文；
- 合并多个句子；
- 使用省略号；
- 返回看似引用但并非连续原文的文本。

即使 Prompt 明确要求逐字引用、禁止省略号、无法引用时改用 `OVERALL_OBSERVATION`，仍不能稳定满足。

#### 处理：Evidence Fragment V1

Java 确定性切分 Answer 的非空行并分配请求内临时引用：

```text
E1: ...
E2: ...
E3: ...
```

LLM 不再复制 quote，而输出：

```text
EvidenceProposal {
    type,
    evidenceRef,
    explanation
}
```

Java 使用同一份 fragment 集合严格验证 `E<n>`，再解析回原始 Answer fragment 并持久化真正 quote。

约束：

- Fragment ID 仅存在于一次 Evaluation 请求中。
- 不进入领域模型、持久化协议和 Review API。
- 不做 fuzzy match。
- 不自动修正非法 ref。
- 不自动 fallback。
- 非法 ref 继续走既有 EvaluationValidationException / retry。

#### 效果

Interview 16/18 等真实 E2E 中连续稳定工作，数据库保存原始 Answer 文本，不泄漏 E<n>。

#### 结论

**状态：✅ 已解决。**

关键经验：

> LLM 负责语义判断，程序负责确定性约束。

---

### 2.3 FOLLOW_UP intent 聚合多个目标

#### 现象

早期 Decision 的自由 intent 聚合多个考察目标，Question Generation 随后生成多问题 FOLLOW_UP。

仅靠 Prompt 要求 intent “具体、原子、单一”仍不够稳定。

#### 处理：Decision Target Ref V1

最初将 weaknesses + missingPoints 作为候选 target，但真实 E2E 发现 weakness 本身也可能是复合目标，因此最终收紧为：

```text
FOLLOW_UP targets = Evaluation.missingPoints only
```

Decision 对 FOLLOW_UP 不再自由生成 intent，只能选择：

```text
targetRef = M1 / M2 / ...
```

Java 严格验证引用并解析为原始 missingPoint description，作为 Question Generation intent。

当 missingPoints 为空时，应用层从 allowedActions 中移除 FOLLOW_UP。

#### 效果

Interview 18 连续两轮真实 E2E 均成功选择唯一 target，没有重新聚合多个目标；具体目标和原始问答已删除。

Q24 时当前 MAIN 链已达到 FOLLOW_UP 2/2，应用正确将 allowedActions 收敛为 `[NEXT_QUESTION]`。

#### 结论

**状态：✅ 已解决并完成真实 E2E 验证。**

---

### 2.4 Evaluation 会制造低价值 missingPoints

#### 现象

脱敏摘要：Interview 18 中，候选人已说明被问及的行为与取舍，Evaluation 仍把进一步展开的细节列为 missingPoints。这些内容虽可讨论，但对有限题数的岗位面试，继续追问价值偏低。

#### 根因判断

当前 missingPoint 更接近：

> 回答还有什么没有讲？

真正需要的产品语义应更接近：

> 当前回答中，哪些关键缺失点对判断候选人的目标岗位能力有明显价值，值得独立消耗一次 FOLLOW_UP？

如果 missingPoints 持续包含所有“还可以继续展开”的细节，系统会形成：

```text
回答
→ 总能发现更多细节
→ missingPoints 长期非空
→ FOLLOW_UP quota 只要存在就容易继续深挖
```

#### 结论

**状态：🔴 遗留。**

暂不采用简单的 `overallScore >= N → 禁止 FOLLOW_UP`，因为高分回答仍可能遗漏真正重要的关键点。后续需要处理“追问价值”，而不是单纯分数。

---

### 2.5 Evaluation / ReferenceAnswer factual grounding 不可靠

#### 现象

即使 Prompt 已明确要求项目经历类 Reference Answer 不得虚构候选人的经历、数据、职责或效果，真实模型仍多次补充未提供的信息，例如：

- 并发量、QPS、批量大小；
- 接口签名或返回结构；
- 限流位置、阈值；
- 压测与效果数据；
- 额外治理方案。

脱敏摘要：Interview 18 的 Reference Answer 继续扩展未经候选人提供的实现方案与风险分析，混淆历史事实和通用建议。

#### 风险

Reference Answer 容易从“帮助用户复盘自己的项目经历”滑向“LLM 根据通用知识重新设计一个标准项目”，并进一步污染 missingPoints、评分和 FOLLOW_UP。

#### 结论

**状态：🔴 E2E-02 遗留。**

不能再简单依赖增加“不得虚构”Prompt，需要后续考虑更结构化的事实边界设计。

---

### 2.6 Interview 主问题重复与 E2E-04 验收状态

这是本轮 E2E 暴露出的 Interview 内主问题重复问题；跨 Interview 的重复属于另一项未覆盖问题。

#### 跨 Interview

脱敏摘要：多个新 Interview 的 Opening 反复选择候选人资料中的同一高信号经历；具体业务与故障链路已删除。

每次 Opening 看到几乎相同的 Candidate / Resume / Project / Position，上下文中没有历史 Interview / Topic Coverage，因此模型不断选择同一个局部最优主题。

#### 单次 Interview

脱敏摘要：Interview 18 从 MAIN Q22 进入 Q23 / Q24 两轮 FOLLOW_UP，达到追问上限后，Decision 正确选择 NEXT_QUESTION。但 NEXT_QUESTION intent 和新 MAIN Q25 仍沿用上一追问的考察目标；原始问题及接口行为细节已删除。

因此：

```text
结构上：Q25 = 新 MAIN       ✅
语义上：Q25 ≈ Q24 的改写   ❌
```

#### Interview 18 历史失败与 Interview 19 真实复验

Interview 18 的 Q25 显示，当时的 NEXT_QUESTION intent 和新 MAIN 仍沿用上一追问的考察目标。该真实失败证据保留为引入 E2E-04 Focus Selector 的背景。

Task 4 自动化 Gate 通过后，使用真实 DashScope 对 Interview 19 的 FOLLOW_UP 达上限转 NEXT_QUESTION 链路进行验收：

```text
MAIN 1 / F1：原首个考察目标（具体内容已脱敏）
  → FOLLOW_UP 1（focus=null）
  → FOLLOW_UP 2（focus=null）
  → followUpCount=2/2，allowedActions=[NEXT_QUESTION]
  → Decision=NEXT_QUESTION
  → Focus Selector 的 selectedMainFocuses 包含 F1
  → F2：与 F1 不同的考察目标（具体内容已脱敏）
  → 新 MAIN Q29：围绕 F2 生成，持久化 focus=F2
```

本次观察中，F1 与 F2 属于不同核心考察目标，Q29 与 F2 语义一致。**E2E-04 在本次真实 LLM 场景中通过验收。**

此结论只证明 Interview 19 当前场景中 focus 发生切换且 Q29 与 F2 一致，不代表语义重复已被确定性消除，也不构成长期保证；后续真实使用继续观察。跨 Interview 重复不属于本次 E2E-04 验收范围，仍作为独立遗留问题记录。

Task 4 自动化 Gate：2026-10-01 执行根目录 `mvn clean verify` 成功；Bootstrap 118 项测试通过，0 失败、0 错误、0 跳过；全 Reactor Spotless 检查通过；Testcontainers MySQL 8.4 校验并执行 V1～V9 Migration。测试 fixture cleanup 修正了旧测试中 MAIN focus 可缺省的假设，以及 FOLLOW_UP 手动重试错用 MAIN Generator 的 stub。`git diff --check` 通过。

## 3. 本轮核心工程经验

### 3.1 Prompt Contract ≠ Business Contract

Prompt 可以改善概率，但无法把自然语言语义要求变成确定性业务约束。

当一个 LLM 输出：

- 可以被程序明确验证；
- 会直接影响核心业务状态；
- 且真实 E2E 已证明 Prompt 无法稳定保证；

应优先考虑结构化引用、程序校验、状态机约束、重试或 fallback，而不是继续堆 Prompt。

### 3.2 LLM 做语义判断，程序做确定性约束

本轮两个最有效的结构性修复都遵循同一原则：

```text
Evidence:
LLM 自由复制 quote
→ Answer Fragment Ref + Java validation

FOLLOW_UP:
LLM 自由生成 intent
→ MissingPoint Target Ref + Java validation
```

这比单纯增加 Prompt 或 Structured Output 更稳定。

### 3.3 真实 E2E 不可被 Mock / Contract Test 替代

Mock 和 Schema Test 能证明协议与代码路径正确，但无法发现：

- LLM 对“原子目标”的真实理解偏差；
- Prompt 约束在真实模型中的退化；
- Evaluation 对不存在事实的补全；
- 多轮对话中的上下文惯性；
- FOLLOW_UP 深度与整场 Interview 覆盖之间的冲突。

因此涉及真实模型行为的关键 Contract 调整必须保留真实 E2E Gate。

### 3.4 Prompt Engineering 需要停止条件

本轮 Evidence exact quote 是典型案例：

```text
LLM 不稳定
→ 加 Prompt
→ E2E 仍失败
→ 再加 Prompt
→ 仍失败
```

在一次清晰 Contract 调整后仍反复出现同类真实失败，应停止继续堆 Prompt，重新检查职责划分。

## 4. 当前状态汇总

| 问题 | 处理方式 | 状态 |
| --- | --- | --- |
| Opening / Question Single Focus | Prompt + Structured Output + Focus Contract | 🟡 改善，未完全解决 |
| Evidence 精确引用 | Answer Fragment + evidenceRef + Java validation | ✅ 已解决 |
| FOLLOW_UP intent 聚合 | missingPoints-only Target Ref + Java resolve | ✅ 已解决 |
| FOLLOW_UP 达上限 | allowedActions 程序约束 | ✅ 已验证 |
| Evaluation 低价值 missingPoints | PR #68 后，Interview 27 / Q49 真实回归通过核心语义边界；MissingPoint 事实状态与追问价值仍由 TD-009 跟踪 | 🟡 核心边界 PASS；P2 residual 待处理 |
| Evaluation / ReferenceAnswer factual grounding | Interview 27 / Q49 验证历史事实与当前建议区分、ANSWER_FRAMEWORK 三类信息边界；Reference Answer factual grounding 等残留问题由 TD-009 跟踪 | 🟡 核心边界 PASS；P2 residual 待处理 |
| Opening 跨 Interview 重复 | 不属于本次 E2E-04 验收范围 | 🔴 遗留 |
| 同一 Interview 中 NEXT_MAIN 与前序 MAIN 语义重复 | Focus Selector；Interview 19 真实复验通过，后续持续观察 | ✅ 当前场景通过；不构成长期保证 |
| Skip | Interview 20 真实 E2E | ✅ PASS |
| Pause / Resume | Interview 20 真实 E2E；Resume 保持同一 currentQuestion，且无 AI 调用 | ✅ PASS |
| Active Finish | Interview 20 真实 E2E；完成状态正确，无新 Question 或 AI 调用 | ✅ PASS |
| Review | Interview 20 首次调用发现 P0 immutable-list 排序异常；commit `6e1973e8f528ff8a512dcdafccc06708de63eedd` 修复后，同一 Interview 回归通过 | ✅ PASS |

## 5. 阶段结论

Core Backend 的真实业务生命周期已完成 E2E 验证；2026-10-02 在最新 `main` 提交 `26668ef` 上执行最终 `mvn clean verify` Closure Gate，结果为 **BUILD SUCCESS**。16 个 Reactor 模块全部成功，全 Reactor Spotless 检查通过；Bootstrap 141 项测试通过，0 失败、0 错误、0 跳过。Testcontainers MySQL 8.4 启动成功，Flyway V1～V9 Migration 执行通过。默认测试未调用真实 LLM，本次未创建新的 Interview。**Core Backend E2E Acceptance 已完成，无 Backend Closure Blocker。**

Interview 20 完整生命周期验收结果：

| 步骤 | 结果 |
| --- | --- |
| Skip | PASS |
| Pause / Resume | PASS；Resume 保持同一 `currentQuestion`，且无 AI 调用 |
| Answer → Evaluation → Decision → FOLLOW_UP | PASS |
| Active Finish | PASS；`COMPLETED`、`currentPhase=null`、`currentQuestion=null`，无新 Question、无 AI 调用 |
| Review | 首次真实调用发现 P0 bug：对 immutable empty list 调用 `sort()` 导致 `UnsupportedOperationException`。commit `6e1973e8f528ff8a512dcdafccc06708de63eedd` 修复后，使用同一个 Interview 20 回归 PASS |

Review 聚合结果：Q30 MAIN=`SKIPPED`；Q31 MAIN=`ANSWERED` 并包含 Evaluation；Q32 FOLLOW_UP=`UNANSWERED`。Overview 计数为 MAIN 2、FOLLOW_UP 1、ANSWERED 1、SKIPPED 1、UNANSWERED 1。

Interview 19 的真实 DashScope 验收中，同一 Interview 的 NEXT_MAIN 切换到不同考察目标，不再重复原 MAIN focus（具体业务内容已脱敏）。该结果只证明当前真实场景通过，不表示语义重复被确定性消除。Interview 18 暂停在 Q25 的历史失败证据继续保留。

本轮保留但不继续修复的问题：E2E-02 Evaluation / ReferenceAnswer factual grounding；Evaluation low-value missingPoints；E2E-05 跨 Interview Focus 重复（后续考虑 Interview History / Training History，本轮不设计方案）；Focus / Question 可能过宽；Follow-up Question 可能预设候选人的实现方案；Evaluator 延迟较高（本轮观察约 37 秒）；候选人项目摘要仍可能混淆两个独立场景的事实关系。

后续 Evaluator 语义回归结论（Interview 27 / Q49）：

历史真实失败中，Evaluation 曾将遗漏扩散为扣分或能力弱点，并混淆历史事实与当前建议；PR #68 使用 `evaluation-v3` 修正 Prompt / Contract 边界。其后 Interview 27 / Q49 使用 `evaluation-v3` + `qwen3-max` 完成 real-LLM regression，核心语义边界 **PASS**，已验证改善包括：

- Accuracy omission：未提及 / 未展开不自动成为 Accuracy 扣分依据。
- 历史事实与当前建议得到区分，当前重新设计思考不反推为历史实施。
- “当时没做 / 记不清”不自动转为能力 Weakness。
- ANSWER_FRAMEWORK 区分来源支持的历史事实、明确不知道 / 未做 / 记不清的信息和当前建议。

仍保留 **P2 residual issues**：未说明与明确未实施的区分、Weakness ideal-answer bias、Reference Answer factual grounding、MissingPoint 事实状态与追问价值。演进关系为：**历史失败 → PR #68 → Interview 27 real-LLM regression（Q49，evaluation-v3 + qwen3-max）→ 核心边界 PASS + P2 residual → TD-009**。残留问题统一由 [技术债清单](../00-project/technical-debt.md) TD-009 跟踪；本次 PASS 限于该真实场景，不代表全部语义问题已解决或模型每次稳定遵守。

## 6. 下一步

2026-10-02 Evaluator 文档补充已按 Interview 27 / Q49 回归结果更新：PR #68 的静态测试证明约束进入模型输入；后续 `evaluation-v3` + `qwen3-max` 真实回归已验证核心语义边界 PASS，替代此前“真实效果待验证”的描述。P2 残留问题继续由 [技术债清单](../00-project/technical-debt.md) TD-009 跟踪；可重复语义回归验证的能力缺口仍由 TD-010 跟踪。两项均不阻塞 Backend Closure；本次不处理这些技术债，也不展开 AI Quality Eval System 设计。

最终 Closure Gate 已通过，下一阶段进入 **Frontend MVP / Full UI E2E**。Slice 6 外围能力仍待开始；后续状态以 [Phase 3 进度](../progress/phase-3.md) 为准。
