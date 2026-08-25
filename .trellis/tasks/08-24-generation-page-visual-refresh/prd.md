# 即梦风格生成页前台视觉优化

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
# 即梦风格生成页前台视觉优化

## 目标

参考即梦 AI 生成页的视觉语言，优化 Dream Space 的图片生成工作台，使首次进入生成页时更像一个沉浸式创作空间：窄导航、清晰的会话侧栏、中央留白创作区、底部悬浮式输入框，以及更克制的中性色和高亮强调色。

## 范围

- 仅修改 `dream_web` 前台生成页的模板展示标记与 CSS。
- 保留现有会话、任务、上传、参数、提交、取消、重试、下载、国际化和响应式逻辑。
- 不修改 API 请求路径、请求体、参数命名、store、后端接口或数据结构。

## 验收标准

- 桌面端具备左侧主导航 + 会话侧栏 + 中央创作区的三级层次，视觉上接近即梦参考页。
- 空状态标题与输入框在中央区域形成稳定的首屏焦点，输入框为宽幅、圆角、轻阴影的悬浮创作面板。
- 输入框、素材、参数面板、会话行和任务结果具备 hover/focus/disabled 状态，且不遮挡文本。
- 深色主题下仍保持可读性；移动端不横向溢出，底部导航与输入面板可用。
- `npm run typecheck`、`npm run build`、现有单元测试通过。
