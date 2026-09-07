# 实施计划

## 阶段 1：后端契约与状态恢复

1. 在 `GenerationService`、`CollectionPreflightService` 增加正式会话必填/归属校验，移除所有新任务路径的隐式建会话分支。
2. 在 v2 task create 中校验 preflight/session 一致性，补充明确错误码和 mapper/service 测试；历史任务重试必须复用已有 `sessionId`。
3. 为 preflight 增加超时扫描/终态恢复所需的查询、配置和事件；复用现有 Worker 调度与事务边界，不新增扣费路径。
4. 补齐 Worker 的结构化阶段日志和 `durationMs`，验证给定日志场景会写 READY/FAILED 终态。

## 阶段 2：前端提交时序与会话隔离

1. 扩展 `GenerationSession` API/store，让 submit 在没有 active session 时先创建会话并更新 URL/历史列表。
2. 将 pending 存储从单值改为按 `sessionId` 索引；迁移旧单值数据时仅在可确认归属时恢复，否则丢弃 UI 临时值并保留服务端可查询状态。
3. 提交、重试、恢复和路由切换全部使用 session-scoped pending；视图只渲染 `visiblePendingSubmission`。
4. SSE 错误/结束、页面恢复和可见性变化统一触发 preflight 状态轮询；READY 自动创建正式任务，终态错误停止 spinner。

## 阶段 3：验证与回归

1. 运行前端单元测试、类型检查和构建，重点覆盖请求顺序、会话切换、响应乱序、幂等重放。
2. 运行 API/Worker Maven 测试，覆盖缺少 session、越权、session mismatch、超时恢复、日志/终态事件。
3. 执行端到端流程：新会话发送 -> 历史会话切换 -> 返回原会话 -> READY -> 正式任务 -> 结果；确认无跨会话展示。
4. 检查受影响包规范和完整工作区状态，更新必要的 spec 经验记录。

## 回滚点

- 后端契约校验和超时恢复可独立回滚；不删除数据库数据。
- 前端 session-scoped pending 可通过恢复旧组件回滚，正式会话接口保持向后兼容。
- 若超时阈值导致误终态，先关闭扫描开关并保留事件，再调整阈值后重新启用。
