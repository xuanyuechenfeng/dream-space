# 生成任务状态一致性与预规划失败修复

## 背景

生成请求在预规划阶段失败时，前端只显示本地 pending 占位，服务端任务列表没有正式任务；同时历史 v2 execution 可能停留在 QUEUED/GENERATING，导致会话删除被错误阻止。预规划失败也缺少可检索的 Worker 日志。

## 需求

1. 过期的 READY 预规划不得阻止会话删除，未过期 READY、QUEUED、PLANNING 仍应阻止删除。
2. 删除判断与实际删除 SQL 使用同一活动定义，避免检查与删除结果不一致。
3. Worker 对账/恢复路径识别并终结超过阈值且无有效队列投递的 v2 execution，释放未消费额度并完成关联任务状态，避免永久僵死。
4. 预规划 provider/解析失败写入包含 preflightId、attempt、maxAttempts、errorCode、retryable、响应长度/形状/有界预览的日志，不记录密钥、完整图片或完整 provider payload。
5. 前端删除因 SESSION_ACTIVE 失败时刷新当前会话，使真实活动任务可见；失败的本地 pending 不得伪装成正式任务。
6. 通过测试覆盖过期/未过期预规划删除保护、日志路径、僵死恢复及前端刷新行为。

## 验收标准

- 对当前会话这类过期 READY + 僵死 execution，刷新页面后能看到真实任务状态；删除不会被过期 READY 错误拦截。
- 真实活动任务仍返回 SESSION_ACTIVE，且前端随后显示服务端任务。
- 非重试型 PLANNING_OUTPUT_INVALID 至少产生一条含错误码和有界响应元数据的 WARN/ERROR 日志。
- Worker/API/前端相关测试通过。
