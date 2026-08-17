package com.dreamspace.api;

import com.dreamspace.api.persistence.admin.*;
import com.dreamspace.common.persistence.config.DreamSpaceProperties;
import java.time.*;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminAuthService {
  private final AdminMapper mapper; private final DreamSpaceProperties props;
  public AdminAuthService(AdminMapper mapper, DreamSpaceProperties props) { this.mapper = mapper; this.props = props; }
  public record CodeRequest(String phone) {}
  public record CodeResponse(String challengeId, Instant expiresAt, long retryAfterSeconds, String demoCode) {}
  public record LoginRequest(String phone, String challengeId, String code) {}
  public record AdminView(String id, String displayName, String phoneMasked, String role, List<String> permissions) {}
  public record SessionResponse(boolean authenticated, AdminView user) { static SessionResponse no() { return new SessionResponse(false, null); } }
  public CodeResponse sendCode(CodeRequest request) {
    String phone = AuthService.normalizePhone(request == null ? null : request.phone());
    if (mapper.findActiveByPhone(phone) == null) throw new ApiException(HttpStatus.UNAUTHORIZED, "ADMIN_UNAUTHORIZED", "管理员账号不可用");
    AdminVerificationCodeRecord active = mapper.findActiveCode(phone); if (active != null) return new CodeResponse(active.id(), active.expiresAt(), 60, demoCode());
    String id = UUID.randomUUID().toString(); Instant exp = Instant.now().plusSeconds(props.auth().codeTtlSeconds()); mapper.insertCode(id, phone, AuthService.hash(id + ":123456"), exp); return new CodeResponse(id, exp, 60, demoCode());
  }
  @Transactional
  public Result login(LoginRequest request) {
    String phone = AuthService.normalizePhone(request == null ? null : request.phone()); AdminVerificationCodeRecord code = request == null ? null : mapper.findCodeById(request.challengeId());
    if (code == null || !phone.equals(code.phone()) || code.consumedAt() != null || code.expiresAt().isBefore(Instant.now()) || code.attempts() >= 5 || request.code() == null || !request.code().matches("\\d{6}")) throw new ApiException(HttpStatus.UNAUTHORIZED, "ADMIN_CODE_INVALID", "验证码错误或已过期");
    if (!java.security.MessageDigest.isEqual(code.codeHash().getBytes(), AuthService.hash(code.id() + ":" + request.code()).getBytes())) { mapper.incrementAttempts(code.id()); throw new ApiException(HttpStatus.UNAUTHORIZED, "ADMIN_CODE_INVALID", "验证码错误或已过期"); }
    if (mapper.consumeCode(code.id()) != 1) throw new ApiException(HttpStatus.UNAUTHORIZED, "ADMIN_CODE_INVALID", "验证码错误或已过期");
    AdminUserRecord admin = mapper.findActiveByPhone(phone); if (admin == null) throw new ApiException(HttpStatus.UNAUTHORIZED, "ADMIN_UNAUTHORIZED", "管理员账号不可用");
    String token = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(java.util.UUID.randomUUID().toString().getBytes()); Instant exp = Instant.now().plus(Duration.ofDays(props.auth().sessionDays())); mapper.insertSession(UUID.randomUUID().toString(), AuthService.hash(token), admin.id(), exp); return new Result(new SessionResponse(true, view(admin)), token, exp);
  }
  public SessionResponse session(String token) { if (token == null) return SessionResponse.no(); AdminSessionRecord s = mapper.findActiveSession(AuthService.hash(token)); if (s == null) return SessionResponse.no(); AdminUserRecord a = mapper.findActiveById(s.adminUserId()); return a == null ? SessionResponse.no() : new SessionResponse(true, view(a)); }
  public void logout(String token) { if (token != null) mapper.deleteSession(AuthService.hash(token)); }
  public record Result(SessionResponse response, String token, Instant expiresAt) {}
  private AdminView view(AdminUserRecord a) {
    List<String> permissions = a.role() == com.dreamspace.common.persistence.database.DatabaseEnums.AdminRole.VIEWER
        ? List.of("tasks:read", "inspirations:read")
        : List.of("tasks:read", "inspirations:read", "inspirations:write");
    return new AdminView(a.id(), a.displayName(), maskPhone(a.phone()), a.role().name().toLowerCase(), permissions);
  }
  private static String maskPhone(String phone) { return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4); }
  private String demoCode() { return "mock".equalsIgnoreCase(props.externalServicesMode()) ? "123456" : null; }
}
