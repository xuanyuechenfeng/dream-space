package com.dreamspace.api.service;

import com.dreamspace.api.common.ApiException;
import com.dreamspace.api.persistence.auth.AuthMapper;
import com.dreamspace.api.persistence.auth.LoginCaptchaRecord;
import com.dreamspace.common.persistence.config.DreamSpaceProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class CaptchaService {
  private static final SecureRandom RANDOM = new SecureRandom();
  private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  private final AuthMapper mapper;
  private final DreamSpaceProperties properties;

  public CaptchaService(AuthMapper mapper, DreamSpaceProperties properties) {
    this.mapper = mapper;
    this.properties = properties;
  }

  public record CaptchaResponse(String captchaId, String imageData, Instant expiresAt,
      long retryAfterSeconds) {}

  public CaptchaResponse issue(String clientFingerprint) {
    String clientKeyHash = hash(clientFingerprint == null ? "unknown" : clientFingerprint);
    Instant now = Instant.now();
    long recent = mapper.countRecentLoginCaptchas(clientKeyHash, now.minus(Duration.ofMinutes(1)));
    if (recent >= properties.auth().captchaIssueLimitPerMinute()) {
      throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "AUTH_CAPTCHA_RATE_LIMITED",
          "验证码请求过于频繁，请稍后再试");
    }

    String id = UUID.randomUUID().toString();
    String code = randomCode();
    Instant expiresAt = now.plusSeconds(properties.auth().captchaTtlSeconds());
    mapper.insertLoginCaptcha(id, clientKeyHash, AuthService.hash(id + ":" + code), expiresAt);
    return new CaptchaResponse(id, svgData(code), expiresAt, 60);
  }

  public void verifyAndConsume(String id, String code) {
    if (id == null || id.isBlank() || code == null || !code.matches("[A-Za-z0-9]{5}")) {
      throw invalid();
    }
    LoginCaptchaRecord challenge = mapper.findLoginCaptcha(id);
    if (challenge == null || challenge.consumedAt() != null
        || challenge.expiresAt().isBefore(Instant.now())
        || challenge.attempts() >= properties.auth().captchaMaxAttempts()) {
      throw invalid();
    }
    String expected = challenge.codeHash();
    String received = AuthService.hash(id + ":" + code.toUpperCase(java.util.Locale.ROOT));
    if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
        received.getBytes(StandardCharsets.UTF_8))) {
      mapper.incrementLoginCaptchaAttempts(id, properties.auth().captchaMaxAttempts());
      throw invalid();
    }
    if (mapper.consumeLoginCaptcha(id, properties.auth().captchaMaxAttempts()) != 1) {
      throw invalid();
    }
  }

  private static String randomCode() {
    StringBuilder code = new StringBuilder(5);
    for (int i = 0; i < 5; i++) code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
    return code.toString();
  }

  private static String svgData(String code) {
    StringBuilder svg = new StringBuilder("<svg xmlns='http://www.w3.org/2000/svg' width='220' height='76' viewBox='0 0 220 76'>");
    svg.append("<rect width='220' height='76' rx='8' fill='#f2efe8'/>");
    for (int i = 0; i < 7; i++) {
      int x1 = RANDOM.nextInt(220), y1 = RANDOM.nextInt(76);
      int x2 = RANDOM.nextInt(220), y2 = RANDOM.nextInt(76);
      svg.append("<path d='M").append(x1).append(' ').append(y1).append(" Q 110 ")
          .append(RANDOM.nextInt(76)).append(' ').append(x2).append(' ').append(y2)
          .append("' stroke='#b8a98f' stroke-width='1.5' fill='none' opacity='.7'/>");
    }
    for (int i = 0; i < code.length(); i++) {
      int x = 22 + i * 39;
      int y = 50 + RANDOM.nextInt(8);
      int rotate = -12 + RANDOM.nextInt(25);
      svg.append("<text x='").append(x).append("' y='").append(y).append("' rotate='")
          .append(rotate).append("' font-family='Arial,sans-serif' font-size='34' font-weight='700' fill='#2f302e'>")
          .append(code.charAt(i)).append("</text>");
    }
    svg.append("</svg>");
    return "data:image/svg+xml;base64," + Base64.getEncoder()
        .encodeToString(svg.toString().getBytes(StandardCharsets.UTF_8));
  }

  private static String hash(String value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException error) {
      throw new IllegalStateException("captcha hashing is unavailable", error);
    }
  }

  private static ApiException invalid() {
    return new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_CAPTCHA_INVALID", "图形验证码错误或已过期");
  }
}
