package com.dreamspace.api.service;

import com.dreamspace.api.common.ApiException;
import com.dreamspace.api.persistence.auth.AuthMapper;
import com.dreamspace.api.persistence.auth.UserRecord;
import com.dreamspace.api.persistence.auth.UserSessionRecord;
import com.dreamspace.api.persistence.auth.VerificationCodeRecord;
import com.dreamspace.common.persistence.config.DreamSpaceProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import java.security.SecureRandom;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
  public static final String AGREEMENT_VERSION = "2026-01";
  private static final SecureRandom RANDOM = new SecureRandom();
  private final AuthMapper mapper; private final DreamSpaceProperties properties;
  private final CaptchaService captcha; private final EmailSender emailSender;
  public AuthService(AuthMapper mapper, DreamSpaceProperties properties) {
    this(mapper, properties, null, null);
  }
  public AuthService(AuthMapper mapper, DreamSpaceProperties properties, CaptchaService captcha) {
    this(mapper, properties, captcha, null);
  }
  @Autowired
  public AuthService(AuthMapper mapper, DreamSpaceProperties properties, CaptchaService captcha, EmailSender emailSender) {
    this.mapper = mapper; this.properties = properties; this.captcha = captcha; this.emailSender = emailSender;
  }
  public record CodeRequest(String phone) {}
  public record CodeResponse(String challengeId, Instant expiresAt, long retryAfterSeconds) {}
  public record EmailCodeRequest(String email) {}
  public record RegisterRequest(String email, String challengeId, String emailCode, String password,
      String version, boolean termsAccepted, boolean privacyAccepted, boolean aiTermsAccepted) {}
  public record LoginRequest(String phone, String challengeId, String code, String version, boolean termsAccepted, boolean privacyAccepted, boolean aiTermsAccepted) {}
  public record PasswordLoginRequest(String phone, String password, String captchaId, String captchaCode,
      String version, boolean termsAccepted, boolean privacyAccepted, boolean aiTermsAccepted) {}
  public record UserView(String id, String phoneMasked, String emailMasked, Instant createdAt) {
    public UserView(String id, String phoneMasked, Instant createdAt) { this(id, phoneMasked, null, createdAt); }
  }
  public record SessionResponse(boolean authenticated, UserView user) { static SessionResponse no() { return new SessionResponse(false, null); } }
  public CodeResponse sendCode(CodeRequest input) {
    normalizePhone(input == null ? null : input.phone());
    throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AUTH_CODE_PROVIDER_UNAVAILABLE",
        "验证码服务未配置");
  }

  @Transactional
  public CodeResponse sendRegistrationCode(EmailCodeRequest input, String clientKey) {
    String email = normalizeEmail(input == null ? null : input.email());
    if (emailSender == null) throw emailUnavailable();
    String emailHash = hash(email);
    String clientHash = hash(clientKey == null ? "" : clientKey);
    Instant since = Instant.now().minusSeconds(60);
    if (mapper.countRecentRegistrationCodesByEmail(emailHash, since)
        >= properties.auth().emailCodeIssueLimitPerMinute()
        || mapper.countRecentRegistrationCodesByClient(clientHash, since)
        >= properties.auth().emailCodeIssueLimitPerMinute() * 2L) {
      throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "AUTH_EMAIL_RATE_LIMITED", "请求过于频繁，请稍后再试");
    }
    String id = UUID.randomUUID().toString();
    String code = String.format("%06d", RANDOM.nextInt(1_000_000));
    Instant expiresAt = Instant.now().plusSeconds(properties.auth().emailCodeTtlSeconds());
    mapper.insertRegistrationEmailCode(id, emailHash, hash(id + ":" + code), clientHash, expiresAt);
    try {
      emailSender.sendRegistrationCode(email, code);
    } catch (RuntimeException error) {
      mapper.deleteRegistrationEmailCode(id);
      throw error instanceof ApiException api ? api : emailUnavailable();
    }
    return new CodeResponse(id, expiresAt, 60);
  }

  @Transactional
  public SessionResult register(RegisterRequest input) {
    String email = normalizeEmail(input == null ? null : input.email());
    if (input == null || !AGREEMENT_VERSION.equals(input.version()) || !input.termsAccepted()
        || !input.privacyAccepted() || !input.aiTermsAccepted()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "AUTH_AGREEMENT_REQUIRED", "请先同意全部协议");
    }
    validatePassword(input.password());
    if (input.challengeId() == null || input.emailCode() == null || !input.emailCode().matches("\\d{6}")) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_EMAIL_CODE_INVALID", "邮箱验证码错误或已过期");
    }
    var challenge = mapper.findRegistrationEmailCode(input.challengeId());
    if (challenge == null || !hash(email).equals(challenge.emailHash()) || challenge.consumedAt() != null
        || challenge.attempts() >= properties.auth().emailCodeMaxAttempts()
        || challenge.expiresAt().isBefore(Instant.now())) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_EMAIL_CODE_INVALID", "邮箱验证码错误或已过期");
    }
    if (!constantEquals(challenge.codeHash(), hash(challenge.id() + ":" + input.emailCode()))) {
      mapper.incrementRegistrationEmailCodeAttempts(challenge.id(), properties.auth().emailCodeMaxAttempts());
      throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_EMAIL_CODE_INVALID", "邮箱验证码错误或已过期");
    }
    if (mapper.consumeRegistrationEmailCode(challenge.id(), properties.auth().emailCodeMaxAttempts()) != 1) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_EMAIL_CODE_INVALID", "邮箱验证码错误或已过期");
    }
    if (mapper.findUserByEmail(email) != null) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "AUTH_REGISTRATION_INVALID", "注册信息无效");
    }
    String userId = UUID.randomUUID().toString();
    try {
      mapper.insertEmailUser(userId, email, PasswordHashing.encode(input.password()));
    } catch (org.springframework.dao.DataIntegrityViolationException error) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "AUTH_REGISTRATION_INVALID", "注册信息无效");
    }
    mapper.upsertAgreement(UUID.randomUUID().toString(), userId, AGREEMENT_VERSION, true, true, true);
    UserRecord user = mapper.findUserById(userId);
    return createSession(user);
  }

  @Transactional
  public SessionResult passwordLogin(PasswordLoginRequest input) {
    String phone = normalizePhone(input == null ? null : input.phone());
    if (input == null || !AGREEMENT_VERSION.equals(input.version()) || !input.termsAccepted()
        || !input.privacyAccepted() || !input.aiTermsAccepted()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "AUTH_AGREEMENT_REQUIRED", "请先同意全部协议");
    }
    validatePassword(input.password());
    if (captcha == null) {
      throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AUTH_CAPTCHA_UNAVAILABLE", "图形验证码服务未配置");
    }
    captcha.verifyAndConsume(input.captchaId(), input.captchaCode());

    UserRecord user = mapper.findUserByPhone(phone);
    if (user != null && "DISABLED".equals(mapper.findUserStatusById(user.id()))) {
      throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED", "账号当前不可用");
    }
    boolean passwordMatches = PasswordHashing.matches(input.password(), user == null ? null : user.passwordHash());
    if (user == null || user.passwordHash() == null || user.passwordHash().isBlank() || !passwordMatches) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_LOGIN_INVALID", "账号或密码错误");
    }
    mapper.upsertAgreement(UUID.randomUUID().toString(), user.id(), AGREEMENT_VERSION, true, true, true);
    return createSession(user);
  }
  @Transactional
  public SessionResult login(LoginRequest input) {
    String phone = normalizePhone(input == null ? null : input.phone());
    if (input == null || !AGREEMENT_VERSION.equals(input.version()) || !input.termsAccepted() || !input.privacyAccepted() || !input.aiTermsAccepted())
      throw new ApiException(HttpStatus.BAD_REQUEST, "AUTH_AGREEMENT_REQUIRED", "请先同意全部协议");
    if (input.challengeId() == null || input.code() == null || !input.code().matches("\\d{6}")) throw new ApiException(HttpStatus.BAD_REQUEST, "AUTH_CODE_INVALID", "验证码无效");
    VerificationCodeRecord challenge = mapper.findCodeById(input.challengeId());
    if (challenge == null || !phone.equals(challenge.phone()) || challenge.consumedAt() != null || challenge.attempts() >= 5)
      throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_CODE_INVALID", "验证码错误或已过期");
    if (challenge.expiresAt().isBefore(Instant.now())) throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_CODE_EXPIRED", "验证码已过期");
    if (!constantEquals(challenge.codeHash(), hash(challenge.id() + ":" + input.code()))) { mapper.incrementAttempts(challenge.id()); throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_CODE_INVALID", "验证码错误或已过期"); }
    if (mapper.consumeCode(challenge.id()) != 1) throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_CODE_INVALID", "验证码错误或已过期");
    String userId = mapper.findUserIdByPhone(phone); if (userId == null) userId = UUID.randomUUID().toString(); mapper.upsertUser(userId, phone); userId = mapper.findUserIdByPhone(phone);
    if ("DISABLED".equals(mapper.findUserStatusById(userId))) throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED", "账号当前不可用");
    mapper.upsertAgreement(UUID.randomUUID().toString(), userId, AGREEMENT_VERSION, true, true, true);
    UserRecord user = mapper.findUserById(userId);
    return createSession(user);
  }
  public SessionResponse session(String token) { if (token == null || token.isBlank()) return SessionResponse.no(); UserSessionRecord s = mapper.findActiveSession(hash(token)); if (s == null) return SessionResponse.no(); mapper.touchSession(s.tokenHash()); UserRecord u = mapper.findUserById(s.userId()); return u == null || "DISABLED".equals(mapper.findUserStatusById(s.userId())) ? SessionResponse.no() : new SessionResponse(true, view(u)); }
  public void logout(String token) { if (token != null && !token.isBlank()) mapper.deleteSession(hash(token)); }
  public record SessionResult(SessionResponse response, String token, Instant expiresAt) {}
  private SessionResult createSession(UserRecord user) {
    mapper.touchLogin(user.id());
    String token = randomToken();
    Instant expires = Instant.now().plus(Duration.ofDays(properties.auth().sessionDays()));
    mapper.insertSession(UUID.randomUUID().toString(), hash(token), user.id(), expires);
    return new SessionResult(new SessionResponse(true, view(user)), token, expires);
  }
  private static void validatePassword(String password) {
    if (password == null || password.length() < 8 || password.length() > 72) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "AUTH_PASSWORD_INVALID", "密码长度必须为 8 到 72 位");
    }
  }
  private UserView view(UserRecord u) { return new UserView(u.id(), mask(u.phone()), maskEmail(u.email()), u.createdAt()); }
  static String normalizePhone(String value) { String p = value == null ? "" : value.replaceAll("\\s+", ""); if (!p.matches("1[3-9]\\d{9}")) throw new ApiException(HttpStatus.BAD_REQUEST, "AUTH_PHONE_INVALID", "请输入正确的手机号"); return p; }
  static String normalizeEmail(String value) {
    String email = value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
    if (email.length() > 254 || !email.matches("[^@\\s]+@(qq\\.com|163\\.com|foxmail\\.com)")) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "AUTH_EMAIL_INVALID", "请输入支持的邮箱地址");
    }
    return email;
  }
  private static ApiException emailUnavailable() { return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AUTH_EMAIL_PROVIDER_UNAVAILABLE", "邮箱验证码服务暂不可用"); }
  static String hash(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException(e); } }
  private static boolean constantEquals(String a, String b) { return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8)); }
  private static String randomToken() { byte[] b = new byte[32]; RANDOM.nextBytes(b); return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(b); }
  private static String mask(String p) { return p == null || p.isBlank() ? null : p.substring(0, 3) + "****" + p.substring(p.length() - 4); }
  private static String maskEmail(String email) {
    if (email == null || email.isBlank()) return null;
    int at = email.indexOf('@'); String local = email.substring(0, at); String domain = email.substring(at + 1);
    return (local.length() <= 2 ? local.charAt(0) + "***" : local.substring(0, 2) + "***") + "@" + domain;
  }
}
