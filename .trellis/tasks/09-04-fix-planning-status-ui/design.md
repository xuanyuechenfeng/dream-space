# 技术设计：规划合同兼容与提交后进行中展示

## 1. 设计目标与边界

解决两个相互关联的问题：规划模型返回结构化 JSON 后因嵌套 `prompt.modelInput` 形状不兼容而失败；前端等待 preflight 完成才创建正式任务，导致用户长时间看到“等待规划”并最终得到误导性的超时提示。

保留既有账务边界：preflight 是异步、非计费的规划阶段，只有 READY 且 planToken 被消费后才创建正式任务和额度预留。前端内部可以使用 pending submission 作为 UI 状态承载，但它不写入正式任务历史、不显示内部计费或预规划术语。

## 2. 根因与现状数据流

```text
submit -> POST preflight -> Worker planning -> READY -> createFromPreflight -> task SSE
                         \-> FAILED/NEEDS_CLARIFICATION
```

- `ChatPlanningModel.requireCollectionContract` 将缺失、`null` 或非 object 的可选 `prompt.modelInput` 规范化为空 object；只有必要字段缺失或内容不完整才以 `PLANNING_OUTPUT_INVALID` 拒绝。
- 日志中的 `planning model response parsed` 说明上述合同阶段已成功。此前 `CollectionPreflightProcessor` 会再用默认 `ObjectMapper` 将同一槽位 Prompt 转成 `PromptPackage`；模型附带未知扩展字段或使用小写枚举（如 `promptRelation: "expanded"`）时，这次二次转换会失败，并被收敛成“集合槽位 Prompt 格式无效”。
- 冻结阶段现使用忽略未知字段且接受大小写无关枚举的专用 mapper，同时保留必要字段和必要内容的严格校验，并记录 preflight、槽位和原始异常以便排障。
- `CollectionPreflightProcessor` 将不可重试合同错误收敛为 FAILED，队列消费者随后确认消息；在该过程完成前，前端只有 preflight 请求，没有正式 task。
- `generation.ts` 的旧 `waitForPreflight` 将等待时间固定为 120 秒，并把连接异常与业务终态混在同一等待体验中；页面没有独立的提交中状态模型。

## 3. 后端设计

### 3.1 规划响应规范化

规划响应只严格校验执行链路实际依赖的必要字段：集合顶层 `mode/shared/slots/confidence/needsClarification`，槽位 `index/label/role/intent/prompt`，以及 Prompt 的非空 `positivePrompt`。`schemaVersion/unknowns/clarificationReason`、槽位辅助数组与 acceptance、Prompt 元数据均为可选；缺失或空值使用安全默认值，未知字段由解析器忽略。

在完整 JSON tree 解析后、合同关键字段校验前增加有限兼容：

1. 对单图与集合槽位 prompt：缺失、JSON null 或非 object 的可选 `modelInput` 规范化为空 object，并继续由 Worker 注入 `slotIndex`、集合模式、输出尺寸等任务所有字段。
2. 对单图与集合 Prompt 的可选辅助字段统一忽略异常类型并使用安全默认值，例如非 object `modelInput` 归一为空对象、未知 `promptRelation` 归一为 `ALIGNED`；不会因这些字段阻断任务。
3. Prompt 合同文本明确列出必填字段、类型和枚举建议，并说明可选字段应使用稳定 JSON 类型。
4. 兼容逻辑只作用于已知集合 prompt 形状，不用正则修改 JSON；顶层 `mode/shared/slots/confidence/needsClarification`、槽位索引与职责文本、非空 `positivePrompt` 仍严格校验。

### 3.2 Preflight 状态传播

- Worker 对不可重试合同失败立即写入 FAILED；对可重试供应商故障按现有队列尝试次数处理，达到上限时写入稳定错误码和用户详情。
- API 查询/SSE 返回 READY、NEEDS_CLARIFICATION、FAILED、EXPIRED 等可区分状态；错误详情经过现有边界，不把业务失败包装成网络超时。
- 保持 preflight READY token 过期、消费和幂等规则不变，避免改变额度语义。

## 4. 前端设计

### 4.1 Store 状态

在 Pinia generation store 增加一个仅存在于客户端内存的 `pendingSubmission`（或等价结构），包含：客户端 key、会话 id、原始草稿快照、显示文案状态、开始时间、目标数量显示值、错误信息和取消/重试标记。它不进入 `sessions`、不伪造 `GenerationTask`，也不参与额度投影。

提交流程：

1. 本地校验通过后同步写入 pending 状态，页面在下一个渲染周期显示“正在准备生成”；自动数量显示“正在确定图片数量”。
2. 发起 preflight 并订阅事件。READY 后调用 `createFromPreflight`，以返回的正式 session/task 替换 pending；同一提交幂等 key 继续复用。
3. 收到 NEEDS_CLARIFICATION/FAILED/EXPIRED 或确定性请求错误时，pending 转为错误态，保留原始描述并给出“重试”或“修改描述”入口。客户端不设置等待截止时间，网络抖动继续轮询，只有服务端权威终态结束等待。
4. 会话切换、重新开始或组件卸载时清理过期 pending；网络响应晚到时用提交 key、会话 transition 和 draft fingerprint 丢弃过时结果。

### 4.2 页面展示

- pending 卡片复用正式任务卡片的密度、边框和稳定尺寸，使用“正在准备生成”“生成进行中”等面向用户的自然语言；不显示“不计费”“临时任务”“预规划”“planToken”等内部词汇。
- 不显示消费额度，直到正式任务响应携带服务器权威 quota/task 数据。
- READY 替换时保留卡片位置和占位网格，避免列表跳动；正式任务仍由现有 SSE 刷新槽位。
- 失败态明确显示“生成未开始/未完成”及后端提供的可理解原因，不使用“规划超时”作为所有错误的兜底文案。
- 移动端保持单列槽位和固定占位高度，桌面端保持双列；按钮使用现有图标和可访问标签。

## 5. 兼容性与风险控制

- 不修改数据库迁移和多图账务模型；不把 pending 状态写入后端。
- 旧单图 submit 协议继续可用；新 preflight 协议存在时统一走异步规划。
- SSE 断线仍通过查询刷新；pending 阶段使用 preflight SSE 和状态查询，必须清理 EventSource 和计时器。
- 主要风险是异步竞态和重复渲染，使用 fingerprint/idempotency key/transition 三重校验，并添加 store 测试覆盖。

## 6. 验证策略

- Java：ChatPlanningModel 合同兼容、必要字段拒绝、CollectionPreflightProcessor 失败状态传播和错误码测试。
- TypeScript/Vue：提交立即显示 pending、READY 原位替换、业务失败原位错误、网络超时兜底、会话切换丢弃晚到响应测试。
- 构建后使用 Playwright 在桌面和移动视口检查无横向溢出、文本重叠和明显布局跳动。
