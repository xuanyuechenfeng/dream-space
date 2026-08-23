# 用户密码与图形验证码登录设计

## 1. 设计边界

现有系统已经将手机号作为 `User.phone` 唯一身份标识，并以 `dreamspace_session` HttpOnly Cookie 保存会话。本次不新建 username、OAuth 或 JWT 体系，不改变管理员独立认证，不修改 `bak/`。

新增密码登录作为独立接口，原 `/dream_web/auth/login` 短信验证码接口保留。由于短信供应商仍未配置，短信接口继续返回 `AUTH_CODE_PROVIDER_UNAVAILABLE`，不使用演示验证码绕过真实供应商约束。

## 2. 数据模型

### 2.1 `User` 扩展

新增可空字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `passwordHash` | `TEXT` | PBKDF2-HMAC-SHA256 编码结果；旧用户为空，密码登录时返回通用认证失败 |

不把密码设置流程混入本次登录接口。密码由后续账户设置/运营初始化流程写入，登录接口只读取哈希。

### 2.2 `LoginCaptcha`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `TEXT` PK | 随机挑战 ID |
| `clientKeyHash` | `TEXT` | IP + User-Agent 的 SHA-256，用于发放限流，不保存原始地址 |
| `codeHash` | `TEXT` | `id:答案` 的 SHA-256 |
| `expiresAt` | `TIMESTAMP(3)` | 默认 5 分钟有效 |
| `consumedAt` | `TIMESTAMP(3)` nullable | 成功校验或达到失败上限后消费 |
| `attempts` | `INTEGER` | 错误次数，数据库条件更新限制并发绕过 |
| `createdAt` | `TIMESTAMP(3)` | 创建时间 |

索引为 `(clientKeyHash, createdAt)`，用于最近一分钟最多发放 10 个挑战。验证码图片不落库，避免保存可重放的图像资产；响应中的 SVG 包含供用户识别的挑战字符和干扰线，但不会在结构化字段中单独返回明文答案。

## 3. 密码策略

- 输入长度 8-72 个字符；前端只做交互提示，后端强制校验。
- 使用 JDK `PBKDF2WithHmacSHA256`，默认 210,000 次迭代、16 字节随机盐、256 位结果。
- 存储格式：`pbkdf2-sha256$<iterations>$<base64url-salt>$<base64url-hash>`。
- 使用 `MessageDigest.isEqual` 比较结果；账号不存在、密码为空或密码错误均执行等价的 dummy hash 校验，并返回 `AUTH_LOGIN_INVALID`。
- 任何日志、异常和响应均不得包含密码、完整手机号、验证码答案或 SVG 中的原始答案。

## 4. API 契约

### 4.1 获取图形验证码

`GET /dream_web/auth/captcha`

响应 `200`：

```json
{
  "captchaId": "uuid",
  "imageData": "data:image/svg+xml;base64,...",
  "expiresAt": "2026-08-18T12:00:00Z",
  "retryAfterSeconds": 60
}
```

服务端通过请求 IP 和 User-Agent 生成不可逆 client key。超过一分钟 10 次返回 `429 AUTH_CAPTCHA_RATE_LIMITED`。响应不得返回明文答案。

### 4.2 密码登录

`POST /dream_web/auth/password-login`

请求：

```json
{
  "phone": "13800138000",
  "password": "user-password",
  "captchaId": "uuid",
  "captchaCode": "a7k2p",
  "version": "2026-01",
  "termsAccepted": true,
  "privacyAccepted": true,
  "aiTermsAccepted": true
}
```

成功响应与现有 `/login` 相同：`authenticated=true` 和脱敏用户信息，同时设置 `dreamspace_session` HttpOnly、SameSite=Lax Cookie。

错误码：

| 错误码 | HTTP | 触发条件 |
| --- | ---: | --- |
| `AUTH_PHONE_INVALID` | 400 | 手机号格式错误 |
| `AUTH_PASSWORD_INVALID` | 400 | 密码为空、长度不符合策略 |
| `AUTH_AGREEMENT_REQUIRED` | 400 | 协议未同意 |
| `AUTH_CAPTCHA_INVALID` | 401 | 验证码错误、过期、已消费或超过次数 |
| `AUTH_CAPTCHA_RATE_LIMITED` | 429 | 客户端发放频率超限 |
| `AUTH_LOGIN_INVALID` | 401 | 用户不存在、未设置密码或密码错误 |

## 5. 后端流程

```text
获取验证码
  -> 计算 clientKeyHash
  -> 查询最近一分钟发放次数
  -> 生成随机字符、SVG、codeHash
  -> 插入 LoginCaptcha
  -> 返回 captchaId + imageData

密码登录
  -> 校验手机号、密码长度和协议
  -> 查询挑战并校验有效期/次数/手机号无关性
  -> 常量时间比较 codeHash
  -> 条件消费验证码（错误则递增 attempts）
  -> 查询 User 并执行 PBKDF2 校验
  -> 校验成功后复用现有协议记录和 UserSession 写入事务
  -> 设置现有 Cookie
```

验证码与用户账号不绑定，防止通过验证码接口泄露账号是否存在；登录错误统一响应。验证码在密码错误时也被消费，强制下一次尝试重新获取验证码。

## 6. 前端交互

`dream_web/src/features/auth/LoginView.vue` 改为：

- 手机号输入；
- 密码输入，使用 `autocomplete="current-password"`；
- 图形验证码图片、刷新按钮和验证码输入；
- 首次进入和验证码过期/提交失败时提供刷新状态；
- 协议勾选、加载状态、错误提示、回跳 `returnTo` 保持现有行为；
- 不再把短信验证码作为默认登录步骤，短信 API 客户端类型仍保留给后续供应商接入。

`dream_web/src/api/client.ts` 增加强类型 `CaptchaResponse`、`PasswordLoginPayload` 和对应请求方法。验证码图片使用服务端返回的 data URL，不拼接答案或前端生成验证码。

## 7. 测试与验收

- API 服务测试覆盖密码策略、协议、验证码错误/过期/消费、旧用户无密码、密码成功登录。
- Captcha 生成测试验证字符集、挑战字段和限流；人工确认图片中可识别字符不会通过独立字段返回。
- 数据迁移资源测试验证迁移序列和新表关键字段。
- 前端执行 `npm/pnpm` 类型检查和构建，人工验证登录页刷新、错误、成功回跳。
- 不添加 MockMvc、WireMock 或外部供应商替身；HTTP 真实验证使用运行中的 API、PostgreSQL 和 Redis。

## 8. 配置与回滚

新增 API 配置：

```yaml
dream-space:
  auth:
    captcha-ttl-seconds: ${AUTH_CAPTCHA_TTL_SECONDS:300}
    captcha-max-attempts: ${AUTH_CAPTCHA_MAX_ATTEMPTS:5}
    captcha-issue-limit-per-minute: ${AUTH_CAPTCHA_ISSUE_LIMIT_PER_MINUTE:10}
```

数据库迁移只增加列、表和索引；回滚通过发布前备份恢复，不删除已存在的用户密码数据。关闭密码入口需通过应用版本回滚或路由开关，不能删除迁移记录。
