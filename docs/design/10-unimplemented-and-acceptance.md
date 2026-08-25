# 10 未实现功能与开发验收清单

本文档区分“设计已定义”和“当前代码已实现”，避免把阶段性交付误认为可上线功能。当前仓库已具备基础工程、持久化、用户与管理员认证、生成/管理 API、Vue 用户端和管理端、Worker 生成管线；本地质量回归与 CI 门禁已完成，真实基础设施集成和生产切流仍需在运维阶段完成。

## 10.0 质量回归完成记录

- 后端已在 JDK 21 下通过 Maven 全量测试和打包；PostgreSQL 17、Redis 8、SFTP 集成服务在本机 Docker 不可用时明确跳过，CI 环境 Docker 不可用则失败。
- 前端 dream_web/manage_web 的 typecheck 和生产构建通过。Playwright 已删除 API 路由拦截，只在 `RUN_REAL_E2E=1` 且真实 API/Worker/模型环境已启动时执行；当前无真实环境，因此 18 项视口场景明确跳过，不能计为业务通过。
- Worker 使用格式完整但不可达的 live 配置实际启动后，仅开放 `4010` 管理端口；readiness 返回 HTTP 503/DOWN，Prometheus 返回 HTTP 200，默认业务端口不可达。
- 旧截图只能作为历史参考；真实任务状态、800x1024、中英文长文本及 `bak` 对照仍需在真实联调环境重新采集。
- 仍未宣称生产就绪：真实供应商联调、完整生成 E2E、备份恢复和灰度回滚必须在 CI/运维任务中完成。

## 10.1 当前已具备

- Vue dream_web/manage_web 的 Vite 入口、路由、TypeScript 配置和代理；dream_web 已完成灵感、详情、登录和生成工作台，manage_web 已完成登录、任务和灵感管理页面。
- Spring Boot API/Worker 入口、Actuator health 配置和 common 持久化基础设施。
- Prisma 迁移 SQL 已复制到 `dream_service/common/src/main/resources/db/migration/`，并保留原始版本顺序。
- MyBatis 枚举/JSON 类型处理、基础 Mapper record、Redis generation queue 和 local/SFTP object storage adapter。
- 额度账户 reserve/consume/release 的基础事务服务、对象键白名单和单元测试。
- API Context、迁移资源和对象键的基础测试；Worker 模型由真实供应商人工验收。
- 用户/管理员独立认证、公开灵感和参考图上传 API，包含资源归属与基础 RBAC。
- 生成 options、quota、session/draft、task/cancel/retry、result 和 SSE API；任务写入后再发布 Redis，并对未发布任务定时补偿。
- Web 生成工作台的会话侧栏、参数、参考图、额度、任务时间线、取消/重试、结果预览和下载交互。
- Worker Redis Stream 消费组、pending reclaim、指数退避、条件抢占、有限重试、死信和终态 ack。
- Harness 生成管线、输入/输出审核端口、真实多模态规划/评估模型、独立图片模型适配器和错误分类。
- EXIF 方向处理、cover crop、真实 WebP 主图/缩略图、SHA-256、对象写入补偿和 `(taskId,index)` 幂等结果持久化。
- 成功/失败额度结算，以及按窗口幂等执行的额度对账；安全缺失结算可修复，金额和账户漂移记录为 `BLOCKED`。
- 管理员独立 Cookie/session、VIEWER/OPERATOR/ADMIN 服务端 RBAC，以及与用户认证隔离的登录和退出链路。
- 管理任务筛选分页、详情与结果安全读取、手机号和错误信息脱敏、额度对账摘要 API。
- 管理灵感查询、创建、编辑、发布和下架 API；来源/授权校验和基于 `updatedAt` 的乐观并发冲突已实现。
- Vue 管理端复用 `bak/apps/admin` 的完整样式表，包含侧栏、筛选、表格、任务详情抽屉、灵感编辑抽屉、只读角色状态和响应式断点。

## 10.2 必须开发的功能

> 账单、用户管理、可配置计费和支付订单的第一阶段实现已落地到迁移 `20260824090000_add_billing_user_management.sql`、`BillingService`、用户账户页和管理端计费路由。下列条目描述的是仍需真实基础设施或生产化增强的部分，不再把已交付的接口标记为“未实现”。

### 10.2.0 已交付的计费能力

- 用户账户余额、额度流水、订单创建/查询/取消和可售产品查询。
- 管理端用户搜索、禁用/启用、会话撤销、额度赠送、用户流水、订单查询和未消费订单全额退款。
- 规则草稿/发布/下线、产品上架/下架、支付回调 token 保护、金额/币种校验和支付事件幂等。
- 初始额度、订单到账、管理员调整和退款扣回均写入可解释的额度流水；用户状态和登录时间纳入账户生命周期。

### 10.2.1 计费生产化剩余项

1. 用真实支付 provider adapter 替换 mock provider 和 `signatureVerified` 请求字段，接入真实验签、商户号校验和退款渠道。
2. 增加 outbox、支付对账、回调死信和退款异步重试；当前接口已保证数据库事务和事件幂等，但未替代供应商运营流程。
3. 对复杂有效期/分批额度引入 CreditLot；当前退款仅允许可用额度足够的全额退款，不能自动处理已消费订单。
4. 补齐管理端订单、产品、规则和审计页面的写入表单、筛选分页和真实权限资源校验。

### 前台用户端

1. 使用真实 Worker 完成生成端到端回归，覆盖成功、部分成功、失败、取消、重试和 SSE 断线恢复。
2. 补齐生成工作台 1440x900、1024x768、800x1024、390x844 截图基线和中英文长文本回归。
3. 参考图上传与 Worker 已统一使用真实 WebP writer，并在 codec 缺失时明确失败；仍需在真实对象存储环境验收异常补偿。
4. 从 `bak/apps/web` 持续核对 DOM、文案、素材、浅色/深色 token、移动断点和 reduced-motion 行为。

### 管理端

1. 使用真实 PostgreSQL、Redis 和对象存储完成管理端端到端回归，覆盖 session 过期、VIEWER 拒绝写入和乐观冲突。
2. 建立 1440x900、800x1024、390x844 的持久化截图基线；当前已完成 1280x720 浏览器检查，完整样式与 `bak/apps/admin` 一致。
3. 补充焦点循环、后台内容不可聚焦和路由切换时抽屉关闭的自动化可访问性回归。

### API 与安全

1. 补齐验证码/IP 限流、CSRF 防护和生成 JSON schema 校验。
2. 为 manage_web task、reconciliation 和 inspiration REST Controller 补齐 OpenAPI 与真实 PostgreSQL 契约测试。
3. readiness contributors 已覆盖 PostgreSQL、Redis、对象存储和模型；仍需在真实基础设施环境验证各依赖逐项中断场景。

### Worker 与模型

1. 使用真实 PostgreSQL + Redis + SFTP 存储完成 Worker 集成回归，验证 reclaim、取消竞态、重复投递和数据库事务回滚。
2. 使用真实供应商人工验证 timeout、429、5xx、401/403、图片 URL 下载和供应商 request ID 脱敏日志。
3. 内容审核运营队列、用户申诉、管理员复核和不可变审计已实现；仍需真实 PostgreSQL 与审核模型联调。
4. SFTP 客户端已统一认证、known_hosts、超时和重试配置；部分写入和网络中断仍需故障注入验收。
5. Worker 已暴露 pending、尝试、dead-letter、模型时延、图片处理、清理失败、审核和对账指标，并提供 Prometheus 告警规则；阈值仍需按生产容量校准。

### 工程与运维

1. 补充 Docker Compose、API/Worker/前端镜像、数据库备份、对象生命周期和本地开发 profile。
2. 建立 OpenAPI/JSON fixtures、Testcontainers PostgreSQL/Redis、MockMvc、Vitest 和 Playwright 测试流水线；模型供应商通过人工真实配置验收。
3. 建立视觉截图基线，覆盖 1440x900、1024x768、800x1024、390x844；检查重复 id、缺失 DOM target、主题变量和中英文溢出。
4. 实现 Node BullMQ bridge、双栈灰度、监控告警和可演练回滚；回滚周期结束后才清理旧运行服务。

## 10.3 阻断项

以下任意一项未完成，不得声明重构完成或切流：

- 与 `bak` 路由、页面交互、文案、颜色、资源和移动断点不一致。
- 任务状态跳跃、SSE 丢事件/越权读取、重复提交重复扣费或额度恒等式破坏。
- 用户 Cookie 可访问管理接口、VIEWER 可写入、对象 key 可路径穿越或任意资源可读。
- 真实密钥进入源码、日志、fixture、截图、Git 历史或 API 错误响应。
- PostgreSQL、Redis、SFTP 或模型不可用时 readiness 仍返回成功。
- 缺少迁移回滚方案、生产备份验证或双栈回滚演练。

## 10.4 功能完成定义

每个开发任务必须同时提交：实现路径、接口/数据库变更、单元测试、集成或 E2E 测试、配置说明、观测指标和回滚步骤。验收顺序为：契约测试通过 -> 业务测试通过 -> 桌面/移动视觉回归通过 -> 安全检查通过 -> 灰度验证通过。
