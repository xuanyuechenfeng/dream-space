# 技术设计

## 变更范围

- `ChatQualityEvaluationModel`：接收并使用 Spring AI OpenAI timeout。
- `GenerationWorkerConfiguration`：将 `OpenAiConnectionProperties#getTimeout()` 注入质量评估模型。
- `GenerationV2SlotProcessor`：在质量评估调用边界实施 fail-open 降级。
- 相关单元测试：覆盖配置超时和异常放行。

## 超时来源

现有 `spring.ai.openai.timeout` 已绑定到 `OpenAiConnectionProperties`，默认值由 Worker 的 `application.yml` 提供。质量评估模型构造函数新增 `Duration` 参数，调用 `OpenAiChatOptions` 时使用该参数，不再引用 `ModelTimeouts.DETECTION`。

内容审核和模型健康检查不在本次变更范围内，继续使用现有策略。

## 异常降级流程

质量评估发生运行时异常时，在 `GenerationV2SlotProcessor` 中捕获异常并结束当前质量循环。当前迭代已经生成的 `image` 保持不变，随后继续执行：

```text
候选图片 → 输出内容审核 → 持久化 → publishSlot → completeExecution
```

不直接修改任务状态，不调用 `recordSlotFailure`，也不触发下一次图片生成。只有质量评估正常返回且明确拒绝时，才继续执行原有的 refinement 或失败逻辑。

异常降级会输出包含 task、slot、iteration、errorCode 的 WARN 日志，并写入 `task.slot.quality_skipped` 事件；这属于可观测性记录，不改变业务结果。

## 成功边界

质量评估异常不等于无条件成功。输出审核、对象存储、发布或数据库结算失败时，任务仍然失败。多槽位任务仍按各槽位结果聚合为 `SUCCEEDED`、`PARTIALLY_SUCCEEDED` 或 `FAILED`。

## 风险

`spring.ai.openai.timeout` 默认值为 600 秒，底层客户端自动重试时，质量评估失败前的实际等待时间可能超过单次 timeout。该行为与统一配置保持一致；本次不调整底层重试策略。
