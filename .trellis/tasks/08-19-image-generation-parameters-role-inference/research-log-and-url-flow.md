# 日志与图片 URL 链路分析

## 问题清单

1. 质量评估模型在 `repairable=true` 时返回 `refinement.instruction`，但 worker 原 `RefinementPatch` 未声明该字段，且 Jackson 开启 `FAIL_ON_UNKNOWN_PROPERTIES`，因此在质量评估阶段抛出 `UnrecognizedPropertyException`，任务被永久失败并释放额度。
2. 设计契约要求评估器只输出结构化 `RefinementPatch`，但实际图片模型需要一段完整、可执行的修订指令；缺少 instruction 会让 refinement 无法可靠驱动下一轮生成。
3. provider 返回 URL 的链路已存在且已被日志证明生效：HTTPS URL -> 下载字节 -> 图片魔数/MIME/大小校验 -> `ProviderImage` -> 质量评估和输出存储。URL 不应在后续阶段再次作为远程资源传递。
4. 日志中的 provider 原图为 1548x1016，而任务要求 2048x1344。输出存储会裁切到目标尺寸，但质量评估之前没有明确的 worker 技术尺寸错误，导致问题容易被模型自由判断。

## 设计方案

- `RefinementPatch` 正式增加 `instruction`，并保留 `targetSections/changes/preserve/reasonCodes`。repairable 评估必须返回非空 instruction；非 repairable/accepted 评估不需要 refinement。
- 质量评估 system prompt 明确声明顶层和 refinement 的完整字段、JSON 类型及 instruction 的语义。解析仍保持未知字段拒绝；仅对缺少结构化数组的兼容响应进行确定性补齐，并将 instruction 复制到 changes。
- provider URL 保持现有安全下载策略（仅 HTTPS、拒绝私网地址、重试 429/5xx、限制 20 MiB、校验图像魔数与声明 MIME），统一转换为字节后下游消费。
- 质量评估请求同时携带任务目标尺寸和实际图片，要求评估器将尺寸偏差作为硬约束并在可修复时返回 `refinement.instruction`；不要在评估前直接终止，以保留修复循环。输出存储仍负责最终裁切/编码。

## 验收

- 日志中的 `refinement.instruction` 可被解析并进入下一轮图片模型请求。
- URL 与 Base64 共享 `ProviderImage` 后续流程。
- 尺寸不一致可被稳定诊断；需要 JDK 21 环境运行 Maven 回归测试。