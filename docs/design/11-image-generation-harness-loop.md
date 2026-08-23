# 图片生成大环节：Harness + Loop Engineering 详细设计

## 1. 设计结论

需求理解、内容结构规划、视觉风格约束、Prompt 构造、图片模型调用不是五个独立产品功能，而是一个不可拆分的“图片生成大环节”。

当前项目处于开发阶段，所有新任务统一进入 staged pipeline，不保留旧的 direct generation 兼容分支。五个阶段必须按固定顺序执行，任一阶段失败都不能跳过后续阶段直接生成图片。

目标：

- 将自然语言需求转换为 RequirementBrief、StructurePlan、VisualSpec、PromptPackage 四类可审计产物。
- 用 Generation Harness 统一执行阶段编排、Schema 校验、超时、重试、幂等、预算和审计。
- 用 Loop Engineering 对图片结果进行技术、结构、文字、视觉和安全评估，并在预算内修订后重生成。
- 规划用多模态 ChatModel，图片生成用独立 ImageGenerationModel；两者配置和密钥完全隔离。
- 复用现有 Redis、PostgreSQL、额度、对象存储、WebP 和 SSE 基础能力。

## 2. 总体架构

~~~mermaid
flowchart LR
  UI["dream_web 生成工作台"] --> API["GenerationController"]
  API --> DB[("PostgreSQL")]
  API --> Q[("Redis Stream generation")]
  Q --> W["Worker Consumer"]
  W --> H["Generation Harness"]
  H --> P["Planning Stages"]
  P --> C["Multimodal Planning ChatModel"]
  H --> L["Loop Engine"]
  L --> I["独立图片生成模型"]
  L --> E["多模态评估模型"]
  I --> O["Image Output Pipeline"]
  O --> S[("Local / SFTP")]
  O --> DB
  H --> DB
~~~

### 2.1 组件职责

| 组件 | 职责 |
| --- | --- |
| API | 校验请求和参考图、预留额度、创建任务、投递 GenerationJob |
| Worker Consumer | 认领任务、建立 attempt、调用 Harness、完成后 ACK |
| Generation Harness | 串联五个阶段，持久化 Artifact 和阶段事件 |
| Planning Pipeline | 执行需求理解、结构规划、视觉约束、Prompt 构造 |
| Loop Engine | 调用图片模型、评估结果、生成修订 Patch、控制循环 |
| Image Output Pipeline | 解码、EXIF、裁剪、WebP、缩略图、SHA-256、对象存储 |
| PostgreSQL | 保存任务、计划、迭代、结果、事件和额度事实 |
| Redis | 只保存队列投递和 Pending 状态，不保存阶段事实 |

## 3. 端到端流程

~~~mermaid
sequenceDiagram
  participant API as API
  participant W as Worker
  participant H as Harness
  participant P as Planning Model
  participant I as Image Model
  participant E as Evaluator
  participant DB as PostgreSQL
  participant S as Object Storage

  API->>DB: 创建 GenerationTask 并 reserve
  API->>W: 发布 GenerationJob
  W->>DB: claim task attempt
  W->>H: execute(task)

  H->>P: 需求理解
  P-->>H: RequirementBrief
  H->>P: 内容结构规划
  P-->>H: StructurePlan
  H->>P: 视觉风格约束
  P-->>H: VisualSpec
  H->>P: Prompt 构造
  P-->>H: PromptPackage

  loop iteration <= maxLoopIterations
    H->>I: PromptPackage + RefinementPatch
    I-->>H: ProviderImage[]
    H->>E: 技术/结构/文字/视觉/安全评估
    E-->>H: EvaluationReport
    alt 达到阈值
      H->>S: 写主图和缩略图
      H->>DB: 写结果、consume、成功事件
    else 可修订且预算足够
      H->>P: 生成 RefinementPatch
    else 超过循环上限
      H->>DB: 失败或部分成功、release/dead-letter
    end
  end
~~~

## 4. 五阶段契约

### 4.1 需求理解与任务拆解

实现类：RequirementUnderstandingStage。

输入只包含用户生成模式、自然语言描述和按模式提供的图片素材。图片类型、行业、展示目标、受众、正文结构、视觉偏好和循环策略均不得由前台填写，而由本阶段从输入中总结或推理。

支持三种输入模式：

1. `TEXT_TO_IMAGE`：纯文本格式的图片内容描述；
2. `EDIT_IMAGE`：一张目标图片 A 加文字调整要求；
3. `RECOMPOSE_IMAGE`：目标图片 A、参考图片 B 加文字调整要求，基于 B 的内容或风格重新设计 A。

输入契约：

~~~json
{
  "mode": "RECOMPOSE_IMAGE",
  "prompt": "保留图片 A 的主体，参考图片 B 的配色和版式重新设计",
  "targetImageId": "upload-a",
  "referenceImageId": "upload-b"
}
~~~

服务端根据模式校验素材数量、归属、MIME、大小和可访问性。ratio、resolution、imageCount 等运行参数使用系统策略默认值，或由模型从描述中推理后经过服务端上限约束；它们不作为前台的结构化内容输入。

输出 RequirementBrief：

~~~json
{
  "imageType": "infographic",
  "industry": "科技产品",
  "coreSubject": "企业级 AI 工作流",
  "displayGoal": "向决策者解释流程和价值",
  "targetAudience": "企业管理者",
  "contentFacts": ["需求输入", "模型规划", "结果评估"],
  "constraints": ["中文", "16:9", "信息可读"],
  "inferredVisualPreferences": {"style": "editorial-tech", "palette": ["teal", "navy"]},
  "inferredLoopStrategy": {"maxIterations": 3, "focus": ["text-readability", "layout"]},
  "unknowns": [],
  "confidence": 0.92,
  "needsClarification": false
}
~~~

规则：

- 只抽取和归纳用户事实，不补充虚构数据、品牌和统计数字。
- 无法识别图片类型时使用 general-visual，并填写 unknowns。
- confidence 小于 0.6 或缺少核心主题时标记 NEEDS_CLARIFICATION；默认继续但记录告警，配置项可改为直接失败。
- `EDIT_IMAGE` 中 targetImageId 是待调整的图片 A；`RECOMPOSE_IMAGE` 中 referenceImageId 是参考图片 B，不能混淆两个角色。
- 参考图默认只能作为风格和构图参考，不复制人物、Logo 或受保护元素。

### 4.2 内容结构规划

实现类：ContentStructurePlanningStage。

必须先将文字内容转换为页面结构，不能把长文本直接拼到图片 Prompt。输出 StructurePlan：

~~~json
{
  "canvas": {"ratio": "16:9", "orientation": "landscape"},
  "readingOrder": ["headline", "summary", "process", "result"],
  "modules": [
    {"id": "headline", "type": "title", "zone": "top-left", "priority": 1},
    {"id": "process", "type": "flowchart", "zone": "center", "priority": 2},
    {"id": "result", "type": "metric-group", "zone": "right", "priority": 3}
  ],
  "textBlocks": [
    {"id": "headline", "content": "AI 工作流", "maxLines": 2, "emphasis": "high"}
  ],
  "chartSpecs": [],
  "layoutRules": ["保持模块间距", "主标题不被图形遮挡"],
  "density": "balanced"
}
~~~

模块类型：title、subtitle、body、card、timeline、flowchart、comparison、metric-group、bar-chart、line-chart、pie-chart、illustration、footer。没有真实数据时不得生成数值图表。

### 4.3 视觉风格生成约束

实现类：VisualConstraintStage。

输出 VisualSpec：

~~~json
{
  "style": "editorial-tech",
  "palette": {
    "primary": "#0E8F7C",
    "secondary": ["#173F5F", "#F4F7F7"],
    "background": "#F7F8F9",
    "text": "#17191C"
  },
  "layout": {
    "grid": "12-column",
    "gutter": "24px",
    "safeArea": "5%",
    "alignment": "left"
  },
  "typography": {"heading": "strong sans", "body": "neutral sans"},
  "contrast": "WCAG-AA",
  "negativeConstraints": ["no watermark", "no unreadable microtext"]
}
~~~

用户在自然语言中明确提到的色彩、行业风格和比例优先级最高；其余字段由模型推理，默认值只能补缺失字段。颜色必须有语义角色。图表风格只描述视觉表达，不生成不存在的数据。negativeConstraints 是硬约束，必须传给图片模型和评估器。

### 4.4 Prompt 构造

实现类：PromptConstructionStage。

Prompt 由前三个 Artifact 和固定模板构造，用户文本不能覆盖 system prompt。输出 PromptPackage：

~~~json
{
  "positivePrompt": "...",
  "negativePrompt": "...",
  "modelInput": {
    "aspectRatio": "16:9",
    "industry": "科技产品",
    "style": "editorial-tech",
    "layout": "12-column editorial layout",
    "modules": ["headline", "flowchart", "metric-group"],
    "chartForms": [],
    "colorRoles": {"primary": "#0E8F7C", "background": "#F7F8F9"}
  },
  "textPolicy": "preserve provided text exactly; do not invent numbers",
  "promptVersion": "image-prompt-v1"
}
~~~

模板顺序固定为：比例和尺寸 -> 行业、主题、展示目标 -> 风格、色彩、布局 -> 模块、阅读顺序、图表 -> 必须出现的文字 -> 参考图规则 -> 负面约束和可读性。

单张图片不要求具备所有字段；缺失字段写 not-specified，不塞入无关默认内容。

### 4.5 图片模型调用

新增领域端口：

~~~java
public interface ImageGenerationModel {
  ImageGenerationResponse generate(ImageGenerationRequest request,
      GenerationAttempt attempt);
}
~~~

OpenAiCompatibleImageGenerationModel 负责：

- 使用独立 baseUrl、apiKey、model、endpoint 和 timeout；
- 支持 URL、Base64、Data URL，统一转换为 ProviderImage；
- 映射 retryable、code、providerRequestId；
- 不记录完整 Prompt、密钥和供应商原始响应；
- 输出继续进入现有 GenerationOutputPipeline；
- Chat Completions 只用于规划，不得当作图片生成接口。

## 5. Harness 设计

### 5.1 阶段运行协议

~~~java
public interface GenerationStage<I, O> {
  String name();
  O execute(I input, StageContext context);
}

public interface ArtifactValidator<T> {
  void validate(T artifact, StageContext context);
}
~~~

GenerationHarness 每个阶段执行：

1. 建立 traceId、taskId、attemptKey、stageRunId；
2. 校验输入版本、大小、敏感字段和参考图归属；
3. 调用配置的真实多模态规划模型；
4. 解析严格 JSON，拒绝 Markdown、自由文本和未知字段；
5. 执行 JSON Schema、业务和安全校验；
6. 保存脱敏后的不可变 Artifact；
7. 写入 GenerationTaskEvent；
8. 按阶段策略重试或终止。

### 5.2 预算

| 预算 | 默认值 | 超限行为 |
| --- | ---: | --- |
| 五阶段总耗时 | 60 秒 | 任务失败并 RELEASE |
| 单阶段重试 | 2 次 | 终止大环节 |
| 图片循环次数 | 3 次 | 最终评估或失败 |
| 单图大小 | 20 MiB | 拒绝 Provider 输出 |
| 输入文本 | 4,000 字符 | 使用 API 现有校验 |
| 参考图 | 4 张、每张 10 MiB | 使用现有上传规则 |
| Prompt 日志 | 仅 hash | 禁止记录原文 |

Schema 独立版本化为 requirement-v1、structure-v1、visual-v1、prompt-v1、evaluation-v1。Schema 错误不可重试；超时、429、5xx 可重试。

## 6. Loop Engineering 设计

### 6.1 Loop 状态

~~~text
PLANNED -> GENERATED -> EVALUATING
EVALUATING -> ACCEPTED
EVALUATING -> REFINING -> GENERATED (iteration + 1)
EVALUATING -> REJECTED
任何阶段 -> FAILED
~~~

任务外部状态仍使用现有 QUEUED、GENERATING、SUCCEEDED、PARTIALLY_SUCCEEDED、FAILED、CANCELLED；Loop 内部状态保存在迭代记录中，不扩展前台任务终态。

### 6.2 评估顺序

1. 技术评估：可解码、MIME、大小、尺寸、比例、对象可写；
2. 结构评估：模块存在、阅读顺序、遮挡；
3. 文字评估：必需文字、严重乱码、虚构数字；
4. 视觉评估：风格、颜色角色、布局、对比度；
5. 策略评估：审核、版权、Logo、敏感内容和负面约束。

EvaluationReport 必须包含 accepted、score、violations、repairable、evidence、evaluatorVersion。评估器不能直接改 Prompt，只能输出 RefinementPatch。

### 6.3 修订策略

~~~json
{
  "instruction": "Apply the requested repair while preserving approved content.",
  "targetSections": ["layout", "textPolicy"],
  "changes": ["increase whitespace around the headline"],
  "preserve": ["16:9", "primary color #0E8F7C", "flowchart module"],
  "reasonCodes": ["TEXT_OVERLAP", "LOW_CONTRAST"]
}
~~~

每次循环保留用户硬约束、比例、imageCount 和通过审核的内容。同一 reasonCode 连续两次未改善时停止。只有 repairable=true 且预算足够时重生成。达到上限但已有合格图片时标记 PARTIALLY_SUCCEEDED，否则 FAILED。幂等键为 generation:<taskId>:<iteration>。

## 7. 模型配置与 Bean 隔离

~~~yaml
spring:
  ai:
    openai:
      base-url: ${AI_PLANNING_BASE_URL:${OPENAI_BASE_URL:}}
      api-key: ${AI_PLANNING_API_KEY:${OPENAI_API_KEY:}}
      timeout: ${AI_PLANNING_TIMEOUT:PT30S}
      chat:
        options:
          model: ${AI_PLANNING_MODEL:${OPENAI_MODEL:}}
          temperature: ${AI_PLANNING_TEMPERATURE:0.2}

dream-space:
  ai:
    planning:
      enabled: ${AI_PLANNING_ENABLED:true}
      max-attempts: ${AI_PLANNING_MAX_ATTEMPTS:2}
    image:
      enabled: ${AI_IMAGE_ENABLED:true}
      provider: ${AI_IMAGE_PROVIDER:openai-compatible}
      base-url: ${AI_IMAGE_BASE_URL:http://localhost:8090}
      api-key: ${AI_IMAGE_API_KEY:}
      model: ${AI_IMAGE_MODEL:}
      endpoint: ${AI_IMAGE_ENDPOINT:/v1/images/generations}
      timeout: ${AI_IMAGE_TIMEOUT:PT60S}
      max-attempts: ${AI_IMAGE_MAX_ATTEMPTS:3}
    harness:
      max-loop-iterations: ${GENERATION_MAX_LOOP_ITERATIONS:3}
      accept-score: ${GENERATION_ACCEPT_SCORE:0.8}
      fail-on-clarification: ${GENERATION_FAIL_ON_CLARIFICATION:false}
      artifact-retention-days: ${GENERATION_ARTIFACT_RETENTION_DAYS:7}
~~~

Bean 约束：

- planningChatModel：规划、Prompt 和评估；
- imageGenerationModel：图片生成，不注入 ChatModel；
- 不提供 mockPlanningModel、mockImageGenerationModel；未配置真实模型时 Worker 启动失败；
- 禁止使用无 qualifier 的 ObjectProvider<ChatModel> 作为图片模型入口；
- AI_IMAGE_* 未配置时必须报配置错误，不能把规划 ChatModel 当图片模型。

## 8. 数据库和事件设计

不修改现有 GenerationTask、GenerationResult 和额度表字段语义，新增两张表。

### 8.1 GenerationPlan

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | UUID/CUID | 主键 |
| taskId | UUID/CUID UNIQUE | 任务一对一 |
| schemaVersion | VARCHAR | 计划协议版本 |
| status | ENUM | PLANNING/RUNNABLE/NEEDS_CLARIFICATION/FAILED |
| inputHash | VARCHAR(64) | Intent 规范化 SHA-256 |
| requirementJson | JSONB | RequirementBrief |
| structureJson | JSONB | StructurePlan |
| visualJson | JSONB | VisualSpec |
| promptJson | JSONB | PromptPackage |
| createdAt/updatedAt | TIMESTAMP | 审计时间 |

### 8.2 GenerationIteration

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | UUID/CUID | 主键 |
| taskId | UUID/CUID | 任务 ID |
| iteration | INTEGER | 从 1 开始，联合唯一 |
| promptHash | VARCHAR(64) | 本轮 Prompt hash |
| status | ENUM | GENERATED/EVALUATING/ACCEPTED/REFINING/FAILED |
| provider/model | VARCHAR | 图片模型身份 |
| providerRequestId | VARCHAR | 脱敏请求 ID |
| evaluationJson | JSONB | EvaluationReport |
| refinementJson | JSONB | RefinementPatch |
| errorCode | VARCHAR | 错误码 |
| startedAt/completedAt | TIMESTAMP | 执行时间 |

索引：taskId + iteration 唯一、status + createdAt、providerRequestId。阶段 JSON 不保存密钥和供应商原始响应。

复用 GenerationTaskEvent，新增事件：

~~~text
task.requirement_understood
task.structure_planned
task.visual_constraints_ready
task.prompt_constructed
task.generation_started
task.evaluation_completed
task.refinement_started
task.generation_accepted
~~~

事件只保存 stageRunId、版本、hash、score、reasonCodes 和脱敏摘要。

## 9. API 契约

Generation Task 的用户输入改为以下最小契约，前台不提交图片类型、行业、展示目标、受众、视觉偏好或循环策略：

~~~json
{
  "mode": "TEXT_TO_IMAGE",
  "prompt": "生成一张解释 AI 工作流的中文信息图"
}
~~~

规则：

- 五阶段始终执行，不提供 legacy-direct；
- 图片类型、行业、展示目标、受众、视觉偏好和循环策略由 Planning Model 生成，再由服务端校验和限制；
- ratio、resolution、imageCount 使用系统策略或模型推理结果，前台不直接提交这些结构化字段；
- API 先 reserve，任何规划、生成、评估或存储失败都 RELEASE；
- GET task 增加 planStatus、currentStage、currentIteration、evaluationScore；
- 新增 GET /dream_web/generation/tasks/{taskId}/plan，仅任务所属用户可访问；
- SSE 增加阶段事件，前台可显示需求理解、结构规划、视觉约束、Prompt 构造、图片生成和结果优化。

## 10. 错误、重试和额度

| 错误 | 是否重试 | 处理 |
| --- | --- | --- |
| 规划超时、429、5xx | 是 | 阶段内重试，最终失败并 RELEASE |
| 规划 JSON Schema 错误 | 否或仅一次修复 | PLANNING_OUTPUT_INVALID |
| 图片超时、429、5xx | 是 | 消耗 iteration/attempt 预算 |
| 图片权限或参数错误 | 否 | IMAGE_PROVIDER_REJECTED |
| 输出不可解码 | 否或重试一次 | PROVIDER_OUTPUT_INVALID |
| 评估未达标 | 是 | 进入 Refinement Loop |
| 内容审核拒绝 | 否 | RELEASE，不进入修订 |
| 对象存储临时失败 | 按 Worker 策略 | 清理已写对象，不可恢复时 RELEASE |

用户额度按任务计费，内部循环不重复扣款。供应商成本可单独记录在 GenerationIteration。

## 11. 安全与合规

- 用户文本、参考图和模型输出是不可信输入，不得覆盖 system prompt 或工具指令。
- 图片 URL 下载必须 HTTPS、域名白名单、禁止私网/loopback、校验大小和 MIME，防 SSRF。
- 规划模型和图片模型密钥分别注入 Secret；日志、死信、API 响应和 Artifact 不保存密钥。
- 参考图不得默认复制人物身份、Logo、商标或受保护内容。
- 评估失败不能通过降低安全阈值绕过审核，fail-open 默认关闭。
- Artifact 默认保留 7 天，支持按 task 删除。

## 12. 测试设计

单元测试：

- 每个 Stage 的合法输出、缺字段、未知字段、超长文本、低置信度和 Schema 兼容；
- Prompt 字段优先级、缺失字段、负面约束和不可捏造数据；
- Loop 达标停止、缺陷修订、重复 reasonCode 停止、最大循环、部分成功和失败；
- Harness 超时、重试、幂等 key、Artifact hash、脱敏和事件。

Provider 契约测试：

- 规划模型文本/参考图输入和结构化 JSON；
- 图片模型 URL/Base64/Data URL、429、5xx、401/403、空响应和超大图片；
- 供应商联调由人工使用真实模型配置完成；不维护 WireMock 或 Stub 外部供应商替身。

集成和 E2E：

- PostgreSQL 计划/迭代唯一约束、事务回滚、终态和额度；
- Redis 重复投递、Pending reclaim、ACK、多个消费者；
- 对象存储部分写失败、清理失败和幂等删除；
- Playwright 阶段时间线、循环优化、成功/部分成功/失败和 SSE 断线重连。

## 13. 实施顺序和验收

1. 新增领域记录、五个 Stage、Schema、Prompt 模板和 Harness 接口。
2. 新增 GenerationPlan/GenerationIteration 迁移和 Mapper。
3. 接入独立规划模型、多模态参考图输入和独立图片模型。
4. 接入真实多模态质量评估模型和有限循环修订。
5. 复用现有输出管线，完成图片结果、额度结算和 SSE 阶段事件。
6. 完成真实 PostgreSQL、Redis、local/SFTP 配置检查后，使用真实供应商人工联调。

验收标准：

- 每个任务都能查询五个阶段的版本化 Artifact 或明确失败原因。
- 图片模型和规划模型配置完全独立，缺少图片配置时不会错误调用 ChatModel。
- Prompt 包含适用的比例、行业、风格、布局、模块、图表和色彩；不适用字段不强制出现。
- Loop 在评分达标时停止，在缺陷可修复时有限修订，超过预算时稳定终止。
- 任务状态、SSE、结果 URL、对象键和额度流水保持现有契约。
- 模型不可用、格式错误、审核拒绝和存储失败均有稳定错误码、事件和额度补偿。
- 测试覆盖规划、生成、评估、循环、重试、死信和安全边界；bak 未修改。
