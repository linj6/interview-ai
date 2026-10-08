# ADR-007：MyBatis-Plus 持久层边界与模块归属

**状态：[已确认]**

## Context

业务访问从 `JdbcTemplate` 迁移到 MyBatis-Plus 后，初始实现让 Interview Application 直接依赖 Mapper 和 DO，
并将 Evaluation 的表映射与 Mapper 放在 interview 模块。这破坏了已确认的模块职责，也让简单 CRUD XML、
exists-before-insert 和状态更新影响行数检查分散在 Application 中。

## Decision

每个业务模块使用 `infrastructure.persistence.entity|mapper|repository`：Application 只依赖 Repository；Mapper
只由 Repository 调用；DO 不进入 Application 或跨模块契约。当前 MVP 不机械增加 `domain.repository` Port。

标准 CRUD 和简单条件查询使用 `BaseMapper` 与 Lambda Wrapper。XML 仅用于多表 Join、条件状态更新、乐观锁等
无法由基础 CRUD 清晰表达的 SQL，Java 代码不编写原始 SQL。

Evaluation 的 DO、Mapper 和 Repository 归 `interview-agent-evaluation`。Interview 通过最小
`interview-agent-evaluation-api` 查询 Profile 并创建正式 Evaluation，不依赖 Evaluation Infrastructure。

Answer 与 Evaluation 直接插入，由数据库唯一约束保证并发一致性；Repository 捕获重复键并转换为业务异常。
Interview 状态更新同时校验主键、期望旧状态、期望旧阶段和版本，递增版本并检查影响行数。

DO 使用 Lombok `@Getter` / `@Setter`，Service / Repository 使用构造器注入；不默认使用 `@Data`。

## Consequences

Evaluation Public API 按 profile/evaluation 业务能力拆分，保持无 Spring、MyBatis、
数据库驱动和实现模块依赖。状态流转 XML 仅接收枚举参数，不硬编码数据库状态字符串；
正式 Evaluation 创建参与 Interview Application 的 T2 事务。

- 模块归属、跨模块契约和依赖方向与总体架构一致；
- Application 不再感知 MyBatis-Plus、DO 或 SQL；
- 数据库约束而非预检查承担最终幂等与并发保障；
- 状态冲突和版本冲突可以被明确区分和记录；
- 新增一个最小 Evaluation API 子模块，但不引入通用 Repository Port、Factory 或 Adapter 框架。
