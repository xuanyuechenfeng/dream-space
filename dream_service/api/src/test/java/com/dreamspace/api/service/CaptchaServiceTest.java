package com.dreamspace.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dreamspace.api.common.ApiException;
import com.dreamspace.api.persistence.auth.AuthMapper;
import com.dreamspace.api.persistence.auth.LoginCaptchaRecord;
import com.dreamspace.common.persistence.config.DreamSpaceProperties;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class CaptchaServiceTest {
  private final DreamSpaceProperties properties = new DreamSpaceProperties(null, null, null, null, null);

  @Test
  void issuesSvgChallengeAndPersistsOnlyHash() {
    AuthMapper mapper = mock(AuthMapper.class);
    when(mapper.countRecentLoginCaptchas(anyString(), any(Instant.class))).thenReturn(0L);
    CaptchaService service = new CaptchaService(mapper, properties);

    CaptchaService.CaptchaResponse response = service.issue("127.0.0.1|test-agent");

    assertThat(response.captchaId()).isNotBlank();
    assertThat(response.imageData()).startsWith("data:image/svg+xml;base64,");
    assertThat(response.expiresAt()).isAfter(Instant.now());
    verify(mapper).insertLoginCaptcha(anyString(), anyString(), anyString(), any(Instant.class));
  }

  @Test
  void rejectsExpiredChallengeWithoutConsumingIt() {
    AuthMapper mapper = mock(AuthMapper.class);
    when(mapper.findLoginCaptcha("expired")).thenReturn(new LoginCaptchaRecord(
        "expired", "client", "hash", Instant.now().minusSeconds(1), null, 0, Instant.now()));
    CaptchaService service = new CaptchaService(mapper, properties);

    assertThatThrownBy(() -> service.verifyAndConsume("expired", "ABCDE"))
        .isInstanceOfSatisfying(ApiException.class,
            error -> assertThat(error.code()).isEqualTo("AUTH_CAPTCHA_INVALID"));
  }

  @Test
  void wrongAnswerIncrementsAttempts() {
    AuthMapper mapper = mock(AuthMapper.class);
    when(mapper.findLoginCaptcha("challenge")).thenReturn(new LoginCaptchaRecord(
        "challenge", "client", AuthService.hash("challenge:RIGHT1"), Instant.now().plusSeconds(60), null, 0, Instant.now()));
    CaptchaService service = new CaptchaService(mapper, properties);

    assertThatThrownBy(() -> service.verifyAndConsume("challenge", "WRONG"))
        .isInstanceOf(ApiException.class);
    verify(mapper).incrementLoginCaptchaAttempts("challenge", properties.auth().captchaMaxAttempts());
  }
}
