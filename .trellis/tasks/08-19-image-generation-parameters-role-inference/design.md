# Technical Design

本任务以 `docs/design/15-image-generation-parameters-and-image-role-inference.md` 为唯一详细设计基线。

核心边界：

- Browser/API：`AUTO + prompt + imageIds + ratio + resolution + width + height`；
- Database：任务保存无角色 `imageIds`、输出参数，`imageCount` 固定为 1；
- Worker Planning：将输入图片以无语义标签发送真实多模态模型，输出强类型 `ImageAssignment`；
- Internal Image Request：只有规划校验后才形成 `targetImageId/referenceImageId`；
- Failure：角色歧义、尺寸冲突和供应商能力不支持均显式失败，不做静默回退。

设计中的参数表、校验顺序、迁移策略、模型契约和验收标准均直接适用于实现。

## 模型响应契约与输出参数优先级补充设计

### 对比结论

- 上一次响应发生在 `VisualSpec` 阶段：`style`、`contrast`、`palette` 和 `layout` 已返回，但 `layout.readingOrder` 为数组、尺寸字段为数字，旧的 `Map<String, String>` 解析失败。
- 本次响应发生在 `RequirementBrief` 阶段：`intent`、图片角色、内容数组和偏好对象的形状符合契约；`confidence` 返回了字符串 `"high"`，与 DTO 的 `double` 不兼容。
- 上次视觉阶段的兼容修复使流程继续向前，本次暴露的是更早阶段中未明确声明标量类型的问题，而不是模型没有返回结果。

### 权威数据边界

1. `WorkerTaskSnapshot` 中已有的 `ratio`、`resolution`、`width`、`height` 是任务硬约束，任何规划阶段都不得从模型响应中读取或覆盖。
2. `SMART` 只允许结构规划模型返回标准比例；最终宽高由 `OutputDimensions` 根据任务分辨率计算，模型返回的尺寸字段即使存在也只作为已知兼容字段丢弃。
3. Worker 在结构规划解析后构造包含最终比例、分辨率和宽高的规范 `StructurePlan.canvas`，后续视觉规划、Prompt 构造、图片模型和质量评估只消费该规范值。

### 统一模型契约

- `RequirementBrief.confidence` 必须是 `[0,1]` 范围内 JSON number；解析器仅将已知兼容标签 `high/medium/low` 映射为 `0.9/0.7/0.4`，未知字符串仍判定为非法。
- `StructurePlan.canvas` 的模型输入只包含 `aspectRatio`（SMART 时必填）和 `composition`；`resolution`、`width`、`height` 属于 Worker 注入字段，不属于模型规划输出。
- 所有阶段提示词明确声明每个字段的 JSON 标量、对象和数组形状，并继续禁止 Markdown、未知顶层字段和额外字段。