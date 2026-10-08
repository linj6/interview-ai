# Interview Agent Java 开发与 API 规范

**文档版本：V1.0**  
**文档状态：[已确认]**  
**适用阶段：Phase 3 及后续阶段**

---

## 1. 规范目标

本规范用于统一 Interview Agent 的 Java API、错误处理、Controller、日志、参数和基础代码风格。

规范遵循：

- 优先满足项目业务和架构约束；
- 保持 MVP 实现简单；
- 避免为未来场景提前建设复杂抽象；
- 阿里巴巴 Java 开发规范作为参考基线；
- 本文档明确的项目规则优先于通用风格建议。

---

## 2. 统一 API 响应结构

所有普通 JSON 业务接口统一返回：

```java
public record Result<T>(
        Integer code,
        String message,
        T data,
        String requestId
) {
}
```

成功响应：

```json
{
  "code": 0,
  "message": "操作成功",
  "data": {},
  "requestId": "..."
}
```

字段说明：

| 字段 | 类型 | 说明 |
|---|---|---|
| `code` | `Integer` | 业务结果编码，`0` 表示成功，非 `0` 表示失败 |
| `message` | `String` | 面向调用方的稳定提示 |
| `data` | 泛型 `T` | 业务数据，无数据时为 `null` |
| `requestId` | `String` | 服务端生成的请求追踪 ID |

约束：

- 不增加重复表达成功状态的 `success` 字段；
- 不增加业务层 `status` 字段；
- 暂不增加 `timestamp` 字段；
- JSON 使用 camelCase；
- HTTP JSON 业务 ID 统一为规范正整数十进制字符串，范围为 1..Long.MAX_VALUE；Java 内部及数据库继续使用 Long/BIGINT，前端使用 string。新增 Project 的 id 可为 null，已有 ID 输入必须为字符串，数字 JSON ID 不再兼容。
- 业务 ID 的格式转换仅在 Web DTO/Jackson 边界完成，显式标记 ID 字段，不按字段名推断，不全局字符串化 Long/long；路径参数仍绑定 Long，URL 结构保持不变。
- durationSeconds 等非 ID Long、数量、错误码与分数保持原有 JSON Number。
- Controller 不直接返回 Domain Entity、DO、Mapper 对象或异常对象；
- 健康检查、文件下载、SSE 等特殊接口可遵循各自协议。

建议提供静态工厂方法：

```java
public static <T> Result<T> success(T data);

public static Result<Void> success();

public static Result<Void> failure(ErrorCode errorCode);

public static <T> Result<T> failure(
        ErrorCode errorCode,
        T data
);
```

---

## 3. 错误码模型

统一错误码接口：

```java
public interface ErrorCode {

    int getCode();

    String getMessage();
}
```

错误码使用数字，不使用字符串：

```text
0       成功
100xx   通用请求错误
110xx   认证授权
20xxx   User
21xxx   Candidate
22xxx   Position
23xxx   Interview
24xxx   Evaluation
25xxx   Report
50xxx   系统错误
90xxx   第三方服务
```

规则：

- 已发布错误码不得复用；
- 同一模块内按序递增；
- 错误码必须表达稳定业务语义；
- 枚举值必须有必要注释；
- 错误码消息不得包含 SQL、堆栈、API Key、Token 或底层异常原文；
- HTTP Status 与业务错误码同时使用，但两者职责分离。

---

## 4. 错误码模块组织

通用能力放入 `interview-agent-common`：

```text
Result
ErrorCode
BusinessException
CommonErrorCode
```

业务错误码由对应业务模块维护：

```text
interview-agent-interview
└── InterviewErrorCode

interview-agent-evaluation
└── EvaluationErrorCode
```

不将所有业务错误码集中放入 Common，避免 Common 依赖和了解所有业务模块。

### 4.1 通用错误码

当前只定义真实需要的通用错误码：

```text
0       SUCCESS

10001   PARAM_ERROR
10002   PARAM_MISSING
10003   PARAM_TYPE_ERROR
10004   REQUEST_METHOD_NOT_SUPPORT
10005   REQUEST_BODY_MISSING

11001   UNAUTHORIZED
11002   FORBIDDEN

50001   SYSTEM_ERROR
50002   SERVICE_UNAVAILABLE
50003   DB_ERROR
50004   TIMEOUT
50005   RATE_LIMIT

90001   THIRD_PARTY_ERROR
90002   AI_SERVICE_ERROR
90003   AI_SERVICE_TIMEOUT
90004   AI_RESPONSE_INVALID
```

尚未实现的认证、Token、短信能力错误码暂不提前加入。

### 4.2 Interview 错误码示例

```text
23001   INTERVIEW_NOT_FOUND
23002   INTERVIEW_STATE_CONFLICT
23003   QUESTION_NOT_FOUND
23004   QUESTION_INTERVIEW_MISMATCH
23005   ANSWER_ALREADY_EXISTS
```

### 4.3 Evaluation 错误码示例

```text
24001   EVALUATION_PROPOSAL_INVALID
24002   EVALUATION_NOT_FOUND
24003   EVALUATION_CALCULATION_ERROR
```

---

## 5. BusinessException

业务模块通过业务异常表达业务失败：

```java
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
```

业务代码示例：

```java
throw new BusinessException(
        InterviewErrorCode.INTERVIEW_NOT_FOUND
);
```

动态消息只有在必要时使用，并且不得泄露底层异常或敏感信息。

---

## 6. 全局异常处理

通过 `@RestControllerAdvice` 统一处理异常：

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
}
```

至少处理：

- `BusinessException`；
- Bean Validation 参数异常；
- 参数类型转换异常；
- 请求方法不支持异常；
- 请求体缺失异常；
- 数据库异常；
- AI / 第三方调用异常；
- 未知异常。

异常响应统一使用 `Result`：

```json
{
  "code": 23001,
  "message": "Interview 不存在",
  "data": null,
  "requestId": "..."
}
```

参数错误采用简化结构：

```json
{
  "code": 10001,
  "message": "参数校验失败：content 不能为空",
  "data": null,
  "requestId": "..."
}
```

MVP 暂不定义 `ValidationErrorData`、`FieldViolation` 或多字段错误列表。

---

## 7. HTTP Status 映射

HTTP Status 由全局异常处理器负责，不放入 `ErrorCode` 接口或枚举。

建议映射：

| 场景 | HTTP Status |
|---|---:|
| 成功 | 200 |
| 参数错误 | 400 |
| 未授权 | 401 |
| 无权限 | 403 |
| 资源不存在 | 404 |
| 状态冲突、重复提交 | 409 |
| Proposal 业务校验失败 | 422 |
| AI 服务异常 | 502 / 503 |
| 数据库异常 | 500 |
| 未知异常 | 500 |

业务错误码表达业务语义，HTTP Status 表达 HTTP 协议层结果。

---

## 8. requestId

`requestId` 只能由服务端生成，不允许前端透传。

处理流程：

```text
请求进入
    ↓
服务端生成 requestId
    ↓
写入 MDC
    ↓
业务日志携带 requestId
    ↓
写入响应 Header 和 Result Body
    ↓
请求结束后清理 MDC
```

规则：

- 客户端传入的 `X-Request-Id` 直接忽略；
- 服务端生成的 requestId 同时写入响应 Header `X-Request-Id`；
- 服务端生成的 requestId 同时写入 `Result.requestId`；
- 请求结束后必须清理 MDC；
- requestId 不作为认证凭证；
- MVP 使用 `OncePerRequestFilter + MDC`；
- 暂不引入复杂分布式链路追踪系统。

---

## 9. Controller 参数

Controller 请求参数禁止使用原始数据类型：

不推荐：

```java
@PathVariable long interviewId
```

推荐：

```java
@PathVariable Long interviewId
```

规则：

- ID 使用包装类型；
- Request DTO 字段优先使用包装类型；
- 必填参数使用 Bean Validation；
- 数值 ID 使用 `@Positive`；
- 可为空字段不得使用原始类型；
- 该规则主要约束 Controller 入参、Request DTO 和跨模块 DTO；
- 除明确需要原始类型的局部计算外，优先使用原始数据类型的包装类型；原始类型具有默认值，无法表达 `null`，容易掩盖缺失数据；
- 领域内部明确非空的局部变量可以根据语义使用原始类型。

---

## 10. Java 开发规范采用范围

阿里巴巴 Java 开发规范作为参考基线，本项目自定义规范作为最终执行依据。

### 10.1 强制规则

- Controller 保持薄层，不编排核心业务；
- Controller 不直接调用 LLM、Prompt 或 Graph；
- Controller 统一返回 `Result<T>`；
- 业务失败使用业务错误码；
- 使用全局异常处理；
- 不向客户端返回异常堆栈和底层错误原文；
- Controller 请求参数使用包装类型；
- 不使用魔法数字和魔法字符串；
- 枚举值必须有必要注释；
- DO 字段必须有必要 JavaDoc；
- 数据库表和字段必须有 SQL COMMENT；
- 关键业务逻辑必须有自动化测试；
- 关键流程必须记录结构化日志；
- 日志不得泄露敏感信息；
- 不允许空 catch 或静默吞掉异常；
- 数据库事务不得跨越 LLM 调用；
- Answer 等不可变历史事实不得被原地修改；
- 业务持久化使用 MyBatis-Plus；Application 只依赖 Repository，不直接依赖 Mapper、DO 或 Wrapper；
- Mapper 继承 `BaseMapper` 且只由 Repository 调用；标准 CRUD 和简单 Lambda 查询不新增 XML；
- 自定义 SQL 必须放在 Mapper XML 中，不得在 Java 或 Mapper 注解中编写 SQL；
- Answer / Evaluation 直接插入并由唯一约束保证并发一致性，不使用 exists-before-insert；
- 状态更新必须检查影响行数，并区分不存在、状态冲突和版本冲突；
- DO 使用 Lombok `@Getter` / `@Setter`，Service / Repository 推荐 `@RequiredArgsConstructor`、`@Slf4j`；
- 不默认使用 Lombok `@Data`，禁止字段注入。

### 10.2 推荐规则

- 优先使用 `private final`；
- 优先使用不可变 DTO；
- 简单不可变 DTO 可以使用 `record`；
- 方法职责保持清晰；
- 避免过深嵌套；
- 日志使用参数化写法；
- 使用参数校验代替重复手工校验；
- 公共方法和复杂业务逻辑增加必要注释；
- 结构化日志包含必要的业务 ID 和 requestId。

### 10.3 暂不强制

- 全量接入 Checkstyle、PMD 或 SpotBugs；
- 对所有方法强制行数限制；
- 所有业务模块机械使用完整四层结构；
- 为每个 Use Case 创建独立 Service；
- 为每个错误码创建独立异常类；
- 提前建设通用异常、重试或校验框架。

---

## 11. 注释与日志

注释必须解释业务含义、约束或设计原因，不为显而易见的代码添加无价值注释。

以下逻辑必须有必要注释：

- 状态流转；
- 事务边界；
- 幂等和 Version 校验；
- Evaluation 失败恢复；
- N/A 权重归一化；
- 分数精度与舍入；
- 错误码业务语义。

关键日志至少包含：

```text
requestId
interviewId
questionId
answerId（如已生成）
```

日志级别：

```text
INFO：关键业务流程
WARN：可恢复失败、重试、状态不一致
ERROR：最终失败、提交失败、恢复失败
DEBUG：开发调试细节
```

禁止记录：

- Answer 原文；
- API Key；
- Token；
- 密码；
- 其他敏感信息。
