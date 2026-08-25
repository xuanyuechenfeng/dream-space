# 用户账单、用户管理、计费规则与支付订单设计

## 1. 目标与边界

本方案补齐四个产品能力：

1. 用户可查看额度流水、生成消费和支付订单。
2. 管理员可检索和管理用户、会话、额度和订单。
3. 运营人员可配置版本化的生成计费规则。
4. 平台可创建支付订单、接收支付回调、发放额度并处理退款。

核心边界：

- `QuotaAccount/QuotaLedgerEntry` 是内部点数账，不是人民币账。
- `BillingOrder/PaymentTransaction/Refund` 是金额账，不直接修改订单金额来表达退款。
- 用户账单是多个不可变记录的查询投影，不再建立一个可被重复写入的“账单余额表”。
- 支付供应商通过 adapter 隔离，业务层不依赖微信、支付宝或 Stripe 的字段。

当前生成系统的额度不变量保持不变：`total = available + reserved + used`。其中 `used = total - available - reserved`。支付购买的额度通过 `GRANT` 进入 `total`，生成任务仍使用 `RESERVE -> CONSUME/RELEASE`。

## 2. 领域模型

```text
User
 ├─ UserSession
 ├─ QuotaAccount ── QuotaLedgerEntry
 ├─ GenerationTask ── GenerationCharge
 └─ BillingOrder ── PaymentTransaction ── Refund

PricingRule (immutable version)
 └─ GenerationCharge / GenerationTask.ruleVersion

CreditProduct
 └─ BillingOrder
```

### 2.1 术语约定

- **Credit**：内部点数，不能直接当作人民币。
- **Quota**：点数余额的运行快照。
- **PricingRule**：生成一次操作应消耗多少点数。
- **CreditProduct**：用户用现金购买多少点数。
- **Order**：购买意图和商业订单。
- **PaymentTransaction**：支付渠道的支付尝试/回调。
- **BillingStatement**：由订单、支付、额度流水和生成任务聚合出的只读视图。

## 3. 数据模型

所有金额使用整数最小货币单位（例如人民币分），所有时间使用 PostgreSQL `timestamptz` UTC。订单、支付、退款、额度流水和审计记录均追加写入，状态改变必须保留事件或审计记录。

### 3.1 用户管理

扩展现有 `User`：

| 字段 | 说明 |
|---|---|
| `status` | `ACTIVE`、`DISABLED`、`DELETED` |
| `displayName` | 可选显示名；手机号仍是登录标识 |
| `disabledAt`、`disabledBy`、`disabledReason` | 禁用审计 |
| `lastLoginAt` | 最近成功登录时间 |
| `deletedAt` | 软删除时间 |

新增索引：`(status, createdAt)`、`(phone)`、`(lastLoginAt)`。

账号禁用后：拒绝登录、拒绝创建生成任务和支付订单；允许读取历史账单的策略由产品决定，默认允许只读。已有会话必须立即失效，可按用户删除或标记 `revokedAt`。

### 3.2 计费规则

新增 `PricingRule`：

| 字段 | 说明 |
|---|---|
| `id` | 规则 ID |
| `code` | 稳定业务编码，如 `IMAGE_GENERATION` |
| `version` | 单调递增版本号 |
| `operation` | `IMAGE_GENERATION` 等 |
| `modelPattern` | 模型匹配条件；支持精确值或通配配置 |
| `resolution` | `2K`、`4K` 或 `ANY` |
| `minWidth/maxWidth/minHeight/maxHeight` | 尺寸匹配条件 |
| `unitCreditCost` | 单张点数 |
| `formula` | 初期固定为 `unitCreditCost * imageCount`，保留扩展字段 |
| `effectiveFrom/effectiveTo` | 生效窗口，不能重叠 |
| `status` | `DRAFT`、`ACTIVE`、`RETIRED` |
| `createdBy/createdAt` | 创建审计 |

规则选择必须产生唯一结果；无规则时拒绝提交，不允许静默使用默认价。提交任务时把 `ruleId`、`ruleVersion`、`unitCost`、`totalCost` 快照到 `GenerationTask`，并写入对应的额度流水 metadata。

新增 `PricingChangeAudit` 或统一审计表，记录发布、下线、复制、回滚和操作者。

### 3.3 可售点数产品

新增 `CreditProduct`：

| 字段 | 说明 |
|---|---|
| `id/code` | 产品标识 |
| `name` | 用户展示名 |
| `creditAmount` | 购买后发放点数 |
| `amountMinor/currency` | 售价和币种 |
| `validityDays` | 点数有效期；首期可为 null 表示永久 |
| `status` | `DRAFT`、`ACTIVE`、`INACTIVE` |
| `sortOrder` | 前台排序 |
| `metadata` | 展示扩展字段 |

产品价格和点数一旦被订单引用，不允许原地修改；修改必须复制为新版本或新产品。

### 3.4 订单、支付和退款

新增 `BillingOrder`：

| 字段 | 说明 |
|---|---|
| `id/orderNo` | 内部 ID 和用户可见订单号 |
| `userId/productId` | 购买人和产品 |
| `quantity` | 产品购买数量 |
| `creditAmount` | 订单应发放点数快照 |
| `amountMinor/currency` | 订单金额快照 |
| `status` | `CREATED`、`PAYING`、`PAID`、`CANCELLED`、`EXPIRED`、`REFUNDING`、`REFUNDED`、`PARTIALLY_REFUNDED` |
| `provider` | 支付渠道 |
| `idempotencyKey` | 用户创建订单幂等键 |
| `expiresAt/paidAt` | 过期和支付时间 |
| `createdAt/updatedAt` | 时间 |

唯一约束：`orderNo`、`(userId, idempotencyKey)`。

新增 `PaymentTransaction`：

| 字段 | 说明 |
|---|---|
| `id/orderId` | 支付尝试和所属订单 |
| `provider/providerTransactionId` | 渠道和渠道流水号 |
| `status` | `INITIATED`、`PENDING`、`SUCCEEDED`、`FAILED`、`CLOSED` |
| `amountMinor/currency` | 渠道确认金额 |
| `signatureVerified` | 回调验签结果 |
| `providerEventId` | 回调事件幂等键 |
| `rawPayloadRef` | 脱敏原文引用；禁止保存密钥 |
| `paidAt/createdAt/updatedAt` | 时间 |

唯一约束：`(provider, providerTransactionId)`、`(provider, providerEventId)`。

新增 `Refund`：`id、orderId、paymentTransactionId、amountMinor、reason、status、providerRefundId、idempotencyKey、createdAt、completedAt`。退款必须走独立记录，不能把原支付改成负数。

### 3.5 额度流水扩展

保留现有 `QuotaLedgerEntry` 的 `RESERVE/CONSUME/RELEASE` 语义，增加：

- `sourceType`：`INITIAL_GRANT`、`ORDER`、`PROMOTION`、`ADMIN_ADJUSTMENT`、`REFUND`。
- `sourceId`：订单、退款或调整记录 ID。
- `ruleId/ruleVersion`：生成消费使用的计费规则快照。
- `reasonCode`、`metadata`：展示和审计信息。
- `expiresAt`：可选的点数有效期。

首期不扩展为负数金额；所有增加点数的操作继续用 `GRANT`，通过 `sourceType` 区分来源。初始 100 点必须在创建额度账户的同一事务中写入 `GRANT(INITIAL_GRANT)`，修复当前“余额存在但没有 grant 流水”的问题。

如果未来需要复杂的过期点数或多批次扣减，再引入 `CreditLot`，按 FIFO/最早过期优先分配；本期不把该复杂度混入生成任务事务。

### 3.6 用户账单查询投影

不新增可写账单余额表。提供数据库 view 或 service projection：

- 订单支付：订单金额、支付渠道、状态、支付时间。
- 点数收入：`GRANT`，显示来源、点数、有效期。
- 生成消费：`CONSUME` 关联任务、模型、分辨率、规则版本。
- 释放/退款：`RELEASE` 或 `GRANT(sourceType=REFUND)`。

每条记录返回 `entryType、occurredAt、creditsDelta、amountMinor、currency、orderNo、taskId、description、status`。金额账和点数账分开展示，避免把 1 点误显示成 1 元。

## 4. 关键状态机

### 4.1 订单

```text
CREATED -> PAYING -> PAID
CREATED -> EXPIRED/CANCELLED
PAYING -> FAILED/EXPIRED
PAID -> REFUNDING -> REFUNDED/PARTIALLY_REFUNDED
```

只有 `PAID` 可以发放点数。重复成功回调必须返回同一订单结果，不得重复 `GRANT`。

### 4.2 支付回调

1. 验证 provider 签名、商户号、订单号、金额、币种。
2. 使用 `(provider, providerEventId)` 幂等落库。
3. 锁定订单，检查金额和产品快照。
4. 订单从 `PAYING/CREATED` 转为 `PAID`。
5. 同一事务写 `GRANT(sourceType=ORDER, sourceId=orderId)` 和 outbox 事件。
6. 回调响应成功；通知失败由 outbox 重试，不回滚已确认支付。

### 4.3 生成计费

```text
submit: resolve active PricingRule
      -> snapshot ruleId/version/unitCost/totalCost
      -> reserve credits
success -> consume reserved credits
failure/cancel -> release reserved credits
```

规则变更不会影响已提交任务。部分成功的首期策略仍按任务快照扣费；若重新支持多张图，必须新增“按结果张数结算”规则并在产品层明确展示。

## 5. API 设计

### 5.1 用户端

| 方法 | 路径 | 说明 |
|---|---|---|
| `GET` | `/dream_web/account` | 用户资料、状态、余额摘要 |
| `GET` | `/dream_web/account/ledger` | 点数流水，分页、类型、时间、任务筛选 |
| `GET` | `/dream_web/account/orders` | 用户订单列表 |
| `GET` | `/dream_web/account/orders/{orderNo}` | 订单详情和支付状态 |
| `GET` | `/dream_web/billing/products` | 当前可购买点数产品 |
| `POST` | `/dream_web/billing/orders` | 创建订单，要求 `idempotencyKey/productId/quantity/provider` |
| `POST` | `/dream_web/billing/orders/{orderNo}/cancel` | 取消未支付订单 |
| `GET` | `/dream_web/billing/orders/{orderNo}/payment` | 获取支付渠道参数/二维码引用 |
| `GET` | `/dream_web/moderation/cases` | 现有审核记录，前端补充展示 |

不提供用户侧“修改余额”接口。支付回调只能由服务端 provider webhook 调用。

### 5.2 管理端

用户：

| 方法 | 路径 | 权限 |
|---|---|---|
| `GET` | `/manage_web/users` | `users:read` |
| `GET` | `/manage_web/users/{id}` | `users:read` |
| `POST` | `/manage_web/users/{id}/disable` | `users:write` |
| `POST` | `/manage_web/users/{id}/enable` | `users:write` |
| `POST` | `/manage_web/users/{id}/revoke-sessions` | `users:write` |
| `GET` | `/manage_web/users/{id}/ledger` | `billing:read` |
| `POST` | `/manage_web/users/{id}/credit-adjustments` | `billing:write` |

计费：

| 方法 | 路径 | 权限 |
|---|---|---|
| `GET` | `/manage_web/billing/orders` | `billing:read` |
| `GET` | `/manage_web/billing/orders/{orderNo}` | `billing:read` |
| `POST` | `/manage_web/billing/orders/{orderNo}/refund` | `billing:write` |
| `GET` | `/manage_web/billing/rules` | `pricing:read` |
| `POST` | `/manage_web/billing/rules` | `pricing:write` |
| `POST` | `/manage_web/billing/rules/{id}/publish` | `pricing:write` |
| `POST` | `/manage_web/billing/rules/{id}/retire` | `pricing:write` |
| `GET` | `/manage_web/billing/products` | `pricing:read` |
| `POST/PATCH` | `/manage_web/billing/products` | `pricing:write` |
| `GET` | `/manage_web/audit-events` | `audit:read` |

扩展现有权限集合：`users:read/write`、`billing:read/write`、`pricing:read/write`、`audit:read`、`orders:refund`。`VIEWER` 只读；`OPERATOR` 可处理用户和退款；`ADMIN` 可发布规则、管理产品和管理员权限。

### 5.3 错误码

`ACCOUNT_DISABLED`、`PRICING_RULE_NOT_FOUND`、`PRICING_RULE_CONFLICT`、`ORDER_IDEMPOTENCY_CONFLICT`、`ORDER_EXPIRED`、`PAYMENT_AMOUNT_MISMATCH`、`PAYMENT_SIGNATURE_INVALID`、`PAYMENT_EVENT_DUPLICATE`、`REFUND_NOT_ALLOWED`、`CREDIT_ADJUSTMENT_REQUIRES_REASON`。

## 6. 管理端和用户端页面

### 用户端

- `/account`：资料、账号状态、当前点数。
- `/account/ledger`：点数流水和生成消费，支持任务详情跳转。
- `/account/orders`：订单列表、支付中、已支付、退款状态。
- `/billing/products`：点数套餐、价格、有效期、支付入口。

设置菜单中的“创作额度”改为链接到 `/account/ledger`；水印开关仍需明确是本地偏好还是服务端生成参数，不能在账单模块隐式处理。

### 管理端

- `/users`：手机号脱敏搜索、状态、注册时间、最近登录、余额、分页。
- `/users/{id}`：资料、会话、任务、额度流水、订单、操作审计。
- `/billing/orders`：订单状态、渠道、金额、用户、时间筛选。
- `/billing/rules`：草稿、规则校验、发布、下线、版本对比。
- `/billing/products`：套餐上下架和版本化价格。
- `/audit-events`：用户、订单、规则、额度调整的审计查询。

所有写操作必须显示影响范围和原因；金额、点数、订单号和操作人不能只在 toast 中反馈。

## 7. 事务与一致性

### 创建生成任务

在现有生成事务中增加：

1. 锁定并读取当前生效规则。
2. 写入任务的规则和费用快照。
3. 在额度账户上执行 reserve。
4. 写 `RESERVE` 流水及 rule metadata。
5. 写 outbox 事件后提交，再投递 Redis。

### 支付成功

支付回调必须使用数据库事务和唯一事件键。订单状态、支付交易和 `GRANT` 必须同事务；任何重试都返回既有 `PAID` 结果。

### 退款

退款成功后：

- 现金侧写 `Refund`。
- 点数侧写反向补偿策略：未使用的已购点数可冻结/扣回；已消费点数不能静默变负，必须进入人工审核或负债状态。
- 订单进入 `REFUNDED/PARTIALLY_REFUNDED`。
- 记录管理员、原因、原订单和支付渠道退款号。

建议首期只允许“未使用订单全额退款”，避免复杂的已消费点数追缴。

## 8. 安全与合规

- 支付回调必须验签、校验金额/币种/商户号和订单归属。
- provider 原始 payload 脱敏或加密保存，日志不得输出支付密钥、完整手机号或支付凭证。
- 管理端所有额度调整、退款、规则发布、用户禁用、会话撤销写不可变审计。
- 用户账单只能读取自己的订单和流水；管理员结果和用户数据继续通过后端权限校验。
- 金额使用 `long`/`BigInteger` 语义，不使用浮点数。
- 订单号、支付事件号、幂等键和 webhook 重试必须有数据库唯一约束。

## 9. 迁移顺序

### Phase 1：账单可见化

1. 给 `User` 增加状态和运营字段。
2. 给额度流水增加来源、规则版本和 metadata。
3. 初始额度创建时补写 `GRANT`。
4. 增加用户账单查询 API 和用户端页面。
5. 增加管理端用户只读页面和审计查询。

### Phase 2：规则配置化

1. 创建 `PricingRule`、发布校验和版本快照。
2. 生成任务改用规则服务计算费用。
3. 增加规则管理页面和权限。
4. 对账服务增加规则快照校验。

### Phase 3：支付订单

1. 创建 `CreditProduct`、`BillingOrder`、`PaymentTransaction`、`Refund`。
2. 先接支付沙箱或 mock provider，跑通 webhook 幂等。
3. 支付成功后发放 `GRANT(sourceType=ORDER)`。
4. 增加订单中心、套餐页和管理端订单查询。
5. 只开放未消费订单全额退款。

### Phase 4：生产化

1. 接入真实支付渠道和密钥托管。
2. 增加 outbox、死信、回调补偿和对账任务。
3. 增加备份恢复、支付差错处理和监控告警。
4. 通过灰度开关逐步开放真实支付。

## 10. 验收标准

- 同一用户重复提交同一订单幂等键只产生一个订单。
- 同一支付回调重试 10 次只发放一次点数。
- 回调金额不符、签名错误、订单已过期均不改变余额。
- 规则发布后，新任务使用新版本，历史任务账单仍显示旧版本和旧费用。
- 初始赠送、购买、退款、管理员调整都能在额度流水中解释来源。
- 生成成功、部分成功、失败、取消、重试不会重复扣减。
- 用户被禁用后不能登录、生成或下单；历史账单可按策略只读。
- VIEWER 不能修改用户、额度、订单、规则；所有越权请求返回 `403`。
- 用户只能读取自己的账单和订单，不能通过任务、订单或结果 ID 越权读取他人数据。
- 账单金额和点数分别展示，且可从明细汇总回订单和额度快照。

