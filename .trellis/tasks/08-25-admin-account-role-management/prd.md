# 管理员账号与角色管理

## Goal

在已完成的数据库 RBAC 基础上，提供管理员账号、系统/自定义角色和权限矩阵的可审计管理能力，保证高风险变更不会留下旧会话或破坏最后一个平台管理员。

## Background

- `AdminUser` 当前以 `active`、旧 `role` 和独立 `AdminSession` 表表示账号；旧 `role` 必须继续保留作为兼容展示字段。
- RBAC 已有 `AdminRoleDefinition`、`AdminPermissionDefinition`、`AdminUserRole`、`AdminRolePermission` 和 `permissionRevision`，关系变更会撤销受影响会话。
- 现有管理端使用 Vue Router、Pinia、`adminApi` 和 `AdminShell`，服务端权限校验由 `@AdminPermission` 完成。
- 19 号管理运营设计定义了管理员、角色和权限 API、职责分离规则、`version` 乐观并发和高风险操作原因要求。

## Requirements

- 新增管理员列表、创建/邀请、显示名编辑、启停、角色替换和会话撤销 API 与管理端页面。
- 新增系统/自定义角色列表、创建/编辑、启停和权限矩阵 API 与管理端页面。
- 新增权限目录只读 API；只允许绑定数据库中已注册且处于 ACTIVE 的权限码。
- 禁止自我禁用、撤销自己的全部管理角色、移除最后一个有效 `ADMIN`，以及禁用最后一个有效 `ADMIN`。
- 所有写操作要求非空原因、当前版本或 `If-Match`，在事务内写统一审计；并发失败返回 `409 RESOURCE_VERSION_CONFLICT`。
- 账号启停、角色分配、角色权限和角色状态变化必须递增受影响账号的 `permissionRevision` 并撤销其全部会话。
- 邀请使用现有管理员验证码登录链路，不新增明文密码或密钥存储；未接受邀请的账号状态不能登录。
- 服务端必须按精确权限码鉴权，前端权限只用于导航和控件隐藏；页面处理加载、空数据、校验失败、冲突、无权限、保存中、成功和服务不可用状态。

## Technical Notes

- 保留 `AdminUser.active` 与 `AdminUser.role`；新增 `status`、`version`、`lastLoginAt`、`createdBy`、`disabledAt`、`disabledBy`、`disabledReason` 时采用可重入迁移，并以 `active` 兼容旧登录查询。
- 本期权限目录新增 `admins:read`, `admins:write`, `roles:read`, `roles:write`；代码常量和迁移种子必须保持一致，未知权限拒绝绑定。
- 系统角色不可删除或修改权限边界；自定义角色可编辑，但不能使用系统角色 code，不能绑定未知权限。
- 角色权限替换和管理员角色替换使用单事务删除/插入，并依赖数据库唯一约束和触发器完成会话失效。
- 审计快照只保存脱敏手机号、角色/权限 code、状态和版本，不写验证码、Cookie 或其他秘密。

## Out Of Scope

- 不实现 SSO、邮件/短信供应商、细粒度 IP 白名单和完整组织/租户层级。
- 不删除旧 `AdminUser.role` 或重构已有用户账单、模型和报表模块。

## Acceptance Criteria

- [ ] 管理员、角色、权限目录 API、页面和精确权限校验完整。
- [ ] 管理员和角色创建、编辑、启停、角色/权限替换均写原因、版本和审计。
- [ ] 角色并发编辑返回 `409 RESOURCE_VERSION_CONFLICT`，旧版本不被覆盖。
- [ ] 自我禁用、最后一个 ADMIN 保护、空角色保护和未知权限绑定均被拒绝。
- [ ] 账号、角色或权限变化后旧会话立即失效，`permissionRevision` 正确递增。
- [ ] JDK 21 后端测试和管理端类型检查通过；Docker 集成测试按环境门控。
- [ ] 1440x900、800x1024、390x844 页面无溢出，键盘可完成列表筛选、编辑、确认和取消。
