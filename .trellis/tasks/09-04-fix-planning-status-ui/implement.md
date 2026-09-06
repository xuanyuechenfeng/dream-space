# 执行计划

## 顺序清单

1. [x] 在 `ChatPlanningModel` 增加单图与集合 prompt 可选字段的缺失/null/异常类型兼容规范化，并同步 prompt 合同文本。
2. [x] 在 `CollectionPreflightProcessor`、API preflight service 和相关 DTO/事件中核对失败、澄清、过期和超时状态，确保稳定错误码与详情可被查询和 SSE 读取；只在必要处修复，不改账务边界。
3. [x] 在 `dream_web/src/stores/generation.ts` 增加客户端 pending submission 状态，重构 submit/preflight 等待流程，使提交后立即可渲染、READY/失败可原位收敛，并处理会话切换及晚到响应。
4. [x] 在 `GenerationWorkspaceView.vue` 与 `styles.css` 增加 pending、业务失败和恢复操作的视觉状态；不呈现“不计费”“临时项”“预规划”等内部语义，保证响应式布局稳定。
5. [x] 补充并运行 Java 与 Vitest 测试，覆盖合同形状、状态传播、提交竞态和单图回归。
6. [x] 启动前端/后端构建或测试服务，用 Playwright 检查桌面和移动端关键状态，记录剩余风险。

## 执行结果

- 前端 `vue-tsc`、Vitest（41 个测试）和 Vite 生产构建通过；覆盖任务开始后输入框立即清空、正式任务响应不回填旧描述、失败后恢复编辑和重试。
- Worker 完整测试（99 个）及 API preflight/任务/额度测试（5 个）通过。
- 针对规划日志中“已解析后仍失败”的路径，补充冻结阶段二次 Prompt 转换兼容测试：小写 `promptRelation` 与未知扩展字段不会再被误判为格式无效；转换真实失败会记录 preflight、槽位和异常详情。
- Playwright 桌面 `1440x900` 与移动 `390x844` 检查无横向溢出；进行中文案为“正在确定图片数量”，未暴露内部计费或规划术语。
- 未启动真实认证后端，因此未提交真实任务；浏览器检查使用 API mock，正式任务/SSE 仍由既有自动化测试覆盖。

## 验证命令

- `mvn -pl dream_service/worker,dream_service/api -am test`
- `npm --prefix dream_web run test -- --run`
- `npm --prefix dream_web run build`
- Playwright：桌面 1440x900、移动 390x844，覆盖提交中、READY 替换、失败重试和多槽位占位。

## 风险文件与回滚点

- 高风险：`ChatPlanningModel.java`、`generation.ts`、`GenerationWorkspaceView.vue`。
- 若合同兼容测试失败，回滚仅限规范化分支，保留必要字段和内容完整性校验。
- 若 UI 竞态测试失败，回滚 pending 渲染改动，不改变 API/preflight 和账务逻辑。
- 不触碰工作区中本任务之外的未提交文件。
