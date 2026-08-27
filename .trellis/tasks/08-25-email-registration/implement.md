# 实施计划

1. 评审并冻结 `prd.md`、`design.md` 中的 API、错误码、域名和 SMTP 约束。
2. 新增数据库迁移：`User.email`、可空手机号、唯一索引和 `RegistrationEmailCode` 表；补迁移测试。
3. 扩展 `DreamSpaceProperties.Auth` 及 `application.yml` 的邮箱验证码和发件人配置；在 API 模块加入 Spring Mail 依赖。
4. 新增邮箱规范化、验证码记录/Mapper、SMTP `EmailSender` 和注册服务；复用现有 PBKDF2、协议、会话事务。
5. 在 `AuthController` 和前端 API client/store 增加注册验证码与注册接口。
6. 更新 `LoginView.vue` 与样式，完成登录/注册切换、邮箱校验、倒计时、错误和回跳。
7. 添加后端单元测试、迁移测试，执行 API/common Maven 测试；执行前端类型检查和构建。
8. 运行真实配置下的 API/数据库冒烟验证；确认未配置 SMTP 时错误明确且不泄露敏感值。
