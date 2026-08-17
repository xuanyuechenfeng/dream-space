# 移除 persistence 模块并重新划分后端代码

## 背景

当前后端由 `common`、`persistence`、`api`、`worker` 四个 Maven 模块组成。`persistence` 同时承载共享基础设施、API 专属数据访问和 Worker 专属数据访问，导致模块边界与实际调用关系不一致。用户要求删除独立的 `persistence` 模块，并按调用方重新归属代码。

## 目标

- 删除 `backend/persistence` Maven 模块。
- API 和 Worker 共同使用的持久化及基础设施代码迁移到 `backend/common`。
- 仅 API 使用的代码迁移到 `backend/api`。
- 仅 Worker 使用的代码迁移到 `backend/worker`。
- 保持现有 HTTP 接口、任务处理、数据库结构、Redis 队列、对象存储及配置项的外部行为不变。

## 范围

- Maven 聚合模块及依赖关系调整。
- Java 源码、包名、import、Spring 配置和 MyBatis Mapper 扫描范围调整。
- 数据库迁移 SQL 和 `persistence` 模块测试迁移。
- 删除迁移后无实际用途的兼容门面和标记接口。

## 不在范围内

- 不修改前端代码、页面、接口协议或数据库表结构。
- 不新增业务功能，不重写 SQL，不改变队列消息格式或对象存储 key 规则。
- 不升级 Spring Boot、Spring AI、MyBatis、PostgreSQL、Redis、AWS SDK 等版本。
- 不顺带重构 API/Worker 业务服务或修复与模块拆分无关的问题。

## 约束

- 共享代码的新包名前缀统一为 `com.dreamspace.common.persistence`。
- API 专属持久化代码的新包名前缀统一为 `com.dreamspace.api.persistence`。
- Worker 专属持久化代码的新包名前缀统一为 `com.dreamspace.worker.persistence`。
- `backend/common` 将从轻量通用类库扩展为共享后端基础设施类库；这是删除 `persistence` 模块后的明确架构取舍。
- 所有现有 `com.dreamspace.persistence` 引用必须消除。
- 迁移过程必须保留数据库迁移 SQL 的文件名、内容和 classpath 位置 `db/migration/*.sql`。

## 验收标准

- [x] `backend/pom.xml` 不再声明 `persistence` 模块，`backend/persistence` 目录不存在。
- [x] API 和 Worker 不再依赖 `dream-space-persistence`，仅通过 `dream-space-common` 共享基础设施代码。
- [x] 仓库中不存在 `com.dreamspace.persistence` 包声明或引用，也不存在 `dream-space-persistence` 依赖引用。
- [x] API 专属 auth、inspiration、upload、admin Mapper/record 位于 API 模块。
- [x] Worker 专属 `QuotaReconciliationMapper` 位于 Worker 模块。
- [x] API/Worker 共同使用的配置、数据库类型、generation、quota、queue、storage、type handler 和共享 record 位于 common 模块。
- [x] API 与 Worker 各自只扫描本模块 Mapper，同时都能加载共享 Mapper 和共享基础设施 Bean。
- [x] 12 个数据库迁移脚本仍按原顺序可发现，数据库迁移集成测试仍可按 Docker 开关执行。
- [x] `mvn test` 在 `backend` 聚合工程通过；若本机未启用 Docker，Docker 门控测试按既有机制跳过而不是失败。
- [x] API 与 Worker 的 Spring 上下文测试通过，现有前后端协议和配置文件无需修改。
