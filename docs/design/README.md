# Dream Space 重构详细设计

本目录把 [Vue + Spring AI 重构概要方案](../knowledge/vue-spring-ai-refactor-design.md) 拆成可直接分配给开发人员的设计分册。`bak/` 是现有 Node/Next 实现的只读基线，前台页面、交互和样式以它为验收标准。

## 文档顺序

1. [总体架构与工程边界](./01-architecture.md)
2. [Vue 前台详细设计](./02-frontend.md)
3. [Spring MVC API 详细设计](./03-backend-api.md)
4. [Worker 与 Spring AI 详细设计](./04-worker-ai.md)
5. [数据库、Redis 与对象存储](./05-data-and-infrastructure.md)
6. [接口契约与安全设计](./06-contracts-and-security.md)
7. [测试、迁移与开发任务](./07-testing-migration.md)
8. [数据库字段级设计](./08-database-schema.md)
9. [配置文件与一级目录说明](./09-configuration-and-layout.md)
10. [未实现功能与开发验收清单](./10-unimplemented-and-acceptance.md)
11. [图片生成 Harness 与 Loop Engineering 详细设计](./11-image-generation-harness-loop.md)
12. [图片生成真实规划与质量评估设计](./12-real-image-planning-evaluation.md)
13. [前台用户端与 Worker 未实现功能真实实现方案](./13-frontend-worker-real-implementation.md)
14. [用户密码与图形验证码登录设计](./14-user-password-captcha-login.md)
15. [图片生成参数与图片角色自动识别详细设计](./15-image-generation-parameters-and-image-role-inference.md)

## 实施规则

- 先冻结契约和视觉基线，再并行开发前端、API、Worker；不允许在迁移过程中顺手改变产品交互。
- 数据库表名、列名、枚举值、幂等键和额度流水语义不可改名；Java 侧只替换访问实现。
- `bak/` 只读。所有新代码放在 `dream_web/`、`manage_web/` 或 `dream_service/`，所有迁移说明放在 `docs/`。
- 每个功能必须同时具备：接口契约测试、业务单元测试、关键链路 E2E；前台页面还必须有桌面和移动截图基线。
- 真实模型、短信和对象存储凭据只通过环境变量或密钥管理系统注入，不能写入源码、测试快照或文档示例。
- 设计文档以 `08` 的实际字段清单、`09` 的配置约束和 `10` 的未实现清单为开发入口；实现完成后必须同步更新对应分册。

## 完成定义

重构只有在以下条件全部满足后才允许切流：用户端和管理端路由与 `bak` 一一对应；浅色/深色/移动断点截图通过；认证、上传、任务状态、SSE、额度和结果资源契约通过；mock 与 OpenAI-compatible ChatModel 均有可重复测试；旧 Node 栈至少保留一个回滚发布周期。
