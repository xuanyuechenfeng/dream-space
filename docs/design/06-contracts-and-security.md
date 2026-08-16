# 06 接口契约与安全设计

## 6.1 生成任务请求/响应

请求字段必须与旧 contracts 对齐：

```json
{
  "sessionId": "session_cuid",
  "prompt": "一张城市夜景",
  "model": "image-4.7",
  "ratio": "16:9",
  "resolution": "2K",
  "imageCount": 2,
  "referenceUploadIds": [],
  "idempotencyKey": "client-generated-key"
}
```

创建成功返回 `taskId`、`sessionId`、`status`、`totalCost`、`quota` 和初始 event cursor。重复 idempotencyKey 返回已有任务；同一 key 的参数不同返回 `GENERATION_IDEMPOTENCY_CONFLICT`。

## 6.2 SSE 事件

事件格式：

```text
id: 1042
event: task.generating
data: {"taskId":"task_cuid","status":"generating","attempt":1,"maxAttempts":3}
```

事件 type 至少包括 `task.queued`、`task.generating`、`task.retrying`、`task.succeeded`、`task.failed`、`task.cancelled`、`task.moderation`。客户端必须支持 Last-Event-ID、重复事件去重、终态关闭和断线重连。

## 6.3 Cookie 和 CSRF

- 用户 cookie：例如 `dream_space_session`；管理员 cookie 使用不同名字。
- 属性：HttpOnly、Secure（生产）、SameSite=Lax、Path=/、过期时间与 `AUTH_SESSION_DAYS` 一致。
- 所有改变状态的请求检查 Origin/Referer 和 CSRF token（若跨站部署）；CORS credentials 仅允许明确 origin。
- 不把 session token、OpenAI key、S3 secret 写入 localStorage、URL、日志或错误响应。

## 6.4 上传安全

- 只接受 jpeg/png/webp，使用 magic bytes + ImageIO 解码双重验证，不信任文件名和 Content-Type。
- 限制 10 MB、40 MP 和尺寸；解码在受限线程池执行，避免压缩炸弹耗尽内存。
- 归一化为 WebP，原始文件名仅作为 metadata；对象键使用服务端生成 UUID。
- 读取资源前校验 userId/taskId 归属；管理员资源读取经过 RBAC。

## 6.5 管理员 RBAC

| 操作 | VIEWER | OPERATOR | ADMIN |
| --- | --- | --- | --- |
| 查看任务/对账 | ✓ | ✓ | ✓ |
| 查看结果资源 | ✓ | ✓ | ✓ |
| 创建/编辑灵感 | - | ✓ | ✓ |
| 发布/取消发布 | - | ✓ | ✓ |
| 管理管理员/系统配置 | - | - | 预留 |

UI 仅负责隐藏按钮，服务端 `AdminPermissionGuard` 是唯一授权依据。管理员手机号在列表和日志中脱敏。

## 6.6 可观测性

每个请求带 `X-Request-Id`，异步消息携带 requestId/taskId。指标至少包括 API latency/error、auth code failure、queue lag/pending、provider latency/error/retry、generation success/failure、quota mismatch、S3 error。日志采用 JSON，prompt 默认不记录完整内容。
