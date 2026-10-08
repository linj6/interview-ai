# ADR-004：Follow-up 使用普通 Question，Answer 不可修改

**状态：归档候选版**

## Context

追问仍然是面试问题，无需独立实体；历史回答应保持真实。

## Decision

Question 使用 `type=MAIN|FOLLOW_UP` 和 `parent_question_id`。

V0.1 的 `parent_question_id` 对 FOLLOW_UP 始终指向所属 MAIN Question；MAIN 的 parent 为空。
连续追问的内容可以基于当前 FOLLOW_UP 的回答生成，但关系仍为 MAIN + FOLLOW_UP 列表两层结构。

Question 状态： CREATED → WAITING_ANSWER → ANSWERED / SKIPPED。

Answer immutable，一个 Interview 中一个 Question 只有一个正式 Answer，使用
`UNIQUE(interview_id, question_id)`。应用不执行 exists-before-insert，数据库唯一约束负责并发一致性，
Repository 将重复键转换为明确业务异常。

## Consequences

Answer 的 `UNIQUE(interview_id, question_id)` 由数据库处理并发重复提交；Repository
将 DuplicateKeyException 转为明确业务错误，不进行 exists-before-insert 预检查。

问题按 MAIN + FOLLOW_UP 两层关系组织，历史回答保持完整；重新回答未来通过新的训练记录实现。
