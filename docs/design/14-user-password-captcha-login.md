# 用户密码与图形验证码登录设计

本设计基于当前 `dream_service`、`dream_web` 和现有手机号 Cookie 会话实现，详细任务设计见 [.trellis/tasks/08-18-user-password-captcha-login/design.md](/D:/softDesign/dream-space/.trellis/tasks/08-18-user-password-captcha-login/design.md)。

## 目标

为用户端增加真实的手机号/账号、密码和图形验证码登录，不使用演示验证码或外部供应商 mock；沿用现有 `dreamspace_session` HttpOnly Cookie、协议同意和登录回跳机制。

## 关键决策

- 继续使用 `User.phone` 作为账号标识，不引入与手机号并行的 username 体系。
- 新增独立 `password-login` API，保留现有短信 `/login` 契约。
- 图形验证码由 API 生成 SVG data URL，答案只保存哈希，挑战一次性消费。
- 密码使用 PBKDF2-HMAC-SHA256，不保存明文；错误响应统一，防止账号枚举。
- 验证码发放通过数据库按客户端哈希限流，数据库迁移只增不改。

## 接口

```text
GET  /dream_web/auth/captcha
POST /dream_web/auth/password-login
GET  /dream_web/auth/session
POST /dream_web/auth/logout
```

密码登录成功返回现有 `AuthSession`，并设置 `dreamspace_session` Cookie。验证码获取返回 `captchaId`、`imageData`、`expiresAt` 和 `retryAfterSeconds`，不单独返回明文答案；答案只可通过图片人工识别。

## 数据和安全

`User.passwordHash` 可为空以兼容已有短信用户；没有密码的旧用户不能通过密码入口登录。`LoginCaptcha` 保存 client key 哈希、答案哈希、过期时间、尝试次数和消费时间。密码校验使用随机盐 PBKDF2 和常量时间比较，登录失败不区分用户不存在、密码未设置和密码错误。

## 页面

`dream_web` 登录页提供手机号、密码、图形验证码图片、刷新操作、协议勾选、错误和回跳状态。服务端返回的 SVG data URL 是唯一图像来源，前端不生成或保存验证码答案。

## 验收

以真实 PostgreSQL 和运行中的 API 验证迁移后记录、验证码挑战生命周期、错误次数限制、成功登录 Cookie 和 `/auth/session`；执行 Java 21 后端测试和前端构建。`bak/` 不修改。
