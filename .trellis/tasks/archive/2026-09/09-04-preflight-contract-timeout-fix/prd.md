# 修复预规划合同校验与超时可观测性

## Goal

TBD.

## Requirements

- TBD

## Acceptance Criteria

- [ ] TBD

## Notes

- Keep `prd.md` focused on requirements, constraints, and acceptance criteria.
- Lightweight tasks can remain PRD-only.
- For complex tasks, add `design.md` for technical design and `implement.md` for execution planning before `task.py start`.
# 预规划合同校验与超时可观测性修复

## 目标

修复多图片预规划因模型返回 1-based 槽位索引而失败的问题，并改善预规划终态事件未被前端观察时被误报为超时的诊断能力。

## 范围

- Worker 接受模型常见的 1-based 槽位索引，规范化为内部 0-based 索引，同时拒绝无法安全转换的索引。
- Worker 合同校验失败日志包含可定位的异常信息和响应形状。
- 前端预规划等待超时保留现有行为，但超时后查询一次状态，若后端已经进入终态则返回真实结果而不是泛化超时。
- 增加针对索引兼容、合同失败和前端终态查询的回归测试。

## 约束

- 不改变已冻结计费参数、队列确认/重试语义和正式生成流程。
- 不把不可重试的合同错误改成无限重试。
- 保留现有 120 秒用户等待边界；模型请求超时仍由 `AI_PLANNING_TIMEOUT` 控制。

## 验收标准

1. 四槽位模型响应使用索引 `1,2,3,4` 时可成功冻结为 `0,1,2,3`。
2. 已经是 `READY`、`FAILED` 或其他终态的预规划不会被前端显示为超时。
3. 合同校验失败日志至少包含异常类型/消息、响应长度和响应形状。
4. 相关 Worker 和 Web 测试通过，未修改无关文件。

## 实施结果

- 已完成 Worker 0-based 槽位索引规范化和合同失败根因日志增强。
- 已完成前端超时前的终态状态查询。
- Worker 模块 92 项测试、前端生成 Store 27 项测试及 TypeScript 类型检查通过。
- 前端全量 Vitest 未执行完成，原因是当前环境无法稳定创建 Vite 临时配置文件。
