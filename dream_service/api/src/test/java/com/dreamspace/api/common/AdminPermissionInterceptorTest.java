package com.dreamspace.api.common;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dreamspace.api.service.AdminAuthService;
import com.dreamspace.common.persistence.database.DatabaseEnums.AdminRole;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.web.method.HandlerMethod;

class AdminPermissionInterceptorTest {
  @Test
  void viewerCannotInvokeOperatorWrite() throws Exception {
    var auth = mock(AdminAuthService.class);
    when(auth.session("admin-token")).thenReturn(new AdminAuthService.SessionResponse(true,
        new AdminAuthService.AdminView("admin-1", "Viewer", "138****0000", "viewer",
            java.util.List.of("tasks:read", "inspirations:read"))));
    var interceptor = new AdminPermissionInterceptor(auth);
    var request = mock(HttpServletRequest.class);
    when(request.getRequestURI()).thenReturn("/manage_web/inspirations/1/publish");
    when(request.getCookies()).thenReturn(new Cookie[] { new Cookie(CookieSupport.ADMIN, "admin-token") });
    var handler = new HandlerMethod(new Endpoint(), Endpoint.class.getDeclaredMethod("publish"));
    assertThatThrownBy(() -> interceptor.preHandle(request, mock(HttpServletResponse.class), handler))
        .isInstanceOfSatisfying(ApiException.class, error -> {
          org.assertj.core.api.Assertions.assertThat(error.code()).isEqualTo("ADMIN_ROLE_REQUIRED");
          org.assertj.core.api.Assertions.assertThat(error.status().value()).isEqualTo(403);
        });
  }

  static final class Endpoint {
    @AdminPermission(minimum = AdminRole.OPERATOR)
    void publish() {}
  }
}
