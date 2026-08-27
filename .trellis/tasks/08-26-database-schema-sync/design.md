# 技术设计

使用 PostgreSQL JDBC 元数据及 `information_schema`/`pg_catalog` 查询实际模型；使用迁移资源中的 DDL 解析代码基线。优先调用仓库的 `scripts/migrate-database.ps1` 和 `DatabaseMigrationService`，使开发库按正式迁移顺序补齐结构。对于已记录但结构不完整的迁移，增加新的幂等修正迁移，避免修改历史文件。

验证范围包括：迁移文件名与 `schema_migrations`、业务表集合、每表列定义、枚举值、主外键/唯一/检查约束、索引和触发器。数据行数仅用于判断迁移风险，不作为模型一致性的替代证据。
