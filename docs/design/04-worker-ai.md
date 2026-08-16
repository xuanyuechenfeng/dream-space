# 04 Worker 与 Spring AI 详细设计

## 4.1 消息模型

Redis Stream `generation` 的消息必须包含：

```json
{
  "schemaVersion": 1,
  "taskId": "task_cuid",
  "attemptKey": "task_cuid:1",
  "attemptNumber": 1,
  "maxAttempts": 3,
  "queuedAt": "2026-08-16T00:00:00Z"
}
```

消费组为 `generation-workers`。每条消息先转为 `GenerationJob`，处理成功 ack；可重试异常进入 pending/retry；不可重试或达到上限写 dead-letter 后 ack，避免无限 pending。

## 4.2 处理状态机

```text
QUEUED
  -> GENERATING
      -> SUCCEEDED
      -> PARTIALLY_SUCCEEDED
      -> FAILED
      -> CANCELLED
```

状态转换必须集中在 `GenerationTaskStateMachine`，所有转换写 `GenerationTaskEvent`。Worker 开始前使用 `(taskId, attemptKey)` 条件更新，防止 Redis redelivery 导致重复生成；终态任务再次消费返回 ignored。

## 4.3 处理管线

1. `TaskClaimStep`：读取任务、检查状态和 attempt 幂等键，写 `task.generating/task.retrying`。
2. `InputModerationStep`：执行 mock 或真实审核，拒绝则写 input moderation 和失败/释放。
3. `ModelInvokeStep`：构造 Spring AI prompt/options，调用 `ChatModel`，解析图片 URL/base64。
4. `OutputModerationStep`：逐图审核，任一拒绝则清理临时输出并失败/释放。
5. `ImagePipelineStep`：EXIF rotate、cover crop、输出目标尺寸、WebP quality 90、缩略图最大宽 480/quality 80、SHA-256。
6. `StorageStep`：先写 `results/<task>/<result>.webp`，再写 `thumbnails/<task>/<result>.webp`；缩略图失败删除原图。
7. `PersistResultStep`：写 `GenerationResult`、审核状态和 succeeded event；重复结果由 `(taskId,index)` 唯一约束保护。
8. `QuotaSettlementStep`：成功 CONSUME，失败/取消 RELEASE；所有 ledger 使用唯一 idempotencyKey。

每一步都必须定义补偿动作。已写入对象但数据库失败时删除对象；数据库已成功但事件发布失败时由 outbox/replay job 补发。

## 4.4 Spring AI 适配器

领域端口：

```java
interface GenerationModel {
    ProviderResponse generate(GenerationRequest request, GenerationAttempt attempt);
}
```

实现 `OpenAiCompatibleGenerationModel`：

- 注入 Spring AI `ChatModel`，不在业务 service 中依赖供应商 SDK。
- `base-url` 指向 OpenAI-compatible 服务；`api-key` 只来自 secret；model、timeout、temperature、maxTokens 可配置。
- 使用固定 system prompt 约束输出协议；要求模型返回可解析的图片引用或 base64，不接受自由文本作为成功结果。
- 将供应商错误映射为 `retryable`、`code`、`providerRequestId`；日志只记录 requestId/providerRequestId。
- mock 实现保留旧 deterministic image pool 和 `[mock-retry-once]`、`[mock-always-retryable-error]` 测试语义。

Spring AI milestone 版本 API 可能调整，所有 `ChatModel` 调用集中在一个 adapter 文件和一个集成测试中，禁止在多个业务类散落 milestone-specific API。

当前 Java 实现对应 `OpenAiCompatibleGenerationProvider`。供应商成功响应统一解析为 `images[]`，单项接受 `url`、`data` 或 `base64`；URL 仅允许 HTTP(S)，单图限制 20 MiB。空响应和瞬时 Spring AI 异常可重试，结构错误不可重试。生产接入时不得在响应、日志或 dead-letter 中保存 prompt、密钥或原始供应商错误体。

## 4.5 重试与死信

可重试：连接超时、HTTP 429、HTTP 5xx、供应商临时不可用、响应体暂时为空。不可重试：参数错误、内容审核拒绝、格式无法解析、权限错误。退避使用指数退避并加抖动；达到 maxAttempts 后写 `GenerationDeadLetter`，保存 errorCode、attempts 和脱敏 payload。

## 4.6 对账任务

Worker 定时按 windowKey 创建 `QuotaReconciliationRun`，扫描：

- QUEUED/GENERATING 是否有 RESERVE；
- SUCCEEDED/PARTIALLY_SUCCEEDED 是否有 CONSUME；
- FAILED/CANCELLED 是否有 RELEASE；
- QuotaAccount total/available/reserved 与 ledger 推导值是否一致。

只自动修复可证明安全的缺失 consume/release；金额漂移、余额不足和未知状态进入 BLOCKED finding。对账窗口创建和 finding 使用唯一键，重复执行不重复记账。

## 4.7 实现落点与开发顺序

| 能力 | Java 实现 | 关键不变量 |
| --- | --- | --- |
| 队列消费 | `GenerationQueueConsumer`、`RedisGenerationQueue` | 先持久化终态/补偿，再 ack；可重试异常保持 pending |
| 任务事务 | `JdbcGenerationWorkerStore`、`GenerationMapper` | 条件状态更新、attemptKey 幂等、事件与额度同事务 |
| 模型调用 | `GenerationProvider`、`OpenAiCompatibleGenerationProvider` | Spring AI API 只出现在适配器；输出先校验再进入图片管线 |
| Mock | `DeterministicMockProvider`、`DeterministicMockContentModerator` | 相同 prompt/model/index 生成相同输入；故障标记可重复 |
| 图片与存储 | `GenerationOutputPipeline` | 主图/缩略图均为真实 WebP；任一步失败清理已写对象 |
| 对账 | `QuotaReconciliationService`、`QuotaReconciliationMapper` | windowKey/findings/ledger 幂等；不猜测修正金额漂移 |

开发或修改 Worker 时按以下顺序验证：领域单元测试 -> WebP/清理测试 -> Spring AI WireMock -> PostgreSQL/Redis/S3 集成测试 -> API/SSE 端到端测试。没有真实 Redis 时只能证明消费者逻辑可编译和单元行为正确，不能把 reclaim/ack 验收标记为生产通过。
