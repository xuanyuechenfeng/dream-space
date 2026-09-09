# 实施计划

1. 调整质量评估模型构造参数和 Worker 配置装配，使用 `OpenAiConnectionProperties#getTimeout()`。
2. 在槽位处理器的质量评估调用边界增加 fail-open 降级，保留日志和跳过事件。
3. 更新质量评估模型 timeout 测试，并新增评估异常后继续发布的槽位处理器测试。
4. 运行 Worker 相关测试和必要的编译检查，确认现有质量拒绝与输出审核失败路径不变。
