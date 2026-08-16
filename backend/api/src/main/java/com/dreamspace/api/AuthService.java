package com.dreamspace.api;

import com.dreamspace.persistence.auth.AuthMapper;
import com.dreamspace.persistence.auth.UserRecord;
import com.dreamspace.persistence.auth.UserSessionRecord;
import com.dreamspace.persistence.auth.VerificationCodeRecord;
import com.dreamspace.persistence.config.DreamSpaceProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import java.security.SecureRandom;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
  public static final String AGREEMENT_VERSION = "2026-01";
  private static final SecureRandom RANDOM = new SecureRandom();
  private final AuthMapper mapper; private final DreamSpaceProperties properties;
  public AuthService(AuthMapper mapper, DreamSpaceProperties properties) { this.mapper = mapper; this.properties = properties; }
  public record CodeRequest(String phone) {}
  public record CodeResponse(String challengeId, Instant expiresAt, long retryAfterSeconds, String demoCode) {}
  public record LoginRequest(String phone, String challengeId, String code, String version, boolean termsAccepted, boolean privacyAccepted, boolean aiTermsAccepted) {}
  public record UserView(String id, String phoneMasked, Instant createdAt) {}
  public record SessionResponse(boolean authenticated, UserView user) { static SessionResponse no() { return new SessionResponse(false, null); } }
  public CodeResponse sendCode(CodeRequest input) {
    String phone = normalizePhone(input == null ? null : input.phone());
    VerificationCodeRecord reusable = mapper.findActiveCode(phone);
    if (reusable != null) return new CodeResponse(reusable.id(), reusable.expiresAt(), 60, "123456");
    String id = UUID.randomUUID().toString(); Instant expires = Instant.now().plusSeconds(properties.auth().codeTtlSeconds());
    mapper.insertCode(id, phone, hash(id + ":123456"), expires); return new CodeResponse(id, expires, 60, "123456");
  }
  @Transactional
  public SessionResult login(LoginRequest input) {
    String phone = normalizePhone(input == null ? null : input.phone());
    if (input == null || !AGREEMENT_VERSION.equals(input.version()) || !input.termsAccepted() || !input.privacyAccepted() || !input.aiTermsAccepted())
      throw new ApiException(HttpStatus.BAD_REQUEST, "AUTH_AGREEMENT_REQUIRED", "请先同意全部协议");
    if (input.challengeId() == null || input.code() == null || !input.code().matches("\\d{6}")) throw new ApiException(HttpStatus.BAD_REQUEST, "AUTH_CODE_INVALID", "验证码无效");
    VerificationCodeRecord challenge = mapper.findCodeById(input.challengeId());
    if (challenge == null || !phone.equals(challenge.phone()) || challenge.consumedAt() != null || challenge.expiresAt().isBefore(Instant.now()) || challenge.attempts() >= 5)
      throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_CODE_INVALID", "验证码错误或已过期");
    if (!constantEquals(challenge.codeHash(), hash(challenge.id() + ":" + input.code()))) { mapper.incrementAttempts(challenge.id()); throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_CODE_INVALID", "验证码错误或已过期"); }
    if (mapper.consumeCode(challenge.id()) != 1) throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_CODE_INVALID", "验证码错误或已过期");
    String userId = mapper.findUserIdByPhone(phone); if (userId == null) userId = UUID.randomUUID().toString(); mapper.upsertUser(userId, phone); userId = mapper.findUserIdByPhone(phone);
    mapper.upsertAgreement(UUID.randomUUID().toString(), userId, AGREEMENT_VERSION, true, true, true);
    UserRecord user = mapper.findUserById(userId); String token = randomToken(); Instant expires = Instant.now().plus(Duration.ofDays(properties.auth().sessionDays())); mapper.insertSession(UUID.randomUUID().toString(), hash(token), userId, expires);
    return new SessionResult(new SessionResponse(true, view(user)), token, expires);
  }
  public SessionResponse session(String token) { if (token == null || token.isBlank()) return SessionResponse.no(); UserSessionRecord s = mapper.findActiveSession(hash(token)); if (s == null) return SessionResponse.no(); mapper.touchSession(s.tokenHash()); UserRecord u = mapper.findUserById(s.userId()); return u == null ? SessionResponse.no() : new SessionResponse(true, view(u)); }
  public void logout(String token) { if (token != null && !token.isBlank()) mapper.deleteSession(hash(token)); }
  public record SessionResult(SessionResponse response, String token, Instant expiresAt) {}
  private UserView view(UserRecord u) { return new UserView(u.id(), mask(u.phone()), u.createdAt()); }
  static String normalizePhone(String value) { String p = value == null ? "" : value.replaceAll("\\s+", ""); if (!p.matches("1[3-9]\\d{9}")) throw new ApiException(HttpStatus.BAD_REQUEST, "AUTH_PHONE_INVALID", "请输入正确的手机号"); return p; }
  static String hash(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException(e); } }
  private static boolean constantEquals(String a, String b) { return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8)); }
  private static String randomToken() { byte[] b = new byte[32]; RANDOM.nextBytes(b); return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(b); }
  private static String mask(String p) { return p.substring(0, 3) + "****" + p.substring(p.length() - 4); }
}
