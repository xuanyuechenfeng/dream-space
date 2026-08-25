# RBAC 数据与鉴权迁移技术设计

完整系统设计见 `docs/design/19-admin-operations-capabilities.md` 第 19.4 节。

## 数据迁移

- 新增 `AdminRoleDefinition`、`AdminPermissionDefinition`、`AdminUserRole`、`AdminRolePermission`，避免与现有 Java/PostgreSQL `AdminRole` 枚举名称冲突。
- 权限目录由迁移初始化，应用启动时只校验代码已知权限均存在，不由运行时隐式创建。
- 初始化 `VIEWER/OPERATOR/ADMIN` 三个系统角色，并按当前 `AdminAuthService` 权限列表建立映射。
- 从现有 `AdminUser.role` 回填 `AdminUserRole`。兼容字段继续维护，但授权读取关系表。
- `AdminUser` 新增 `permissionRevision BIGINT NOT NULL DEFAULT 1`；授权关系变化时递增并撤销账号现有 Session。

## 代码契约

- `@AdminPermission` 新增必填 `value` 权限码；不保留默认 VIEWER 语义，避免漏标接口被隐式放行。
- `AdminPermissionInterceptor` 对 `/manage_web/auth/**` 之外的管理接口要求 Handler 显式声明权限；缺失注解视为服务端配置错误并拒绝。
- `AdminAuthService.session` 从数据库读取管理员和有效权限，`AdminView.role` 暂保留旧主角色展示。
- `AdminPrincipal` 增加权限集合及 `allows(String)`；业务层需要时可做二次防御检查。
- 权限映射：任务/审核为 `tasks:read/write`，灵感为 `inspirations:read/write`，用户为 `users:read/write`，订单与退款为 `billing:read/write`，规则/产品为 `pricing:read/write`，审计为 `audit:read`。

## 缓存与兼容

本期先采用数据库读取，避免在未建立角色管理写接口前引入缓存失效复杂度。后续角色管理任务可用 `permissionRevision` 增加缓存。旧角色列作为回填和展示来源，不再作为 Controller 最终授权依据。

## 失败语义

- 未登录：`401 UNAUTHORIZED`。
- 缺少权限：`403 ADMIN_PERMISSION_REQUIRED`，响应不泄露用户数据。
- Controller 未声明权限：`403 ADMIN_PERMISSION_CONFIGURATION_ERROR` 并记录错误日志。
- 数据库不存在已注册权限或管理员无角色：拒绝授权，不回退到角色等级。

## 回滚

保留旧角色字段和枚举。若上线后需回滚，可恢复旧拦截器读取角色；新增 RBAC 表和数据不删除。迁移只增加对象，不修改既有 YAML 或业务数据。
