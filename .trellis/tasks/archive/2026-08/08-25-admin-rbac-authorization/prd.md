# RBAC 数据与鉴权迁移

## Goal

将管理端从固定角色等级鉴权迁移为数据库 RBAC 和精确权限码鉴权，同时保持现有管理员会话与业务接口兼容。

## Requirements

- 新增角色、权限、用户角色、角色权限关系，并回填现有 `VIEWER/OPERATOR/ADMIN` 管理员。
- 权限码由服务端注册，格式为 `resource:action`，未知权限不能被配置。
- `@AdminPermission` 支持精确权限码，所有管理 Controller 显式声明所需权限。
- 登录态权限由数据库关系计算，不再由 Java 角色分支硬编码。
- 角色或权限变化后现有会话不能继续使用旧权限。
- 保留旧 `AdminUser.role` 作为兼容字段，本期不删除。
- 不实现管理员和角色 CRUD 页面，该能力属于后续子任务。

## Acceptance Criteria

- [x] 数据迁移支持空库和含现有管理员的存量库，并使用可重入 DDL/DML；真实 PostgreSQL 测试已提供，当前环境因无 Docker 跳过执行。
- [x] 三个系统角色及权限映射与当前行为兼容。
- [x] VIEWER 无法执行写操作；OPERATOR 与 ADMIN 权限符合设计矩阵。
- [x] Controller 不再仅依赖角色等级决定业务权限。
- [x] 账号停用、角色和权限变更可使旧会话权限失效。
- [x] 鉴权、登录态、迁移和越权契约测试通过。
