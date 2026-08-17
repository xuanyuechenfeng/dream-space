# 技术设计：移除 persistence 模块

## 1. 设计原则

本次变更是模块边界重排，不改变业务行为。归属判断以“谁直接使用”和“是否构成跨应用契约”为准：API 与 Worker 共同使用的代码进入 `common`，只被一个应用使用的代码跟随该应用。数据库、Redis 和对象存储实现虽然属于基础设施，但在删除独立模块后仍需由两个应用共享，因此放入 `common` 的 `persistence` 子包，而不是散落在通用 DTO 根包中。

## 2. 目标模块结构

```text
backend/
├── common/
│   ├── src/main/java/com/dreamspace/common/
│   │   ├── existing shared contracts
│   │   └── persistence/
│   │       ├── config/
│   │       ├── database/
│   │       ├── generation/
│   │       ├── quota/
│   │       ├── queue/
│   │       ├── reconciliation/
│   │       ├── storage/
│   │       └── typehandler/
│   └── src/main/resources/db/migration/
├── api/
│   └── src/main/java/com/dreamspace/api/persistence/
│       ├── admin/
│       ├── auth/
│       ├── inspiration/
│       └── upload/
└── worker/
    └── src/main/java/com/dreamspace/worker/persistence/reconciliation/
```

Maven 聚合顺序调整为 `common`、`api`、`worker`。API 和 Worker 均只声明对 `dream-space-common` 的内部模块依赖。

## 3. 精确迁移方案

### 3.1 迁移到 common

- `config/DreamSpaceProperties` -> `common.persistence.config.DreamSpaceProperties`
- `config/PersistenceConfiguration` -> `common.persistence.config.SharedPersistenceConfiguration`
- `config/PersistenceReadinessProbe` -> `common.persistence.config.PersistenceReadinessProbe`
- `database/*` -> `common.persistence.database.*`
- `generation/*` -> `common.persistence.generation.*`
- `quota/*` -> `common.persistence.quota.*`
- `queue/*` -> `common.persistence.queue.*`
- `storage/*` -> `common.persistence.storage.*`
- `typehandler/*` -> `common.persistence.typehandler.*`
- `reconciliation/QuotaReconciliationRunRecord` -> `common.persistence.reconciliation.QuotaReconciliationRunRecord`
- `src/main/resources/db/migration/*` -> common 的相同资源路径

不迁移 `PersistenceBoundary`。根包下的兼容门面 `DatabaseMigrationService` 删除，只保留 `common.persistence.database.DatabaseMigrationService`。

### 3.2 迁移到 api

- `admin/*` -> `api.persistence.admin.*`
- `auth/*` -> `api.persistence.auth.*`
- `inspiration/*` -> `api.persistence.inspiration.*`
- `upload/*` -> `api.persistence.upload.*`

这些类允许引用 common 的数据库枚举、generation record、reconciliation record 和 storage contract，但 common 禁止反向依赖 API。

### 3.3 迁移到 worker

- `reconciliation/QuotaReconciliationMapper` -> `worker.persistence.reconciliation.QuotaReconciliationMapper`

该 Mapper 引用的 task、quota 和 reconciliation record 继续来自 common。common 禁止反向依赖 Worker。

## 4. Spring 与 MyBatis 配置

`SharedPersistenceConfiguration` 只负责共享基础设施：

- 扫描 `com.dreamspace.common.persistence.generation` 与 `com.dreamspace.common.persistence.quota` Mapper。
- 注册 JSON 与数据库枚举 TypeHandler。
- 提供 `QuotaTransactionService`、`GenerationQueue`、`ObjectStorageFactory`、Local/S3 storage、S3 client/presigner 等 Bean。
- 启用 `DreamSpaceProperties`。
- 通过显式 Bean 或显式 import 保证 readiness probe 可加载，不依赖跨根包 component scan。

API 新增 `ApiPersistenceConfiguration`，只扫描 `com.dreamspace.api.persistence`。Worker 新增 `WorkerPersistenceConfiguration`，只扫描 `com.dreamspace.worker.persistence`。两个启动类显式导入共享配置和各自配置，并继续启用相同的属性类。

Redis `GenerationQueue` 由共享配置单点提供。Worker 原有的重复 Redis queue Bean 定义移除，Worker generation 配置只保留模型 Provider 等 Worker 专属 Bean。

## 5. Maven 依赖

原 `persistence/pom.xml` 的运行时依赖移动到 `common/pom.xml`：MyBatis starter、JDBC starter、PostgreSQL、Redis starter、AWS S3、AWS URL connection client 和 configuration processor。原 persistence 测试依赖也移动到 common 的 test scope。

API/Worker 删除 `dream-space-persistence` 依赖。由于 Maven compile 依赖默认传递，API/Worker 中的本地 Mapper 与配置仍能访问 MyBatis/Spring 基础设施；不重复声明同一依赖，除非实际构建证明需要显式化。

## 6. 数据与兼容性

- 所有迁移 SQL 原样移动，文件名和 classpath glob 不变。
- 不修改 SQL 注解、表名、字段名、枚举数据库值、事务边界或 JSON 映射。
- 不保留旧 `com.dreamspace.persistence.*` 兼容包。该代码仅在本仓库模块内使用，本次一次性更新所有调用方，避免形成永久兼容债务。
- HTTP URL、请求/响应结构、配置 key 和环境变量保持不变。

## 7. 测试设计

- common：迁移并更新原 persistence 的全部单元/集成测试；校验 12 个 SQL 资源、枚举映射、quota record、对象 key、Redis 队列及可选 PostgreSQL 迁移。
- api：运行服务单元测试、MockMvc 契约测试和应用上下文测试，验证 API-local Mapper 与 shared Mapper 均可注册。
- worker：运行 generation、ChatModel、reconciliation 测试，并补强/保留应用上下文覆盖以验证 Worker-local Mapper 与 shared Mapper。
- 静态检查：`rg` 确认旧包名、旧 artifactId、旧模块目录均已清零。
- 全量验证：从 `backend` 执行 Maven reactor `test`。

## 8. 风险与控制

| 风险 | 控制措施 |
|---|---|
| Mapper 因扫描范围变化未注册 | 拆出 API/Worker 本地配置，并用上下文测试验证 |
| common 不在启动类默认 component scan 范围 | 两个应用显式 `@Import` shared configuration |
| TypeHandler 注册遗漏 | 保留完整枚举注册清单并运行数据库/上下文测试 |
| 迁移 SQL 移动后无法发现 | 保持 `db/migration` classpath 路径，迁移资源测试移入 common |
| Redis queue 出现重复 Bean | 共享配置成为单一 Bean 所有者，删除 Worker 重复定义 |
| common 依赖膨胀 | 在文档中明确 common 是共享后端基础设施模块，不再定义为纯工具模块 |

## 9. 回滚方案

变更应作为一个原子重构提交完成。若全量构建或上下文启动失败，回滚该提交即可恢复原 `persistence` 模块。数据库 schema、迁移脚本内容和外部配置均不变化，因此无需数据回滚。
