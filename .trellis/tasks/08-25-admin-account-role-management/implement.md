# 管理员账号与角色管理实施计划

1. 规划与迁移
   - [x] 完成管理员扩展字段、权限码种子、索引和会话失效迁移。
   - [x] 更新迁移资源与 PostgreSQL 契约测试，覆盖存量账号回填和重复执行。
   - [x] 对齐开发库物理模型：邀请状态、状态/active 一致性、版本约束、createdBy 和幂等键约束。
2. 后端持久化与服务
   - [x] 扩展 Admin records/Mapper，增加分页、锁定、版本更新和角色权限替换查询。
   - [x] 实现 AdminManagementService 的策略校验、事务、幂等和审计。
   - [x] 增加管理员、角色、权限目录 Controller 与精确权限注解。
3. 管理端
   - [x] 增加账号与角色 API 类型、路由、导航和页面状态。
   - [x] 实现账号状态操作、权限矩阵、原因确认和冲突提示。
4. 回归与质量
   - [ ] 覆盖管理员生命周期、角色权限矩阵、最后 ADMIN、未知权限、并发冲突和会话失效（PostgreSQL 集成仍需 Docker）。
   - [x] 运行 JDK 21 Maven API/common 测试、管理端 typecheck 和 diff check。
5. 收尾
   - [ ] 复核 YAML 测试配置和旧 AdminUser.role/active 兼容字段未被删除。
   - [x] 同步数据库设计与当前系统知识文档。
   - [ ] 评估是否需要把账号管理的锁定/审计约定更新到 `.trellis/spec/`。

## Validation Commands

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn test -f dream_service/pom.xml
node 'C:\Program Files\nodejs\node_modules\npm\bin\npm-cli.js' run typecheck --prefix manage_web
git diff --check
```
