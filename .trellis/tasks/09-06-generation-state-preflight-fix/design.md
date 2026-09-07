# 技术设计

## 边界

后端共用 GenerationMapper 统一活动预规划 SQL；Worker 在现有 QuotaReconciliationService 中增加 v2 execution 的过期恢复步骤，复用 GenerationV2Mapper 的终结与结算能力，避免直接删除历史数据。前端仅调整 generation store 的失败刷新链路。

## 状态定义

- 活动预规划：`QUEUED`、`PLANNING`，以及 `READY AND expiresAt > CURRENT_TIMESTAMP`。
- 活动任务：保留 `GenerationTask` 的 QUEUED/GENERATING 语义，但 v2 任务必须同时存在有效 QUEUED/GENERATING execution；没有有效 execution 的任务由恢复器终结。
- 僵死 execution：仅对已进入 `GENERATING` 且超过保守恢复窗口的 execution 自动恢复，避免 Worker 停机期间的 QUEUED 积压被误终结；释放剩余 reserve，并把无成功槽的任务置为 FAILED。

## 一致性与兼容

删除 API 先读 guard，再由 DELETE SQL 重复 guard；两处采用同一 expiresAt 条件。恢复操作使用现有幂等 ledger key 和事务，失败不删除数据。日志只输出 bounded preview。

## 风险控制

恢复窗口作为 Worker 配置，默认使用已有重试/队列可容忍的保守分钟数；每次恢复记录事件和 WARN 日志。若额度结算不满足不变量，保留 reconciliation finding 为 BLOCKED，不强行修改账户。
