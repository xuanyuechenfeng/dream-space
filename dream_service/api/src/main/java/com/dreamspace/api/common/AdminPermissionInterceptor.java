package com.dreamspace.api.common;

import com.dreamspace.api.service.AdminAuthService;
import com.dreamspace.common.persistence.database.DatabaseEnums.AdminRole;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AdminPermissionInterceptor implements HandlerInterceptor {
  private static final Logger LOG = LoggerFactory.getLogger(AdminPermissionInterceptor.class);
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
    if (permission == null || !AdminPermissions.ALL.contains(permission.value())) {
      LOG.error("Invalid admin permission declaration controller={} method={} permission={} path={}",
          method.getBeanType().getName(), method.getMethod().getName(),
          permission == null ? null : permission.value(), request.getRequestURI());
      throw new ApiException(HttpStatus.FORBIDDEN, "ADMIN_PERMISSION_CONFIGURATION_ERROR",
          "管理接口权限配置错误");
    }
    String token = CookieSupport.read(request, CookieSupport.ADMIN);
    var session = auth.session(token);
    if (!session.authenticated() || session.user() == null) throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "请先登录管理员账号");
    AdminRole role = AdminRole.valueOf(session.user().role().toUpperCase());
    var principal = new AdminPrincipal(session.user().id(), session.user().displayName(), role,
        new java.util.HashSet<>(session.user().permissions()));
    request.setAttribute(PRINCIPAL_ATTRIBUTE, principal);
    if (!principal.allows(permission.value())) {
      throw new ApiException(HttpStatus.FORBIDDEN, "ADMIN_PERMISSION_REQUIRED", "管理员权限不足");
    }
    return true;
  }
}
