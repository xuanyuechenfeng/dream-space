# 实施计划：移除 persistence 模块

## 执行清单

1. [x] 记录变更前基线：运行 `backend` 全量测试并保存任何既有失败。
2. [x] 扩展 `common/pom.xml`，接收 persistence 的运行时和测试依赖；从聚合 POM 删除 `persistence` module；从 API/Worker POM 删除 `dream-space-persistence`。
3. [x] 将共享源码移动到 `common.persistence.*`，同步修改 package/import；将 `PersistenceConfiguration` 重命名并收敛为共享基础设施配置。
4. [x] 将 12 个数据库迁移脚本原样移动到 common；迁移原 persistence 测试及 test support，删除无用兼容门面和 marker。
5. [x] 将 admin、auth、inspiration、upload 数据访问代码移动到 `api.persistence.*`，更新 API 主代码和测试 import，新增 API Mapper 配置。
6. [x] 将 `QuotaReconciliationMapper` 移动到 `worker.persistence.reconciliation`，更新 Worker 主代码和测试 import，新增 Worker Mapper 配置。
7. [x] 更新 API/Worker 启动类，显式导入共享配置与应用本地 Mapper 配置；清理 Worker 的重复 queue Bean 定义。
8. [x] 删除空的 `backend/persistence` 目录，并执行旧包名/旧模块引用静态扫描。
9. [x] 运行 common、api、worker 的定向测试；修复包移动或上下文注册问题。
10. [x] 运行 `backend` Maven reactor 全量测试，并检查 Git diff 只包含本任务文件。

## 验证命令

```powershell
Set-Location backend
./mvnw.cmd test
Set-Location ..
rg -n "com\.dreamspace\.persistence|dream-space-persistence|<module>persistence</module>" backend
Test-Path backend/persistence
```

预期：Maven 测试通过；`rg` 无结果；`Test-Path` 返回 `False`。Docker 未启用时，Testcontainers 测试应遵循既有 `DockerTestSupport` 门控行为。

## 检查门

- Maven 模块图仅为 `common -> api/worker`，不存在反向依赖。
- common 中不出现 `com.dreamspace.api` 或 `com.dreamspace.worker` import。
- API Mapper scan 不覆盖 Worker；Worker Mapper scan 不覆盖 API。
- SQL 文件内容与迁移前一致，可用 Git diff 的 rename/content 检查确认。
- API/Worker 的配置 key、环境变量和外部契约无变化。

## 回滚点

- 完成 Maven 依赖调整后先执行 compile，若依赖传递不满足则在目标模块显式补依赖。
- 完成每一类包迁移后运行对应模块测试，避免到最后才定位 Mapper/Bean 注册问题。
- 在删除 `backend/persistence` 前确认所有源文件、测试和资源均已有唯一目标位置。
- 任何无法在本任务范围内解决的行为回归，停止并回滚整个原子重构，不修改数据库或外部环境。
