# 管理端运营能力技术设计

正式设计见 `docs/design/19-admin-operations-capabilities.md`。

## 设计边界

系统拆分为六个协作边界：Admin IAM、User Operations、Model Gateway、Billing、Runtime Configuration、Audit & Analytics。现有生成、额度和支付交易模型继续作为事实源，管理端只提供治理能力和查询投影。

## 核心决策

1. 将当前 `VIEWER/OPERATOR/ADMIN` 最低角色校验迁移为数据库 RBAC 和 `resource:action` 权限码，同时保留角色枚举兼容窗口。
2. 模型供应商、模型和路由策略进入数据库注册表；密钥只保存 Secret 引用，Worker 获取已发布路由快照。
3. 计费规则、路由策略和系统配置均使用 `DRAFT -> PUBLISHED -> RETIRED` 的不可变版本发布模型。
4. 额度赠送与扣减通过 `CreditAdjustment` 工作流编排，最终余额仍只由 `QuotaTransactionService` 和不可变流水改变。
5. 使用统一 `AuditEvent` 承载所有管理操作；现有领域审计表在迁移期通过查询适配器合并。
6. 日报表读取日聚合表；运营看板组合业务聚合与运行指标，不直接把 Prometheus 指标当作财务事实。

## 兼容策略

- 现有用户、订单、产品、规则和 Billing API 保持可用，在新权限注解、统一审计和页面完善后逐步切换。
- YAML 中的模型配置作为 bootstrap/fallback 输入保留，发布动态路由前 Worker 行为不变。
- 迁移采用扩展、回填、双读校验、切换、清理五步法；每阶段均可通过功能开关回退。

## 非目标

- 不建设法定会计总账、发票和税务系统。
- 不把数据库、Redis、对象存储地址纳入在线系统配置。
- 不在数据库明文保存支付或模型供应商密钥。
