# RBAC 数据与鉴权迁移实施计划

1. 数据层
   - [x] 增加 RBAC 迁移、约束、索引、默认权限和角色映射。
   - [x] 扩展迁移资源测试和 PostgreSQL 集成测试。
2. 持久化与服务
   - [x] 增加 RBAC records 和 AdminMapper 查询。
   - [x] `AdminAuthService` 改为数据库权限解析。
   - [x] `AdminPrincipal` 支持权限集合。
3. Web 鉴权
   - [x] `AdminPermission` 改为权限码契约。
   - [x] 拦截器按权限码拒绝未授权和漏标接口。
   - [x] 为所有管理 Controller 标注精确权限。
4. 契约与回归
   - [x] 更新 AdminAuth 和拦截器测试。
   - [x] 覆盖 VIEWER/OPERATOR/ADMIN 兼容矩阵。
   - [x] 运行 API/common 单元测试和 Maven 全量测试。
5. 回滚检查
   - [x] 确认旧 `AdminUser.role` 和 YAML 配置未被删除或修改。

## Verification

- JDK 21 `mvn test`: common 18 tests (2 Docker-gated tests skipped), API 33 tests, worker 46 tests; no failures.
- `manage_web` `npm run typecheck`: passed.
- `git diff --check` for RBAC scope: passed; only line-ending warnings.
- PostgreSQL migration integration coverage was added, but Testcontainers execution was skipped because Docker is unavailable in the current environment.
- Independent check fixed session invalidation for both sides of relationship updates, definition/account status changes, added startup permission-catalog validation, and added a protected-controller permission contract test.
