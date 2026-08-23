# 07 测试、迁移与开发任务

## 7.1 测试分层

| 层级 | 工具 | 必测内容 |
| --- | --- | --- |
| Java 单元 | JUnit 5、Mockito | 费用、状态机、权限、参数校验、对象键、错误映射 |
| Mapper 集成 | Testcontainers PostgreSQL | 18 张表查询、唯一键、锁、事务、JSON、迁移 |
| Redis 集成 | Testcontainers Redis | Stream ack/reclaim、幂等、限流、SSE fan-out |
| AI 集成 | 人工真实供应商联调 | ChatModel、图片模型、超时、429、5xx、空响应、图片解码 |
| API 契约 | Spring MockMvc + JSON fixtures | 路径、状态码、响应字段、Cookie、SSE event |
| 前端单元 | Vitest | stores、API adapter、参数计算、过滤、事件去重 |
| 前端 E2E | Playwright | 用户端/管理端核心流程，桌面/移动 |
| 视觉回归 | Playwright screenshot | 颜色、布局、断点、暗色、弹窗、抽屉、加载态 |

## 7.2 必测业务场景

### 认证

1. 普通用户获取验证码、错误码、过期、重试、三协议未勾选和登录回跳。
2. 管理员独立登录；用户 Cookie 访问 `/manage_web/*` 返回 401/403；VIEWER 写操作返回 403。
3. 登出后 session 失效；过期 session 不能续期为无限期。

### 生成

1. 2K/4K、1-8 张、参考图 0-4 张、额度不足和参数非法。
2. 同 idempotencyKey 重放只生成一个任务；参数冲突明确报错。
3. queued/generating 取消；Worker 取到已取消任务不调用模型。
4. mock 成功、一次重试、持续可重试错误、输入审核拒绝、输出审核拒绝。
5. SSE 初始回放、断线重连、重复 event、终态关闭、任务隔离。
6. 结果主图/缩略图、本地或 SFTP 存储、下载和对象清理。

### 额度与对账

验证 reserve -> consume/release 的金额恒等式、并发提交、重复 settlement、missing ledger 自动修复和 BLOCKED finding。

## 7.3 前台视觉验收

从 `bak/apps/web/app/globals.css`、`bak/apps/admin/app/globals.css` 和现有素材生成基线截图。每个页面至少覆盖：

| 页面 | 视口 |
| --- | --- |
| 灵感列表/详情 | 1440x900、1024x768、390x844 |
| 登录 | 1440x900、390x844 |
| 生成空会话/进行中/成功/错误 | 1440x900、1024x768、390x844 |
| 管理登录/任务/灵感编辑抽屉 | 1440x900、800x1024、390x844 |

截图比较应忽略时间、随机首图、动画和网络加载；颜色、尺寸、位置、可见性必须在阈值内。测试需检查重复 id、缺少 DOM target、主题变量、中文/英文溢出和 reduced-motion。

## 7.4 迁移阶段与交付物

| 阶段 | 交付物 | 退出条件 |
| --- | --- | --- |
| M0 契约冻结 | OpenAPI/JSON fixture、表/enum 清单、视觉截图 | 旧端点和页面清单评审通过 |
| M1 工程骨架 | Vue 两应用、Spring 多模块、CI、Docker profile | 空壳可启动，health 通过 |
| M2 前台壳 | 路由、布局、token、素材、主题、响应式 | 静态页面截图通过 |
| M3 基础 API | health/auth/inspirations/uploads | API 契约和认证 E2E 通过 |
| M4 生成 API | sessions/options/quota/tasks/SSE/results | mock generation smoke 通过 |
| M5 Worker/AI | Redis consumer、mock、ChatModel、图像和对账 | 任务/额度/死信集成通过 |
| M6 管理端 | manage_web auth/tasks/inspirations/RBAC | 管理端 E2E 和权限通过 |
| M7 双栈切换 | bridge、灰度、监控、回滚 | 双栈同 fixture 结果一致 |
| M8 清理 | 移除 Node 运行服务，保留 `bak` 归档 | 发布窗口无错误且回滚期结束 |

## 7.5 可直接分配的开发任务

### 前端

- FE-01：初始化 `dream_web`、`manage_web` 两个独立 Vite 5 工程，各自维护严格 TypeScript 配置和锁文件。
- FE-02：迁移用户端全局 token、导航、主题、语言、登录布局和素材。
- FE-03：实现灵感列表/详情及搜索历史、复制、做同款。
- FE-04：实现登录、协议弹窗、auth intent 和 Cookie session。
- FE-05：实现生成会话、composer、参数、上传、时间线、SSE 和结果预览。
- FE-06：实现管理 shell、任务表/详情抽屉、灵感编辑抽屉和 RBAC 状态。
- FE-07：建立视觉回归和移动断点 E2E。

### 后端

- BE-01：Maven parent、common domain、错误模型、配置和 health。
- BE-02：PostgreSQL Mapper、迁移执行、事务测试和 JSON/enum 映射。
- BE-03：Redis/SFTP adapter、对象键策略和上传 ImagePipeline。
- BE-04：用户/管理员 auth、Cookie、协议确认和 RBAC。
- BE-05：inspiration、session、quota、generation REST 与 SSE。
- BE-06：Redis Stream worker、状态机、重试、dead-letter 和事件。
- BE-07：Spring AI ChatModel adapter、mock provider、图像输出和对账。
- BE-08：管理任务/灵感 API、OpenAPI fixture、Testcontainers 集成。

### 发布与质量

- OPS-01：Docker Compose、profile、secret 注入、readiness、指标和日志。
- OPS-02：BullMQ bridge、双写/双读窗口和回滚脚本。
- OPS-03：灰度发布、数据库备份、对象生命周期和回滚演练。

## 7.6 开发完成定义

每个任务合并前必须包含代码路径、测试、配置说明和回滚说明。任何视觉差异、费用差异、任务状态跳跃、SSE 丢事件、越权资源读取或明文凭据都视为阻断问题，不得以“功能可用”通过。
