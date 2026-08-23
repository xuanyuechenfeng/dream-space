package com.dreamspace.api.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dreamspace.api.common.ApiException;
import com.dreamspace.api.persistence.auth.AuthMapper;
import com.dreamspace.api.persistence.auth.UserRecord;
import com.dreamspace.common.persistence.config.DreamSpaceProperties;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AuthServiceContractTest {
  @Test
  void rejectsMalformedPhoneBeforePersistence() {
    var props = new DreamSpaceProperties(null, null, null, null, null);
    var service = new AuthService(mock(AuthMapper.class), props);
    assertThatThrownBy(() -> service.sendCode(new AuthService.CodeRequest("123")))
        .isInstanceOf(ApiException.class)
        .hasMessage("请输入正确的手机号");
  }

  @Test
  void requiresARealSmsProviderForValidPhone() {
    var service = new AuthService(mock(AuthMapper.class),
        new DreamSpaceProperties(null, null, null, null, null));

    assertThatThrownBy(() -> service.sendCode(new AuthService.CodeRequest("13800138000")))
        .isInstanceOfSatisfying(ApiException.class,
            e -> org.assertj.core.api.Assertions.assertThat(e.code())
                .isEqualTo("AUTH_CODE_PROVIDER_UNAVAILABLE"));
  }

  @Test
  void rejectsMissingAgreementWithStableCode() {
    var props = new DreamSpaceProperties(null, null, null, null, null);
    var service = new AuthService(mock(AuthMapper.class), props);
    var input = new AuthService.LoginRequest("13800138000", "challenge", "123456", AuthService.AGREEMENT_VERSION, true, false, true);
    assertThatThrownBy(() -> service.login(input))
        .isInstanceOfSatisfying(ApiException.class, e -> org.assertj.core.api.Assertions.assertThat(e.code()).isEqualTo("AUTH_AGREEMENT_REQUIRED"));
  }

  @Test
  void passwordLoginCreatesSessionAndPersistsAgreement() {
    AuthMapper mapper = mock(AuthMapper.class);
    CaptchaService captcha = mock(CaptchaService.class);
    var props = new DreamSpaceProperties(null, null, null, null, null);
    String encoded = PasswordHashing.encode("correct-password");
    when(mapper.findUserByPhone("13800138000")).thenReturn(new UserRecord(
        "user-1", "13800138000", encoded, Instant.now(), Instant.now()));
    AuthService service = new AuthService(mapper, props, captcha);
    var input = new AuthService.PasswordLoginRequest("13800138000", "correct-password",
        "captcha-1", "ABCDE", AuthService.AGREEMENT_VERSION, true, true, true);

    AuthService.SessionResult result = service.passwordLogin(input);

    org.assertj.core.api.Assertions.assertThat(result.response().authenticated()).isTrue();
    org.assertj.core.api.Assertions.assertThat(result.response().user().phoneMasked())
        .isEqualTo("138****8000");
    verify(captcha).verifyAndConsume("captcha-1", "ABCDE");
    verify(mapper).upsertAgreement(anyString(), org.mockito.ArgumentMatchers.eq("user-1"),
        org.mockito.ArgumentMatchers.eq(AuthService.AGREEMENT_VERSION),
        org.mockito.ArgumentMatchers.eq(true), org.mockito.ArgumentMatchers.eq(true),
        org.mockito.ArgumentMatchers.eq(true));
    verify(mapper).insertSession(anyString(), anyString(),
        org.mockito.ArgumentMatchers.eq("user-1"), org.mockito.ArgumentMatchers.any(Instant.class));
  }

  @Test
  void passwordLoginUsesGenericFailureForMissingUser() {
    AuthMapper mapper = mock(AuthMapper.class);
    CaptchaService captcha = mock(CaptchaService.class);
    var service = new AuthService(mapper,
        new DreamSpaceProperties(null, null, null, null, null), captcha);
    var input = new AuthService.PasswordLoginRequest("13800138000", "wrong-password",
        "captcha-1", "ABCDE", AuthService.AGREEMENT_VERSION, true, true, true);

    assertThatThrownBy(() -> service.passwordLogin(input))
        .isInstanceOfSatisfying(ApiException.class,
            error -> org.assertj.core.api.Assertions.assertThat(error.code())
                .isEqualTo("AUTH_LOGIN_INVALID"));
  }
}
