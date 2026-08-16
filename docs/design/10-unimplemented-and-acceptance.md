# 10 未实现功能与开发验收清单

本文档区分“设计已定义”和“当前代码已实现”，避免把阶段性交付误认为可上线功能。当前仓库已具备基础工程、持久化、用户与管理员认证、生成/管理 API、Vue 用户端和管理端、Worker 生成管线；真实基础设施集成回归、全量质量回归和生产切流仍未完成。

## 10.1 当前已具备

- Vue web/admin 的 Vite 入口、路由、TypeScript 配置和代理；web 已完成灵感、详情、登录和生成工作台，admin 已完成登录、任务和灵感管理页面。
- Spring Boot API/Worker 入口、Actuator health 配置和 persistence 模块。
- Prisma 迁移 SQL 已复制到 `backend/persistence/src/main/resources/db/migration/`，并保留原始版本顺序。
- MyBatis 枚举/JSON 类型处理、基础 Mapper record、Redis generation queue 和 local/S3 object storage adapter。
- 额度账户 reserve/consume/release 的基础事务服务、对象键白名单和单元测试。
- API Context、迁移资源、对象键和 Worker ChatModel mock 的基础测试。
- 用户/管理员独立认证、公开灵感和参考图上传 API，包含资源归属与基础 RBAC。
- 生成 options、quota、session/draft、task/cancel/retry、result 和 SSE API；任务写入后再发布 Redis，并对未发布任务定时补偿。
- Web 生成工作台的会话侧栏、参数、参考图、额度、任务时间线、取消/重试、结果预览和下载交互。
- Worker Redis Stream 消费组、pending reclaim、指数退避、条件抢占、有限重试、死信和终态 ack。
- 八步生成管线、输入/输出审核端口、确定性 Mock、Spring AI `ChatModel` OpenAI-compatible 适配器和错误分类。
- EXIF 方向处理、cover crop、真实 WebP 主图/缩略图、SHA-256、对象写入补偿和 `(taskId,index)` 幂等结果持久化。
- 成功/失败额度结算，以及按窗口幂等执行的额度对账；安全缺失结算可修复，金额和账户漂移记录为 `BLOCKED`。
- 管理员独立 Cookie/session、VIEWER/OPERATOR/ADMIN 服务端 RBAC，以及与用户认证隔离的登录和退出链路。
- 管理任务筛选分页、详情与结果安全读取、手机号和错误信息脱敏、额度对账摘要 API。
- 管理灵感查询、创建、编辑、发布和下架 API；来源/授权校验和基于 `updatedAt` 的乐观并发冲突已实现。
- Vue 管理端复用 `bak/apps/admin` 的完整样式表，包含侧栏、筛选、表格、任务详情抽屉、灵感编辑抽屉、只读角色状态和响应式断点。

## 10.2 必须开发的功能

### 前台用户端

1. 使用真实 Worker 完成生成端到端回归，覆盖成功、部分成功、失败、取消、重试和 SSE 断线恢复。
2. 补齐生成工作台 1440x900、1024x768、800x1024、390x844 截图基线和中英文长文本回归。
3. 将参考图上传链路也切换到与 Worker 相同的真实 WebP writer；运行时缺少 writer 时必须明确失败。
4. 从 `bak/apps/web` 持续核对 DOM、文案、素材、浅色/深色 token、移动断点和 reduced-motion 行为。

### 管理端

1. 使用真实 PostgreSQL、Redis 和对象存储完成管理端端到端回归，覆盖 session 过期、VIEWER 拒绝写入和乐观冲突。
2. 建立 1440x900、800x1024、390x844 的持久化截图基线；当前已完成 1280x720 浏览器检查，完整样式与 `bak/apps/admin` 一致。
3. 补充焦点循环、后台内容不可聚焦和路由切换时抽屉关闭的自动化可访问性回归。

### API 与安全

1. 补齐验证码/IP 限流、CSRF 防护和生成 JSON schema 校验。
2. 为 admin task、reconciliation 和 inspiration REST Controller 补齐 OpenAPI 与真实 PostgreSQL 契约测试。
3. 完成 readiness contributors：PostgreSQL、Redis、对象存储不可用时必须返回不可就绪，不能伪造成功。

### Worker 与模型

1. 使用真实 PostgreSQL + Redis + S3-compatible 存储完成 Worker 集成回归，验证 reclaim、取消竞态、重复投递和数据库事务回滚。
2. 为 OpenAI-compatible 供应商补充 timeout、429、5xx、401/403 和图片 URL 下载的 WireMock 契约用例，并校验供应商 request ID 脱敏日志。
3. 接入生产内容审核实现；当前审核端口及确定性 Mock 已具备，生产模式仍需绑定实际审核服务。
4. 为 S3 client/presigner 配置补充 endpoint、region、credentials、TTL 和部分写入失败的集成测试。
5. 建立 Worker 指标和告警：pending 数、重试次数、dead-letter 数、模型时延、图片处理时延、清理失败和对账 `BLOCKED` 数。

### 工程与运维

1. 补充 Docker Compose、API/Worker/前端镜像、数据库备份、对象生命周期和本地开发 profile。
2. 建立 OpenAPI/JSON fixtures、Testcontainers PostgreSQL/Redis、WireMock、MockMvc、Vitest 和 Playwright 测试流水线。
3. 建立视觉截图基线，覆盖 1440x900、1024x768、800x1024、390x844；检查重复 id、缺失 DOM target、主题变量和中英文溢出。
4. 实现 Node BullMQ bridge、双栈灰度、监控告警和可演练回滚；回滚周期结束后才清理旧运行服务。

## 10.3 阻断项

以下任意一项未完成，不得声明重构完成或切流：

- 与 `bak` 路由、页面交互、文案、颜色、资源和移动断点不一致。
- 任务状态跳跃、SSE 丢事件/越权读取、重复提交重复扣费或额度恒等式破坏。
- 用户 Cookie 可访问管理接口、VIEWER 可写入、对象 key 可路径穿越或任意资源可读。
- 真实密钥进入源码、日志、fixture、截图、Git 历史或 API 错误响应。
- PostgreSQL、Redis、S3 或模型不可用时 readiness 仍返回成功。
- 缺少迁移回滚方案、生产备份验证或双栈回滚演练。

## 10.4 功能完成定义

每个开发任务必须同时提交：实现路径、接口/数据库变更、单元测试、集成或 E2E 测试、配置说明、观测指标和回滚步骤。验收顺序为：契约测试通过 -> 业务测试通过 -> 桌面/移动视觉回归通过 -> 安全检查通过 -> 灰度验证通过。
