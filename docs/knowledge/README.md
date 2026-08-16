# Dream Space 知识库

本目录记录 Dream Space 当前代码实现，而不是产品愿景或历史原型。

## 文档入口

- [当前系统设计方案](./current-system-design.md)：总体架构、技术栈、数据库、模块功能、前台页面设计、配置、目录和未实现能力。
- [Vue + Spring AI 重构设计方案](./vue-spring-ai-refactor-design.md)：Vue 3/Vite 5 前端、Spring Boot 4/Spring MVC/Spring AI 后端迁移方案，以及与 `bak` 前台功能和视觉风格的等价契约。
- [重构详细设计分册](../design/README.md)：可直接指导开发的架构、前台、API、Worker/AI、数据、安全、测试和迁移任务设计。

## 范围与口径

- 分析范围覆盖 `apps/`、`packages/`、`infrastructure/`、`scripts/`、`e2e/` 以及根目录工程配置。
- 以 `main` 同步时的实际源码为准；页面行为以 TSX 和 CSS 为准，接口行为以 NestJS controller/service、契约包和 Prisma schema 为准。
- `mock` 外部服务模式是当前默认可运行模式。文档中标注为“未实现”的能力，表示代码中没有可用的生产实现，不代表产品永远不会建设。
- 生成文档后如模块、路由、表结构或配置发生变化，应同步更新主文档。
