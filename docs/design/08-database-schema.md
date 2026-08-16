# 08 数据库字段级设计

## 8.1 基线与命名

数据库以 `bak/packages/db/prisma/schema.prisma` 及其迁移 SQL 为唯一结构基线。表名和列名保留 Prisma 生成的大小写（例如 `GenerationTask.createdAt`），Java Mapper 必须显式引用双引号标识符，禁止把字段自动转换为 snake_case。生产环境只执行版本化迁移，禁止 ORM 自动建表或修改表结构。

所有 `DateTime` 使用 PostgreSQL `timestamptz` 并以 UTC 写入；金额、数量、次数均为非负整数；JSON 字段必须经过 schema 校验后再进入业务层。

## 8.2 枚举

| 枚举 | 允许值 | 用途 |
| --- | --- | --- |
| `InspirationCategory` | `PORTRAIT`、`PHOTOGRAPHY`、`ANIME`、`ILLUSTRATION`、`DESIGN` | 灵感分类 |
| `InspirationStatus` | `DRAFT`、`PUBLISHED`、`ARCHIVED` | 灵感发布状态 |
| `InspirationSourceType` | `AI_PUBLIC_GALLERY`、`LICENSED`、`INTERNAL` | 素材来源 |
| `GenerationTaskStatus` | `QUEUED`、`GENERATING`、`SUCCEEDED`、`PARTIALLY_SUCCEEDED`、`FAILED`、`CANCELLED` | 生成任务状态 |
| `QuotaLedgerType` | `GRANT`、`RESERVE`、`CONSUME`、`RELEASE` | 额度流水类型 |
| `ModerationStatus` | `PENDING`、`APPROVED`、`REJECTED` | 输入/输出审核 |
| `GenerationRatio` | `smart`、`21:9`、`16:9`、`3:2`、`4:3`、`1:1`、`3:4`、`2:3`、`9:16` | 生成比例，数据库值使用 `@map` 值 |
| `GenerationResolution` | `2K`、`4K` | 生成分辨率 |
| `AdminRole` | `VIEWER`、`OPERATOR`、`ADMIN` | 管理端 RBAC |
| `QuotaReconciliationRunStatus` | `RUNNING`、`COMPLETED`、`FAILED` | 对账运行状态 |
| `QuotaReconciliationFindingKind` | `MISSING_RESERVE`、`MISSING_RELEASE`、`MISSING_CONSUME`、`SETTLEMENT_AMOUNT_MISMATCH`、`TOTAL_DRIFT`、`RESERVED_DRIFT`、`AVAILABLE_DRIFT` | 对账问题类型 |
| `QuotaReconciliationFindingStatus` | `OPEN`、`REPAIRED`、`BLOCKED` | 对账问题状态 |

## 8.3 表和字段

下表中的 `PK`、`FK`、`UQ`、`IDX` 分别表示主键、外键、唯一约束和索引。未标注可空的字段均为 `NOT NULL`。

### 内容与认证

| 表 | 字段（类型与约束） | 关系/索引 |
| --- | --- | --- |
| `Inspiration` | `id varchar PK`、`slug varchar UQ`、`title varchar`、`prompt text`、`category InspirationCategory`、`imagePath varchar`、`thumbnailPath varchar`、`width int`、`height int`、`modelName varchar`、`ratio varchar`、`resolutionLabel varchar`、`authorDisplayName varchar`、`sourceType InspirationSourceType`、`sourceName varchar`、`sourceUrl varchar?`、`licenseBasis varchar`、`isAiGenerated boolean default true`、`likeCount int default 0`、`sortOrder int default 0`、`status InspirationStatus default DRAFT`、`publishedAt timestamptz?`、`createdAt timestamptz`、`updatedAt timestamptz` | `IDX(status, category, sortOrder)`、`IDX(status, publishedAt)` |
| `User` | `id varchar PK`、`phone varchar UQ`、`createdAt timestamptz`、`updatedAt timestamptz` | 被 session、任务、额度、上传和协议表引用 |
| `VerificationCode` | `id varchar PK`、`phone varchar`、`codeHash varchar`、`expiresAt timestamptz`、`consumedAt timestamptz?`、`attempts int default 0`、`createdAt timestamptz` | `IDX(phone, createdAt)` |
| `UserSession` | `id varchar PK`、`tokenHash varchar UQ`、`userId varchar FK`、`expiresAt timestamptz`、`createdAt timestamptz`、`lastSeenAt timestamptz` | `FK userId -> User.id ON DELETE CASCADE`、`IDX(userId, expiresAt)` |
| `AgreementAcceptance` | `id varchar PK`、`userId varchar FK`、`version varchar`、`termsAccepted boolean default false`、`privacyAccepted boolean default false`、`aiTermsAccepted boolean default false`、`acceptedAt timestamptz` | `UQ(userId, version)`、级联删除 |
| `AdminUser` | `id varchar PK`、`phone varchar UQ`、`displayName varchar`、`role AdminRole default VIEWER`、`active boolean default true`、`createdAt timestamptz`、`updatedAt timestamptz` | `IDX(active, role)` |
| `AdminVerificationCode` | `id varchar PK`、`phone varchar`、`codeHash varchar`、`expiresAt timestamptz`、`consumedAt timestamptz?`、`attempts int default 0`、`createdAt timestamptz` | `IDX(phone, createdAt)` |
| `AdminSession` | `id varchar PK`、`tokenHash varchar UQ`、`adminUserId varchar FK`、`expiresAt timestamptz`、`createdAt timestamptz`、`lastSeenAt timestamptz` | `FK adminUserId -> AdminUser.id ON DELETE CASCADE`、`IDX(adminUserId, expiresAt)` |

### 生成与资源

| 表 | 字段（类型与约束） | 关系/索引 |
| --- | --- | --- |
| `GenerationSession` | `id varchar PK`、`userId varchar FK`、`title varchar`、`draft jsonb?`、`createdAt timestamptz`、`updatedAt timestamptz` | `FK userId -> User.id ON DELETE CASCADE`、`IDX(userId, updatedAt)` |
| `GenerationTask` | `id varchar PK`、`sessionId varchar FK`、`userId varchar FK`、`status GenerationTaskStatus default QUEUED`、`prompt text`、`model varchar`、`ratio GenerationRatio`、`resolution GenerationResolution`、`imageCount int`、`referenceImageUrls jsonb`、`unitCost int`、`totalCost int`、`idempotencyKey varchar`、`queueJobId varchar?`、`attempts int default 0`、`lastAttemptKey varchar?`、`errorCode varchar?`、`errorMessage varchar?`、`inputModerationStatus ModerationStatus default PENDING`、`outputModerationStatus ModerationStatus default PENDING`、`startedAt timestamptz?`、`completedAt timestamptz?`、`createdAt timestamptz`、`updatedAt timestamptz` | `UQ(userId, idempotencyKey)`、`IDX(sessionId, createdAt)`、`IDX(userId, status, createdAt)`；两个 user/session 外键均级联 |
| `GenerationResult` | `id varchar PK`、`taskId varchar FK`、`index int`、`imagePath varchar`、`objectKey varchar? UQ`、`thumbnailObjectKey varchar? UQ`、`checksumSha256 varchar?`、`width int`、`height int`、`mimeType varchar`、`byteSize int`、`thumbnailWidth int?`、`thumbnailHeight int?`、`thumbnailByteSize int?`、`moderationStatus ModerationStatus default PENDING`、`isAiGenerated boolean default true`、`createdAt timestamptz` | `UQ(taskId, index)`、`IDX(taskId)`；级联删除 |
| `ReferenceUpload` | `id varchar PK`、`userId varchar FK`、`objectKey varchar UQ`、`originalFilename varchar`、`mimeType varchar`、`byteSize int`、`width int`、`height int`、`checksumSha256 varchar`、`createdAt timestamptz`、`deletedAt timestamptz?` | `IDX(userId, createdAt)`；级联删除 |
| `GenerationTaskEvent` | `id bigint PK auto_increment`、`taskId varchar FK`、`type varchar`、`status GenerationTaskStatus`、`payload jsonb`、`createdAt timestamptz` | `IDX(taskId, id)`；SSE 使用 `id` 作为 cursor |
| `GenerationDeadLetter` | `id varchar PK`、`taskId varchar FK UQ`、`errorCode varchar`、`errorMessage varchar`、`attempts int`、`payload jsonb`、`createdAt timestamptz`、`resolvedAt timestamptz?` | `IDX(resolvedAt, createdAt)`；级联删除 |

### 额度与对账

| 表 | 字段（类型与约束） | 关系/索引 |
| --- | --- | --- |
| `QuotaAccount` | `userId varchar PK/FK`、`total int default 100`、`available int default 100`、`reserved int default 0`、`createdAt timestamptz`、`updatedAt timestamptz` | `userId -> User.id ON DELETE CASCADE`；一用户一账户 |
| `QuotaLedgerEntry` | `id varchar PK`、`userId varchar FK`、`taskId varchar? FK`、`type QuotaLedgerType`、`amount int`、`balanceAfter int`、`idempotencyKey varchar UQ`、`createdAt timestamptz` | `IDX(userId, createdAt)`、`IDX(taskId)`；task 删除时 `taskId` 置空 |
| `QuotaReconciliationRun` | `id varchar PK`、`windowKey varchar UQ`、`status QuotaReconciliationRunStatus default RUNNING`、`startedAt timestamptz`、`completedAt timestamptz?`、`scannedUsers int default 0`、`scannedTasks int default 0`、`mismatchCount int default 0`、`repairedCount int default 0`、`errorMessage varchar?`、`createdAt timestamptz` | `IDX(createdAt, status)` |
| `QuotaReconciliationFinding` | `id varchar PK`、`runId varchar FK`、`userId varchar FK`、`taskId varchar?`、`kind QuotaReconciliationFindingKind`、`status QuotaReconciliationFindingStatus default OPEN`、`idempotencyKey varchar`、`expectedAmount int?`、`actualAmount int?`、`details jsonb`、`repairedAt timestamptz?`、`createdAt timestamptz` | `UQ(runId, idempotencyKey)`、`IDX(runId, status)`、`IDX(userId, createdAt)`、`IDX(taskId, kind)`；run/user/account 均级联 |

## 8.4 事务不变量

1. `QuotaAccount.available + QuotaAccount.reserved = QuotaAccount.total` 始终成立。
2. `RESERVE` 只能从 `available` 转入 `reserved`；成功任务用等额 `CONSUME` 扣除 reserved，失败/取消用等额 `RELEASE` 退回 available。
3. `GenerationTask.totalCost = unitCost * imageCount`，`4K` 单张成本为 2，`2K` 单张成本为 1。
4. `(userId, idempotencyKey)` 和 `QuotaLedgerEntry.idempotencyKey` 是幂等边界；重复请求不得重复扣费或重复投递。
5. 任务事件只追加不更新；SSE 重连从 `GenerationTaskEvent.id` 回放并按 id 去重。

## 8.5 Mapper 实现约束

- MyBatis SQL 中对 camelCase 表/列名使用双引号；禁止依赖全局驼峰转换。
- `JsonNodeTypeHandler` 只处理已知 JSON 列；未知 JSON 结构在 service 层拒绝。
- 数据库枚举通过显式 `DatabaseEnumTypeHandler` 映射，`GenerationRatio` 使用数据库中的冒号值。
- `SELECT ... FOR UPDATE` 仅用于额度账户和需要状态抢占的任务；普通列表查询不持锁。
- 迁移文件按时间戳排序执行，并在启动日志记录已执行的最高版本。
