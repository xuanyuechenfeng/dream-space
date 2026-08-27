# 邮箱验证用户注册设计

## 1. 边界与兼容性

普通用户仍使用 `AuthService`、`UserSession` 和 `dreamspace_session` Cookie。手机号短信 `/codes`、短信 `/login`、密码 `/password-login` 和管理员认证不改协议。注册只新增邮箱链路，不修改 `bak/`。

邮箱规范化为 `trim().toLowerCase(Locale.ROOT)`；使用 `^[^@\\s]+@(qq\\.com|163\\.com|foxmail\\.com)$`，并限制总长度不超过 254。邮箱只作为身份键，不向日志输出完整值。

## 2. 数据模型与迁移

现有 `User.phone` 改为可空；新增可空 `email TEXT`，并建立唯一索引（PostgreSQL 对 NULL 不冲突）。`passwordHash` 继续保存 PBKDF2 编码结果。`UserRecord` 增加 email 字段，同时保留旧构造器以兼容现有测试和调用方。

新增 `RegistrationEmailCode` 表：`id` 主键、`emailHash`、`codeHash`、`expiresAt`、`consumedAt`、`attempts`、`clientKeyHash`、`createdAt`。索引为 `(emailHash, createdAt)` 和 `(clientKeyHash, createdAt)`。验证码正文只存在邮件正文和短暂的内存变量，不写数据库、日志或 API 响应。

## 3. API 合约

### 3.1 发送注册验证码

`POST /dream_web/auth/register/codes`

请求：`{ "email": "name@qq.com" }`。成功响应 `200`：`{ "challengeId": "...", "expiresAt": "...", "retryAfterSeconds": 60 }`。非法域名返回 `AUTH_EMAIL_INVALID`；SMTP 未配置或发送失败返回 `503 AUTH_EMAIL_PROVIDER_UNAVAILABLE`；频率超限返回 `429 AUTH_EMAIL_RATE_LIMITED`。已注册邮箱仍执行统一校验和响应，不返回存在性信息。

### 3.2 注册

`POST /dream_web/auth/register`

请求包含 `email`、`challengeId`、`emailCode`、`password`、`version`、`termsAccepted`、`privacyAccepted`、`aiTermsAccepted`。成功响应复用 `AuthSession`，并设置现有 Cookie。验证码错误/过期/重复消费统一为 `AUTH_EMAIL_CODE_INVALID`；邮箱已存在、用户创建冲突和无效输入统一为 `AUTH_REGISTRATION_INVALID`，避免账号枚举。

## 4. 后端流程

```text
register/codes
  -> 规范化并校验邮箱域名
  -> 计算 emailHash/clientKeyHash，查询最近一分钟发放次数
  -> 生成 6 位数字验证码，写入 RegistrationEmailCode(codeHash)
  -> 通过 EmailSender 发送模板邮件
  -> 返回 challengeId/expiresAt/retryAfterSeconds

register
  -> 校验邮箱、密码长度和协议
  -> 查询挑战并常量时间比较 codeHash
  -> 条件消费挑战（失败递增 attempts）
  -> 事务内检查 email 唯一性并插入 User(email,passwordHash)
  -> 写入 AgreementAcceptance，复用 createSession 建立 Cookie 会话
```

邮件发送抽象为 `EmailSender`，生产实现使用 Spring Boot Mail 的 SMTP 配置；没有可用 sender 时抛出 `AUTH_EMAIL_PROVIDER_UNAVAILABLE`。发送失败删除刚创建的未消费挑战，避免用户拿到无效挑战；数据库唯一约束是最终并发保护。

## 5. 配置与安全

新增 `dream-space.auth` 配置：`email-code-ttl-seconds`、`email-code-max-attempts`、`email-code-issue-limit-per-minute`、`email-from`。SMTP 使用标准 `spring.mail.*` 环境变量注入，禁止把密码写入仓库。密码继续 PBKDF2 哈希；邮箱日志只允许哈希或掩码。

## 6. 前端设计

`LoginView.vue` 保持现有克制的深色品牌视觉和双语文案，表单增加登录/注册切换：注册态显示邮箱、密码、邮箱验证码输入和发送验证码按钮；登录态保留手机号、密码和图形验证码。发送按钮显示 60 秒倒计时并在邮箱变更时重置。提交失败保留服务端错误文案并刷新图形验证码（注册验证码不自动重发）。成功后沿用 `returnTo` 与 pending auth intent 恢复逻辑。优先在现有认证页内复用字段和协议弹窗，避免重复认证布局。

## 7. 测试与回滚

- `AuthServiceContractTest`：允许域名、拒绝域名、协议/密码错误、验证码错误/消费、重复邮箱和成功会话。
- `EmailVerificationServiceTest`：规范化、哈希不泄露、过期/次数/限流、SMTP 不可用。
- 迁移资源测试检查新列、唯一索引和验证码表。
- 前端执行项目类型检查与 `npm run build`。
- 回滚使用应用版本回滚和数据库备份；不删除邮箱或密码数据，不回退已执行迁移。
