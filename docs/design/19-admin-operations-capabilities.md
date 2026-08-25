# 19 管理端运营能力详细设计

## 19.1 文档目的

本文以 2026-08-25 的仓库代码和数据库迁移为基线，分析以下十一项管理端运营能力，并给出可直接拆分研发任务的目标设计：

1. 用户管理。
2. 管理员账号和角色管理。
3. 权限配置。
4. 模型/供应商管理。
5. 模型路由和降级策略。
6. 计费规则配置。
7. 额度赠送和扣减。
8. 账单/收入报表。
9. 系统配置管理。
10. 审计日志查询。
11. 运营数据看板。

本文是增量设计，不替代 [18 用户账单、用户管理、计费规则与支付订单设计](./18-billing-user-management-and-payments.md)。第 18 章定义用户账单与交易基础，本章重点补齐管理治理、模型运营、财务分析和数据运营。

状态口径：

- **已实现**：后端、数据和主要管理交互均可用，仅剩生产联调或一般性增强。
- **部分实现**：已有数据或接口，但流程、权限、页面、生产一致性中的至少一项不完整。
- **未实现**：没有可供管理端使用的领域模型和完整入口；零散配置或监控指标不算该能力已实现。

## 19.2 当前能力盘点

| 能力 | 状态 | 当前已有 | 主要缺口 | 优先级 |
| --- | --- | --- | --- | --- |
| 用户管理 | 部分实现 | 用户列表/详情 API、启停、撤销会话、额度流水、正向赠送；管理端用户列表 | 缺少 `/users/{id}` 页面及任务/订单/流水聚合；筛选、批量操作、风险提示不足 | P0 |
| 管理员账号和角色管理 | 未实现 | `AdminUser`、`AdminSession`，固定 `VIEWER/OPERATOR/ADMIN` 枚举 | 无账号列表、邀请/启停、角色表、角色分配、会话治理 | P0 |
| 权限配置 | 部分实现 | 登录响应返回权限码；服务端按最低角色拦截 | 权限由 Java 列表硬编码，注解只接受角色等级，无法配置或精确授权 | P0 |
| 模型/供应商管理 | 未实现 | Worker YAML、Spring 配置类、单供应商适配器、健康探测 | 无数据库注册表、管理 API、凭据引用、模型能力和生命周期管理 | P1 |
| 模型路由和降级 | 未实现 | 任务级有限重试、错误可重试分类、Worker readiness | 无多目标路由、权重、熔断、降级链、版本发布和任务路由快照 | P1 |
| 计费规则配置 | 部分实现 | `PricingRule` 表及创建/发布/下线 API，生成任务保存规则版本；只读列表页 | 页面不能编辑发布；查价未完整匹配模型和尺寸；缺预览、优先级、冲突证明和回滚 | P0 |
| 额度赠送和扣减 | 部分实现 | 管理员正向 `GRANT`、不可变额度流水、退款扣回 | 管理员负向调整被明确拒绝；无申请/复核/拒绝、限额和批量导入 | P0 |
| 账单/收入报表 | 未实现 | 订单、支付、退款、额度流水等交易明细 | 无统一口径、日聚合、趋势、收入/退款/点数负债/模型成本与导出 | P1 |
| 系统配置管理 | 未实现 | YAML 与 `DreamSpaceProperties` 静态配置 | 无配置定义、环境作用域、版本发布、回滚、动态刷新和生效回执 | P2 |
| 审计日志查询 | 部分实现 | Billing 和 Moderation 各自审计；Billing 审计基础列表页 | 非统一模型；无法按动作、操作者、结果、时间检索；缺导出和敏感字段策略 | P0 |
| 运营数据看板 | 未实现 | Worker 暴露队列、模型时延、死信、审核、对账等 Micrometer 指标 | 无管理端聚合 API、KPI 口径、趋势、异常卡片和业务下钻 | P2 |

判断依据：

- `AdminPermission` 当前只有 `minimum: AdminRole`，没有权限码。
- `AdminAuthService` 按三个枚举角色返回硬编码权限集合。
- 管理端已有 `/users`、`/billing/orders`、`/billing/products`、`/billing/rules`、`/audit-events` 路由，但没有 `/users/{id}`、管理员、模型、配置、报表和看板路由。
- `BillingService.adjustCredits` 明确拒绝负数并返回 `CREDIT_DEBIT_UNSUPPORTED`。
- `BillingMapper.findActivePricingRule` 只按操作和分辨率查询，`modelPattern` 与宽高范围尚未参与最终筛选。
- Worker 供应商、URL、模型和重试参数来自 YAML；已有指标与 readiness，但不存在动态路由注册表。

## 19.3 目标架构与边界

```text
manage_web
   |
   v
Admin API ------------------------------------------------------+
   |                 |                  |                       |
   v                 v                  v                       v
Admin IAM       User Operations    Billing & Reports    Audit & Analytics
   |                                    |                       |
   +--------------------+---------------+-----------------------+
                        |
                        v
               Runtime Configuration
                        |
                        v
                   Model Gateway
                        |
               published route snapshot
                        |
                        v
                      Worker
```

目标系统分为六个边界：

| 边界 | 责任 | 不负责 |
| --- | --- | --- |
| Admin IAM | 管理员账号、角色、权限、会话和服务端授权 | 普通用户登录、业务数据所有权 |
| User Operations | 用户资料、状态、会话、任务/账单聚合、额度调整流程 | 直接改写额度余额 |
| Billing & Reports | 计费规则、订单/支付/退款事实、财务和额度聚合 | 法定会计、发票和税务 |
| Runtime Configuration | 可在线治理的业务/运行策略版本 | DB、Redis、对象存储地址和明文密钥 |
| Model Gateway | 供应商、模型、路由、健康、熔断、调用事实和成本 | 生成业务状态机和用户计费账本 |
| Audit & Analytics | 统一审计、业务指标聚合、看板查询 | 替代应用日志或 Prometheus |

### 19.3.1 事实源原则

- 用户额度事实源仍是 `QuotaLedgerEntry`，`QuotaAccount` 只是锁定后的余额投影。
- 金额事实源是 `BillingOrder`、`PaymentTransaction`、`Refund`，报表聚合不可反向修改交易。
- 生成事实源是 `GenerationTask`、`GenerationIteration` 和结果记录。
- 管理权限事实源是 RBAC 关系表，前端登录态中的权限仅为展示缓存。
- 模型选择事实源是任务/尝试保存的路由快照，而不是事后读取当前路由。
- 审计事实源是追加写 `AuditEvent`，应用日志不能作为合规审计替代品。

## 19.4 Admin IAM：管理员、角色与权限

### 19.4.1 数据模型

#### AdminUser 扩展

| 字段 | 类型 | 约束/说明 |
| --- | --- | --- |
| `id` | text | 现有主键 |
| `phone` | text | 唯一，完整值仅限授权接口返回 |
| `displayName` | varchar(120) | 必填 |
| `status` | varchar(16) | `INVITED/ACTIVE/DISABLED`；替代单一 `active` 布尔值 |
| `lastLoginAt` | timestamptz | 最近成功登录 |
| `createdBy` | text | 首个管理员允许为 `system` |
| `disabledAt/disabledBy/disabledReason` | nullable | 禁用审计快照 |
| `version` | integer | 乐观并发控制 |

#### 新增 RBAC 表

| 表 | 核心字段 | 关键约束 |
| --- | --- | --- |
| `AdminRole` | `id, code, name, description, system, status, version` | `code` 唯一；系统角色不可删除 |
| `AdminPermission` | `id, code, resource, action, description, riskLevel` | `code` 唯一，格式 `resource:action` |
| `AdminUserRole` | `adminUserId, roleId, assignedBy, assignedAt` | 组合唯一；至少保留一个有效 `ADMIN` |
| `AdminRolePermission` | `roleId, permissionId, grantedBy, grantedAt` | 组合唯一 |

权限码以资源和动作表达，不用角色等级表达：

```text
dashboard:read            users:read                 users:disable
users:session-revoke      users:credit-grant         users:credit-debit
admins:read               admins:write               roles:read
roles:write               providers:read             providers:write
providers:credential-bind models:read                models:write
routes:read               routes:publish             pricing:read
pricing:write             pricing:publish            billing:read
billing:refund            reports:read               reports:export
config:read               config:write               config:publish
audit:read                audit:export
```

### 19.4.2 默认角色

| 角色 | 默认责任 | 关键权限 |
| --- | --- | --- |
| `VIEWER` | 全局只读观察 | dashboard、tasks、users、billing、pricing、providers、routes、audit 的 read |
| `OPERATOR` | 用户与内容运营 | 用户启停、撤销会话、小额赠送、审核和灵感写操作 |
| `FINANCE` | 订单和财务运营 | billing、reports、退款、额度赠送/扣减申请 |
| `MODEL_OPERATOR` | 模型运营 | provider/model 写入、健康测试、路由草稿；不含发布 |
| `CONFIG_ADMIN` | 业务配置运营 | config 写入/发布；不含管理员和财务操作 |
| `ADMIN` | 平台治理 | 全权限、角色配置、路由发布、高风险复核 |

默认角色是初始化数据，不在代码中硬编码权限列表。允许创建自定义角色，但权限码由程序版本注册，管理端不能任意创建未知权限码。

### 19.4.3 服务端鉴权

目标注解：

```java
@AdminPermission("users:disable")
```

鉴权步骤：

1. 校验管理端 Session，读取 `AdminUser.status`。
2. 根据 `AdminUserRole -> AdminRolePermission` 计算有效权限。
3. 命中权限则构造 `AdminPrincipal`，否则返回 `403 ADMIN_PERMISSION_REQUIRED`。
4. 写操作在业务事务内写审计；鉴权拒绝单独写结果为 `DENIED` 的安全审计。

权限集合可在 Redis 或本地缓存 5 分钟，缓存键包含 `adminId + permissionRevision`。角色、权限或账号状态变化时递增 revision、撤销该管理员全部 Session，并发布失效消息。任何缓存故障必须回源数据库，不能默认放行。

兼容期内保留旧 `role` 字段，只作为迁移映射和页面展示；所有 Controller 完成权限码改造后再移除最低角色语义。

### 19.4.4 管理接口

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| `GET` | `/manage_web/admins` | `admins:read` | 账号、状态、角色、最近登录分页 |
| `POST` | `/manage_web/admins` | `admins:write` | 邀请/创建管理员 |
| `PATCH` | `/manage_web/admins/{id}` | `admins:write` | 显示名和状态更新，要求 `version` |
| `PUT` | `/manage_web/admins/{id}/roles` | `admins:write` | 替换角色集合，禁止移除最后一个 ADMIN |
| `POST` | `/manage_web/admins/{id}/revoke-sessions` | `admins:write` | 撤销全部会话 |
| `GET` | `/manage_web/roles` | `roles:read` | 角色及权限数量 |
| `POST/PATCH` | `/manage_web/roles` | `roles:write` | 创建或更新自定义角色 |
| `PUT` | `/manage_web/roles/{id}/permissions` | `roles:write` | 替换权限集合，要求版本号 |
| `GET` | `/manage_web/permissions` | `roles:read` | 按资源分组返回系统权限目录 |

管理员不能禁用自己、撤销自己的唯一管理角色或删除最后一个有效 ADMIN。角色变更、管理员启停属于高风险操作，必须填写原因并在确认对话框展示影响账号和会话数量。

## 19.5 用户管理与额度调整

### 19.5.1 用户详情工作台

新增 `/users/{id}`，采用同一页面内的标签页：

| 标签 | 内容 | 数据源 |
| --- | --- | --- |
| 概览 | 状态、手机号脱敏、注册/登录、额度四元组、订单和任务摘要 | User、QuotaAccount、聚合查询 |
| 生成任务 | 状态、模型、分辨率、费用、错误、结果跳转 | GenerationTask |
| 额度流水 | 类型、增减值、余额、来源、规则版本、任务/订单关联 | QuotaLedgerEntry |
| 支付订单 | 金额、产品、支付/退款状态、渠道、时间 | BillingOrder 等 |
| 会话 | 创建、最近访问、到期状态；只提供全部撤销 | UserSession |
| 操作记录 | 用户启停、额度调整、退款及操作者 | AuditEvent |

列表补齐状态、注册时间、最近登录时间、余额区间和有无异常账务筛选。手机号查询只允许完整或后四位精确匹配，响应继续脱敏。批量禁用和批量额度操作本期不开放，避免误操作扩散。

### 19.5.2 额度调整模型

新增 `CreditAdjustment`：

| 字段 | 说明 |
| --- | --- |
| `id` | 调整单 ID，同时作为额度流水 `sourceId` |
| `userId` | 目标用户 |
| `direction` | `GRANT/DEBIT` |
| `amount` | 正整数，方向不编码在数值正负中 |
| `reasonCode/reason` | 标准原因和人工说明 |
| `status` | `PENDING_APPROVAL/APPROVED/REJECTED/EXECUTED/FAILED/CANCELLED` |
| `requestedBy/approvedBy` | 申请人和复核人 |
| `requestedAt/decidedAt/executedAt` | 流程时间 |
| `idempotencyKey` | 唯一 |
| `ledgerEntryId/errorCode` | 执行结果 |
| `version` | 乐观锁 |

规则：

- 单笔赠送不超过配置阈值且申请人有 `users:credit-grant` 时可自动批准；超过阈值进入复核。
- 所有扣减默认需要 `users:credit-debit` 和第二人复核；申请人不得复核自己的请求。
- 扣减锁定 `QuotaAccount` 后执行，必须满足 `available >= amount`，不扣 reserved，不产生负余额。
- 执行通过 `QuotaTransactionService` 写 `GRANT` 或新的 `DEBIT` 流水类型；禁止 SQL 直接更新余额。
- 请求重试由 `idempotencyKey` 返回既有调整单；`EXECUTED` 不可撤销，只能创建反向调整单。

状态机：

```text
REQUEST -> PENDING_APPROVAL -> APPROVED -> EXECUTED
                         \-> REJECTED
REQUEST -> APPROVED -> EXECUTED        (满足自动批准条件)
APPROVED -> FAILED                     (并发余额变化等)
PENDING_APPROVAL -> CANCELLED          (仅申请人，未复核前)
```

## 19.6 模型与供应商管理

### 19.6.1 数据模型

| 表 | 关键字段 | 说明 |
| --- | --- | --- |
| `AiProvider` | `id, code, name, protocol, baseUrl, status, timeoutMs, maxConcurrency, version` | 供应商连接定义；协议首期支持 `OPENAI_COMPATIBLE` |
| `AiModel` | `id, providerId, code, providerModelName, modality, capabilities, status, costPolicy, version` | 同一模型名在不同供应商下是不同模型 |
| `ModelCredential` | `id, providerId, environment, secretRef, status, rotatedAt` | 只保存 Secret Manager/Vault/K8s Secret 引用 |
| `ProviderHealthSnapshot` | `providerId, modelId, status, latencyMs, errorCode, checkedAt, expiresAt` | 当前健康投影，可覆盖更新 |
| `ModelInvocation` | `id, taskId, attemptId, stage, providerId, modelId, routeVersionId, status, durationMs, inputUnits, outputUnits, estimatedCostMinor, providerRequestId, createdAt` | 每次调用事实，供追踪和成本聚合 |

`capabilities` 使用受控 JSON Schema，至少包含：支持的宽高/分辨率、最大图片数、参考图数量、图片格式、是否支持编辑、审核或文本规划。发布路由时验证所有目标满足路由能力约束。

供应商状态：`DRAFT/ACTIVE/MAINTENANCE/DISABLED`；模型状态：`DRAFT/ACTIVE/DEPRECATED/DISABLED`。`MAINTENANCE` 和 `DISABLED` 不接受新流量，但不改写历史调用。

### 19.6.2 凭据与健康检查

- API 和数据库永不接收、返回或审计真实密钥，只管理 `secretRef`。
- 凭据解析由 Worker/部署环境完成；管理 API 只返回 `configured: true/false` 和引用末段的掩码。
- “测试连接”创建异步探测任务，返回 `probeId`；探测使用最小无副作用请求并受 30 秒超时限制。
- 失败详情只保留标准化错误码：`AUTH_FAILED/RATE_LIMITED/TIMEOUT/UNREACHABLE/INVALID_RESPONSE`，原始响应脱敏后进入运行日志。
- 自动健康每 60 秒更新快照；连续失败不会直接永久禁用供应商，而由熔断器控制实时流量。

### 19.6.3 管理接口和页面

| 方法 | 路径 | 权限 |
| --- | --- | --- |
| `GET/POST` | `/manage_web/model-providers` | `providers:read/write` |
| `GET/PATCH` | `/manage_web/model-providers/{id}` | `providers:read/write` |
| `PUT` | `/manage_web/model-providers/{id}/credential-ref` | `providers:credential-bind` |
| `POST` | `/manage_web/model-providers/{id}/probes` | `providers:write` |
| `GET/POST` | `/manage_web/models` | `models:read/write` |
| `GET/PATCH` | `/manage_web/models/{id}` | `models:read/write` |
| `GET` | `/manage_web/model-invocations` | `providers:read` |

页面路由为 `/models/providers`、`/models/catalog`、`/models/invocations`。供应商列表优先展示状态、健康、P95 时延、近一小时错误率和绑定模型数；凭据按钮只显示绑定/轮换，不提供明文查看。

## 19.7 模型路由、重试、熔断与降级

### 19.7.1 路由模型

| 表 | 核心字段 |
| --- | --- |
| `ModelRoute` | `id, code, name, stage, status, currentVersionId` |
| `ModelRouteVersion` | `id, routeId, version, matchConditions, timeoutMs, totalMaxAttempts, status, createdBy, publishedBy, publishedAt` |
| `ModelRouteTarget` | `id, routeVersionId, modelId, priority, weight, maxAttempts, timeoutMs, conditions, enabled` |

`stage` 至少包括 `PLANNING`、`IMAGE_GENERATION`、`INPUT_MODERATION`、`OUTPUT_MODERATION`。`matchConditions` 可匹配环境、任务模式、分辨率、宽高、是否包含参考图、用户灰度分组；条件采用受控字段和操作符，不允许执行表达式脚本。

路由版本状态为 `DRAFT/PUBLISHED/RETIRED`。发布时执行：条件 Schema 校验、目标能力校验、目标状态校验、权重合计校验、循环降级检测和模拟请求覆盖检查。发布采用原子切换 `currentVersionId`，历史版本只读。

### 19.7.2 选择算法

1. 根据 `stage + request context` 找到唯一已发布路由。
2. 按 `priority` 从小到大分组；同优先级目标按稳定哈希加权选择，哈希键使用 `taskId`，保证任务重试倾向同一目标。
3. 排除停用、能力不匹配、熔断打开和并发已满的目标。
4. 将 `routeVersionId/providerId/modelId` 写入 `GenerationIteration` 后再调用供应商。
5. 可重试错误先在当前目标内重试，再按降级顺序换目标；不可重试业务拒绝不得降级规避。

### 19.7.3 错误策略

| 错误 | 当前目标重试 | 换目标 | 说明 |
| --- | --- | --- | --- |
| 连接失败、超时、429、5xx | 是，指数退避并尊重 Retry-After | 是 | 受总尝试次数限制 |
| 401/403 | 否 | 是 | 立即标记凭据健康异常 |
| 模型不存在/能力不支持 | 否 | 是 | 路由发布校验遗漏时兜底 |
| 输入审核拒绝 | 否 | 否 | 业务终态，不能通过换模型规避 |
| 请求参数非法 | 否 | 否 | 标记配置或程序错误 |
| 输出不可解码 | 可重试一次 | 是 | 保存标准化错误，不保存敏感响应 |

熔断状态保存在 Redis，按 `providerId + modelId + stage` 隔离：最近 20 次调用中失败率达到 50% 且至少 10 次失败时打开 60 秒；随后允许 2 个半开探测。Redis 不可用时退化为进程内熔断，不得绕过任务总尝试上限。

一次生成任务的用户费用按提交时 `PricingRule` 快照计算，不因平台内部降级或重试额外扣点。每次供应商调用成本单独写入 `ModelInvocation`，用于毛利分析。

### 19.7.4 发布和回滚

- 草稿页面支持 20 组样例请求预演，输出命中路由、目标顺序和排除原因。
- 发布需要 `routes:publish`；生产环境发布必须填写原因。
- 回滚不是修改旧版本，而是把一个历史版本复制为新版本并发布，从而保留完整时间线。
- Worker 每 15 秒拉取 `routeRevision`，同时接收失效消息；拉取失败继续使用最后一个已验证快照。
- 新 Worker 启动且没有可用动态快照时使用现有 YAML bootstrap 配置；数据库中存在已发布路由后不静默回退到未知默认模型。

## 19.8 计费规则配置完善

现有 `PricingRule` 继续使用，但查价和发布契约需要补强。

### 19.8.1 唯一匹配规则

输入上下文统一为：

```json
{
  "operation": "IMAGE_GENERATION",
  "modelId": "model-id",
  "resolution": "4K",
  "width": 4096,
  "height": 4096,
  "imageCount": 1,
  "mode": "AUTO",
  "submittedAt": "2026-08-25T08:00:00Z"
}
```

匹配次序：`operation` 必须精确；模型精确匹配优先于通配；分辨率精确优先于 `ANY`；宽高必须落入闭区间；时间必须落入 `[effectiveFrom, effectiveTo)`。同等特异度命中多条规则时返回 `PRICING_RULE_AMBIGUOUS` 并拒绝提交，不能按数据库返回顺序任选一条。

发布前对所有 ACTIVE 规则执行区间交叠检查。数据库增加用于候选查询的索引，最终唯一性由领域服务证明并由测试覆盖。

### 19.8.2 管理功能

- 草稿创建、编辑和复制；ACTIVE/RETIRED 不允许原地修改。
- 价格预览：输入模型、尺寸、数量、时间，返回候选、胜出规则和总点数。
- 影响分析：展示过去 7 天相同请求按新规则计算后的费用变化分布，但不修改历史。
- 定时生效和提前下线；下线前检查是否仍有覆盖该请求空间的规则。
- 版本对比和“复制历史版本为新草稿”回滚。

接口补充：

| 方法 | 路径 | 权限 |
| --- | --- | --- |
| `GET/POST` | `/manage_web/billing/rules` | `pricing:read/write` |
| `GET/PATCH` | `/manage_web/billing/rules/{id}` | `pricing:read/write` |
| `POST` | `/manage_web/billing/rules/preview` | `pricing:read` |
| `POST` | `/manage_web/billing/rules/{id}/impact-analysis` | `pricing:read` |
| `POST` | `/manage_web/billing/rules/{id}/publish` | `pricing:publish` |
| `POST` | `/manage_web/billing/rules/{id}/retire` | `pricing:publish` |
| `POST` | `/manage_web/billing/rules/{id}/clone` | `pricing:write` |

## 19.9 账单、收入与成本报表

### 19.9.1 报表边界与口径

本模块是运营财务报表，不是法定会计总账。金额统一使用最小货币单位，默认展示时区为 `Asia/Shanghai`，原始时间仍保存 UTC。

| 指标 | 口径 |
| --- | --- |
| 支付收入 | 统计期内首次进入 `PAID` 的订单金额 |
| 退款金额 | 统计期内 `Refund` 首次进入 `SUCCEEDED` 的金额 |
| 净收入 | 支付收入 - 退款金额 |
| 售出点数 | 已支付订单快照中的 `creditAmount` |
| 赠送点数 | `GRANT` 且来源不是 ORDER 的点数 |
| 消耗点数 | `CONSUME.amount` |
| 未消耗点数余额 | 所有有效 `QuotaAccount.available + reserved`，作为运营负债参考而非会计负债 |
| 模型成本 | 成功和实际计费失败调用的 `ModelInvocation.estimatedCostMinor` 汇总 |
| 估算毛利 | 净收入 - 模型成本；明确标记为估算值 |

### 19.9.2 聚合表

| 表 | 粒度 | 维度/指标 |
| --- | --- | --- |
| `BillingDailyAggregate` | 日期、币种 | paidOrders、grossRevenue、refundAmount、netRevenue、creditsSold |
| `CreditDailyAggregate` | 日期、来源类型 | granted、debited、reserved、consumed、released、closingAvailable |
| `GenerationDailyAggregate` | 日期、operation、modelId、resolution | submitted、succeeded、failed、creditsCharged、durationP50/P95 |
| `ModelCostDailyAggregate` | 日期、providerId、modelId、stage、currency | calls、success、input/output units、estimatedCost、durationP95 |
| `UserDailyAggregate` | 日期 | registrations、activeUsers、payingUsers、disabledUsers |

聚合任务按日 `D+0` 增量运行并在次日重算 `D-2..D`，以吸收延迟支付和退款。每次运行记录 `AggregateRun(id, type, window, watermark, status, rowCount, checksum)`，同一窗口可重入。原始事实变化时通过状态时间而非 `updatedAt` 归属统计日。

### 19.9.3 报表 API 与导出

| 方法 | 路径 | 权限 |
| --- | --- | --- |
| `GET` | `/manage_web/reports/revenue` | `reports:read` |
| `GET` | `/manage_web/reports/credits` | `reports:read` |
| `GET` | `/manage_web/reports/generation` | `reports:read` |
| `GET` | `/manage_web/reports/model-costs` | `reports:read` |
| `POST` | `/manage_web/report-exports` | `reports:export` |
| `GET` | `/manage_web/report-exports/{id}` | `reports:export` |

超过 31 天或超过 10,000 行的导出走异步任务，对象存储文件 24 小时过期。导出文件记录筛选条件、口径版本、生成时间和时区；手机号默认脱敏。下载链接必须短期签名且写审计。

## 19.10 系统配置管理

### 19.10.1 配置分层

| 类型 | 示例 | 是否进入配置中心 |
| --- | --- | --- |
| 业务策略 | 新用户初始额度、自动审批上限、订单有效期 | 是 |
| 运行策略 | 队列最大尝试次数、功能开关、审核阈值 | 是，需声明是否支持动态刷新 |
| 模型策略 | 供应商、模型、路由和成本 | 否，由 Model Gateway 管理 |
| 基础设施 | DB/Redis/SFTP 地址、端口、部署拓扑 | 否，继续由部署配置管理 |
| 密钥 | API key、支付密钥、数据库密码 | 否，只允许外部 Secret 引用 |

### 19.10.2 数据模型

| 表 | 关键字段 |
| --- | --- |
| `SystemConfigDefinition` | `key, name, description, valueType, schema, defaultValue, scopeType, dynamic, sensitive, owner, status` |
| `SystemConfigVersion` | `id, key, environment, version, valueJson, status, createdBy, publishedBy, reason, createdAt, publishedAt` |
| `SystemConfigSnapshot` | `environment, revision, valuesJson, checksum, publishedAt` |
| `ConfigApplyReceipt` | `snapshotRevision, service, instanceId, status, appliedAt, errorCode` |

`valueType` 支持 boolean、integer、decimal、string、duration、enum、JSON；每个 key 必须有 JSON Schema、默认值、负责人和动态性声明。配置版本采用 `DRAFT/PUBLISHED/RETIRED`，发布先生成完整快照，再原子推进环境 revision。

### 19.10.3 生效和回滚

1. 管理员编辑草稿，服务端执行类型、Schema、跨字段和环境约束验证。
2. 预览展示旧值、新值、影响服务、是否需重启及灰度范围。
3. `config:publish` 发布快照并写审计/失效事件。
4. 服务实例拉取、校验 checksum、原子替换本地不可变快照并上报回执。
5. 动态不安全的配置标记 `restartRequired=true`，发布后不在运行中强制应用。
6. 回滚通过发布历史值的新版本完成，不能删除已发布版本。

API：`GET /system-config/definitions`、`GET/POST/PATCH /system-config/versions`、`POST /system-config/versions/{id}/publish`、`POST /system-config/versions/{id}/clone`、`GET /system-config/apply-receipts`。

## 19.11 统一审计日志

### 19.11.1 数据模型

新增统一 `AuditEvent`：

| 字段 | 说明 |
| --- | --- |
| `id` | UUID/ULID，主键 |
| `occurredAt` | 事件发生时间 |
| `actorType/actorId/actorDisplay` | `ADMIN/SYSTEM/USER` 和操作者快照 |
| `action` | 稳定动作码，如 `USER_DISABLED` |
| `resourceType/resourceId` | 主操作对象 |
| `relatedResourceType/relatedResourceId` | 可选关联对象 |
| `result` | `SUCCEEDED/FAILED/DENIED` |
| `reason` | 人工操作原因；高风险写操作必填 |
| `beforeJson/afterJson` | 字段级脱敏快照 |
| `requestId/ipHash/userAgentSummary` | 请求追踪信息；IP 保存不可逆哈希或受控加密值 |
| `metadata` | Schema 受控扩展字段 |

审计记录只能插入，业务账号无 UPDATE/DELETE 权限。按月分区，在线保留 12 个月，归档保留期由合规策略决定。禁止写入验证码、Cookie、Authorization、API key、支付凭证、完整手机号和完整供应商原始响应。

现有 `BillingAuditEvent`、`ModerationAuditEvent` 不立即删除：先建立统一写入器，新操作双写并比对；旧记录通过 `UNION ALL` 查询适配器展示；完成回填和校验后切换统一表，领域表进入只读历史状态。

### 19.11.2 查询能力

支持时间范围、操作者、动作、资源类型/ID、结果、请求 ID 和关键词筛选；默认最近 24 小时，单次范围最多 90 天。详情抽屉展示结构化前后差异，敏感字段始终遮罩。导出走异步任务并要求 `audit:export`。

## 19.12 运营数据看板

### 19.12.1 三类数据不能混用

- **业务指标**：用户、任务、点数、订单，通过聚合表查询。
- **财务报表**：收入、退款、成本、毛利，使用第 19.9 节固定口径。
- **运行时指标**：队列 pending、模型时延、错误率、死信、实例健康，从监控系统读取或由受控后端代理聚合。

看板可同时展示三类数据，但每个卡片必须标注数据更新时间和来源。运行时指标不落入财务聚合表，Prometheus 不作为订单或收入事实源。

### 19.12.2 首屏布局

路由 `/dashboard`，默认时间范围“今天”，支持 24 小时、7 天、30 天和自定义范围：

1. KPI：新增用户、活跃用户、生成任务、成功率、消耗点数、支付收入、退款金额、估算毛利。
2. 生成趋势：提交/成功/失败折线，可按模型和分辨率下钻。
3. 模型可靠性：供应商健康、P95 时延、错误率、熔断状态和降级次数。
4. 业务漏斗：注册 -> 首次生成 -> 首次付费 -> 复购。
5. 风险区域：死信、额度对账 BLOCKED、支付回调失败、聚合延迟、配置应用失败。
6. 高风险操作流：最近的额度扣减、退款、角色变更、路由/配置/价格发布。

卡片只负责概览和跳转，不在看板内重复建设完整管理表格。桌面使用 12 列网格；平板 2 列；移动端单列。趋势图必须有表格替代文本，颜色不是唯一状态信号。

### 19.12.3 API

```text
GET /manage_web/dashboard/summary?from=&to=&timezone=
GET /manage_web/dashboard/generation-trend?from=&to=&bucket=&modelId=
GET /manage_web/dashboard/model-health?window=1h
GET /manage_web/dashboard/funnel?from=&to=
GET /manage_web/dashboard/alerts
GET /manage_web/dashboard/risky-actions
```

摘要响应包含 `dataAsOf`、`aggregationStatus` 和每个指标的 `value/previousValue/changeRate`。聚合延迟超过 15 分钟时页面显示“数据延迟”，不得用零值伪装成功。

## 19.13 管理端信息架构

```text
运营概览
  /dashboard
内容运营
  /tasks
  /moderation
  /inspirations
用户与财务
  /users
  /users/:id
  /billing/orders
  /billing/products
  /billing/rules
  /reports/revenue
  /reports/credits
模型运营
  /models/providers
  /models/catalog
  /models/routes
  /models/invocations
平台治理
  /admins
  /roles
  /system-config
  /audit-events
```

导航项按权限过滤，无权访问具体 URL 时服务端返回 403，前端显示无权限页而不是跳回首页。每个写页面必须处理：加载、空数据、校验失败、并发冲突、权限不足、保存中、成功、服务不可用八类状态。

高风险操作统一使用确认对话框，展示对象、影响、原因输入和是否需要复核；禁止使用 `window.prompt`。规则、路由和配置编辑使用“草稿编辑 -> 预览 -> 发布”连续工作流，离开未保存页面时提示。

## 19.14 跨模块一致性设计

### 19.14.1 事务边界

- 用户禁用：更新用户状态、撤销会话、写审计在同一数据库事务；会话缓存失效事件通过 outbox 发布。
- 额度调整执行：锁定调整单和额度账户、写流水、更新余额投影、标记 `EXECUTED`、写审计在同一事务。
- 规则/路由/配置发布：写发布状态、推进 revision、写审计/outbox 在同一事务。
- 报表聚合只读取事实并幂等 upsert 聚合，不跨事务修改事实。
- 模型调用记录每次尝试独立落库；它不能决定用户扣费是否成功。

### 19.14.2 幂等与并发

所有管理写请求支持 `Idempotency-Key` 或业务 `idempotencyKey`。编辑型资源使用 `version` 或 `If-Match`；并发冲突返回 `409 RESOURCE_VERSION_CONFLICT` 和当前版本摘要。发布接口对同一草稿重试返回既有发布结果。

### 19.14.3 Outbox 事件

```text
admin.permissions.changed
user.status.changed
pricing.rule.published
model.route.published
system.config.published
credit.adjustment.executed
payment.refund.succeeded
```

事件只通知缓存失效、Worker 拉取或聚合调度，数据库事实仍是恢复依据。消费者按 `eventId` 幂等，失败进入可查询死信。

## 19.15 安全与合规

- 管理端 Cookie 与用户端 Cookie 继续隔离；所有写请求启用 CSRF 防护。
- 权限必须在服务端校验，不能信任前端返回的 permissions。
- 高风险权限遵循最小授权和职责分离；申请人与复核人不得相同。
- 供应商和支付密钥只使用外部 Secret 引用；YAML 中已有测试值按项目规则保留，不在本设计任务中修改。
- 金额、点数和计数均用整数；时长、比例计算避免浮点累计误差。
- 日志和审计统一脱敏；普通查看权限不能获得完整手机号或凭据引用全值。
- 报表导出、审计导出和用户详情查看均记录访问审计。
- 管理 API 添加按管理员、IP 和动作维度限流；探测、导出、批量查询单独限额。

## 19.16 可观测性与告警

| 指标 | 标签 | 告警建议 |
| --- | --- | --- |
| `admin_api_request_total` | route、result | 5 分钟失败率 > 5% |
| `admin_permission_denied_total` | permission | 突增或单账号连续拒绝 |
| `credit_adjustment_total` | direction、status | FAILED > 0 或大额请求 |
| `model_route_selection_total` | route、version、target、outcome | 无可用目标 > 0 |
| `model_fallback_total` | from、to、reason | 10 分钟持续增长 |
| `provider_circuit_state` | provider、model、stage | OPEN 超过 5 分钟 |
| `aggregate_job_delay_seconds` | aggregate | 延迟 > 900 秒 |
| `config_apply_receipt_total` | revision、status | 任一生产实例 FAILED |
| `audit_write_failure_total` | domain | 任意失败立即告警 |

日志必须包含 `requestId`、`adminId`、`action`、`resourceId` 和错误码，但不记录 before/after 完整敏感内容。看板读取失败不影响生成和支付主链路。

## 19.17 数据迁移与上线顺序

### Phase A：治理基础（P0）

1. 新增 RBAC、`CreditAdjustment`、统一 `AuditEvent` 和 outbox 表。
2. 从现有 `AdminUser.role` 回填系统角色关系，双读比对权限。
3. Controller 从最低角色改为权限码，先记录差异再强制执行。
4. 上线管理员/角色页面、用户详情、额度复核、规则编辑和统一审计。
5. 完成后将硬编码权限列表降级为应急兼容路径。

### Phase B：模型运营（P1）

1. 新增供应商、模型、凭据引用、调用事实和路由表。
2. 从现有 YAML 生成只读 bootstrap 记录，不修改 YAML 原值。
3. Worker 先影子计算动态路由并与静态选择对比，不切流。
4. 发布首个动态路由，小流量灰度；验证路由快照、重试、熔断和成本记录。
5. 全量后仍保留最后已验证快照和 YAML 启动兜底。

### Phase C：报表与配置（P1/P2）

1. 新增聚合表和运行表，回填最近 90 天并与交易明细对账。
2. 上线报表只读页和导出，再启用收入/成本看板。
3. 新增系统配置定义和版本；先迁移低风险业务开关。
4. 验证所有实例生效回执后，逐项迁移动态运行策略。

### Phase D：运营看板（P2）

1. 上线摘要、趋势、模型健康和风险 API。
2. 建立数据新鲜度和空值语义。
3. 完成权限、下钻、可访问性和多视口回归。

### 回滚原则

- RBAC 可临时切回旧角色映射，但数据库关系和审计记录不删除。
- 动态路由切回最后已验证版本；无快照时才使用 YAML bootstrap。
- 配置回滚发布新版本，不修改历史版本。
- 聚合或看板异常只关闭读入口，不影响交易和生成主链路。
- 迁移只增不改；旧列、旧表在至少一个稳定发布周期后另行清理。

## 19.18 测试与验收

### 19.18.1 Admin IAM

- VIEWER 请求任意写接口均返回 403，前端隐藏入口不计作安全验收。
- 修改角色权限后，已登录管理员下一次请求即按新权限执行，旧 Session 权限不可继续使用。
- 不能禁用自己、不能移除最后一个 ADMIN、不能创建未知权限码。
- 两个管理员并发编辑同一角色时只有一个版本成功。

### 19.18.2 用户与额度

- 用户详情的任务、订单、流水只能返回目标用户数据。
- 禁用用户后新登录、生成和下单均失败，历史数据保持可查询。
- 同一额度调整幂等键重试 10 次只产生一条调整单和一条额度流水。
- 扣减不会使用 reserved，不会导致 available 或 total 为负。
- 大额赠送和所有扣减不能由申请人自审。

### 19.18.3 模型与路由

- 凭据 API、日志、审计和页面源码中均不存在密钥明文。
- 同一任务和路由版本的稳定哈希选择可重复，能力不匹配目标不会被调用。
- 429/5xx 可重试并降级，审核拒绝和非法参数不会降级。
- 熔断打开后不再向故障目标发送常规请求，半开探测成功后恢复。
- 路由发布后新任务使用新版本，旧任务重试继续保存可解释的版本/目标事实。
- 平台降级和重试不重复扣用户点数。

### 19.18.4 计费与报表

- 模型和尺寸条件参与规则匹配；多规则同等命中时拒绝提交。
- 规则发布前能检测时间窗口和请求空间冲突。
- 历史任务始终展示提交时的规则版本和费用。
- 任意日报数字可下钻到原始订单、退款、流水或调用事实，并能重新聚合得到相同校验和。
- 净收入、点数和模型成本的时区、币种、退款归属日符合文档口径。

### 19.18.5 配置、审计和看板

- 非动态配置发布后明确提示重启，不在运行中部分生效。
- 动态配置 checksum 无效时实例拒绝应用并继续使用上一快照。
- 所有高风险管理动作均有 `SUCCEEDED/FAILED/DENIED` 审计，敏感字段被遮罩。
- 审计表业务账号无法 UPDATE/DELETE。
- 聚合延迟时看板显示延迟状态，不能显示伪造的零值。
- 1440x900、800x1024、390x844 下无文本溢出、控件遮挡和不可达操作。

## 19.19 建议任务拆分

| 顺序 | 子任务 | 依赖 | 独立验收产物 |
| --- | --- | --- | --- |
| 1 | RBAC 数据与鉴权迁移 | 无 | 权限码服务端生效、兼容比对通过 |
| 2 | 管理员和角色页面 | 1 | 账号、角色、权限、会话管理 E2E |
| 3 | 用户详情与统一审计 | 1 | 六标签详情页和跨领域审计查询 |
| 4 | 额度调整审批 | 1、3 | 赠送/扣减/双人复核与流水对账 |
| 5 | 计费规则工作台 | 1 | 编辑、预览、影响分析、发布回滚 |
| 6 | 供应商与模型注册表 | 1 | 凭据引用、探测、模型能力管理 |
| 7 | 路由与 Worker 接入 | 6 | 灰度、重试、熔断、降级和快照 |
| 8 | 调用成本与日聚合 | 6、7 | 五类聚合表和明细对账 |
| 9 | 财务报表与导出 | 8 | 收入、点数、成本和异步导出 |
| 10 | 系统配置中心 | 1 | 版本发布、实例回执和回滚 |
| 11 | 运营看板 | 3、8、9 | KPI、趋势、健康、风险和下钻 |

每个子任务的完成定义必须包含：迁移、接口契约、服务测试、权限测试、管理端交互、审计、指标、故障场景和回滚验证。不要将十一项能力作为一次大发布同时上线。
