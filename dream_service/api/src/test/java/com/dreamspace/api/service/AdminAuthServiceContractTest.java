package com.dreamspace.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dreamspace.api.persistence.admin.AdminMapper;
import com.dreamspace.api.persistence.admin.AdminSessionRecord;
import com.dreamspace.api.persistence.admin.AdminUserRecord;
import com.dreamspace.common.persistence.config.DreamSpaceProperties;
import com.dreamspace.common.persistence.database.DatabaseEnums.AdminRole;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AdminAuthServiceContractTest {
  @Test
  void sessionUsesIndependentAdminUserContract() {
    AdminMapper mapper = mock(AdminMapper.class);
    String token = "admin-cookie-token";
    when(mapper.findActiveSession(AuthService.hash(token))).thenReturn(
        new AdminSessionRecord("session", "hash", "admin-1", Instant.now().plusSeconds(60), null, null));
    when(mapper.findActiveById("admin-1")).thenReturn(
        new AdminUserRecord("admin-1", "18812340000", "审核员", AdminRole.VIEWER, true, null, null));
    AdminAuthService service = new AdminAuthService(mapper,
        new DreamSpaceProperties(null, null, null, null, null));

    AdminAuthService.SessionResponse response = service.session(token);

    assertThat(response.authenticated()).isTrue();
    assertThat(response.user().phoneMasked()).isEqualTo("188****0000");
    assertThat(response.user().role()).isEqualTo("viewer");
    assertThat(response.user().permissions()).containsExactly("tasks:read", "inspirations:read");
  }
}
