package com.dreamspace.api.common;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dreamspace.api.controller.AdminBillingController;
import com.dreamspace.api.controller.AdminInspirationsController;
import com.dreamspace.api.controller.AdminModerationController;
import com.dreamspace.api.controller.AdminTasksController;
import com.dreamspace.api.service.AdminAuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.method.HandlerMethod;

class AdminPermissionInterceptorTest {
  @Test
  void viewerCannotInvokeWriteWithoutExactPermission() throws Exception {
    var auth = authenticated(List.of(AdminPermissions.INSPIRATIONS_READ));
    var request = request("/manage_web/inspirations/1/publish");
    var handler = new HandlerMethod(new Endpoint(), Endpoint.class.getDeclaredMethod("publish"));
    assertThatThrownBy(() -> new AdminPermissionInterceptor(auth)
        .preHandle(request, mock(HttpServletResponse.class), handler))
        .isInstanceOfSatisfying(ApiException.class, error -> {
          assertThat(error.code()).isEqualTo("ADMIN_PERMISSION_REQUIRED");
          assertThat(error.status().value()).isEqualTo(403);
        });
  }

  @Test
  void exactPermissionBuildsPrincipalAndAllowsRequest() throws Exception {
    var auth = authenticated(List.of(AdminPermissions.INSPIRATIONS_WRITE));
    var request = request("/manage_web/inspirations/1/publish");
    var handler = new HandlerMethod(new Endpoint(), Endpoint.class.getDeclaredMethod("publish"));

    assertThat(new AdminPermissionInterceptor(auth)
        .preHandle(request, mock(HttpServletResponse.class), handler)).isTrue();
    verify(request).setAttribute(org.mockito.ArgumentMatchers.eq(
        AdminPermissionInterceptor.PRINCIPAL_ATTRIBUTE),
        org.mockito.ArgumentMatchers.argThat(value -> value instanceof AdminPrincipal principal
            && principal.allows(AdminPermissions.INSPIRATIONS_WRITE)));
  }

  @Test
  void managementEndpointWithoutDeclarationIsRejectedAsConfigurationError() throws Exception {
    var request = request("/manage_web/undeclared");
    var handler = new HandlerMethod(new Endpoint(), Endpoint.class.getDeclaredMethod("undeclared"));

    assertThatThrownBy(() -> new AdminPermissionInterceptor(mock(AdminAuthService.class))
        .preHandle(request, mock(HttpServletResponse.class), handler))
        .isInstanceOfSatisfying(ApiException.class, error -> {
          assertThat(error.code()).isEqualTo("ADMIN_PERMISSION_CONFIGURATION_ERROR");
          assertThat(error.status().value()).isEqualTo(403);
        });
  }

  @Test
  void managementEndpointWithUnknownPermissionIsRejectedAsConfigurationError() throws Exception {
    var request = request("/manage_web/unknown");
    var handler = new HandlerMethod(new Endpoint(), Endpoint.class.getDeclaredMethod("unknown"));

    assertThatThrownBy(() -> new AdminPermissionInterceptor(mock(AdminAuthService.class))
        .preHandle(request, mock(HttpServletResponse.class), handler))
        .isInstanceOfSatisfying(ApiException.class, error -> {
          assertThat(error.code()).isEqualTo("ADMIN_PERMISSION_CONFIGURATION_ERROR");
          assertThat(error.status().value()).isEqualTo(403);
        });
  }

  @Test
  void everyProtectedManagementMappingDeclaresARegisteredPermission() {
    List.of(AdminTasksController.class, AdminModerationController.class,
            AdminInspirationsController.class, AdminBillingController.class)
        .forEach(controller -> java.util.Arrays.stream(controller.getDeclaredMethods())
            .filter(method -> AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class))
            .forEach(method -> {
              AdminPermission permission = AnnotatedElementUtils.findMergedAnnotation(
                  method, AdminPermission.class);
              assertThat(permission)
                  .as("%s#%s permission", controller.getSimpleName(), method.getName())
                  .isNotNull();
              assertThat(AdminPermissions.ALL)
                  .as("%s#%s registered permission", controller.getSimpleName(), method.getName())
                  .contains(permission.value());
            }));
  }

  private static AdminAuthService authenticated(List<String> permissions) {
    var auth = mock(AdminAuthService.class);
    when(auth.session("admin-token")).thenReturn(new AdminAuthService.SessionResponse(true,
        new AdminAuthService.AdminView("admin-1", "Viewer", "138****0000", "viewer",
            permissions)));
    return auth;
  }

  private static HttpServletRequest request(String uri) {
    var request = mock(HttpServletRequest.class);
    when(request.getRequestURI()).thenReturn(uri);
    when(request.getCookies()).thenReturn(
        new Cookie[] {new Cookie(CookieSupport.ADMIN, "admin-token")});
    return request;
  }

  static final class Endpoint {
    @AdminPermission(AdminPermissions.INSPIRATIONS_WRITE)
    void publish() {}

    void undeclared() {}

    @AdminPermission("unknown:permission")
    void unknown() {}
  }
}
