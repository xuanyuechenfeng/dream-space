# 09 配置文件与一级目录说明

## 9.1 配置文件职责

| 文件 | 归属 | 开发用途 | 生产注意事项 |
| --- | --- | --- | --- |
| `backend/pom.xml` | 后端父工程 | 锁定 Java、Spring Boot、Spring AI、MyBatis、Redis、S3 和测试依赖版本 | 只在父工程升级 BOM，不在子模块重复声明版本 |
| `backend/api/pom.xml` | API | MVC、Validation、Security、Actuator 和 persistence 依赖 | API 不引入模型供应商 SDK |
| `backend/worker/pom.xml` | Worker | Spring AI、Redis consumer、图像处理和 persistence 依赖 | Worker 使用非 Web profile，单独扩容 |
| `backend/persistence/pom.xml` | 持久化 | MyBatis、JDBC、PostgreSQL、Redis、S3、Testcontainers | 不放 Controller 和页面逻辑 |
| `backend/api/src/main/resources/application.yml` | API | HTTP 端口、数据源、Redis、对象存储、认证和额度配置 | 密码和外部 URL 使用环境变量覆盖默认值 |
| `backend/worker/src/main/resources/application.yml` | Worker | 非 Web 启动、Spring AI OpenAI-compatible ChatModel、队列和存储配置 | `OPENAI_API_KEY` 必须由 Secret 注入 |
| `frontend/web/vite.config.ts` | 用户端 | Vite 入口、`/api` 代理、端口 3000 和别名 | 生产构建不把本地代理配置带入静态资源 |
| `frontend/admin/vite.config.ts` | 管理端 | Vite 入口、`/api` 代理、端口 3001 和别名 | 管理端 origin 必须单独配置 |
| `frontend/web/.env.example` | 用户端 | 仅声明 `VITE_API_PROXY_TARGET` 示例 | 不写真实 token、Cookie 或生产地址 |
| `frontend/admin/.env.example` | 管理端 | 仅声明 `VITE_API_PROXY_TARGET` 示例 | 同上 |
| `frontend/pnpm-workspace.yaml` | 前端 | 管理 web/admin 两个 workspace | CI 使用锁文件安装，不更新锁文件外的依赖 |
| `backend/persistence/src/main/resources/db/migration/` | 数据库 | 版本化 SQL 迁移 | 迁移只增不改；生产执行前做备份 |

## 9.2 后端环境变量

| 变量 | 默认/示例 | 必填场景 | 说明 |
| --- | --- | --- | --- |
| `API_PORT` | `4000` | API | HTTP 端口 |
| `DATABASE_JDBC_URL` | `jdbc:postgresql://localhost:5432/dream_space` | API/Worker | Spring datasource URL |
| `DATABASE_USER` | `dream_space` | API/Worker | 数据库账号 |
| `DATABASE_PASSWORD` | 空 | 非本地环境 | 数据库密码，使用 Secret |
| `DATABASE_URL` | 空 | persistence 迁移/探针 | 业务配置中的数据库地址记录 |
| `REDIS_URL` | `redis://localhost:6379` | API/Worker | Stream 与限流共用连接 |
| `REDIS_RECLAIM_IDLE` | `PT30S` | Worker | pending 消息 reclaim 空闲时间 |
| `OBJECT_STORAGE_MODE` | `local` | API/Worker | `local` 或 `s3` |
| `LOCAL_STORAGE_DIR` | `./var/objects` | local | 对象根目录，禁止指向仓库源码目录 |
| `S3_ENDPOINT` | 空 | s3/MinIO | S3 兼容 endpoint |
| `S3_BUCKET` | `dream-space` | s3 | bucket 名称 |
| `S3_ACCESS_KEY` / `S3_SECRET_KEY` | 空 | s3 | 只从 Secret 注入 |
| `S3_REGION` | `us-east-1` | s3 | 签名区域 |
| `S3_SIGNED_URL_TTL_SECONDS` | `300` | s3 | 结果下载签名有效期 |
| `EXTERNAL_SERVICES_MODE` | `mock` | 本地/测试 | `mock`、`real`；生产必须显式设置 |
| `OPENAI_BASE_URL` | `http://localhost:8089` | Worker | OpenAI-compatible `/chat/completions` 地址 |
| `OPENAI_API_KEY` | 不提交 | Worker | 模型密钥 |
| `OPENAI_MODEL` | `local-mock-model` | Worker | 模型名称 |
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
| `frontend/` | Vue 3 + TypeScript + Vite 5 应用 | 仅放前端源码、静态资源和 workspace 配置 |
| `frontend/web/` | 用户端入口 `index.html`、`src/main.ts`、路由、视图和样式 | 页面和 CSS 以 `bak/apps/web` 为视觉基线 |
| `frontend/admin/` | 管理端入口、路由、视图和样式 | 权限按钮只是表现，授权由 API 决定 |
| `backend/common/` | 跨 API/Worker 的版本、状态、错误和契约模型 | 不依赖数据库或外部网络 |
| `backend/persistence/` | Mapper、迁移、Redis queue、对象存储和配置适配器 | 不包含 HTTP Controller |
| `backend/api/` | Spring MVC Controller、鉴权、同步事务、SSE 和队列 producer | 不直接调用 `ChatModel` |
| `backend/worker/` | Stream consumer、模型适配、图像管线、结果持久化和对账 | 不暴露用户 API |
| `backend/mvnw` / `backend/mvnw.cmd` | Maven Wrapper | CI 和本地统一使用 Wrapper |
| `.trellis/` | 开发任务状态和验收记录 | 不把凭据写入任务记录 |
| `.gitignore` | 构建产物、缓存、环境文件和 Trellis 运行文件 | `.m2repo/`、`.pnpm-store/` 不得提交 |

## 9.4 本地启动顺序

1. 启动 PostgreSQL、Redis 和可选 MinIO；执行 `backend/persistence` 迁移。
2. 启动 API：`backend/mvnw.cmd -pl api -am spring-boot:run`，确认 `/health/live` 和 `/health/ready`。
3. 启动 Worker：`backend/mvnw.cmd -pl worker -am spring-boot:run`，确认 Stream consumer 已加入 `generation-workers`。
4. 在 `frontend/` 安装依赖并分别启动 web/admin；浏览器通过 Vite 代理访问 API。
5. 本地默认使用 mock 验证码、mock 模型和 local object storage；联调真实服务前显式切换 `EXTERNAL_SERVICES_MODE=real`。
