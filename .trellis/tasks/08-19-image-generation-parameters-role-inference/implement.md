# Implementation Plan

## 1. Persistence And Domain

- [x] 新增 GenerationTask 输入参数迁移、单图约束和 `custom` 比例值。
- [x] 更新数据库枚举、任务 Record、Mapper 和 Worker 快照。
- [x] 保证 JSONB 图片列表使用结构化类型处理。

## 2. API Contract

- [x] 扩展 options 返回比例、分辨率、尺寸、额度和可用状态。
- [x] 将 Draft/TaskRequest 改为 `imageIds + ratio + resolution + width + height`。
- [x] 实现图片归属、尺寸、比例、分辨率、幂等和固定单图校验。
- [x] 更新任务响应、retry、额度和相关测试。

## 3. Worker Planning And Generation

- [x] 增加 `InputImageRole`、`ImageAssignment` 和 RequirementBrief 字段。
- [x] 将所有输入图以无角色标签传给真实规划模型。
- [x] 校验意图、角色数量、图片 ID、唯一性和置信度。
- [x] 将硬输出参数和验证后的角色传入 Prompt、图片模型和质量评估。
- [x] 统一规划模型各阶段 JSON schema，明确 `confidence` 数字类型并兼容已知标签格式。
- [x] 由 Worker 注入并覆盖结构计划中的最终比例、分辨率和宽高；SMART 仅读取模型比例并自行计算尺寸。
- [x] 实现 smart 输出参数解析/持久化及输出尺寸校验。

## 4. Frontend

- [x] 更新 API 类型和 Pinia 草稿状态。
- [x] 增加尺寸计算/校验纯函数及单元测试。
- [x] 增加比例、分辨率、尺寸参数面板。
- [x] 改造附件为无角色素材列表，移除旧角色分支。
- [x] 保持当前工作台风格并适配桌面、平板、移动端。
- [x] 生成页面移除任务搜索/状态筛选，自动滚动到底部，隐藏任务状态徽标并加宽输入气泡。
- [x] 生成结果输出与下载统一为 PNG，并保留历史 WebP 对象读取兼容。

## 5. Validation

- [x] Maven 全量测试。
- [x] dream_web typecheck、unit、build。
- [x] `git diff --check` 和旧字段/旧控件扫描。
- [ ] 本地页面桌面与移动视口检查。
- [ ] 记录真实模型人工联调所需配置与验证用例。

验证说明：本地页面路由与登录拦截正常；生成工作台需要人工登录后检查桌面/移动参数浮层。真实模型联调必须配置规划 ChatModel、图片模型、PostgreSQL、Redis 和 S3，不使用供应商替身，需人工验证 0/1/2 张素材、调换上传顺序、低置信度角色失败、2K/4K 与 smart 回写场景。

## Rollback

- 代码回滚时保留已应用的新增迁移，通过后续迁移恢复约束；不修改或删除已执行迁移。
- API/Worker/前端必须作为同一发布单元回滚，避免新旧请求契约混用。
