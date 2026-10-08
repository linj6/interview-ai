# Docker 一键部署

**状态：[已确认]**：Public Release Task 3.2 按用户确认的最小范围实现。

## 部署边界

从源码构建三个服务：Frontend（Nginx 静态文件与同源 `/api` 代理）、Backend（模块化单体 Fat JAR）、MySQL 8.4。仅 Frontend 发布宿主端口，默认绑定 `127.0.0.1`；数据库使用 Compose 项目独立的 `mysql-data` named volume。无需宿主 JDK、Maven、Node 或 MySQL，需要 Docker Engine、Docker Compose，以及可访问镜像仓库、Maven 仓库、npm registry 的网络。

Backend 使用 Maven/JDK 17 构建、JRE 17 运行，Frontend 使用 Node 22 构建、Nginx 1.28 运行。Windows 检出的 Java/XML 可能使用 CRLF，Docker 构建仅将容器内副本规范为 LF 后执行原有 Maven/Spotless 检查，不改宿主源码，也不跳过格式检查。

Backend Docker 构建通过 `--settings /opt/maven-settings.xml` 显式使用仓库中的 `docker/maven/settings.xml`，将 Maven Central（`central`）请求转向阿里云 Maven Public 镜像 `https://maven.aliyun.com/repository/public`。此配置仅用于 Docker 构建，不修改宿主 Maven 配置。

Backend 使用 `prod`，通过 `mysql:3306` 连接数据库。Flyway 自动应用当前 V1–V12，并初始化演示候选人、职位和评价规则；无需额外 SQL 导入。MySQL、JDBC、JVM 使用 Asia/Shanghai（数据库 server time zone 为 `+08:00`）。模型调用需要 Backend 出站访问 DashScope。

本部署面向单用户个人实例，支持维护 Personal Profile 并完成文本面试闭环；无登录认证、无多用户数据隔离，不适合直接匿名公网开放，不代表公网生产服务已经验收。业务范围及 Question Quality V1 验收状态仍见 [PROJECT_CONTEXT](../PROJECT_CONTEXT.md) 和 [Phase 3](../progress/phase-3.md)。

## 配置与启动

在仓库根目录复制示例：

```powershell
Copy-Item .env.example .env
```

编辑 `.env`，填写不同的 `DB_PASSWORD`、`MYSQL_ROOT_PASSWORD` 以及有效的 `AI_DASHSCOPE_API_KEY`。`AI_DASHSCOPE_CHAT_MODEL` 默认 `qwen-plus`；`FRONTEND_PORT` 默认 `8080`，端口冲突时更改该值。密码和 Key 只进入对应服务的运行环境，不作为 build arg、不进入前端或镜像。包含 `$` 或 `#` 等字符的值建议使用单引号包围，避免 Compose 插值或注释解析。`.env` 已由 Git 忽略，两个构建上下文采用允许列表排除本地秘密和产物。

Compose 固定数据库名和应用用户名为 `interview_agent`，生成内部 `DB_URL`；示例中的本地 `DB_URL`/`DB_USERNAME` 只供手工本地启动，Compose 不使用它们。终端已有的同名环境变量优先于 `.env`，部署前应核对这些变量。

```powershell
docker compose config --quiet
docker compose up --build -d
docker compose ps
docker compose logs --tail 100 backend
```

完整的 `docker compose config` 输出包含运行秘密，检查时不要分享原始输出。首次构建需要下载依赖；启动顺序为 MySQL 健康 → Backend 启动及迁移完成并健康 → Frontend。三个服务健康后打开 `http://localhost:8080`（或配置的端口）。健康检查不调用模型，不能证明 Key 有效。

前端 `/api/*` 原样转发到 Backend，不删除 `/api`，API 错误不参与 SPA fallback；Session / Review 刷新回退到 `index.html`。代理连接超时 10 秒，发送/读取超时各 600 秒，为同步多步骤 LLM 调用留出时间；不自动重放业务 POST。超时或断网不代表业务未提交，应按现有 UI 先读取状态再决定是否手动重试。代理通过 Docker DNS 重新解析 Backend，支持容器重建后的地址变化。

Compose 已固定使用 `prod`；配置有效 Key 并在界面操作真实面试属于人工运行 opt-in，会产生模型费用。独立 Demo 已验证 Create → Prepare → Start → Submit → FOLLOW_UP → Finish → Review；追问由系统根据本轮结果决定，并非每次提交必然触发。验证记录见 [Phase 3](../progress/phase-3.md#public-release-task-1demo-截图与-readme-集成2026-10-08)，AI 问题与评价质量仍具有不确定性。自动化真实模型 Smoke 仍仅使用 [开发指南](development-guide.md) 约定的 `llm-smoke`，默认构建不调用模型。

## 状态、日志与停止

```powershell
docker compose ps
docker compose logs --tail 100
docker compose logs -f backend
docker compose down
docker compose up -d
```

`down` 删除容器和项目网络，保留数据库 volume；再次 `up` 使用原有数据。修改源码后执行 `docker compose up --build -d`。也可执行 `docker compose up -d --force-recreate` 重建容器而保留数据库。已有 volume 的 MySQL 初始化环境变量不会自动修改已创建的数据库用户密码，修改凭据需与现有数据库一致，不能靠重建容器完成密码轮换。

仅当明确要删除该 Compose 项目的所有数据库事实并重新初始化时执行：

```powershell
docker compose down --volumes
```

该命令不可恢复地删除项目 named volume 内的 Interview、Plan、Answer、Evaluation 等数据。不要用它修复既有 Migration 失败，不自动执行 Flyway repair，也不要复用本地开发库 volume。隔离验收可用 `docker compose -p <独立项目名>`，并为 Frontend 选择空闲端口；每条命令保持相同项目名。
