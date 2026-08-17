package com.dreamspace.api;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.dreamspace.api.persistence.auth.AuthMapper;
import com.dreamspace.common.persistence.config.DreamSpaceProperties;
import org.junit.jupiter.api.Test;

class AuthServiceContractTest {
  @Test
  void rejectsMalformedPhoneBeforePersistence() {
    var props = new DreamSpaceProperties(null, null, null, null, null, null, null);
    var service = new AuthService(mock(AuthMapper.class), props);
    assertThatThrownBy(() -> service.sendCode(new AuthService.CodeRequest("123")))
        .isInstanceOf(ApiException.class)
        .hasMessage("请输入正确的手机号");
  }

  @Test
  void rejectsMissingAgreementWithStableCode() {
    var props = new DreamSpaceProperties(null, null, null, null, null, null, null);
    var service = new AuthService(mock(AuthMapper.class), props);
    var input = new AuthService.LoginRequest("13800138000", "challenge", "123456", AuthService.AGREEMENT_VERSION, true, false, true);
    assertThatThrownBy(() -> service.login(input))
        .isInstanceOfSatisfying(ApiException.class, e -> org.assertj.core.api.Assertions.assertThat(e.code()).isEqualTo("AUTH_AGREEMENT_REQUIRED"));
  }
}
