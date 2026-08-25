package com.dreamspace.api.controller;

import com.dreamspace.api.common.AdminPermission;
import com.dreamspace.api.common.AdminPermissionInterceptor;
import com.dreamspace.api.common.AdminPermissions;
import com.dreamspace.api.common.AdminPrincipal;
import com.dreamspace.api.common.ApiException;
import com.dreamspace.api.service.BillingService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/manage_web")
public class AdminBillingController {
  private final BillingService billing;
  public AdminBillingController(BillingService billing) { this.billing = billing; }
  @GetMapping("/users") @AdminPermission(AdminPermissions.USERS_READ) BillingService.Page<BillingService.UserItem> users(@RequestParam(required = false) String query,
      @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize) { return billing.users(query, page, pageSize); }
  @GetMapping("/users/{id}") @AdminPermission(AdminPermissions.USERS_READ) BillingService.UserDetail user(@PathVariable String id) { return billing.userDetail(id); }
  @GetMapping("/users/{id}/ledger") @AdminPermission(AdminPermissions.USERS_READ) BillingService.Page<BillingService.LedgerItem> userLedger(@PathVariable String id, @RequestParam(required = false) String type,
      @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize) { return billing.userLedger(id, type, page, pageSize); }
  @PostMapping("/users/{id}/disable") @AdminPermission(AdminPermissions.USERS_WRITE) BillingService.UserDetail disable(@PathVariable String id, @RequestBody Reason body, HttpServletRequest request) { return billing.disable(principal(request), id, body == null ? null : body.reason()); }
  @PostMapping("/users/{id}/enable") @AdminPermission(AdminPermissions.USERS_WRITE) BillingService.UserDetail enable(@PathVariable String id, HttpServletRequest request) { return billing.enable(principal(request), id); }
  @PostMapping("/users/{id}/revoke-sessions") @AdminPermission(AdminPermissions.USERS_WRITE) @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT) void revoke(@PathVariable String id, HttpServletRequest request) { billing.revokeSessions(principal(request), id); }
  @PostMapping("/users/{id}/credit-adjustments") @AdminPermission(AdminPermissions.USERS_WRITE) BillingService.UserDetail adjust(@PathVariable String id, @RequestBody Adjustment body, HttpServletRequest request) { return billing.adjustCredits(principal(request), id, body == null ? 0 : body.amount(), body == null ? null : body.reason()); }
  @GetMapping("/billing/rules") @AdminPermission(AdminPermissions.PRICING_READ) List<BillingService.PricingRuleView> rules() { return billing.pricingRules(); }
  @PostMapping("/billing/rules") @AdminPermission(AdminPermissions.PRICING_WRITE) BillingService.PricingRuleView createRule(@RequestBody BillingService.PricingRuleInput body, HttpServletRequest request) { return billing.createPricingRule(principal(request), body); }
  @PostMapping("/billing/rules/{id}/publish") @AdminPermission(AdminPermissions.PRICING_WRITE) BillingService.PricingRuleView publish(@PathVariable String id, HttpServletRequest request) { return billing.publishPricingRule(principal(request), id); }
  @PostMapping("/billing/rules/{id}/retire") @AdminPermission(AdminPermissions.PRICING_WRITE) BillingService.PricingRuleView retire(@PathVariable String id, HttpServletRequest request) { return billing.retirePricingRule(principal(request), id); }
  @GetMapping("/billing/products") @AdminPermission(AdminPermissions.PRICING_READ) List<BillingService.ProductView> products() { return billing.adminProducts(); }
  @PostMapping("/billing/products") @AdminPermission(AdminPermissions.PRICING_WRITE) BillingService.ProductView createProduct(@RequestBody(required = false) BillingService.ProductInput body, HttpServletRequest request) { return billing.createProduct(principal(request), body); }
  @PostMapping("/billing/products/{id}/activate") @AdminPermission(AdminPermissions.PRICING_WRITE) BillingService.ProductView activateProduct(@PathVariable String id, HttpServletRequest request) { return billing.setProductStatus(principal(request), id, "ACTIVE"); }
  @PostMapping("/billing/products/{id}/inactivate") @AdminPermission(AdminPermissions.PRICING_WRITE) BillingService.ProductView inactivateProduct(@PathVariable String id, HttpServletRequest request) { return billing.setProductStatus(principal(request), id, "INACTIVE"); }
  @GetMapping("/billing/orders") @AdminPermission(AdminPermissions.BILLING_READ) BillingService.Page<BillingService.AdminOrderView> orders(@RequestParam(required = false) String status, @RequestParam(required = false) String query,
      @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize) { return billing.adminOrders(status, query, page, pageSize); }
  @GetMapping("/billing/orders/{orderNo}") @AdminPermission(AdminPermissions.BILLING_READ) BillingService.AdminOrderView order(@PathVariable String orderNo) { return billing.adminOrder(orderNo); }
  @PostMapping("/billing/orders/{orderNo}/refund") @AdminPermission(AdminPermissions.BILLING_WRITE) BillingService.RefundView refund(@PathVariable String orderNo, @RequestBody(required = false) RefundRequest body, HttpServletRequest request) { return billing.refundOrder(principal(request), orderNo, body == null ? null : body.reason(), body == null ? null : body.idempotencyKey()); }
  @GetMapping("/audit-events") @AdminPermission(AdminPermissions.AUDIT_READ) BillingService.Page<BillingService.AuditView> audit(@RequestParam(required = false) String subjectType, @RequestParam(required = false) String subjectId,
      @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize) { return billing.auditEvents(subjectType, subjectId, page, pageSize); }
  private AdminPrincipal principal(HttpServletRequest request) { Object value = request.getAttribute(AdminPermissionInterceptor.PRINCIPAL_ATTRIBUTE); if (!(value instanceof AdminPrincipal p)) throw new ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "管理员会话无效"); return p; }
  public record Reason(String reason) {}
  public record Adjustment(int amount, String reason) {}
  public record RefundRequest(String reason, String idempotencyKey) {}
}
