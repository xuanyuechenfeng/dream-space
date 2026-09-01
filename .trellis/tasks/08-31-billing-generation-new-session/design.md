# 账单记录与生成页新会话优化设计

## 1. 范围与原则

本次改动覆盖用户账单查询、账户页计数、生成页会话状态和空态布局。数据库账本、额度结算、管理员审计、生成任务协议及存储结构不变。

现有 `GenerationWorkspaceView.vue` 与 `styles.css` 含有未提交的生成页视觉与交互改动，包括标题区、三个示例提示词、输入框键盘行为、历史提示词和移动端历史抽屉。本任务在这些改动上补全需求，不重写组件，也不回退既有工作。

截图中的“提示模块”定义为新会话空态内的标题、说明、三个示例提示词与输入区域，不新增独立通知横幅。

## 2. 用户账单读取投影

### 2.1 数据边界

账单展示链路为：

```text
GET /dream_web/account/ledger
  -> BillingService.ledger
  -> BillingMapper 用户账单 list/count
  -> AccountView
```

`RESERVE` 和 `RELEASE` 是结算及对账所需的真实账本事件，只从普通用户读取投影中隐藏。`QuotaTransactionService`、`QuotaLedgerEntry`、枚举、迁移、额度汇总和管理员接口均不修改。

### 2.2 查询契约

在 `BillingMapper` 定义一个用户账单可见性条件，显式排除 `RESERVE` 与 `RELEASE`。该条件同时用于普通用户的列表和计数查询，并位于 `ORDER BY`、`LIMIT`、`OFFSET` 之前，确保：

- `items`、`total`、`pageCount` 使用同一数据集合；
- 不因前端或分页后的过滤产生短页、空页或错误总数；
- 用户传入 `type=RESERVE`/`RELEASE` 时自然得到 `items=[]`、`total=0`。

管理员 `GET /manage_web/users/{id}/ledger` 使用单独的完整列表和计数查询，仅保留现有可选类型筛选，不附加用户可见性条件。`BillingService.userLedger` 不再委托用户侧 `ledger`，而是独立执行相同的页码校验和完整查询。

### 2.3 账户页计数

`AccountView.vue` 保存账单与订单响应中的 `total`，标题使用服务端总数，而不是当前页 `items.length`。页面仍沿用现有首屏请求，不在本任务新增分页控件。

## 3. 生成会话状态契约

### 3.1 URL 与状态

生成页以 URL 明确区分本地新会话和已保存会话：

| URL | 活动会话 | 草稿 | 服务端写入 |
| --- | --- | --- | --- |
| `/generate` | `null` | 本地空白或恢复的未提交草稿 | 进入页面和点击“新建会话”均不写入 |
| `/generate/:sessionId` | 指定会话 | 服务端会话草稿 | 只读取指定会话，后续编辑按既有逻辑保存 |
| `/generate` 首次提交 | API 响应前为 `null` | 提交快照 | 任务接口原子创建一个会话和首个任务 |

生成 store 新增一个同步的新会话重置动作，负责关闭 SSE、清空活动会话、恢复标准空白草稿、重置事件游标并重新应用已加载的生成选项。它不调用 `createSession`，也不清空历史会话列表。

`load(sessionId)` 始终加载 options、quota 和历史摘要；仅当传入 `sessionId` 时读取指定会话，否则进入本地新会话状态，不再自动打开排序后的第一条历史记录。

### 3.2 路由与交互

- 点击历史项：打开该会话并切换到 `/generate/:sessionId`。
- 点击“新建会话”：调用本地重置动作并切换到 `/generate`；历史列表不增加记录。
- 从 `/generate/:sessionId` 导航回 `/generate`：路由监听必须处理 `sessionId` 变为 `undefined`，同步恢复空态。
- 从 `/generate` 首次提交：沿用现有 `generation.submit()`，请求中省略 `sessionId`；成功后使用返回的会话 ID 替换路由。
- 删除当前会话：沿用删除 API，随后进入 `/generate` 空态。

`createSession` API 和 store 方法不从后端删除，以保持兼容；本次用户入口不再调用它来预建空会话。

### 3.3 登录后恢复创作意图

`restoreIntent()` 恢复提示词、参数和参考图后：

- 当前 URL 为 `/generate` 时，仅把恢复值写入本地 draft，不创建会话；若 `submitOnRestore=true`，直接调用无 `sessionId` 的 submit，由服务端原子创建会话和任务。
- 当前 URL 显式指向已有会话时，继续通过既有 `saveDraft()` 保存到该会话。
- 参考图上传保持现有上传接口；无活动会话时 `saveDraft()` 仍为无操作，上传 ID 留在本地 draft 中等待首次提交。

## 4. 空态结构与居中布局

复用现有 DOM，将 `.generation-main` 作为唯一坐标系：顶部 header、可滚动 timeline 和底部 composer 组成三行网格。空态 timeline 使用二维居中，`.empty-session` 以主画布可用宽度为基准，不以浏览器视口或固定像素偏移定位。

需要收敛的旧规则：

- 禁用 `.generation-main.is-empty::after` 的第三行占位，避免与 composer 同占一行；
- 空态弹性行使用 `minmax(0, 1fr)`，移除桌面/移动端硬编码最小高度；
- 移除 `.empty-session` 的 `translateY`，避免短屏裁切；
- 桌面与平板保留三个等宽示例，移动端改为单列并展示全部三个；
- composer 保持正常网格流、水平自动边距和稳定侧边距，不使用 fixed/absolute；
- 在窄平板隐藏键盘提示，确保 footer 控件不横向溢出。

所有新增或调整的选择器限定在 `.generation-page`，避免影响灵感详情页复用的 composer 样式。已加载会话不带 `is-empty`，其 timeline 滚动和任务布局保持不变。

## 5. 异常与并发

- options/quota/历史加载失败继续使用 store 现有错误状态；页面不能因失败回退显示旧活动会话。
- submit 继续使用现有请求指纹和幂等键。未知网络错误重试复用键，明确 API 错误释放键。
- 路由只在 submit 成功后进入会话地址；失败时保留 `/generate` 与本地草稿。
- 切换新会话时关闭旧会话 SSE，避免后台任务刷新污染空态。

## 6. 验证与回滚

后端通过 mapper SQL 合约测试与 service 分页测试证明用户投影和管理员完整查询分离。前端通过 store 单元测试证明默认加载、显式会话、重置及首次提交行为，并通过 Playwright/浏览器检查空态几何关系与横向溢出。

视觉矩阵至少覆盖 `1440x900`、`1024x768`、`800x1024` 和 `390x844`，补充 `320x568` 短屏检查。验证三个提示项可见，空态与 composer 中心线一致、互不重叠，移动端 composer 位于底部导航之上。

回滚只需回退应用代码和测试；本任务无迁移、无数据删除。账本数据始终保留，因此回滚不会影响额度结算或审计。
