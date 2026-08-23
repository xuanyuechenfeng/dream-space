# 实施计划

1. 更新 PRD、设计文档和 API/数据库边界，激活 Trellis 任务。
2. 新增数据库迁移：`User.passwordHash`、`LoginCaptcha` 表和索引；更新迁移资源测试。
3. 扩展 `DreamSpaceProperties.Auth` 的验证码配置，并在 API `application.yml` 增加注释配置项。
4. 实现 PBKDF2 密码工具、验证码生成/校验服务、SVG 渲染和请求限流。
5. 扩展 Auth Mapper/记录，新增验证码查询、失败次数更新、条件消费和密码用户查询。
6. 新增 `GET /dream_web/auth/captcha` 与 `POST /dream_web/auth/password-login`，复用现有协议、会话 Cookie 和统一异常处理。
7. 将 `dream_web` 登录页面改为账号、密码、图形验证码登录，保留协议、加载、错误和回跳体验。
8. 增加后端单元/契约测试，执行 Java 21 下 common、api、worker 测试；执行前端类型检查和构建。
9. 使用真实 PostgreSQL/API 验证迁移、验证码获取、密码登录和会话查询；检查日志不含敏感值。
