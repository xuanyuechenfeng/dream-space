# 实施计划

1. 修正 GenerationMapper 的预规划活动 SQL，并补 API service 单元测试。
2. 在 Worker 对账器增加 v2 僵死 execution 查询与事务恢复，覆盖成功槽、失败槽、额度释放和任务终态。
3. 强化 CollectionPreflightProcessor provider 异常日志上下文，确保 attempt 信息和 preflightId 可检索。
4. 调整前端 generation store：删除失败时重新读取会话并保留 active/session 投影；组件仅展示服务端活动任务与本地 pending 的明确区别。
5. 运行后端 API/Worker 测试、前端 typecheck/unit test；检查现有 Responses 迁移未被覆盖。
