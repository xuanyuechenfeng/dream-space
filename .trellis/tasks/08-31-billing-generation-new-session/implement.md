# 实施计划

## 1. 账单读取投影

1. 在 `BillingMapper` 增加普通用户账单可见性 SQL 常量，将其同时用于用户列表与计数，并为管理员补齐不带该条件的完整列表查询。
2. 调整 `BillingService.userLedger`，独立使用管理员完整 list/count，保留类型规范化、页码限制和 `pageCount` 计算；普通用户 `ledger` 继续使用过滤后的 list/count。
3. 更新 `AccountView.vue`，分别保存 ledger/order 的服务端 `total`，账单标题显示总数而非当前页长度。
4. 扩展 `BillingMapperSqlTest`：断言用户 list/count 同时排除 `RESERVE`、`RELEASE` 且过滤早于分页；断言管理员 list/count 保留 nullable enum cast 且无用户可见性条件。
5. 新增 `BillingServiceTest`：验证用户分页取过滤后的 count，`type=RESERVE/RELEASE` 可返回空页；验证管理员路径使用完整查询并能返回技术流水。

回滚点：账单改动可以作为独立批次回退，不涉及表结构和账本写入。

## 2. 新会话状态

1. 在 generation store 增加本地新会话重置动作，关闭事件连接并清理 active/draft/cursor，但保留历史摘要。
2. 修改 `load(sessionId)`：无 ID 时执行本地重置，有 ID 时只打开指定会话；不再自动打开最新历史。
3. 调整 `GenerationWorkspaceView.vue` 的“新建会话”、无 ID 路由监听和删除后跳转，使 `/generate` 始终对应无活动会话空态。
4. 调整 `restoreIntent()`：无活动会话时只恢复本地 draft；自动提交继续通过无 `sessionId` submit 原子创建会话和首任务。
5. 扩展 `generation.test.ts`，覆盖：有历史时 `/generate` 仍无 active；显式 ID 只打开对应会话；本地重置不调用创建接口并清空旧草稿/SSE；首次提交省略 `sessionId` 且历史仅新增一个会话。

回滚点：状态行为可独立回退；保留现有 `createSession` API 以避免协议级回滚。

## 3. 空态视觉

1. 保留现有空态标题、说明、三个 starter prompts、header、composer、历史提示词与移动历史抽屉结构。
2. 在 `.generation-page` 作用域内收敛空态网格规则：清除旧伪元素、使用 `auto minmax(0, 1fr) auto`、让 timeline 二维居中并移除 viewport transform。
3. 统一空态组与 composer 的主画布中心线和宽度约束；移动端堆叠并展示全部三个提示项，窄平板隐藏键盘提示以避免 footer 溢出。
4. 检查已加载会话、参数弹层、参考图、错误提示、textarea 增高、深色主题和侧栏折叠状态，避免空态规则泄漏到会话状态或灵感详情 composer。

回滚点：视觉规则限定在生成页，可不影响 store/API 地独立回退。

## 4. 验证顺序

1. 后端定向测试：`mvn -pl api -am -Dtest=BillingMapperSqlTest,BillingServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`。
2. 前端定向测试：`npm run test:unit -- src/stores/generation.test.ts`。
3. 前端静态验证：`npm run typecheck`、`npm run build`、`npm run test:unit`。
4. 后端回归：`mvn -pl api -am test`。
5. 启动本地前端并用浏览器/Playwright 检查 `1440x900`、`1024x768`、`800x1024`、`390x844`；额外检查 `320x568`，断言无横向溢出、三个提示可见、空态和 composer 不重叠且水平中心一致。
6. 在可用完整后端环境时运行 `RUN_REAL_E2E=1 npm run test:e2e`；若依赖服务不可用，在交付说明中明确记录未执行项，不用静态结果替代真实链路结论。
7. 最终按 PRD 逐项复核用户账单、管理员账单、默认 `/generate`、显式会话、新建会话、首次提交和恢复登录意图。

## 5. 评审门

- 开始实现前由用户确认本 PRD、设计与实施计划。
- 实现后执行全范围 Trellis check，重点检查工作树中既有未提交生成页改动未被覆盖。
- 不修改或清理与本任务无关的认证、邮件、YAML 配置和设计文档改动。
