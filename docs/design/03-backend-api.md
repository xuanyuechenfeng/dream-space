# 03 Spring MVC API 详细设计

## 3.1 分层

```text
HTTP Controller
  -> Request DTO / Validator
  -> Application Service (@Transactional)
  -> Domain rule / Port
  -> Repository / Redis / ObjectStorage adapter
```

Controller 不直接拼 SQL、不直接写 Redis、不直接调用 ChatModel。所有响应包含 `requestId`；错误由 `@RestControllerAdvice` 转为统一结构。

## 3.2 Controller 与接口

| Controller | 方法 | 路径 | 认证 |
| --- | --- | --- | --- |
| `HealthController` | GET | `/health` | 无 |
| `AuthController` | POST | `/auth/codes` | 无 |
|  | POST | `/auth/login` | 无 |
|  | GET | `/auth/session` | 用户 Cookie |
|  | POST | `/auth/logout` | 用户 Cookie |
| `InspirationsController` | GET | `/inspirations` | 无 |
|  | GET | `/inspirations/{slug}` | 无 |
| `UploadsController` | POST | `/uploads/references` | 用户 Cookie |
|  | GET | `/uploads/references/{uploadId}/content` | 用户 Cookie |
| `GenerationController` | GET | `/generation/quota` | 用户 Cookie |
|  | GET | `/generation/options` | 用户 Cookie |
|  | GET | `/generation/sessions` | 用户 Cookie |
|  | GET/PATCH/DELETE | `/generation/sessions/{sessionId}` | 用户 Cookie |
|  | PATCH | `/generation/sessions/{sessionId}/draft` | 用户 Cookie |
|  | POST | `/generation/tasks` | 用户 Cookie |
|  | GET | `/generation/tasks/{taskId}` | 用户 Cookie |
|  | POST | `/generation/tasks/{taskId}/cancel` | 用户 Cookie |
|  | GET | `/generation/tasks/{taskId}/events` | 用户 Cookie，SSE |
|  | GET | `/generation/results/{resultId}/content` | 用户 Cookie |
|  | GET | `/generation/results/{resultId}/thumbnail` | 用户 Cookie |
| `AdminAuthController` | POST/GET | `/admin/auth/codes`, `/admin/auth/session` | 管理员 Cookie/无 |
|  | POST | `/admin/auth/login`、`/admin/auth/logout` | 管理员 Cookie |
| `AdminTasksController` | GET | `/admin/tasks` | 管理员 RBAC |
|  | GET | `/admin/tasks/{taskId}` | 管理员 RBAC |
|  | GET | `/admin/tasks/results/{resultId}/{kind}` | 管理员 RBAC |
|  | GET | `/admin/tasks/reconciliation/runs` | 管理员 RBAC |
| `AdminInspirationsController` | GET/POST | `/admin/inspirations` | 管理员 RBAC |
|  | GET/PATCH | `/admin/inspirations/{id}` | 管理员 RBAC |
|  | POST | `/admin/inspirations/{id}/publish` | OPERATOR/ADMIN |
|  | POST | `/admin/inspirations/{id}/unpublish` | OPERATOR/ADMIN |

## 3.3 统一响应与错误

成功响应直接返回现有 JSON 契约；分页统一包含 `items,total,page,pageSize,pageCount`。错误结构：

```json
{
  "code": "GENERATION_QUOTA_INSUFFICIENT",
  "message": "额度不足",
  "details": { "required": 4, "available": 2 },
  "requestId": "req_..."
}
```

最低错误码集合：`AUTH_CODE_INVALID`、`AUTH_CODE_EXPIRED`、`AUTH_AGREEMENT_REQUIRED`、`FORBIDDEN`、`NOT_FOUND`、`VALIDATION_FAILED`、`UPLOAD_INVALID_TYPE`、`UPLOAD_TOO_LARGE`、`GENERATION_IDEMPOTENCY_CONFLICT`、`GENERATION_QUOTA_INSUFFICIENT`、`GENERATION_CANCEL_NOT_ALLOWED`、`PROVIDER_TEMPORARILY_UNAVAILABLE`、`GENERATION_FAILED`、`ADMIN_ROLE_REQUIRED`。

## 3.4 认证与权限

- 生成随机 session token，只将 SHA-256 hash 写入 `UserSession`/`AdminSession`；原 token 只放 HttpOnly、Secure、SameSite=Lax Cookie。
- 用户 Cookie 与管理员 Cookie 使用不同名字、不同表、不同 filter；管理员不能通过用户 Cookie 访问管理接口。
- `AdminPermissionInterceptor` 根据 `AdminRole` 执行 VIEWER/OPERATOR/ADMIN 权限矩阵；页面隐藏不是安全控制。
- CORS 只允许 `WEB_ORIGIN`、`ADMIN_ORIGIN`，允许 credentials，不允许通配 origin。

## 3.5 事务边界

### 提交任务

`GenerationApplicationService.submit()` 一个事务内完成：校验 session/user -> 解析参数 -> 计算 unit/total cost -> 以 `(userId,idempotencyKey)` 查询已有任务 -> 锁定额度账户 -> 更新 reserved/available -> 写 RESERVE ledger -> 创建 task/event。事务提交后再投递 Redis；投递失败由 outbox/retry job 补发，不在事务内调用网络服务。

### 取消任务

只允许 QUEUED/GENERATING；数据库条件更新保证并发下只有一个请求成功。成功后写 CANCELLED event 和 RELEASE ledger。Worker 抢到任务后再次读取状态，已取消任务直接 ack，不调用模型。

### 结果访问

根据 result -> task -> user 归属校验；本地模式流式返回 `image/webp`，S3 模式返回 TTL 受限的签名 URL或由 API 代理，禁止暴露任意 object key。

## 3.6 参数校验

- prompt 去除首尾空白，prompt 和参考图不能同时为空；长度限制与旧 contracts 保持一致。
- imageCount 1-8；references 0-4；MIME 仅 jpeg/png/webp；单文件不超过 10 MB、像素不超过 40 MP。
- ratio 只能使用 `smart,21:9,16:9,3:2,4:3,1:1,3:4,2:3,9:16`；resolution 只能 `2K/4K`。
- `4K` 单张费用 2，`2K` 单张费用 1；totalCost = unitCost * imageCount。
