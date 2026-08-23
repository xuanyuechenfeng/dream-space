package com.dreamspace.api.common;

import com.dreamspace.api.service.AdminAuthService;
import com.dreamspace.common.persistence.database.DatabaseEnums.AdminRole;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AdminPermissionInterceptor implements HandlerInterceptor {
  public static final String PRINCIPAL_ATTRIBUTE = AdminPrincipal.class.getName();
  private final AdminAuthService auth;

  public AdminPermissionInterceptor(AdminAuthService auth) {
    this.auth = auth;
  }

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
    if (!(handler instanceof HandlerMethod method) || !request.getRequestURI().startsWith("/manage_web/")) return true;
    if (request.getRequestURI().startsWith("/manage_web/auth/")) return true;
    AdminPermission permission = AnnotatedElementUtils.findMergedAnnotation(method.getMethod(), AdminPermission.class);
    if (permission == null) permission = AnnotatedElementUtils.findMergedAnnotation(method.getBeanType(), AdminPermission.class);
    if (permission == null) permission = new DefaultPermission();
    String token = CookieSupport.read(request, CookieSupport.ADMIN);
    var session = auth.session(token);
    if (!session.authenticated() || session.user() == null) throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "请先登录管理员账号");
    AdminRole role = AdminRole.valueOf(session.user().role().toUpperCase());
    var principal = new AdminPrincipal(session.user().id(), session.user().displayName(), role);
    request.setAttribute(PRINCIPAL_ATTRIBUTE, principal);
    if (!principal.allows(permission.minimum())) throw new ApiException(HttpStatus.FORBIDDEN, "ADMIN_ROLE_REQUIRED", "管理员权限不足");
    return true;
  }

  private static final class DefaultPermission implements AdminPermission {
    @Override public AdminRole minimum() { return AdminRole.VIEWER; }
    @Override public Class<? extends java.lang.annotation.Annotation> annotationType() { return AdminPermission.class; }
  }
}
