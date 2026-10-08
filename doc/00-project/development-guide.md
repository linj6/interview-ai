# Interview Agent 本地开发、测试与验收指南

> 文档状态：已确认
>
> 适用范围：当前 v0.1 Backend / Frontend 本地运行、Create → Prepare → Start 与 Turn 验证；本文维护操作方式，不记录 Task 进度。
>
> 本文用于本地开发、自动化测试和人工验收；架构与业务规则仍以 `doc/` 中对应的正式设计文档为准。

## 1. 开发环境

从干净源码运行完整三服务部署见 [Docker 一键部署](docker-deployment.md)；以下为宿主工具开发和测试方式。

- JDK 17 或更高版本
- Maven 3.9 及以上
- Docker Desktop（供 Testcontainers 运行自动化测试）
- 本地 MySQL 8.x（供本地启动应用与人工验收）
- Windows 用户建议在 PowerShell 中执行本文命令。

先确认基础工具可用：

```powershell
java -version
mvn -version
docker version
```

执行自动化测试前，应先启动 Docker Desktop 并等待 Docker Engine 就绪。测试会自行创建临时 MySQL 容器，不依赖本地 3306 端口的 MySQL 服务。

## 2. 本地 MySQL 与应用启动

本地启动应用时，需要一个可连接的 MySQL 数据库。以下命令仅为本地示例；密码必须替换为自己的本地安全值，不能提交到仓库。

```powershell
docker run --name interview-agent-mysql `
  -e TZ=Asia/Shanghai `
  -e MYSQL_DATABASE=interview_agent `
  -e MYSQL_USER=interview_agent `
  -e MYSQL_PASSWORD=replace-with-local-password `
  -e MYSQL_ROOT_PASSWORD=replace-with-root-password `
  -p 127.0.0.1:3306:3306 `
  -d mysql:8.4 `
  --default-time-zone="+08:00"
```

后续再次使用同一容器时：

```powershell
docker start interview-agent-mysql
```

在同一个 PowerShell 窗口设置数据库连接信息：

```powershell
$env:DB_URL = "jdbc:mysql://localhost:3306/interview_agent?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
$env:DB_USERNAME = "interview_agent"
$env:DB_PASSWORD = "replace-with-local-password"
```

启动应用前，先安装 bootstrap 模块依赖的工程模块（不执行测试）；再直接以 bootstrap 模块的 `pom.xml` 运行 Spring Boot 插件。这样可避免从聚合工程执行时无法定位启动类。

```powershell
mvn -pl interview-agent-bootstrap -am install -DskipTests
mvn -f interview-agent-bootstrap/pom.xml spring-boot:run
```

应用启动时 Flyway 会自动执行尚未应用的 Migration。通过健康检查确认应用可用：

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
```

预期响应中的 `status` 为 `UP`。默认 Spring profile 关闭真实 LLM，健康检查成功不表示 AI 面试可运行；体验面试需按第 5 节显式使用 local profile。前端运行和 Node 版本要求见 [Frontend README](../../frontend/README.md)，在 frontend/ 执行 npm ci / npm run dev，开发服务器代理 /api 到 Backend。

如需检查 Migration 记录，可在本地 MySQL 中查询 `flyway_schema_history`。当前全新空库应成功应用 V1 至 V12。V12 删除 question.focus，保留历史可空 plan_item_id，不回填 PlanItem。历史无 Plan/关联数据不能继续 V1 生成路径，兼容边界见 [数据模型](../04-data/data-model.md)。既有库如有旧版本 checksum 或失败记录，应先核对实际 Migration 状态，不自动 repair 或清理数据。

```powershell
docker exec -it interview-agent-mysql mysql -uinterview_agent -p interview_agent -e "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;"
```

## 3. 自动化测试

在仓库根目录执行：

```powershell
mvn clean verify
```

该命令运行默认测试集，其中数据库集成测试通过 Testcontainers 启动临时 MySQL。默认测试不调用真实 LLM，也不需要配置 DashScope API Key。

持久化边界与 MyBatis-Plus 使用方式见 [`architecture.md`](../02-architecture/architecture.md)、[`module-structure.md`](../02-architecture/module-structure.md) 和 [ADR-007](../adr/ADR-007-mybatis-plus-persistence-boundary.md)。

### Spotless 常用命令

验证范围和执行时机遵循仓库根目录 `AGENTS.md`。Spotless 已绑定到 Maven `validate` 阶段；运行 `mvn test` 或 `mvn clean verify` 时也会执行格式检查。

```powershell
mvn -pl interview-agent-interview spotless:apply
mvn -pl interview-agent-interview spotless:check
```

多个模块可以用逗号传给 `-pl`，例如 `mvn -pl interview-agent-interview,interview-agent-ai spotless:check`。

若出现 `Could not find a valid Docker environment`，请确认 Docker Desktop 已启动、Docker Engine 已就绪，并在终端重新执行 `docker version` 后再运行测试。

## 4. Maven LLM Smoke 验证

Maven 的 llm-smoke profile 用于显式执行真实 LLM 集成测试，不是 Spring runtime profile。测试按自身配置使用 local 等 Spring profile；无 API Key 时真实调用测试可条件跳过，BUILD SUCCESS 本身不代表模型连通已验证。默认 mvn clean verify 不调用真实模型，不产生模型费用。

显式验证时在 PowerShell 设置当前窗口环境变量并执行 verify；真实调用会产生模型费用：

```powershell
$env:AI_DASHSCOPE_API_KEY = "设置你的 DashScope API Key"
$env:AI_DASHSCOPE_CHAT_MODEL = "qwen-plus"

mvn -Pllm-smoke -pl interview-agent-bootstrap -am verify
```

注意：`set AI_DASHSCOPE_API_KEY=...` 是 `cmd.exe` 的写法，在 PowerShell 中不会按预期设置环境变量。不要将 API Key 写入 `application.yml`、提交记录或任何仓库文件。

检查 LlmConnectivityIT / RealEvaluatorPersistenceIT 是否实际执行及其结果，并在百炼工作台确认调用。仅 package -DskipTests 不执行 Smoke Test，也不能证明连通。

## 5. 本地真实 LLM 运行与 Create → Prepare → Start

先设置第 2 节数据库环境变量与第 4 节 LLM 环境变量，以普通 Maven 构建生成 Fat JAR，并显式选择 Spring local profile 启动。local / prod 都通过 app.ai.real-llm-enabled=true 启用真实 DashScope 实现，模型参数从环境变量注入；默认 profile 为 false。运行真实面试会产生模型费用。

Maven llm-smoke 是自动化 Smoke 验证的 opt-in；手动运行的 opt-in 是显式选择 Spring local/prod 与设置模型环境变量，打包无需 llm-smoke。prod 是运行配置，不代表项目已完成公开部署或多用户访问控制。

```powershell
mvn package -pl interview-agent-bootstrap -am -DskipTests
java -jar interview-agent-bootstrap\target\interview-agent-bootstrap-0.1.0-SNAPSHOT.jar `
  --spring.profiles.active=local
```

另开一个 PowerShell 窗口，依次执行：

```powershell
$created = Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/interviews -ContentType "application/json" -Body '{"mode":"COMPREHENSIVE"}'
$created

$id = $created.data.id
$prepared = Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/interviews/$id/prepare"
$prepared
$detail = Invoke-RestMethod -Uri "http://localhost:8080/api/interviews/$id"
if ($detail.data.planPrepared -ne $true) { throw "Interview Plan 尚未准备成功" }
$started = Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/interviews/$id/start"
$started

Invoke-RestMethod -Uri "http://localhost:8080/api/interviews/$id"
```

验收要点：

1. 创建结果为 CREATED、planPrepared=false；Prepare 成功持久化 Plan 后仍为 CREATED，GET 返回 planPrepared=true。仅 TECHNICAL_FOCUS 额外提供合法 focusArea，枚举和组合规则见 [当前需求 Contract](../01-requirements/requirements.md#question-quality-v1-配置与-http-contract当前有效)。
2. 启动成功后，面试状态为 `IN_PROGRESS`，当前阶段为 `WAITING_ANSWER`。
3. 返回的首题为 `MAIN` 类型、`WAITING_ANSWER` 状态，题目文本非空。
4. 查询接口能返回与启动结果一致的面试和首题。
5. 在百炼工作台能看到 Prepare/Opening 的实际模型调用；成功 Prepare 后不得再次 Prepare，也不自动 Start。
6. 对同一面试重复调用启动接口，应被拒绝，且不应生成第二道首题。

Prepare 失败不持久化 Plan，可重新 Prepare；Opening 失败恢复 CREATED 并保留 Plan，可重新 Start。写请求失败或响应不确定时，先 GET 对账再决定重试，避免自动重放 POST。本文给出验证操作，不宣称 V1 真实完整链路已通过；当前验收边界见 [PROJECT_CONTEXT](../PROJECT_CONTEXT.md)。

## 6. Answer / Turn 与复盘验证

当前回答提交接口：

```text
POST /api/interviews/{interviewId}/questions/{questionId}/answer
```

真实 Evaluator 的自动化 Smoke 验证是显式 opt-in，并会产生模型费用。先以安全环境变量提供 API Key，再运行：

```powershell
$env:AI_DASHSCOPE_API_KEY = "设置你的 DashScope API Key"
$env:AI_DASHSCOPE_CHAT_MODEL = "qwen-plus"
mvn -Pllm-smoke -pl interview-agent-bootstrap -am verify
```

RealEvaluatorPersistenceIT 使用临时 MySQL，准备正式 Plan 关联及 WAITING_ANSWER Question，经 Answer 接口调用真实模型并验证评价持久化。它不是浏览器 Create → Prepare → Start 全流程验收；无 API Key 时条件跳过。

本地手工调用时，在完成第 5 节创建、准备与启动后执行：

```powershell
$questionId = $started.data.firstQuestion.id
$body = @{ content = "在这里填写候选人的回答" } | ConvertTo-Json
Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8080/api/interviews/$id/questions/$questionId/answer" `
  -ContentType "application/json" `
  -Body $body
```

成功后确认返回 data.answer / data.evaluation，原 Question 为 ANSWERED；GET Interview 确认后续状态：FOLLOW_UP/NEXT_QUESTION 进入 IN_PROGRESS + WAITING_ANSWER 并返回新 currentQuestion，END 进入 COMPLETED 且 currentPhase/currentQuestion=null。PROCESSING_TURN 是 T1 后、T2 成功前的阶段，不是成功 Turn 的停留状态。总分由 Java BigDecimal 计算，前端在完成后的 Review 展示评价。

```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/interviews/$id"
```

Turn 最终失败时保留 Answer 与 PROCESSING_TURN；用户先读取状态，符合恢复前提时 POST /api/interviews/{id}/retry 重跑完整 Turn，不重发 Answer。Pause/Resume、Skip、Finish 与截止时间的合法性由 Backend 决定；完整复盘只允许 COMPLETED 后 GET /api/interviews/{id}/review。Review 是已有事实的只读聚合，不生成整场 AI 报告或综合分。

## 7. API 响应与 requestId

普通 JSON 业务接口统一返回：

```json
{
  "code": 0,
  "message": "操作成功",
  "data": {},
  "requestId": "服务端生成的 UUID"
}
```

失败响应同样使用该结构；HTTP Status 与 `code` 分别表达协议层结果和业务语义。服务端会忽略客户端传入的 `X-Request-Id`，重新生成 requestId，并同时写入响应 Header、响应体和 MDC。健康检查等特殊接口不适用普通业务响应包装。

## 8. 常见问题

| 现象 | 处理方式 |
| --- | --- |
| Testcontainers 报 Docker 环境不可用 | 启动 Docker Desktop，确认 `docker version` 能显示 Client 和 Server 信息后重试。 |
| 应用启动时数据库连接失败 | 检查 MySQL 容器是否运行、`DB_URL`/`DB_USERNAME`/`DB_PASSWORD` 是否在当前终端设置。 |
| 启动时报 Mapper statement 未找到 | 检查 XML 是否位于模块的 `src/main/resources/mapper/` 下，且 namespace 与 Mapper 完整类名一致。 |
| DashScope 提示 API Key 未设置 | 在 PowerShell 使用 `$env:AI_DASHSCOPE_API_KEY` 设置，并在同一终端执行 Maven 命令。 |
| 3306 或 8080 端口被占用 | 释放占用进程，或按本地需要调整数据库 URL / Spring Boot 端口。 |

## 9. 维护规则

- 新 Slice 引入新的本地依赖、构建命令、自动化测试方式或人工验收路径时，应在同一 PR 更新本文。
- 不在本文复制架构决策；架构变化必须先按 `doc/00-project/documentation-guide.md` 的治理流程更新正式设计文档。
- 示例仅使用安全占位符，禁止写入 API Key、数据库密码或其他敏感信息。
