# 核对并修正开发环境数据库表模型

## Goal

核对开发环境 PostgreSQL 实际表模型与当前代码仓库迁移基线的一致性，并将缺失或过期的开发环境结构修正到当前迁移版本。

## Requirements

- 以 `dream_service/common/src/main/resources/db/migration` 中按文件名排序的全部迁移作为代码要求的结构基线。
- 只读采集开发库的表、列、类型、默认值、可空性、约束、索引、枚举、触发器及 `schema_migrations` 状态。
- 对发现的差异执行可重复的正式迁移或等价数据库 DDL，不修改业务数据，保留现有迁移文件不可变。
- 修正后重新采集并验证结构与迁移版本均一致；连接失败或权限不足时明确报告，不伪造完成结果。

## Acceptance Criteria

- [x] 已识别开发库连接和当前迁移最高版本。
- [x] 已完成代码基线与实际模型差异清单。
- [x] 已应用必要的开发库模型修正。
- [x] 修正后表、列、约束、索引、枚举和迁移记录通过复核。

## Notes

- Keep `prd.md` focused on requirements, constraints, and acceptance criteria.
- Lightweight tasks can remain PRD-only.
- For complex tasks, add `design.md` for technical design and `implement.md` for execution planning before `task.py start`.
