package com.dreamspace.api.controller;

import com.dreamspace.api.common.AdminPermission;
import com.dreamspace.api.common.AdminPermissionInterceptor;
import com.dreamspace.api.common.AdminPermissions;
import com.dreamspace.api.common.ApiException;
import com.dreamspace.api.common.AdminPrincipal;
import com.dreamspace.api.service.ModerationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/manage_web/moderation")
public class AdminModerationController {
  private final ModerationService service;

  public AdminModerationController(ModerationService service) { this.service = service; }

  @GetMapping("/cases")
  @AdminPermission(AdminPermissions.TASKS_READ)
  ModerationService.AdminPage cases(@RequestParam(required = false) String status,
      @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize) {
    return service.list(status, page, pageSize);
  }

  @GetMapping("/cases/{caseId}")
  @AdminPermission(AdminPermissions.TASKS_READ)
  ModerationService.AdminDetail detail(@PathVariable String caseId) { return service.detail(caseId); }

  @PostMapping("/cases/{caseId}/resolve")
  @AdminPermission(AdminPermissions.TASKS_WRITE)
  ModerationService.AdminDetail resolve(@PathVariable String caseId, @RequestBody ResolveRequest body,
      HttpServletRequest request) {
    Object value = request.getAttribute(AdminPermissionInterceptor.PRINCIPAL_ATTRIBUTE);
    if (!(value instanceof AdminPrincipal principal)) throw new ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "管理员会话无效");
    return service.resolve(principal, caseId, body == null ? null : body.outcome(), body == null ? null : body.note());
  }

  public record ResolveRequest(String outcome, String note) {}
}
