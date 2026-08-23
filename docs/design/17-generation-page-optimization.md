# 生成页面交互与 PNG 输出优化设计

## 1. 背景与目标

生成工作台当前仍提供任务搜索和状态筛选，任务卡片顶部重复展示成功/失败状态标识，生成后也不会稳定地把最新对话内容带入视口底部；用户输入区域在桌面端偏窄，生成结果下载则使用 WebP 文件名和 WebP 存储契约。本次优化目标是收敛生成页的信息层级，并让模型 PNG 输出在生成页下载时保持 PNG 格式。

## 2. 需求范围

- 删除生成页顶部的“搜索任务”输入框、“搜索”逻辑和“全部状态”选择框及其筛选逻辑。
- 对话内容首次加载、切换会话、SSE 刷新导致内容变化，以及用户点击生成成功后，将 `.timeline` 滚动到内容底部。
- 任务对话区域不再展示生成成功、生成失败、取消等任务状态徽标；进行中的任务仍保留必要的占位画布、取消入口和错误结果区域，避免用户失去操作反馈。
- 增大用户输入气泡的桌面最大宽度，并保持移动端 100% 宽度和无横向溢出。
- 模型输出以 PNG 编码保存：生成结果和缩略图使用 `.png` 对象键及 `image/png` 内容类型；前端下载使用 `.png` 文件名。历史 WebP 对象读取保持兼容。

## 3. 前端设计

### 3.1 状态和渲染

`GenerationWorkspaceView.vue` 移除 `search`、`statusFilter`、`visibleTasks` 的筛选分支，任务列表直接按创建时间排序。顶部 `generation-top` 保留为空的结构占位或直接移除其筛选内容，避免无意义控件占据首屏空间。任务卡片头部只保留比例、分辨率、尺寸和额度摘要，不再渲染 `.task-status`。

错误和生成中的可操作反馈保留在结果舞台中：生成中显示占位和取消按钮，失败/取消显示错误文案及现有“再次编辑/重新生成”操作；成功状态由结果图片本身表达。

### 3.2 自动滚动

为 `.timeline` 增加模板 ref，并集中使用 `scrollTimelineToEnd()`：

1. `load()`、`openSession()` 完成并经过 `nextTick` 后执行；
2. `submit()` 在 `generation.submit()` 和路由更新完成后执行；
3. 监听活动会话任务列表的长度、任务状态、结果数量和更新时间，SSE 刷新后执行。

滚动采用 `element.scrollTo({ top: element.scrollHeight, behavior: "smooth" })`，在 `prefers-reduced-motion` 下改为 `behavior: "auto"`。使用 `requestAnimationFrame`/`nextTick` 等待图片和占位节点进入 DOM；滚动失败不影响生成流程。

### 3.3 输入宽度

桌面端 `.input-bubble` 最大宽度从 460px 提升到 620px，同时保留 `width: min(100%, 620px)`；平板和移动端继续由现有响应式规则覆盖为全宽。

## 4. PNG 输出设计

### 4.1 Worker 编码边界

新增 `PngImageWriter`，沿用现有图像解码、裁切、缩略图尺寸和 SHA-256 校验流程，将最终图像和缩略图编码为 PNG。`GenerationOutputPipeline` 使用该 writer，并写入：

- `results/{taskId}/{resultId}.png`
- `thumbnails/{taskId}/{resultId}.png`
- `mimeType = image/png`

参考图上传仍使用现有 WebP 规范化 writer，不在本次范围内改变。

### 4.2 存储和读取兼容性

`ObjectKeyPolicy` 允许 `results`、`thumbnails` 下的 `.png` 与历史 `.webp`，`references` 继续只允许 `.webp`。Local/SFTP object storage 根据扩展名返回 `image/png` 或 `image/webp`，从而保证 API 响应的 Content-Type 与实际字节一致。数据库不新增字段，历史结果记录继续可读。

### 4.3 API 与前端下载

生成结果 API 继续使用数据库中的 `mimeType` 返回二进制内容；新任务自然返回 `image/png`。前端下载读取响应 Blob，并将文件名固定为 `dream-space-{resultId}.png`。不通过改扩展名伪装格式，也不改变预览 URL。

## 5. 验收标准

- 生成页 DOM 和源码中不存在任务搜索输入、状态筛选控件及相关状态筛选逻辑。
- 加载会话、切换会话、提交生成和 SSE 更新后，滚动容器的 `scrollTop` 到达底部附近。
- 任务卡片不显示成功/失败/取消状态徽标；生成中和失败舞台仍可读且可操作。
- 桌面输入气泡最大宽度为 620px，390px 移动视口无横向滚动。
- 新生成结果对象键为 `.png`，内容类型为 `image/png`，前端下载文件名以 `.png` 结尾；历史 WebP 结果仍能读取。
- `dream_web` typecheck、build、unit tests，以及相关 Worker/Common Maven 测试通过。

## 6. 回滚

前端可独立回滚交互变化。PNG 输出回滚时保留新 PNG 对象和读取兼容规则，仅停止写入 PNG；不删除已生成对象，也不修改既有迁移。
