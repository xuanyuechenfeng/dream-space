# 09 配置文件与一级目录说明

## 9.1 配置文件职责

| 文件 | 归属 | 开发用途 | 生产注意事项 |
| --- | --- | --- | --- |
| `dream_service/pom.xml` | 后端父工程 | 锁定 Java、Spring Boot、Spring AI、MyBatis、Redis 和测试依赖版本 | 只在父工程升级 BOM，不在子模块重复声明版本 |
| `dream_service/api/pom.xml` | API | MVC、Validation、Security、Actuator 和 common 依赖 | API 不引入模型供应商 SDK |
| `dream_service/worker/pom.xml` | Worker | Spring AI、Redis consumer、图像处理和 common 依赖 | Worker 使用非 Web profile，单独扩容 |
| `dream_service/common/pom.xml` | 公共层 | MyBatis、JDBC、PostgreSQL、Redis、SFTP、Testcontainers | 不放 Controller 和页面逻辑 |
| `dream_service/api/src/main/resources/application.yml` | API | HTTP 端口、数据源、Redis、对象存储、认证和额度配置 | 密码和外部 URL 使用环境变量覆盖默认值 |
| `dream_service/worker/src/main/resources/application.yml` | Worker | 非 Web 启动、Spring AI OpenAI-compatible ChatModel、队列和存储配置 | `OPENAI_API_KEY` 必须由 Secret 注入 |
| `dream_web/vite.config.ts` | 用户端 | Vite 入口、`/api` 代理、端口 3000 和别名 | 生产构建不把本地代理配置带入静态资源 |
| `manage_web/vite.config.ts` | 管理端 | Vite 入口、`/api` 代理、端口 3001 和别名 | 管理端 origin 必须单独配置 |
| `dream_web/.env.example` | 用户端 | 仅声明 `VITE_API_PROXY_TARGET` 示例 | 不写真实 token、Cookie 或生产地址 |
| `manage_web/.env.example` | 管理端 | 仅声明 `VITE_API_PROXY_TARGET` 示例 | 同上 |
| `dream_web/package-lock.json`、`manage_web/package-lock.json` | 前端 | 两个独立工程的依赖锁定 | 分别在对应目录执行 `npm ci` |
| `dream_service/common/src/main/resources/db/migration/` | 数据库 | 版本化 SQL 迁移 | 迁移只增不改；生产执行前做备份 |

## 9.2 后端环境变量

| 变量 | 默认/示例 | 必填场景 | 说明 |
| --- | --- | --- | --- |
| `API_PORT` | `4000` | API | HTTP 端口 |
| `DATABASE_JDBC_URL` | `jdbc:postgresql://localhost:5432/dream_space` | API/Worker | Spring datasource URL，优先于兼容变量 `DATABASE_URL` |
| `DATABASE_USER` | `dream_space` | API/Worker | 数据库账号 |
| `DATABASE_PASSWORD` | 空 | 非本地环境 | 数据库密码，使用 Secret |
| `DATABASE_URL` | 空 | API/Worker 兼容部署 | `DATABASE_JDBC_URL` 未设置时作为 Spring datasource URL |
| `REDIS_URL` | `redis://localhost:6379` | API/Worker | Stream 与限流共用连接 |
| `REDIS_RECLAIM_IDLE` | `PT30S` | Worker | pending 消息 reclaim 空闲时间 |
| `OBJECT_STORAGE_MODE` | `local` | API/Worker | `local` 或 `sftp` |
| `LOCAL_STORAGE_DIR` | `D:/softDesign/dream-space/storage` | local | API 与 Worker 单机部署时必须指向同一对象根目录 |
| `SFTP_HOST` / `SFTP_PORT` | 空 / `22` | sftp | SFTP 服务器地址和端口 |
| `SFTP_USERNAME` / `SFTP_PASSWORD` | 空 | sftp | 用户名和密码；密码或私钥至少配置一项 |
| `SFTP_PRIVATE_KEY_FILE` / `SFTP_PRIVATE_KEY_PASSPHRASE` | 空 | sftp | 可选私钥认证 |
| `SFTP_KNOWN_HOSTS_FILE` | 空 | sftp 严格校验 | SSH known_hosts 文件路径 |
| `SFTP_STRICT_HOST_KEY_CHECKING` | `true` | sftp | 是否启用主机密钥校验 |
| `SFTP_ROOT_DIRECTORY` | `/dream-space` | sftp | 远程对象根目录 |
| `SFTP_CONNECT_TIMEOUT` / `SFTP_OPERATION_TIMEOUT` | `PT10S` / `PT60S` | sftp | 连接和单次操作超时 |
| `SFTP_MAX_ATTEMPTS` | `3` | sftp | SFTP 操作最大重试次数 |
| `COOKIE_SECURE` | `false` | API | 是否为用户和管理员会话 Cookie 设置 `Secure` 属性；HTTPS 部署必须设为 `true` |
| `OPENAI_BASE_URL` | `http://localhost:8089` | Worker | OpenAI-compatible `/chat/completions` 地址 |
| `OPENAI_API_KEY` | 不提交 | Worker | 模型密钥 |
| `OPENAI_MODEL` | 无默认值，必须由环境变量提供 | Worker | 规划/评估模型名称 |
| `OPENAI_TIMEOUT_MS` | `30000` | Worker | 单次模型请求超时 |
| `OPENAI_MAX_ATTEMPTS` | `3` | Worker | 任务最大尝试次数 |
| `QUEUE_MAX_ATTEMPTS` | `3` | API/Worker | 队列消息最大尝试次数 |
| `QUEUE_RETRY_BACKOFF` | `PT0.5S` | Worker | 指数退避基准 |
| `AUTH_CODE_TTL_SECONDS` | `300` | API | 验证码有效期 |
| `AUTH_SESSION_DAYS` | `30` | API | 用户/管理员 Cookie session 有效期 |
| `QUOTA_INITIAL_TOTAL` | `100` | API | 新用户初始额度 |

环境变量加载优先级为：密钥管理系统/容器环境 > `.env.local`（仅开发）> `application.yml` 默认值。任何密钥都不得出现在 Git、测试 fixture、截图、异常响应或日志中。

## 9.3 一级目录和关键文件

| 路径 | 内容 | 变更规则 |
| --- | --- | --- |
| `docs/` | 概要、详细设计、迁移基线和知识库 | 设计变更先改文档再改代码 |
| `docs/design/` | 本次重构可直接分配的详细设计分册 | 每个新增模块补接口、测试、回滚章节 |
| `docs/knowledge/` | 当前系统和重构概要知识库 | 只记录已验证事实和决策 |
| `docs/migration-baselines/` | 路由、视觉、HTTP、数据契约基线 | `bak/` 变化需重新核对 |
| `bak/` | 原 Node/Next 实现，只读参考和回滚版本 | 禁止直接修改 |
| `dream_web/` | 独立 Vue 3 + TypeScript + Vite 5 用户端工程 | 独立安装、构建、测试和部署；页面 CSS 以 `bak/apps/web` 为视觉基线 |
| `manage_web/` | 独立 Vue 3 + TypeScript + Vite 5 管理端工程 | 独立安装、构建、测试和部署；授权由 API 决定 |
| `dream_service/common/` | 跨 API/Worker 的版本、状态、错误和契约模型 | 不依赖数据库或外部网络 |
| `dream_service/api/` | Spring MVC Controller、鉴权、同步事务、SSE 和队列 producer | 不直接调用 `ChatModel` |
| `dream_service/worker/` | Stream consumer、模型适配、图像管线、结果持久化和对账 | 不暴露用户 API |
| `dream_service/mvnw` / `dream_service/mvnw.cmd` | Maven Wrapper | CI 和本地统一使用 Wrapper |
| `.trellis/` | 开发任务状态和验收记录 | 不把凭据写入任务记录 |
| `.gitignore` | 构建产物、缓存、环境文件和 Trellis 运行文件 | `.m2repo/`、`node_modules/` 不得提交 |

## 9.4 本地启动顺序

1. 启动 PostgreSQL、Redis；使用 `local` 模式时准备共享本地目录，使用 `sftp` 模式时准备 SFTP 目录和凭据；执行 `dream_service/common` 迁移。
2. 启动 API：`dream_service/mvnw.cmd -pl api -am spring-boot:run`，确认 `/health/live` 和 `/health/ready`。
3. 启动 Worker：`dream_service/mvnw.cmd -pl worker -am spring-boot:run`，确认 Stream consumer 已加入 `generation-workers`。
4. 分别在 `dream_web/`、`manage_web/` 安装依赖并启动；浏览器通过 Vite 代理访问 API。
5. API 不再提供演示验证码；短信供应商未配置时验证码接口明确返回服务未配置。Worker 必须显式配置真实规划、评估和图片模型。
