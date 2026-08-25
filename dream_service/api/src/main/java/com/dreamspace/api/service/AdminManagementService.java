package com.dreamspace.api.service;

import com.dreamspace.api.common.ApiException;
import com.dreamspace.api.common.AdminPrincipal;
import com.dreamspace.api.persistence.admin.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminManagementService {
  private final AdminManagementMapper mapper;
  private final BillingMapper auditMapper;
  private final ObjectMapper json;
  public AdminManagementService(AdminManagementMapper mapper, BillingMapper auditMapper, ObjectMapper json) { this.mapper = mapper; this.auditMapper = auditMapper; this.json = json; }
  public record Page<T>(List<T> items, long total, int page, int pageSize, int pageCount) {}
  public record AccountView(String id, String phoneMasked, String displayName, String status, String role, List<String> roleIds, int version, Instant createdAt, Instant updatedAt, Instant lastLoginAt, Instant disabledAt, String disabledReason) {}
  public record RoleView(String id, String code, String name, String description, boolean system, String status, int version, long accountCount, long permissionCount, List<String> permissionIds) {}
  public record PermissionView(String id, String code, String resource, String action, String description, String riskLevel) {}
  public record AccountInput(String phone, String displayName, String roleId) {}
  public record AccountPatch(String displayName, String status, String reason, int version) {}
  public record RoleInput(String code, String name, String description) {}
  public record RolePatch(String name, String description, String status, int version) {}
  public record IdsInput(List<String> ids, String reason, int version) {}

  public Page<AccountView> accounts(String query, String status, String roleId, int page, int pageSize) {
    int p = Math.max(1, page), size = Math.min(100, Math.max(1, pageSize));
    long total = mapper.countAccounts(blank(query), blank(status), blank(roleId));
    return new Page<>(mapper.listAccounts(blank(query), blank(status), blank(roleId), size, (p - 1) * size).stream().map(this::account).toList(), total, p, size, (int) Math.ceil(total / (double) size));
  }
  @Transactional public AccountView create(AdminPrincipal actor, AccountInput input, String idem) {
    if (input == null || blank(input.phone()) == null || blank(input.displayName()) == null || blank(input.roleId()) == null) bad("管理员手机号、名称和角色不能为空");
    if (mapper.countActiveRoles(List.of(input.roleId())) != 1) notFound("角色不存在或已停用");
    String key = blank(idem); if (key == null) bad("Idempotency-Key 不能为空"); String existing = mapper.findIdempotentSubject("CREATE_ADMIN", key); if (existing != null) return account(mapper.lockAccount(existing));
    String id = UUID.randomUUID().toString(); mapper.insertAccount(id, AuthService.normalizePhone(input.phone()), input.displayName().trim(), legacyRole(List.of(roleCode(input.roleId()))), false, "INVITED", actor.id());
    mapper.insertUserRoles(id, List.of(input.roleId()), actor.id());
    mapper.insertIdempotency("CREATE_ADMIN", key, id); AccountView result = account(mapper.lockAccount(id)); audit(actor.id(), "CREATE_ADMIN", "ADMIN_USER", id, null, result, "创建管理员账号"); return result;
  }
  @Transactional public AccountView update(AdminPrincipal actor, String id, AccountPatch input) {
    AdminAccountRecord current = mapper.lockAccount(id); if (current == null) notFound("管理员不存在");
    if (input == null || blank(input.displayName()) == null || input.reason() == null || input.reason().isBlank()) bad("名称和变更原因不能为空");
    boolean active = "ACTIVE".equalsIgnoreCase(input.status());
    if (!active && actor.id().equals(id)) conflict("不能停用当前登录账号");
    if (!active && current.active() && "ADMIN".equalsIgnoreCase(current.role()) && mapper.countActiveAdmins() <= 1) conflict("不能停用最后一个平台管理员");
    if (mapper.updateAccount(id, input.displayName().trim(), active ? "ACTIVE" : "DISABLED", active, actor.id(), input.reason(), input.version()) != 1) versionConflict();
    AccountView result = account(mapper.lockAccount(id)); audit(actor.id(), active ? "ENABLE_ADMIN" : "DISABLE_ADMIN", "ADMIN_USER", id, current, result, input.reason()); return result;
  }
  @Transactional public AccountView replaceRoles(AdminPrincipal actor, String id, IdsInput input) {
    AdminAccountRecord current = mapper.lockAccount(id); if (current == null) notFound("管理员不存在");
    List<String> ids = input == null || input.ids() == null ? List.of() : input.ids().stream().filter(Objects::nonNull).distinct().toList();
    if (ids.isEmpty() && actor.id().equals(id)) conflict("不能移除自己的全部角色");
    if (input == null || input.reason() == null || input.reason().isBlank()) bad("变更原因不能为空");
    if (!ids.isEmpty() && mapper.countActiveRoles(ids) != ids.size()) bad("包含未知或已停用角色");
    if (current.active() && mapper.roleCodes(ids).stream().noneMatch("ADMIN"::equals) && "ADMIN".equalsIgnoreCase(current.role()) && mapper.countActiveAdmins() <= 1) conflict("不能移除最后一个平台管理员角色");
    if (mapper.listUserRoleIds(id).equals(ids) && current.version() != input.version()) versionConflict();
    List<String> beforeRoles = mapper.listUserRoleIds(id); mapper.deleteUserRoles(id); if (!ids.isEmpty()) mapper.insertUserRoles(id, ids, actor.id()); mapper.updateLegacyRole(id, legacyRole(mapper.roleCodes(ids)));
    if (mapper.updateAccount(id, current.displayName(), current.active() ? "ACTIVE" : "DISABLED", current.active(), actor.id(), input.reason(), input.version()) != 1) versionConflict();
    AccountView result = account(mapper.lockAccount(id)); audit(actor.id(), "REPLACE_ADMIN_ROLES", "ADMIN_USER", id, beforeRoles, ids, input.reason()); return result;
  }
  @Transactional public void revokeSessions(AdminPrincipal actor, String id, String reason) { if (mapper.lockAccount(id) == null) notFound("管理员不存在"); if (reason == null || reason.isBlank()) bad("撤销原因不能为空"); mapper.revokeSessions(id); audit(actor.id(), "REVOKE_ADMIN_SESSIONS", "ADMIN_USER", id, null, null, reason); }
  public List<RoleView> roles() { return mapper.listRoles().stream().map(r -> new RoleView(r.id(), r.code(), r.name(), r.description(), r.system(), r.status(), r.version(), r.accountCount(), r.permissionCount(), mapper.listRolePermissionIds(r.id()))).toList(); }
  @Transactional public RoleView createRole(AdminPrincipal actor, RoleInput input) { if (input == null || blank(input.code()) == null || blank(input.name()) == null) bad("角色编码和名称不能为空"); String code = input.code().trim().toUpperCase(Locale.ROOT); if (!code.matches("[A-Z][A-Z0-9_-]{1,79}")) bad("角色编码格式无效"); String id = UUID.randomUUID().toString(); mapper.insertRole(id, code, input.name().trim(), input.description()); RoleView result = roles().stream().filter(r -> r.id().equals(id)).findFirst().orElseThrow(); audit(actor.id(), "CREATE_ADMIN_ROLE", "ADMIN_ROLE", id, null, result, "创建角色"); return result; }
  @Transactional public RoleView updateRole(AdminPrincipal actor, String id, RolePatch input) { AdminRoleDefinitionRecord role = mapper.lockRole(id); if (role == null) notFound("角色不存在"); if (role.system()) bad("系统角色不可编辑"); if (input == null || blank(input.name()) == null) bad("角色名称不能为空"); String status = input.status() == null ? role.status() : input.status().toUpperCase(Locale.ROOT); if (!Set.of("ACTIVE", "DISABLED").contains(status)) bad("角色状态无效"); if (mapper.updateRole(id, input.name().trim(), input.description(), status, input.version()) != 1) versionConflict(); RoleView result = roles().stream().filter(r -> r.id().equals(id)).findFirst().orElseThrow(); audit(actor.id(), "UPDATE_ADMIN_ROLE", "ADMIN_ROLE", id, role, result, "更新角色"); return result; }
  @Transactional public RoleView replacePermissions(AdminPrincipal actor, String id, IdsInput input) { AdminRoleDefinitionRecord role = mapper.lockRole(id); if (role == null) notFound("角色不存在"); List<String> ids = input == null || input.ids() == null ? List.of() : input.ids().stream().filter(Objects::nonNull).distinct().toList(); if (!ids.isEmpty() && mapper.countActivePermissions(ids) != ids.size()) bad("包含未知或已停用权限"); if (input == null || input.reason() == null || input.reason().isBlank()) bad("变更原因不能为空"); List<String> before = mapper.listRolePermissionIds(id); if (mapper.bumpRoleVersion(id, input.version()) != 1) versionConflict(); mapper.deleteRolePermissions(id); if (!ids.isEmpty()) mapper.insertRolePermissions(id, ids, actor.id()); RoleView result = roles().stream().filter(r -> r.id().equals(id)).findFirst().orElseThrow(); audit(actor.id(), "REPLACE_ROLE_PERMISSIONS", "ADMIN_ROLE", id, before, ids, input.reason()); return result; }
  public List<PermissionView> permissions() { return mapper.listPermissions().stream().map(p -> new PermissionView(p.id(), p.code(), p.resource(), p.action(), p.description(), p.riskLevel())).toList(); }
  private AccountView account(AdminAccountRecord a) { List<String> ids = mapper.listUserRoleIds(a.id()); return new AccountView(a.id(), mask(a.phone()), a.displayName(), a.status(), a.role().toLowerCase(Locale.ROOT), ids, a.version(), a.createdAt(), a.updatedAt(), a.lastLoginAt(), a.disabledAt(), a.disabledReason()); }
  private String roleCode(String id) { List<String> codes = mapper.roleCodes(List.of(id)); if (codes.isEmpty()) notFound("角色不存在"); return codes.get(0); }
  private static String legacyRole(List<String> codes) { if (codes.contains("ADMIN")) return "ADMIN"; if (codes.contains("OPERATOR")) return "OPERATOR"; return "VIEWER"; }
  private void audit(String actor, String action, String type, String id, Object before, Object after, String reason) { try { auditMapper.insertAudit(UUID.randomUUID().toString(), actor, "ADMIN", action, type, id, json.writeValueAsString(before), json.writeValueAsString(after), reason); } catch (Exception e) { throw new IllegalStateException("admin audit serialization failed", e); } }
  private static String blank(String v) { return v == null || v.isBlank() ? null : v.trim(); }
  private static String mask(String v) { return v == null || v.length() < 7 ? "***" : v.substring(0, 3) + "****" + v.substring(v.length() - 4); }
  private static void bad(String m) { throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", m); }
  private static void notFound(String m) { throw new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", m); }
  private static void conflict(String m) { throw new ApiException(HttpStatus.CONFLICT, "POLICY_CONFLICT", m); }
  private static void versionConflict() { throw new ApiException(HttpStatus.CONFLICT, "RESOURCE_VERSION_CONFLICT", "资源已被其他管理员修改"); }
}
