# 02 Vue 前台详细设计

## 2.1 应用与路由

`frontend/web` 和 `frontend/admin` 是两个独立 Vite 应用，分别部署在 3000/3001 或统一域名的 `/`、`/admin` 路径。路由必须与 `bak` 保持一致：

| 应用 | 路径 | 视图 | 主要状态 |
| --- | --- | --- | --- |
| web | `/` | `HomeRedirectView` | 重定向 `/inspiration` |
| web | `/inspiration` | `InspirationGalleryView` | category、query、history、loading/error |
| web | `/inspiration/:slug` | `InspirationDetailView` | inspiration、neighbors、liked/following、composer |
| web | `/login` | `LoginView` | phone、challenge、code、agreements、legal |
| web | `/generate` | `GenerationWorkspaceView` | session、draft、tasks、filters、SSE |
| web | `/generate/:sessionId` | `GenerationWorkspaceView` | sessionId、draft、task timeline |
| admin | `/` | `AdminHomeRedirectView` | 重定向 `/tasks` |
| admin | `/login` | `AdminLoginView` | phone、challenge、countdown、error |
| admin | `/tasks` | `AdminTasksView` | filters、page、detail、reconciliation |
| admin | `/inspirations` | `AdminInspirationsView` | filters、editor、status、page |

## 2.2 代码结构

```text
frontend/web/src/
├── main.ts
├── App.vue
├── router/index.ts
├── layouts/InspirationShell.vue
├── features/
│   ├── auth/LoginView.vue
│   ├── inspiration/{Gallery,Detail}.vue
│   └── generation/{Workspace,Composer,TaskTimeline,SessionSidebar}.vue
├── stores/{auth,preferences,quota,generation}.ts
├── api/{client,auth,inspirations,generation,uploads}.ts
├── styles/{tokens,globals,inspiration,generation,login}.css
└── assets/inspiration/          # 从 bak/apps/web/public/inspiration 原样复制
```

管理端同样按 `layouts/features/stores/api/styles` 分层。组件只负责视图和交互，API 调用集中在 `api/`，跨页面状态集中在 Pinia store；禁止在模板中拼接 API URL 或直接访问 localStorage。

## 2.3 Store 设计

### `authStore`

状态：`loading`、`authenticated`、`user`、`error`。动作：`loadSession()`、`sendCode()`、`login()`、`logout()`。启动时先调用 session；401 只清理内存状态，不删除其他用户数据。

### `preferencesStore`

状态：`language: zh|en`、`theme: system|light|dark`。持久化键保持 `dream-space-language`、`dream-space-theme`，应用到 `<html data-theme>` 和 `.app[data-language]`。主题 token 必须来自旧 CSS，不在组件内写颜色。

### `quotaStore`

只在已登录且生成页需要时请求额度；计算 `remainingPercent`，低于 30% 使用 warning，低于等于 10% 使用 danger。提交前用服务端返回的 available 再校验一次，不能只信前端值。

### `generationStore`

状态：`sessions`、`activeSessionId`、`draft`、`tasks`、`filters`、`eventSource`、`loading`、`error`。创建、删除、重命名和任务提交成功后更新 store；SSE 事件按 `event.id` 去重，断线使用 last cursor 重连。

## 2.4 页面功能实现要求

### 灵感页

- 分类按钮值必须来自后端 category enum；搜索输入 220ms 防抖。
- 搜索历史使用 LocalStorage，最多 8 项；重复查询移到顶部，清空时同步删除键。
- 返回结果前执行与 `bak` 等价的随机重排，并避免首项连续重复。
- 处理 loading、请求失败、空结果三种独立视图；卡片点击进入 slug，做同款进入生成页并预填参数。

### 详情页

- 服务端加载 slug；404 使用 NotFoundView，其他错误使用 ErrorView。
- 复制提示词调用 Clipboard API，失败时显示错误而不静默。
- 点赞/关注保持当前本地状态表现；本次不新增持久化 API。
- 未登录触发 auth intent，登录后回到原生成动作；翻页只使用 API 返回 neighbors。

### 登录页

- 手机号只允许 11 位数字；验证码按钮保留倒计时和重发禁用。
- 登录按钮必须同时满足 challenge、6 位 code、三类协议勾选。
- 协议弹窗支持 Escape、遮罩关闭、滚动和键盘焦点；文案与 `bak` 一致。

### 生成工作台

- 参数限制：图片 1-8 张、参考图最多 4 张、JPG/PNG/WebP、2K/4K 和旧比例枚举。
- prompt 或参考图为空、额度不足、提交中时禁用提交按钮。
- 排队/生成显示取消；终态显示编辑/重跑；结果点击全屏预览，下载保留桌面悬停、移动端常显行为。
- 删除会话必须二次确认；参考图删除只删除草稿引用，上传对象删除由后端生命周期处理。

### 管理端

- `AdminShell` 在 session 未认证时 replace `/login`；401 触发重新验证。
- 任务页保留对账摘要、筛选、分页、表格和右侧详情抽屉。
- 灵感页保留创建/编辑/发布/取消发布和只读角色表现；写权限由后端再次校验。

## 2.5 严格视觉基线

用户端直接迁移 `bak/apps/web/app/globals.css` 的类名和变量，管理端直接迁移 `bak/apps/admin/app/globals.css`。Vue 模板应尽量复用旧 class，避免出现第二套相近 CSS。

### 用户端 token

```css
:root {
  --bg: #f7f8f9;
  --surface: #ffffff;
  --surface-strong: #f0f2f3;
  --text: #17191c;
  --muted: #6f747c;
  --border: #e5e8eb;
  --primary: #0e8f7c;
  --primary-soft: #e7f4f1;
  --danger: #d04444;
  --warning: #b26a16;
  --nav-width: 72px;
  --session-width: 280px;
  --radius: 8px;
}
```

深色 token：`#0f1012`、`#191b1e`、`#24272b`、`#f3f5f6`、`#a5abb1`、`#30343a`、`#183a35`。管理端 token：`#f4f6f7`、`#ffffff`、`#eef1f2`、`#dfe4e6`、`#1b1f23`、`#687178`、`#087f6d`、`#e4f4f0`、`#bb3e46`。

### 断点验收

| 断点 | 用户端 | 管理端 |
| --- | --- | --- |
| `1399/1599px` | 隐藏激励/日期工具 | - |
| `1199px` | 压缩生成筛选和 composer | 表格减少列 |
| `800px` | - | 顶部窄导航、抽屉全宽、表格减少列 |
| `767px` | 底部 64px 导航、两列瀑布流、详情上下布局 | - |
| `520px` | 登录表单收窄 | 表单改单列、抽屉全宽 |

## 2.6 构建与验收

Vite 必须配置严格 TypeScript、路径别名、`/api` proxy、资源 base 和 source map 策略。开发命令分别为 `pnpm --dir frontend/web dev`、`pnpm --dir frontend/admin dev`；生产构建输出 `dist/`，由 Nginx 或对象存储托管。

前台合并条件：旧页面与新页面在 1440x900、1024x768、390x844 三个视口截图通过；所有交互测试通过；axe 无新增严重无障碍问题；不存在重复 DOM id、缺失目标、未处理 Promise 或硬编码 API 地址。
