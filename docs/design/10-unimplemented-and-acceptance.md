# 10 未实现功能与开发验收清单

本文档区分“设计已定义”和“当前代码已实现”，避免把阶段性交付误认为可上线功能。当前仓库已具备基础工程、持久化、用户认证/灵感/上传、生成 API 和 Vue 用户端工作台；Worker、管理端业务、全量质量回归和生产切流仍未完成。

## 10.1 当前已具备

- Vue web/admin 的 Vite 入口、路由壳、TypeScript 配置和代理；web 已完成灵感、详情、登录和生成工作台页面。
- Spring Boot API/Worker 入口、Actuator health 配置和 persistence 模块。
- Prisma 迁移 SQL 已复制到 `backend/persistence/src/main/resources/db/migration/`，并保留原始版本顺序。
- MyBatis 枚举/JSON 类型处理、基础 Mapper record、Redis generation queue 和 local/S3 object storage adapter。
- 额度账户 reserve/consume/release 的基础事务服务、对象键白名单和单元测试。
- API Context、迁移资源、对象键和 Worker ChatModel mock 的基础测试。
- 用户/管理员独立认证、公开灵感和参考图上传 API，包含资源归属与基础 RBAC。
- 生成 options、quota、session/draft、task/cancel/retry、result 和 SSE API；任务写入后再发布 Redis，并对未发布任务定时补偿。
- Web 生成工作台的会话侧栏、参数、参考图、额度、任务时间线、取消/重试、结果预览和下载交互。

## 10.2 必须开发的功能

### 前台用户端

1. 使用真实 Worker 完成生成端到端回归，覆盖成功、部分成功、失败、取消、重试和 SSE 断线恢复。
2. 补齐生成工作台 1440x900、1024x768、800x1024、390x844 截图基线和中英文长文本回归。
3. 加强上传 WebP 编码一致性；运行时缺少 WebP writer 时不能仅修改 MIME 元数据。
4. 从 `bak/apps/web` 持续核对 DOM、文案、素材、浅色/深色 token、移动断点和 reduced-motion 行为。

### 管理端

1. 完成管理员独立登录、session 过期处理和 VIEWER/OPERATOR/ADMIN 权限表现。
2. 完成任务列表筛选分页、详情抽屉、结果资源、对账摘要和错误信息脱敏。
3. 完成灵感创建、编辑、发布、取消发布、草稿校验和乐观更新冲突提示。
4. 从 `bak/apps/admin` 迁移表格、抽屉、表单、导航和响应式断点；移动端不能出现横向溢出。

### API 与安全

1. 补齐验证码/IP 限流、CSRF 防护和生成 JSON schema 校验。
2. 实现 admin task、reconciliation 和 inspiration REST Controller。
3. 补齐 PostgreSQL Mapper 的管理端、死信和对账表读写、分页和并发冲突处理。
4. 完成 readiness contributors：PostgreSQL、Redis、对象存储不可用时必须返回不可就绪，不能伪造成功。

### Worker 与模型

1. 实现 Redis Stream consumer 的抢占、ack、pending reclaim、重试退避和 dead-letter 持久化。
2. 实现 `GenerationTaskStateMachine` 和八步生成管线，包括审核、图像处理、缩略图、对象清理和结果落库。
3. 完成 Spring AI 2.0.0-M5 `ChatModel` 的 OpenAI-compatible adapter、WireMock fixture 和错误分类。
4. 完成额度结算和定时对账；缺失流水可安全修复，金额漂移必须进入 `BLOCKED`。
5. 接入 S3 client/presigner 的 Spring 配置，补齐 endpoint、region、credentials 和 TTL 校验。

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
