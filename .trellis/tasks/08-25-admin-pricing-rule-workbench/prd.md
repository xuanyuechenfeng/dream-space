# 计费规则工作台

## Goal

将现有计费规则基础能力补齐为可编辑、可预览、可发布和可回滚的运营工作台。

## Requirements

- 查价完整匹配 operation、model、resolution、dimensions、time window。
- 同等特异度多规则命中必须拒绝计费。
- 实现草稿编辑、预览、影响分析、冲突检测、发布、下线和复制历史版本。
- ACTIVE/RETIRED 规则不可原地修改。
- 依赖 RBAC 子任务。

## Acceptance Criteria

- [ ] 新任务唯一命中正确规则并保存完整快照。
- [ ] 发布前可证明请求空间和时间窗口无歧义。
- [ ] 历史任务费用不受新规则影响。
- [ ] 管理端工作流和权限、并发、审计测试通过。
