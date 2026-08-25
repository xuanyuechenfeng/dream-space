package com.dreamspace.api.controller;

import com.dreamspace.api.common.ApiException;
import com.dreamspace.api.common.CookieSupport;
import com.dreamspace.api.service.AuthService;
import com.dreamspace.api.service.BillingService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/dream_web/account", "/dream_web/billing"})
public class BillingController {
  private final BillingService billing;
  private final AuthService auth;
  public BillingController(BillingService billing, AuthService auth) { this.billing = billing; this.auth = auth; }
  @GetMapping Account account(HttpServletRequest request) { return new Account(billing.account(user(request))); }
  @GetMapping("/ledger") BillingService.Page<BillingService.LedgerItem> ledger(@RequestParam(required = false) String type,
      @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize, HttpServletRequest request) {
    return billing.ledger(user(request), type, page, pageSize);
  }
  @GetMapping("/products") java.util.List<BillingService.ProductView> products() { return billing.products(); }
  @PostMapping("/orders") BillingService.OrderView createOrder(@RequestBody(required = false) OrderRequest body, HttpServletRequest request) {
    return billing.createOrder(user(request), body == null ? null : body.productId(), body == null ? 0 : body.quantity(), body == null ? null : body.provider(), body == null ? null : body.idempotencyKey());
  }
  @GetMapping("/orders/{orderNo}") BillingService.OrderView order(@org.springframework.web.bind.annotation.PathVariable String orderNo, HttpServletRequest request) { return billing.order(user(request), orderNo); }
  @GetMapping("/orders") BillingService.Page<BillingService.OrderView> orders(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize, HttpServletRequest request) { return billing.orders(user(request), page, pageSize); }
  @PostMapping("/orders/{orderNo}/cancel") BillingService.OrderView cancel(@PathVariable String orderNo, HttpServletRequest request) { return billing.cancelOrder(user(request), orderNo); }
  private String user(HttpServletRequest request) {
    var session = auth.session(CookieSupport.read(request, CookieSupport.USER));
    if (!session.authenticated() || session.user() == null) throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "请先登录");
    return session.user().id();
  }
  public record Account(BillingService.Account account) {}
  public record OrderRequest(String productId, int quantity, String provider, String idempotencyKey) {}
}
