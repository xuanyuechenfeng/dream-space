# 生成会话时序与预规划状态一致性设计

## 1. 现状与根因

### 1.1 会话展示错乱

当前 `generation` Pinia store 将 `pendingSubmission` 作为单例，并用固定的 `sessionStorage` key 持久化。切换历史会话时，`clearSessionState()` 会重新读取该值；`GenerationWorkspaceView.vue` 又无条件渲染 pending 卡片。因此新会话尚未完成预规划时，任何历史会话都会显示同一张“正在确定图片数量”卡片。

正式任务本身按 `GenerationTask.sessionId` 查询，后端 `listTasks(sessionId)` 已具备会话隔离；问题主要发生在前端本地 pending 状态，以及 v2 预规划在无 `sessionId` 时仍允许在正式创建阶段补建会话。

### 1.2 “确认图片数量”长时间停留

日志时间线显示：

- `19:56:19.104` 开始规划模型请求；
- `19:57:12.508` 收到长度 1543 的模型响应；
- `19:57:12.546` 响应解析成功；
- `19:57:12.594` Worker 队列消息确认。

这说明该样例不是模型一直无响应，而是一次约 53 秒的同步模型调用后已经完成解析。当前实现只在 Worker 写入预规划终态后由前端通过 SSE/5 秒轮询感知，缺少终态写入和耗时日志，且 pending 文案长期固定为“确定图片数量”，容易把正常慢调用、SSE 断开、正式任务创建失败混为同一种状态。设计将把数据库状态作为唯一事实源，并补齐终态闭环与超时恢复。

## 2. 目标架构与状态机

```text
新会话页面
  -> POST /generation/sessions
  -> 返回正式 sessionId，更新 URL/历史列表
  -> 写入 session-scoped pending(preparing)
  -> POST /generation/preflights(sessionId)
  -> Worker: QUEUED -> PLANNING -> READY | NEEDS_CLARIFICATION | FAILED | EXPIRED
  -> READY: POST /generation/tasks(idempotencyKey, planToken)
  -> 正式 task 写入同一 sessionId，返回 SessionDetail + TaskView
```

状态约束：

- `GenerationSession` 必须先于新的 v2 `GenerationPreflight` 存在。
- `GenerationPreflight.sessionId` 必填且必须属于当前用户。
- `GenerationTask.sessionId` 必须等于预规划的 `sessionId`。
- pending 只是一份 UI 恢复投影，不进入任务列表、额度或会话任务数组。
- 正式任务创建成功后，清除该 session 的 pending；失败则保留终态错误和重试/编辑入口。

## 3. 前端设计

### 3.1 提交编排

在 `generation` store 增加 `ensureSessionForSubmit(snapshot)`：

1. 有 `active.id` 时校验并复用当前正式会话。
2. 无 active 会话时调用现有 `api.generation.createSession(snapshot)`，将返回值写入 `active` 和 `sessions`，并由页面将 URL 切换到 `/generate/{id}`。
3. 只有第 1/2 步成功后，才创建 pending 并发送 preflight。
4. 会话创建失败时不写 pending，不清空 composer，错误直接展示给用户。

`submit()` 的 idempotency fingerprint 改为始终包含正式 `sessionId`；预规划和正式创建都复用同一份业务幂等键，预规划使用带前缀的独立 transport key。正式任务响应丢失时，以正式创建幂等键查询/重放，不重新创建会话。

### 3.2 会话归属的 pending 状态

将单值 `pendingSubmission` 改为 `pendingBySession: Map<sessionId, PendingGenerationSubmission>`（持久化时使用版本化 JSON 对象或按 sessionId 分 key）。`PendingGenerationSubmission.sessionId` 改为必填；仅允许以下显示规则：

- 当前 `active.id === pending.sessionId`：展示 pending/preflight 卡片；
- 当前没有 active 会话：不展示任何历史 pending 卡片，只展示新会话编辑器；
- 打开其他历史会话：隐藏原 pending，不清除它；
- 返回所属会话：重新读取该会话 pending，并先调用 `preflightStatus` 再决定显示准备中、失败或继续正式创建。

`hasDetail`、时间线 pending 卡片、错误提示和滚动监听统一使用 `visiblePendingSubmission`，禁止直接读取全局 pending。`startNewSession()` 只取消当前页面的本地等待和 active 绑定，不删除其他正式会话的 pending 恢复记录；“新建会话”按钮在提交进行中仍保持禁用，避免一个 store 并行提交两份未归属请求。

### 3.3 恢复与 SSE/轮询

`load(sessionId)` / `openSession(sessionId)` 完成会话加载后，只恢复该 `sessionId` 的 pending。恢复流程调用 `preflightStatus`，其结果按服务端状态渲染：

- `QUEUED/PLANNING`：准备中，并显示阶段/已耗时信息；
- `READY`：立即调用正式任务创建；
- `NEEDS_CLARIFICATION/FAILED/EXPIRED/SUPERSEDED/CONSUMED`：转为可操作的终态，不再无限 spinner。

SSE 仅作为低延迟通知；`onerror`、正常 close、页面重新可见时都触发一次状态查询，固定轮询继续运行直到终态。轮询请求使用 session transition 和 pending ID 做竞态保护，旧会话响应不能覆盖新会话状态。

## 4. 后端设计

### 4.1 会话前置不变量

在 `CollectionPreflightService.validate` 中将 `sessionId` 设为必填，并调用 `generation.getSession(userId, sessionId)` 校验所有权；缺失时返回 `GENERATION_SESSION_REQUIRED`，不插入 preflight、不发布队列消息。

在 `CollectionPreflightService.create` 中删除“`preflight.sessionId` 为空则生成 UUID 并插入会话”的兜底分支，改为校验锁定的预规划仍绑定同一正式会话。任务插入前再次读取/校验该会话，确保任务和预规划不能跨会话。

`GenerationService.submit` 对所有新的任务创建请求执行同一会话前置校验：`sessionId` 缺失或不属于当前用户直接返回 `GENERATION_SESSION_REQUIRED` / `GENERATION_SESSION_MISMATCH`，不再在任务创建时隐式插入会话。历史 v1 任务继续可读；历史任务的重试、继续生成由服务端从原任务携带其已有 `sessionId`，不会产生无会话请求。若存在外部旧客户端，需在发布前升级其调用方或单独保留明确标记的兼容 API，不得让浏览器新流程走隐式建会话分支。

### 4.2 原子性与幂等

- 会话创建是独立的 `POST /sessions` 事务，先提交后才允许预规划。
- 预规划插入、初始事件和队列发布沿用现有事务；发布失败由现有 pending queue publisher 重发。
- 正式 v2 任务创建继续在锁定 preflight 的事务内完成：锁 preflight -> 校验 session -> 插入 task/plan/slots/execution -> reserve quota -> consume preflight。
- 以正式任务幂等键唯一约束保证响应丢失重放最多生成一个任务；重放返回原任务所在的 session detail。
- 会话删除活动保护继续把 `QUEUED/PLANNING/READY`（未过期）预规划视为活动，终态预规划不阻止删除。

### 4.3 预规划终态与超时恢复

补充 Worker/API 可检索日志和事件字段：`preflightId`、`sessionId`、`stage`、`attempt`、`maxAttempts`、`durationMs`、`responseLength`、`responseShape`、有界 `responsePreview`、`status`、`errorCode`、`retryable`。不写 API key、完整图片 base64 或完整 provider payload。

`CollectionPreflightProcessor` 在以下节点记录结构化日志：claim、planning started、model response received、plan frozen、finish success/failure、queue acknowledgement。`finishPreflight` 返回 0 时记录“already terminal/stale”而不是静默确认。

增加可配置的预规划最大耗时和恢复调度：扫描长时间处于 `PLANNING` 的记录，事务内写入 `FAILED/PLANNING_TIMEOUT` 及终态事件；不创建 task、不扣额度。超时值必须大于正常模型 P95，并通过配置覆盖，默认建议 120 秒或按实际模型延迟调整。Worker HTTP 请求 timeout、恢复阈值和前端文案保持一致。

## 5. API 契约调整

不新增页面专用接口，复用现有资源：

- `POST /dream_web/generation/sessions`：提交前创建正式会话。
- `POST /dream_web/generation/preflights`：`sessionId` 必填；响应继续返回 `id/status/eventsUrl`。
- `GET /dream_web/generation/preflights/{id}`：返回最新状态；终态必须包含 `errorCode/errorDetails` 或 READY 的 token/输出。
- `POST /dream_web/generation/tasks`：v2 body 的 `planToken` 对应的 preflight 必须与正式 session 一致。

错误码建议：`GENERATION_SESSION_REQUIRED`、`GENERATION_SESSION_MISMATCH`、`GENERATION_PREFLIGHT_TIMEOUT`（对外可映射为“生成准备超时，请重试”）。

## 6. 测试设计

### 前端 store/component

- 无 active 会话提交时，断言调用顺序为 `createSession -> preflight -> createFromPreflight`，且三个请求使用同一 session ID。
- 切换到其他历史会话时，所属会话 pending 不显示；返回原会话可恢复；新会话页面没有旧 pending。
- SSE 断开后轮询 READY/FAILED 都能结束 pending；响应乱序不能污染当前 active 会话。
- 会话创建失败、预规划失败、正式创建响应丢失、刷新恢复分别验证 composer/pending/idempotency 行为。
- 组件桌面和移动端只渲染 `visiblePendingSubmission`，历史会话之间无任务串线。

### API/Worker

- 缺失或越权 `sessionId` 的 preflight 请求返回 4xx，且 mapper 不插入记录、不发布队列。
- v2 create 拒绝 preflight/session 不一致，重放同一幂等键只返回原任务。
- Worker 日志/事件覆盖模型耗时、READY、FAILED、异常和超时；`finishPreflight` 已终态时不误写新 task。
- 超时恢复只终结 preflight，不产生 task/额度 ledger；会话活动删除规则与终态一致。
- 使用给定日志时序构造回归测试：模型 53 秒后成功返回，前端在下一次状态轮询内进入正式任务而非持续确认数量。

## 7. 发布与回滚

1. 先发布兼容后端校验和日志/超时恢复，再发布前端“先建会话”编排。
2. 观察 preflight READY/FAILED/timeout、任务创建幂等冲突、会话创建失败率和 SSE/轮询延迟指标。
3. 若前端发布异常，可回滚前端；后端在过渡期允许旧 v1 直提交，但 v2 缺少 sessionId 的请求继续拒绝，避免再次产生无归属预规划。
4. 数据库不删除旧记录；超时恢复仅写状态和事件，可通过人工/重试入口重新发起。
