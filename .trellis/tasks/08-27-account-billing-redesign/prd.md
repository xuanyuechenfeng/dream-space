# Redesign account billing experience

## Goal

为已登录用户提供清晰、可信且高效的额度与账单管理页面，帮助用户快速了解余额、购买额度、核对流水和订单状态。

## Requirements

- 保留现有账户、流水、产品、订单接口与创建订单行为。
- 首屏展示可用额度、总额度、已使用/预留额度和账号状态，并提供明确的余额进度可视化。
- 购买额度以可比较的产品卡片呈现，突出额度、价格、单位成本和有效期，下单过程中提供加载状态。
- 流水与支付订单通过标签页切换，表格支持空状态、状态徽标、金额/点数对齐和移动端横向滚动。
- 支持刷新数据、错误提示和响应式布局；视觉风格与现有 Dream Space 前端一致但具有更强的信息层级。

## Acceptance Criteria

- [ ] `AccountView.vue` 不再使用无样式的连续文本和原生按钮堆叠。
- [ ] 余额、套餐、流水、订单四类信息在桌面与移动端均可读且不重叠。
- [ ] 点击购买仍调用现有 `api.account.createOrder`，成功后刷新账户数据。
- [ ] 加载失败、空数据、购买中状态均有明确反馈。
- [ ] `npm run typecheck` 与 `npm run build` 通过。

## Notes

- Keep `prd.md` focused on requirements, constraints, and acceptance criteria.
- Lightweight tasks can remain PRD-only.
- For complex tasks, add `design.md` for technical design and `implement.md` for execution planning before `task.py start`.
