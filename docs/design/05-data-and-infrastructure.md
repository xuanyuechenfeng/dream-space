# 05 数据库、Redis 与对象存储详细设计

## 5.1 PostgreSQL 保持原则

`bak/packages/db/prisma/schema.prisma` 和 `bak/packages/db/prisma/migrations` 是 schema 基线。重构不得通过 Java entity 自动建表或重命名字段；生产只执行版本化 SQL。Spring 启动禁止 `ddl-auto=create/update`。

### 表分组

| 分组 | 表 |
| --- | --- |
| 内容 | `Inspiration` |
| 用户认证 | `User`、`VerificationCode`、`UserSession`、`AgreementAcceptance` |
| 管理认证 | `AdminUser`、`AdminVerificationCode`、`AdminSession` |
| 生成 | `GenerationSession`、`GenerationTask`、`GenerationResult`、`GenerationDeadLetter`、`ReferenceUpload`、`GenerationTaskEvent` |
| 额度 | `QuotaAccount`、`QuotaLedgerEntry`、`QuotaReconciliationRun`、`QuotaReconciliationFinding` |

MyBatis Mapper 按业务域拆包：`auth`、`inspiration`、`generation`、`quota`、`admin`。Mapper 方法返回显式 DTO/record；JSON 字段 `draft/referenceImageUrls/payload/details` 使用 Jackson `JsonNode` 或明确 record，禁止 `Map<String,Object>` 在业务层扩散。

## 5.2 事务和并发

- 额度账户更新使用数据库行锁或条件更新；条件不满足即视为额度不足/并发冲突。
- 任务创建唯一键为 `(user_id,idempotency_key)`；ledger 唯一键阻止重复 reserve/consume/release。
- Worker 抢占任务使用状态 + lastAttemptKey 条件更新；事件按自增 id 排序，SSE cursor 使用 event id。
- 管理员发布灵感使用乐观更新时间或版本校验，避免覆盖其他运营人员编辑。

## 5.3 Redis 设计

| Key/Stream | 用途 | TTL/清理 |
| --- | --- | --- |
| `generation` | 任务 Stream | 由保留策略和 pending reclaim 管理 |
| `generation-workers` | consumer group | 不设置 TTL |
| `auth:code:<challengeId>`（如启用） | 验证码限流/短期缓存 | 与 AUTH_CODE_TTL_SECONDS 一致 |
| `rate:<scope>:<id>` | API/短信/上传限流 | 滑动窗口 TTL |
| `sse:<taskId>`（可选） | 实时事件 fan-out | 终态后短期保留 |

Redis 不是任务事实来源；任务状态、额度和事件最终以 PostgreSQL 为准。Redis 故障时 API readiness 失败或降级为不可提交，不在内存中伪造成功任务。

## 5.4 对象存储

允许的对象键：

```text
references/<id>/<file>.webp
results/<taskId>/<resultId>.webp
thumbnails/<taskId>/<resultId>.webp
```

所有 adapter 共用 `ObjectKeyPolicy`：前缀白名单、字符白名单、扩展名白名单、根目录逃逸检查。Local FS 使用原子临时文件 + rename；S3 使用 PutObject/GetObject/DeleteObject 和短期签名 URL。

上传原图先写临时 key，数据库成功后转正；任务结果先写主图再缩略图，任一步失败执行清理。删除必须幂等，404 不阻塞业务补偿。

## 5.5 配置和基础设施

API/Worker 环境变量保持旧名称：`DATABASE_URL`、`REDIS_URL`、`OBJECT_STORAGE_MODE`、`LOCAL_STORAGE_DIR`、S3 参数、`AUTH_CODE_TTL_SECONDS`、`AUTH_SESSION_DAYS`、`QUOTA_RECONCILIATION_*`。新增：`OPENAI_BASE_URL`、`OPENAI_API_KEY`、`OPENAI_MODEL`、`OPENAI_TIMEOUT_MS`、`OPENAI_MAX_ATTEMPTS`。

Docker Compose 继续提供 PostgreSQL 17、Redis 8、MinIO；新增 API/Worker/前台构建镜像，健康检查分别调用 `/health/live`、`/health/ready`。生产不把 `.env` 打入镜像。
