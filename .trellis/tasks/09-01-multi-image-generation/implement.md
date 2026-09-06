# 多图片生成与失败续生成实施计划

## 前置门槛

- [x] 用户已评审并批准 `prd.md`、`design.md` 和本实施计划；任务目录与状态已建立并进入实施。
- [x] 已检查工作树，保留用户已有的 `PostgresMigrationIntegrationTest.java` 和 `scratch/CompareSchema.java` 改动；不把它们纳入本任务提交。
- [x] 创建功能开关和灰度配置：preflight、版本 2 execution、2-4 张数量、继续缺失；默认关闭版本 2 写入，保留旧单图读路径。

## 1. 共享契约、枚举和迁移

- [x] 在 `common` 增加 `CollectionMode`、槽位/执行状态、`ImageCollectionPlan`、`ResultSlotPlan`、`GenerationPreflight` 和 `GenerationExecution` 的共享记录/枚举。
- [x] 将队列消息升级为共享 `GenerationWorkItem` envelope，包含 `PREFLIGHT | EXECUTION` kind、targetId、schemaVersion 和投递幂等信息；两类消息分别以 preflightId 和 executionId 为 targetId。
- [x] 为集合计划、槽位验收、槽位级 Prompt 和预规划响应建立严格 JSON Schema/DTO 校验；保留事实不猜、视觉可补和 1-4 上限。
- [x] 新增时间戳迁移：扩展 `GenerationTask` 数量 CHECK、`settlementVersion`、`preflightId`、`consumedCost`；新增 preflight、slot、execution 表及索引；扩展 plan、iteration、ledger 的 execution/slot 字段。
- [x] 为旧任务补齐只读兼容映射，不回写历史任务，不修改已经执行的迁移文件。
- [x] 更新 enum 注册、迁移资源测试和 schema/integration fixtures。

回滚点：迁移只追加不回退；在关闭版本 2 写入口且排空 execution 后，可回退应用代码。

## 2. 异步预规划与任务创建

- [x] API 增加 `CollectionPreflightService`：同步校验参数/素材归属，先选择并快照 pricing rule/version/unitCost，再创建 preflight 和 `GenerationPreflightEvent`、发布 PREFLIGHT work item并提供 GET/SSE；API 不引入 Spring AI。
- [x] Worker 增加 `CollectionPreflightProcessor`：读取素材、输入审核、真实集合规划、数量/槽位/规格校验、smart 尺寸解析和冻结计划持久化。
- [x] 实现 `POST /dream_web/generation/preflights`，保存 draftKey/inputHash 并返回 QUEUED；同 key 同输入重放，不同输入冲突，同 draftKey 新输入取代旧 READY/PLANNING 记录。
- [x] Worker 提交 READY 时在同一事务写冻结计划、`readyAt`、`expiresAt = readyAt + 10 minutes` 和 `preflight.ready` 事件；澄清/失败也与对应 `GenerationPreflightEvent` 原子提交。
- [x] API 在 GET READY preflight 时签发绑定 preflight/user/input/expiry 的签名短期 token；数据库不保存可用 token，以 preflight 行锁和 consumedAt 保证单次消费。
- [x] 将 token 设计为单次原子消费；正式创建事务内锁 token，创建 session/task/plan/slots/execution，一次性预留完整额度并写 `task.queued`。
- [x] 余额不足或创建异常时整体回滚，token 保持可用至过期；正式创建响应重试按 create 幂等键返回同一任务。
- [x] 将 `GenerationService.retry` 拆为 `continueMissing` 与 `fullRegenerate` 语义：前者原任务缺失槽位预留，后者重新 preflight/create 新任务且无来源关联。
- [x] 取消 API 改为按 execution/slot 结算，保留成功槽位，释放未成功槽位。
- [x] 为 TaskView、SessionDetail、Options 增加集合模式、槽位摘要、完成/缺失/消费统计和数量选项。

回滚点：先保留旧 `POST /generation/tasks` 的版本 1 只读/兼容入口；新前端开关关闭时 API 不创建版本 2 任务。

## 3. 额度与对账

- [x] 扩展 `QuotaTransactionService`，支持 execution 级 reserve/release 和 task+slot 级 consume，幂等键分别为 `reserve:{executionId}`、`consume:{taskId}:slot:{index}`、`release:{executionId}`。
- [x] 成功槽位消费只扣 `unitCost`；失败/取消释放 `reservedAmount-consumedAmount-releasedAmount`；账户锁、账本写入和槽位状态更新在同一事务。
- [x] 改造 `QuotaReconciliationService`：版本 1 沿用旧任务级校验，版本 2 按 execution 聚合 reserve/consume/release、按成功槽位校验 consume；无法安全修复时记录 BLOCKED。
- [x] 增加 `RESERVE = CONSUME + RELEASE`、`consumedCost = succeededSlotCount * unitCost`、账户 reserved/available 漂移测试。

风险点：旧对账器不能在版本 2 上运行；灰度期间必须确保只有新对账分支扫描版本 2。

## 4. Worker 槽位执行

- [x] 正式任务发布共享 envelope 的 `EXECUTION` 消息，以 executionId 为 targetId；Worker 从 execution/plan 读取槽位列表和执行批次幂等信息。
- [x] Worker 读取已冻结 `ImageCollectionPlan`，不再为版本 2 任务执行二次规划；按槽位顺序逐一 claim，供应商每次固定 `n=1`。
- [x] 扩展 `ImageGenerationRequest` 传递 `slotIndex`、`slotAttempt` 和槽位 Prompt；供应商 request id 使用 task/execution/slot/attempt 组合。
- [x] 将质量评估恢复为槽位级：技术尺寸/比例、槽位职责、共享事实/视觉连续性和输出审核均属于同一槽位尝试；可修复错误耗尽后才最终失败。
- [x] 新增成功事务：对象写入后按 task/execution/slot/quota 顺序加锁，插入唯一 result，consume 单张费用，标记 slot 成功，写 `task.result.succeeded`，最后一个槽位再结束 execution/task。
- [x] 新增最终失败事务：标记当前槽位失败、后续保持 waiting，结束 execution，释放剩余 reserve，按成功数设置 task 终态并写事件。
- [x] 处理 Worker 重启、队列重复、对象写入后 DB 失败和取消/成功竞态；迟到结果必须清理且不得消费。

回滚点：版本 2 Worker 与版本 1 Worker 并行读取不同 execution/schema 版本；禁止让旧 Worker 领取版本 2 execution。

## 5. SSE 与前端工作台

- [x] 增加并集中定义 `task.execution.queued`、`task.slot.started`、`task.slot.retrying`、`task.result.succeeded`、`task.slot.failed`、`task.execution.released` 事件类型和 payload 解码器。
- [x] SSE 仍按 event id/Last-Event-ID 保证重放；收到槽位事件刷新任务投影并刷新 quota，不在前端自行推算账本。
- [x] `GenerationDraft` 增加 `imageCountMode`/`imageCount`；`submit()` 串联 POST preflight、preflight SSE/GET 和 create，保留单次 submitting 状态和 create 幂等键。
- [x] 将模糊 `retry()` 拆为 `continueMissing()` 与 `regenerateAll()`，防止成功槽位被重复提交。
- [x] 参数面板增加自动/1/2/3/4；展示数字冲突提示、结构槽位冲突澄清和预计额度。
- [x] 按 preflight 槽位预展示完整网格；成功替换对应占位，失败、尚未启动和取消使用不同状态；提供继续缺失/全部重新生成操作。
- [x] 保持现有会话、新建会话、素材、比例/尺寸、下载、移动端抽屉和深浅色主题行为。

## 6. 定向验证

### 后端

```text
mvn -pl common,api,worker -am test
mvn -pl common -am -Dtest=MigrationResourceTest,PostgresMigrationIntegrationTest test
mvn -pl api -am -Dtest=GenerationServiceTest,GenerationPreflightServiceTest test
mvn -pl worker -am -Dtest=CollectionPreflightProcessorTest,GenerationProcessorTest,JdbcGenerationWorkerStoreTest,QuotaReconciliationServiceTest test
```

### 前端

```text
npm --prefix dream_web run typecheck
npm --prefix dream_web run build
npm --prefix dream_web run test:unit
```

### 场景与真实链路

- [x] API 契约覆盖数字数量冲突、四季 `DIMENSIONAL`、总结+流程 `SEQUENCE`、事实缺失澄清、token 过期/消费、余额不足回滚。
- [x] Worker 契约覆盖槽位 2 失败不调用槽位 3/4、逐张消费、失败释放、取消竞态、重启续跑和对象 cleanup。
- [ ] Playwright 覆盖 `1440x900`、`1024x768`、`390x844`、`320x568` 的槽位稳定布局、SSE 逐张替换和两种重生成操作。
- [ ] `RUN_REAL_E2E=1 npm --prefix dream_web run test:e2e` 仅在 API、PostgreSQL、Redis、对象存储、Worker 和真实模型均可用时执行；本轮未执行，依赖环境未启用。

## 7. 发布门与最终检查

- [ ] 先灰度单图版本 2，再灰度 2-4 图；检查首图延迟、槽位失败率、部分成功率、消费/释放不变量、孤立对象和对账 finding。
- [ ] 验证版本 1 历史任务、管理员账单、用户账单和既有单图任务无回归。
- [x] 完成 PRD AC1-AC11 逐项证据记录。
- [x] 运行 `python ./.trellis/scripts/task.py validate multi-image-generation`。
- [x] 按 Trellis Phase 3.3 完成 `.trellis/spec/` 审查，并更新 backend database 与 frontend state 规范中的跨层契约和对账边界。
- [ ] 生成工作提交计划；不包含用户已有的无关 dirty 文件，不推送远程。

## 本轮证据

- 后端完整测试：`mvn -pl common,api,worker -am test`，159 项通过，2 项外部依赖集成测试按环境跳过。
- Worker 定向测试：预规划、队列、v2 槽位/结算、provider request id 共 23 项通过；新增素材角色与质量 refinement 断言。
- 前端：`typecheck` 通过，单元测试 36 项通过，生产构建通过；构建保留既有 `photography-08.webp` 运行时解析警告。
- Trellis context 校验通过；真实 E2E 未运行，因 PostgreSQL、Redis、对象存储和真实模型链路未启用。
- 用户已有的 `dream_service/common/src/test/java/com/dreamspace/common/persistence/PostgresMigrationIntegrationTest.java` 与 `scratch/CompareSchema.java` dirty 改动未作为本任务范围处理。
