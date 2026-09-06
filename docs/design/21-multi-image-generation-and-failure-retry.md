# 多图片生成与失败续生成设计

## 1. 目标

一次用户输入支持 1-4 张图片。用户可以只指定数量，也可以要求一组有明确关系的图片，例如“春夏秋冬各一张”或“总结图、流程 1、流程 2、流程 3”。系统按槽位独立返回、逐张计费；某槽位最终失败不影响其他槽位执行，成功结果不退款，默认只补齐缺失槽位。

## 2. 核心结论

- 数量参数为“自动、1、2、3、4”，手动数量优先于纯数字提示词；结构化槽位数量必须匹配手动数量，冲突时阻止创建。
- 所有请求统一执行一次不计费的异步集合预规划：API 创建 preflight 并入队，Worker 完成审核和真实模型规划；READY 后前端自动创建正式任务并整单预留额度。
- 一个任务只冻结一份图片集合计划；计划包含共享事实/视觉约束和每个槽位的职责、内容范围、验收规则及独立 Prompt。
- Worker 使用有界线程池并发执行槽位，每次供应商请求 `n=1`；并发数由 `dream-space.worker.slot-concurrency` 控制，默认 4 且限制为 1-4。质量修复和临时错误重试属于各自槽位，不重复计费。
- 每个成功槽位独立 `CONSUME`；失败/取消只 `RELEASE` 未成功槽位。全部重新生成直接创建一个新的独立任务，原任务不变。
- 任务创建后立即预展示全部槽位，SSE 只替换对应占位，区分失败、尚未启动和已取消。

## 3. 异步预规划协议

`POST /dream_web/generation/preflights` 接收原始提示词、素材、输出参数和数量模式。API 同步完成请求格式、素材归属以及影响计费的模型/分辨率参数校验，并在入队前快照 `pricingRuleId`、`pricingRuleVersion` 和 `unitCost`。首次响应返回 `QUEUED`、preflight id 和 SSE 地址，不等待模型规划。

API 和正式生成共用同一个版本化队列 envelope：

```text
GenerationWorkItem
  schemaVersion
  kind: PREFLIGHT | EXECUTION
  targetId: preflightId | executionId
  deliveryId / attempt
```

Worker 按 `kind` 分派。`PREFLIGHT` 由 `CollectionPreflightProcessor` 领取，依次写入 PLANNING、读取素材、执行输入审核、生成集合计划、校验数量/槽位/Prompt，并在已冻结计价档内解析 smart 尺寸；正式 `EXECUTION` 只读取冻结计划，不再次规划。Worker 不得更改已快照的计价选择字段，规划要求跨计价档规格时返回澄清或失败。

当前版本的 `CollectionPreflightProcessor` 调用 `PlanningModel.collection(...)` 生成结构化集合提案，再由 Worker 执行确定性的边界校验和冻结。规划模型需要把“春夏秋冬”拆成共享同一主体、按季节维度变化的四个槽位；把“总结 + 流程 1/2/3”拆成职责不同、内容范围和验收条件独立的四个槽位。每个槽位都拥有独立 Prompt，因此同一原始输入可以产生有明确差异的图片；共享事实、视觉连续性、素材角色、数量、规格和事实不猜约束由冻结校验统一保证。模型不可用、置信度不足、事实缺失或槽位冲突时返回澄清或失败，不得悄悄改变数量或槽位职责。

Preflight 状态及事件使用独立的 `GenerationPreflightEvent`，不复用要求 taskId 的任务事件。至少包含 `preflight.queued`、`preflight.planning`、`preflight.ready`、`preflight.needs_clarification` 和 `preflight.failed`；状态变更和事件在同一事务提交，SSE 用单调 event id 支持重连。

Worker 成功提交 READY 时同时写入 `readyAt` 和 `expiresAt = readyAt + 10 minutes`。前端收到 `preflight.ready` 后读取 preflight，API 才签发绑定 preflightId、userId、inputHash 和 expiresAt 的签名 `planToken`。Token 本身不落库；同一 READY 结果可重复读取，但正式创建通过 preflight 行锁和 `consumedAt` 只允许消费一次。

读取 preflight 的业务结果包括：

- `READY`：返回未过期的 `planToken`、目标数量、集合类型、槽位标签、解析后的输出参数、单张价格和预计总价；
- `NEEDS_CLARIFICATION`：缺少流程事实、槽位职责或存在多个合理解释，无 token；
- `FAILED`：输入审核拒绝、规划模型不可恢复失败或计划校验失败，无 token；
- 4xx：仅用于同步可确定的请求格式、素材归属和输出参数非法。

规划只允许补全不改变意图的视觉细节。不得编造业务事实、流程、数字、文案或槽位职责。数字冲突可按手动数量执行并提示；“春夏秋冬”与“总结+流程1/2/3”这类可枚举职责冲突必须要求用户修正。

预规划成功后，前端自动调用 `POST /dream_web/generation/tasks`，请求只携带正式幂等键和 `planToken`。服务端验证签名、用户、输入哈希和过期时间，并在一个事务中锁定 preflight、创建任务/集合计划/结果槽位/执行批次、按 `目标数量 × 冻结单张价格` 预留额度、标记 preflight 已消费，然后发布 `kind=EXECUTION,targetId=executionId` 的工作项。余额不足时整体回滚，token 在到期前仍可重试。

`planToken` 的 HMAC 密钥通过部署配置 `PREFLIGHT_TOKEN_SECRET` 注入。未配置或为空时，READY 结果仍可查询，但 API 拒绝签发 token 并返回 `PREFLIGHT_TOKEN_UNAVAILABLE`；不会使用硬编码或进程内临时密钥。

## 4. 集合计划

```text
ImageCollectionPlan
  mode: VARIATIONS | DIMENSIONAL | SEQUENCE
  shared: facts, subject, audience, visual system, continuity rules
  slots[0..N-1]: label, role, intent, content scope, prompt, acceptance
```

- `VARIATIONS`：只要求生成 N 张时，生成 N 个不偏离核心意图的方案；
- `DIMENSIONAL`：四季、昼夜、配色等显式维度一一对应槽位；
- `SEQUENCE`：总结图、步骤图、分镜等职责不同的系列，整组覆盖事实，每张只展开自身范围。

首次规划后所有槽位 Prompt 冻结。继续缺失和 Worker 自动恢复不得重新解释原始输入；全部重新生成的新任务可以重新规划。一个任务所有槽位共享比例、分辨率、宽高、模型、素材和单张价格，混合规格必须拆分任务。

## 5. 执行和状态

正式任务由一个或多个 `GenerationExecution` 批次组成：首次生成或继续缺失各占一个批次；同一任务最多一个活动批次。

```text
WAITING -> GENERATING -> SUCCEEDED
                    \-> FAILED
WAITING/GENERATING -> CANCELLED
```

Worker 为每个批次把待处理槽位提交到有界线程池；每个槽位独立完成 claim、图片模型调用、技术/语义/安全校验、对象存储和结算事务。父流程等待本批次所有槽位结束，但不会因某个槽位失败而取消兄弟槽位。所有槽位终态后统一聚合：全部成功为 `SUCCEEDED`，部分成功为 `PARTIALLY_SUCCEEDED`，全部失败为 `FAILED`。

可重试供应商错误和质量修复只在当前槽位内执行；达到限制才算最终失败。队列重复、Worker 重启和迟到响应通过 execution/slot/result 唯一约束与状态锁处理；重投递跳过已成功或已失败的终态槽位，只恢复未完成槽位并再次执行聚合。

## 6. 额度

版本 2 执行批次使用以下幂等键：

```text
reserve:{executionId}
consume:{taskId}:slot:{slotIndex}
release:{executionId}
```

成功事务同时插入 `(taskId, index)` 唯一结果、消费 `unitCost`、标记槽位成功并写结果事件。单槽位失败只记录该槽位终态，不提前释放共享批次额度；所有槽位结束后聚合事务一次性释放该批次未消费预留。取消或执行级致命错误直接释放剩余预留。任务的 `estimatedCost/totalCost` 表示完整预计费用，`consumedCost` 表示已成功槽位实际消费。

每个执行批次终态必须满足：

```text
reservedAmount = consumedAmount + releasedAmount
consumedCost = succeededSlotCount * unitCost
```

旧任务按结算版本 1 继续使用任务级对账；新任务按版本 2 按 execution 和 slot 对账。旧对账器不得扫描版本 2 执行批次。

## 7. 失败、取消和重生成

- 继续缺失：原任务上为所有非成功槽位建立新 execution，只补齐缺失位置；成功图片和消费不变。
- 全部重新生成：使用原输入重新执行 preflight/create，创建无来源关联的新任务；原任务和图片保留。
- 取消：按 task/execution/slot/quota 的固定顺序加锁，再停止当前及后续未成功槽位并释放剩余预留；已成功图片不撤销；迟到供应商结果清理且不扣费。

## 8. 前台与 SSE

任务创建后按计划顺序展示全部槽位标签。状态至少区分：等待中、生成中、已完成、当前失败、尚未生成、已取消。`task.result.succeeded` 事件到达后只替换对应槽位，汇总同步显示 `已完成 x/N` 和实际消费额度。部分失败/取消提供“继续生成缺失图片”和“全部重新生成”两个独立操作。

正式任务创建后立即进入服务端权威的等待/执行展示，不使用前端经过时长把任务中断为“超时重试”。正式任务创建前的提交快照保存在 `sessionStorage`，页面刷新或路由切换后可按 preflight id 和幂等键恢复；界面只显示自然的生成准备状态，不展示 preflight、临时任务或计费实现概念。提交状态建立时立即清空输入框，失败后的“再次编辑”操作才恢复原提示词。

事件 payload 仅包含 task/execution/slot/result 标识、计数、金额、版本和 reason code；SSE 使用 event id/`Last-Event-ID`，前端收到事件后刷新服务端任务和额度投影，不自行修改账本。

## 9. 数据改造

新增 `GenerationPreflight`、`GenerationPreflightEvent`、`GenerationResultSlot`、`GenerationExecution`。Preflight 保存 `QUEUED | PLANNING | READY | NEEDS_CLARIFICATION | FAILED | CONSUMED | EXPIRED | SUPERSEDED` 状态、输入哈希、冻结计划、API 侧价格快照以及 nullable `readyAt/expiresAt`；只有 READY 事务写入二者。`GenerationTask` 放宽数量 CHECK 到 1-4，增加 `settlementVersion`、`preflightId`、`consumedCost`；`GenerationPlan` 保存 `collectionJson`；`GenerationIteration`、`QuotaLedgerEntry` 增加 execution/slot 维度。现有 `GenerationResult(taskId,index)` 唯一约束继续作为结果幂等保护。

所有迁移采用新的时间戳文件，旧迁移不可改。版本 1 历史任务只读兼容，不批量重写。

## 10. 发布与验证

先上线双读和新对账，再上线 API 和 Worker，最后打开前端数量控件；先灰度单图版本 2，再灰度 2-4 图。回滚时关闭版本 2 写入口，排空或事务化取消活动 execution 后再回退 API/前端；数据库迁移不回退。

必须覆盖：数量与槽位冲突、事实澄清、`PREFLIGHT | EXECUTION` 分派、preflight 事件重放、价格快照先于入队、`readyAt` 起算的 token 过期/消费幂等、整单预留、逐槽位消费/释放、供应商调用实际重叠、单槽位失败不阻断兄弟槽位、Worker 重启、重复消息、迟到结果、取消竞态、继续缺失、全部重新生成、SSE 逐张展示和桌面/移动布局。真实 E2E 只能在 API、数据库、队列、存储、Worker 和真实模型均可用时执行。

## 11. 相关决策

- [ADR 0001](../adr/0001-reserve-full-multi-image-cost-and-settle-per-slot.md)：整单预留、逐槽位结算
- [ADR 0002](../adr/0002-freeze-one-image-collection-plan-per-task.md)：每任务冻结一份集合计划
- [ADR 0003](../adr/0003-preflight-collection-plan-before-task-creation.md)：创建任务前完成集合预规划
