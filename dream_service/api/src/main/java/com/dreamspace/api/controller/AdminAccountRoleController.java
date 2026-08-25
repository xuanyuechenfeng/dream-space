package com.dreamspace.api.controller;

import com.dreamspace.api.common.*;
import com.dreamspace.api.service.AdminManagementService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/manage_web")
public class AdminAccountRoleController {
  private final AdminManagementService service;
  public AdminAccountRoleController(AdminManagementService service) { this.service = service; }
  @GetMapping("/admins") @AdminPermission(AdminPermissions.ADMINS_READ)
  AdminManagementService.Page<AdminManagementService.AccountView> accounts(@RequestParam(required=false) String query, @RequestParam(required=false) String status, @RequestParam(required=false) String roleId, @RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="20") int pageSize) { return service.accounts(query, status, roleId, page, pageSize); }
  @PostMapping("/admins") @AdminPermission(AdminPermissions.ADMINS_WRITE)
  AdminManagementService.AccountView create(@RequestBody AdminManagementService.AccountInput body, @RequestHeader(value="Idempotency-Key", required=false) String idem, HttpServletRequest req) { return service.create(principal(req), body, idem); }
  @PatchMapping("/admins/{id}") @AdminPermission(AdminPermissions.ADMINS_WRITE)
  AdminManagementService.AccountView update(@PathVariable String id, @RequestBody AdminManagementService.AccountPatch body, HttpServletRequest req) { return service.update(principal(req), id, body); }
  @PutMapping("/admins/{id}/roles") @AdminPermission(AdminPermissions.ADMINS_WRITE)
  AdminManagementService.AccountView roles(@PathVariable String id, @RequestBody AdminManagementService.IdsInput body, HttpServletRequest req) { return service.replaceRoles(principal(req), id, body); }
  @PostMapping("/admins/{id}/revoke-sessions") @ResponseStatus(HttpStatus.NO_CONTENT) @AdminPermission(AdminPermissions.ADMINS_WRITE)
  void revoke(@PathVariable String id, @RequestBody AdminManagementService.IdsInput body, HttpServletRequest req) { service.revokeSessions(principal(req), id, body == null ? null : body.reason()); }
  @GetMapping("/roles") @AdminPermission(AdminPermissions.ROLES_READ) List<AdminManagementService.RoleView> roles() { return service.roles(); }
  @PostMapping("/roles") @AdminPermission(AdminPermissions.ROLES_WRITE) AdminManagementService.RoleView createRole(@RequestBody AdminManagementService.RoleInput body, HttpServletRequest req) { return service.createRole(principal(req), body); }
  @PatchMapping("/roles/{id}") @AdminPermission(AdminPermissions.ROLES_WRITE) AdminManagementService.RoleView updateRole(@PathVariable String id, @RequestBody AdminManagementService.RolePatch body, HttpServletRequest req) { return service.updateRole(principal(req), id, body); }
  @PutMapping("/roles/{id}/permissions") @AdminPermission(AdminPermissions.ROLES_WRITE) AdminManagementService.RoleView permissions(@PathVariable String id, @RequestBody AdminManagementService.IdsInput body, HttpServletRequest req) { return service.replacePermissions(principal(req), id, body); }
  @GetMapping("/permissions") @AdminPermission(AdminPermissions.ROLES_READ) List<AdminManagementService.PermissionView> permissions() { return service.permissions(); }
  private AdminPrincipal principal(HttpServletRequest req) { Object value = req.getAttribute(AdminPermissionInterceptor.PRINCIPAL_ATTRIBUTE); if (value instanceof AdminPrincipal p) return p; throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "管理员会话无效"); }
}
