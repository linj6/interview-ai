# Interview Agent 项目路线图

> **文档生命周期：Original Roadmap / Historical**。本文记录原始产品与技术路线；后续实际范围已根据开发和验证结果调整，不代表当前完成情况或固定发布承诺。下方正文保留当时的版本、阶段、目标和规划，不为匹配当前实现重写。
>
> 当前项目入口见 [README](../../README.md) 与 [PROJECT_CONTEXT](../PROJECT_CONTEXT.md)，当前需求范围见 [requirements](../01-requirements/requirements.md)。

**版本：V1.1**  
**状态：[已确认]**

## 定位

本路线图描述产品与技术演进方向，不承担详细架构、数据模型或实现任务设计。

核心原则：

- MVP 优先；
- 产品价值优先；
- 避免技术堆叠；
- 渐进演进；
- 每个阶段产生可运行、可验证的结果；
- 架构设计达到“足够实现”后及时进入编码，通过真实运行反馈迭代。

## 产品演进

```text
V0.1 MVP
    ↓
V0.2 能力画像与趋势
    ↓
V0.3 题库 + RAG
    ↓
V0.4 真实面试复盘
    ↓
V0.5+ 高级 AI 能力
```

### V0.1 MVP

建立完整 AI 面试训练闭环：

- 登录；
- 候选人、简历和项目经历；
- 职位与面试方向；
- 创建面试；
- 完整面试 / 指定方向面试；
- AI 出题；
- 文本回答；
- 动态追问；
- 单题多维评价；
- 完整面试复盘；
- 历史面试记录。

成功标准：

- 能完成约 30～45 分钟文本面试；
- 能根据回答进行动态追问；
- 能提供多维、可解释的评价；
- 能在面试结束后查看完整问题链、回答和逐题评价；
- 能从逐题评价中识别优势、问题与改进方向。

### V0.2

能力画像、维度趋势、历次面试对比、薄弱知识点、改进情况与 Interview Readiness Score。

### V0.3

题库、知识点、RAG、相似题、问题改写、混合检索与针对性训练。

### V0.4

真实面试记录、题目整理、回答复盘、AI 评价、薄弱点沉淀与后续训练。

### V0.5+

Multi-Agent、Memory、MCP、Skill、Deep Research、Evaluator Dataset、Prompt / Context / Harness / Loop Engineering、Voice Interview。

以上均不是 V0.1 前置条件。

## 技术演进

```text
传统后端
    ↓
LLM Application
    ↓
AI Workflow / Agent
    ↓
Evaluation
    ↓
RAG / Memory
    ↓
MCP / Skill
    ↓
Advanced AI Engineering
```

V0.1 重点体现：

- Java 后端工程；
- 模块化单体；
- LLM Integration；
- 短生命周期 Agent Workflow；
- Structured Output；
- Evaluation；
- 最小可追踪性。

## Phase

- Phase 0：项目定义 — 已完成；
- Phase 1：需求分析 — 已完成；
- Phase 2：技术选型与系统设计 — 已完成；
- Phase 3：MVP 实现 — 进行中；
- Phase 4：测试、验收与部署 — 待开始。

## Phase 3 原则

Phase 3 第一目标是打通完整面试主链路，而不是建设通用框架。

优先：

```text
创建 Interview
→ 生成第一题
→ 提交 Answer
→ Evaluate
→ Decision
→ 生成下一题或结束
→ 查看完整复盘
```

实现遵循：

- 只实现真实主链路需要的 Public API；
- 不机械地为每个模块套完整四层结构；
- 只让 Turn 使用 Graph；
- Context 先使用最近有限轮次和确定性裁剪；
- Prompt、Profile 和重试采用最小静态实现；
- 没有真实需求时不引入 Redis、MQ、RAG、Memory 或通用 AI Framework。

## 管理原则

Roadmap 是方向，不是刚性承诺。新增、延后或删除能力时更新路线图；架构级变更遵循文档治理与 ADR 流程。
