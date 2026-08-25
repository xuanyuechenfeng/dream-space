package com.dreamspace.api.service;

import com.dreamspace.api.common.ApiException;
import com.dreamspace.api.common.AdminPrincipal;
import com.dreamspace.api.persistence.admin.BillingLedgerRecord;
import com.dreamspace.api.persistence.admin.BillingMapper;
import com.dreamspace.api.persistence.admin.BillingUserRecord;
import com.dreamspace.api.persistence.admin.CreditProductRecord;
import com.dreamspace.api.persistence.admin.PricingRuleRecord;
import com.dreamspace.api.persistence.admin.BillingOrderRecord;
import com.dreamspace.api.persistence.admin.AdminBillingOrderRecord;
import com.dreamspace.api.persistence.admin.BillingAuditRecord;
import com.dreamspace.api.persistence.admin.RefundRecord;
import com.dreamspace.common.persistence.quota.QuotaAccountRecord;
import com.dreamspace.common.persistence.quota.QuotaTransactionService;
import com.dreamspace.common.persistence.config.DreamSpaceProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillingService {
  private final BillingMapper mapper;
  private final QuotaTransactionService quota;
  private final ObjectMapper json;
  private final int initialQuota;

  public BillingService(BillingMapper mapper, QuotaTransactionService quota, ObjectMapper json) {
    this(mapper, quota, json, null);
  }
  @org.springframework.beans.factory.annotation.Autowired
  public BillingService(BillingMapper mapper, QuotaTransactionService quota, ObjectMapper json, DreamSpaceProperties properties) {
    this.mapper = mapper; this.quota = quota; this.json = json;
    this.initialQuota = properties == null ? 100 : properties.quota().initialTotal();
  }

  public record Account(String userId, String phoneMasked, String status, String displayName,
      int total, int available, int reserved, int used, Instant createdAt, Instant lastLoginAt) {}
  public record Page<T>(List<T> items, long total, int page, int pageSize, int pageCount) {}
  public record LedgerItem(String id, String type, int amount, int balanceAfter, String sourceType,
      String sourceId, String taskId, String ruleId, Integer ruleVersion, String reasonCode, Instant createdAt) {}
  public record UserItem(String id, String phoneMasked, String status, String displayName,
      Instant createdAt, Instant lastLoginAt, Instant disabledAt, String disabledReason) {}
  public record UserDetail(UserItem user, Account account, long sessionCount) {}
  public record PricingRuleInput(String code, int version, String operation, String modelPattern,
      String resolution, Integer minWidth, Integer maxWidth, Integer minHeight, Integer maxHeight,
      int unitCreditCost, String formula, Instant effectiveFrom, Instant effectiveTo) {}
  public record PricingRuleView(String id, String code, int version, String operation, String modelPattern,
      String resolution, Integer minWidth, Integer maxWidth, Integer minHeight, Integer maxHeight,
      int unitCreditCost, String formula, Instant effectiveFrom, Instant effectiveTo, String status,
      String createdBy, Instant createdAt, Instant updatedAt) {}
  public record ProductView(String id, String code, String name, int creditAmount, long amountMinor,
      String currency, Integer validityDays, String status, int sortOrder, Instant createdAt, Instant updatedAt) {}
  public record OrderView(String orderNo, String productCode, String productName, int quantity, int creditAmount,
      long amountMinor, String currency, String status, String provider, Instant expiresAt, Instant paidAt, Instant createdAt) {}
  public record AdminOrderView(String orderNo, String userId, String phoneMasked, String productCode, String productName,
      int quantity, int creditAmount, long amountMinor, String currency, String status, String provider,
      Instant expiresAt, Instant paidAt, Instant createdAt) {}
  public record RefundView(String id, String orderNo, long amountMinor, String reason, String status,
      String providerRefundId, String idempotencyKey, Instant createdAt, Instant completedAt) {}
  public record AuditView(String id, String actorId, String actorType, String action, String subjectType,
      String subjectId, com.fasterxml.jackson.databind.JsonNode before, com.fasterxml.jackson.databind.JsonNode after,
      String reason, Instant createdAt) {}
  public record ProductInput(String code, String name, int creditAmount, long amountMinor, String currency,
      Integer validityDays, int sortOrder) {}

  public Account account(String userId) {
    BillingUserRecord user = requiredUser(userId);
    return account(user, quota.ensureAndRead(userId, initialQuota));
  }

  public Page<LedgerItem> ledger(String userId, String type, int page, int pageSize) {
    requiredUser(userId);
    String normalized = optionalType(type);
    int p = range(page, 1, 1_000_000);
    int size = range(pageSize, 1, 100);
    long total = mapper.countLedger(userId, normalized);
    List<LedgerItem> items = mapper.listLedger(userId, normalized, size, (p - 1) * size).stream().map(this::ledger).toList();
    return new Page<>(items, total, p, size, (int) Math.ceil(total / (double) size));
  }

  public Page<UserItem> users(String query, int page, int pageSize) {
    int p = range(page, 1, 1_000_000);
    int size = range(pageSize, 1, 100);
    String q = query == null || query.isBlank() ? null : bounded(query, 100);
    long total = mapper.countUsers(q);
    return new Page<>(mapper.listUsers(q, size, (p - 1) * size).stream().map(this::user).toList(), total, p, size,
        (int) Math.ceil(total / (double) size));
  }

  public UserDetail userDetail(String id) {
    BillingUserRecord user = requiredUser(id);
    return new UserDetail(user(user), account(user, quota.ensureAndRead(id, initialQuota)), mapper.countSessions(id));
  }

  public Page<LedgerItem> userLedger(String id, String type, int page, int pageSize) {
    requiredUser(id);
    return ledger(id, type, page, pageSize);
  }

  @Transactional
  public UserDetail disable(AdminPrincipal actor, String id, String reason) {
    BillingUserRecord before = requiredUser(id);
    String text = requiredReason(reason);
    if (mapper.disableUser(id, actor.id(), text) != 1) throw bad("USER_STATE_CONFLICT", "用户状态已变化");
    mapper.revokeSessions(id);
    BillingUserRecord after = requiredUser(id);
    audit(actor.id(), "DISABLE_USER", "USER", id, before, after, text);
    return userDetail(id);
  }

  @Transactional
  public UserDetail enable(AdminPrincipal actor, String id) {
    BillingUserRecord before = requiredUser(id);
    if (mapper.enableUser(id) != 1) throw bad("USER_STATE_CONFLICT", "用户状态已变化");
    BillingUserRecord after = requiredUser(id);
    audit(actor.id(), "ENABLE_USER", "USER", id, before, after, null);
    return userDetail(id);
  }

  @Transactional
  public void revokeSessions(AdminPrincipal actor, String id) {
    requiredUser(id);
    mapper.revokeSessions(id);
    audit(actor.id(), "REVOKE_USER_SESSIONS", "USER", id, null, null, "管理员撤销全部用户会话");
  }

  @Transactional
  public UserDetail adjustCredits(AdminPrincipal actor, String id, int amount, String reason) {
    requiredUser(id);
    if (amount == 0 || Math.abs((long) amount) > 1_000_000) throw bad("CREDIT_AMOUNT_INVALID", "额度调整值无效");
    String text = requiredReason(reason);
    if (amount < 0) throw bad("CREDIT_DEBIT_UNSUPPORTED", "首期只支持赠送额度，扣减请走人工补偿流程");
    QuotaAccountRecord before = quota.ensureAndRead(id, initialQuota);
    String adjustmentId = UUID.randomUUID().toString();
    if (!quota.grant(id, amount, "ADMIN_ADJUSTMENT", adjustmentId, text, initialQuota)) throw bad("CREDIT_ADJUSTMENT_FAILED", "额度调整失败");
    QuotaAccountRecord after = quota.ensureAndRead(id, initialQuota);
    audit(actor.id(), "CREDIT_GRANT", "USER", id, before, after, text);
    return userDetail(id);
  }

  public List<PricingRuleView> pricingRules() { return mapper.listPricingRules().stream().map(this::pricing).toList(); }

  @Transactional
  public PricingRuleView createPricingRule(AdminPrincipal actor, PricingRuleInput input) {
    if (input == null || input.code() == null || input.code().isBlank() || input.version() < 1
        || input.operation() == null || input.operation().isBlank() || input.unitCreditCost() < 1)
      throw bad("PRICING_RULE_INVALID", "计费规则参数无效");
    Instant from = input.effectiveFrom() == null ? Instant.now() : input.effectiveFrom();
    PricingRuleRecord rule = new PricingRuleRecord(UUID.randomUUID().toString(), input.code().trim(), input.version(),
        input.operation().trim().toUpperCase(Locale.ROOT), optional(input.modelPattern(), "*"), optional(input.resolution(), "ANY"),
        input.minWidth(), input.maxWidth(), input.minHeight(), input.maxHeight(), input.unitCreditCost(),
        optional(input.formula(), "unitCost*imageCount"), from, input.effectiveTo(), "DRAFT", actor.id(), Instant.now(), Instant.now());
    if (mapper.insertPricingRule(rule) != 1) throw bad("PRICING_RULE_CONFLICT", "计费规则版本已存在");
    audit(actor.id(), "CREATE_PRICING_RULE", "PRICING_RULE", rule.id(), null, rule, null);
    return pricing(rule);
  }

  @Transactional
  public PricingRuleView publishPricingRule(AdminPrincipal actor, String id) {
    PricingRuleRecord rule = mapper.listPricingRules().stream().filter(item -> item.id().equals(id)).findFirst()
        .orElseThrow(() -> bad("NOT_FOUND", "计费规则不存在"));
    if (mapper.listPricingRules().stream().anyMatch(item -> !item.id().equals(id) && "ACTIVE".equals(item.status())
        && item.operation().equals(rule.operation()) && item.resolution().equals(rule.resolution()) && overlaps(item, rule)))
      throw bad("PRICING_RULE_CONFLICT", "同一操作和分辨率的生效窗口重叠");
    if (mapper.updatePricingStatus(id, "ACTIVE", rule.status(), rule.effectiveFrom()) != 1) throw bad("PRICING_RULE_CONFLICT", "规则状态已变化");
    PricingRuleView result = pricing(mapper.listPricingRules().stream().filter(item -> item.id().equals(id)).findFirst().orElse(rule));
    audit(actor.id(), "PUBLISH_PRICING_RULE", "PRICING_RULE", id, rule, result, null);
    return result;
  }

  @Transactional
  public PricingRuleView retirePricingRule(AdminPrincipal actor, String id) {
    PricingRuleRecord rule = mapper.listPricingRules().stream().filter(item -> item.id().equals(id)).findFirst()
        .orElseThrow(() -> bad("NOT_FOUND", "计费规则不存在"));
    if (mapper.updatePricingStatus(id, "RETIRED", rule.status(), rule.effectiveFrom()) != 1) throw bad("PRICING_RULE_CONFLICT", "规则状态已变化");
    audit(actor.id(), "RETIRE_PRICING_RULE", "PRICING_RULE", id, rule, null, null);
    return pricing(rule);
  }

  public List<ProductView> products() { return mapper.listActiveProducts().stream().map(this::product).toList(); }

  public List<ProductView> adminProducts() { return mapper.listAllProducts().stream().map(this::product).toList(); }

  @Transactional
  public ProductView createProduct(AdminPrincipal actor, ProductInput input) {
    if (input == null || input.code() == null || !input.code().matches("[A-Za-z0-9_-]{2,80}")
        || input.name() == null || input.name().isBlank() || input.name().length() > 160
        || input.creditAmount() <= 0 || input.amountMinor() <= 0 || input.currency() == null
        || !input.currency().matches("[A-Za-z]{3}") || input.validityDays() != null && input.validityDays() <= 0)
      throw bad("PRODUCT_INVALID", "产品参数无效");
    CreditProductRecord product = new CreditProductRecord(UUID.randomUUID().toString(), input.code().trim(), input.name().trim(),
        input.creditAmount(), input.amountMinor(), input.currency().toUpperCase(Locale.ROOT), input.validityDays(), "DRAFT",
        input.sortOrder(), Instant.now(), Instant.now());
    if (mapper.insertProduct(product, actor.id()) != 1) throw bad("PRODUCT_CONFLICT", "产品编码已存在");
    audit(actor.id(), "CREATE_CREDIT_PRODUCT", "PRODUCT", product.id(), null, product, null);
    return product(product);
  }

  @Transactional
  public ProductView setProductStatus(AdminPrincipal actor, String id, String status) {
    CreditProductRecord product = mapper.findProduct(id);
    if (product == null) throw bad("NOT_FOUND", "产品不存在");
    if (!List.of("ACTIVE", "INACTIVE").contains(status)) throw bad("PRODUCT_STATUS_INVALID", "产品状态无效");
    if (mapper.updateProductStatus(id, status, product.status()) != 1) throw bad("PRODUCT_STATE_CONFLICT", "产品状态已变化");
    CreditProductRecord after = mapper.findProduct(id);
    audit(actor.id(), "ACTIVE".equals(status) ? "ACTIVATE_CREDIT_PRODUCT" : "INACTIVATE_CREDIT_PRODUCT", "PRODUCT", id, product, after, null);
    return product(after);
  }

  @Transactional
  public OrderView createOrder(String userId, String productId, int quantity, String provider, String key) {
    requiredActiveUser(userId);
    if (key == null || !key.matches("[A-Za-z0-9:_-]{8,128}")) throw bad("ORDER_IDEMPOTENCY_INVALID", "订单幂等键无效");
    BillingOrderRecord existing = mapper.findOrderByKey(userId, key);
    if (existing != null) return order(existing);
    if (quantity < 1 || quantity > 20) throw bad("ORDER_QUANTITY_INVALID", "购买数量无效");
    CreditProductRecord product = mapper.findProduct(productId);
    if (product == null || !"ACTIVE".equals(product.status())) throw bad("PRODUCT_UNAVAILABLE", "额度产品不可用");
    String normalizedProvider = provider == null || provider.isBlank() ? "mock" : provider.trim().toLowerCase(Locale.ROOT);
    BillingOrderRecord order = new BillingOrderRecord(UUID.randomUUID().toString(), "DS" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT), userId, product.id(), product.code(), product.name(), quantity, product.creditAmount() * quantity, product.amountMinor() * quantity, product.currency(), "CREATED", normalizedProvider, key, Instant.now().plusSeconds(30 * 60), null, Instant.now(), Instant.now());
    if (mapper.insertOrder(order) != 1) throw bad("ORDER_CONFLICT", "订单创建失败");
    return order(order);
  }

  @Transactional
  public OrderView paymentWebhook(String provider, String orderNo, String transactionId, String eventId, long amountMinor, String currency, boolean signatureVerified) {
    if (!signatureVerified) throw bad("PAYMENT_SIGNATURE_INVALID", "支付回调验签失败");
    if (provider == null || provider.isBlank() || orderNo == null || orderNo.isBlank() || transactionId == null || transactionId.isBlank() || eventId == null || eventId.isBlank() || currency == null || currency.isBlank() || amountMinor <= 0)
      throw bad("PAYMENT_WEBHOOK_INVALID", "支付回调参数无效");
    BillingOrderRecord order = mapper.findOrderByNo(orderNo);
    if (order == null) throw bad("NOT_FOUND", "订单不存在");
    if (!order.provider().equals(provider) || order.amountMinor() != amountMinor || !order.currency().equalsIgnoreCase(currency))
      throw bad("PAYMENT_AMOUNT_MISMATCH", "支付订单金额或币种不匹配");
    var existingEvent = mapper.findPaymentEvent(provider, eventId);
    if (existingEvent != null) {
      if (!order.id().equals(existingEvent.orderId())) throw bad("PAYMENT_EVENT_CONFLICT", "支付事件已关联其他订单");
      return order(order);
    }
    if (mapper.insertPayment(UUID.randomUUID().toString(), order.id(), provider, transactionId, eventId, amountMinor, currency) != 1)
      return order(order);
    if ("PAID".equals(order.status())) return order(order);
    if (Instant.now().isAfter(order.expiresAt())) throw bad("ORDER_EXPIRED", "订单已过期");
    if (mapper.markOrderPaid(orderNo) != 1) throw bad("PAYMENT_STATE_CONFLICT", "订单状态已变化");
    if (!quota.grant(order.userId(), order.creditAmount(), "ORDER", order.id(), "支付订单到账", initialQuota)) throw bad("CREDIT_GRANT_FAILED", "额度发放失败");
    return order(mapper.findOrderByNo(orderNo));
  }

  public OrderView order(String userId, String orderNo) {
    BillingOrderRecord order = mapper.findOrder(userId, orderNo);
    if (order == null) throw bad("NOT_FOUND", "订单不存在");
    return order(order);
  }

  @Transactional
  public OrderView cancelOrder(String userId, String orderNo) {
    requiredUser(userId);
    BillingOrderRecord order = mapper.findOrder(userId, orderNo);
    if (order == null) throw bad("NOT_FOUND", "订单不存在");
    if (mapper.cancelOrder(userId, orderNo) != 1) throw bad("ORDER_STATE_CONFLICT", "订单当前不可取消");
    return order(mapper.findOrder(userId, orderNo));
  }
  public Page<OrderView> orders(String userId, int page, int pageSize) {
    requiredUser(userId); int p = range(page, 1, 1_000_000); int size = range(pageSize, 1, 100); long total = mapper.countOrders(userId);
    return new Page<>(mapper.listOrders(userId, size, (p - 1) * size).stream().map(this::order).toList(), total, p, size, (int) Math.ceil(total / (double) size));
  }

  public Page<AdminOrderView> adminOrders(String status, String query, int page, int pageSize) {
    int p = range(page, 1, 1_000_000); int size = range(pageSize, 1, 100);
    String normalizedStatus = status == null || status.isBlank() ? null : bounded(status.trim().toUpperCase(Locale.ROOT), 24);
    String normalizedQuery = query == null || query.isBlank() ? null : bounded(query, 100);
    long total = mapper.countAdminOrders(normalizedStatus, normalizedQuery);
    return new Page<>(mapper.listAdminOrders(normalizedStatus, normalizedQuery, size, (p - 1) * size).stream().map(this::adminOrder).toList(), total, p, size, (int) Math.ceil(total / (double) size));
  }

  public AdminOrderView adminOrder(String orderNo) {
    AdminBillingOrderRecord order = mapper.findAdminOrder(orderNo);
    if (order == null) throw bad("NOT_FOUND", "订单不存在");
    return adminOrder(order);
  }

  @Transactional
  public RefundView refundOrder(AdminPrincipal actor, String orderNo, String reason, String idempotencyKey) {
    String text = requiredReason(reason);
    if (idempotencyKey == null || !idempotencyKey.matches("[A-Za-z0-9:_-]{8,128}")) throw bad("REFUND_IDEMPOTENCY_INVALID", "退款幂等键无效");
    BillingOrderRecord order = mapper.findOrderByNo(orderNo);
    if (order == null) throw bad("NOT_FOUND", "订单不存在");
    RefundRecord replay = mapper.findRefundByKey(idempotencyKey);
    if (replay != null) {
      if (!order.id().equals(replay.orderId())) throw bad("REFUND_IDEMPOTENCY_CONFLICT", "退款幂等键已用于其他订单");
      return refund(replay, orderNo);
    }
    if (!"PAID".equals(order.status())) throw bad("REFUND_NOT_ALLOWED", "只有已支付订单可退款");
    RefundRecord refund = new RefundRecord(UUID.randomUUID().toString(), order.id(), null, order.amountMinor(), text,
        "REQUESTED", null, idempotencyKey, actor.id(), Instant.now(), null);
    if (mapper.insertRefund(refund) != 1) {
      RefundRecord concurrent = mapper.findRefundByKey(idempotencyKey);
      if (concurrent != null) return refund(concurrent, orderNo);
      throw bad("REFUND_CONFLICT", "退款创建失败");
    }
    if (!quota.revoke(order.userId(), order.creditAmount(), "REFUND", refund.id(), "支付订单退款扣回", initialQuota))
      throw bad("REFUND_NOT_ALLOWED", "订单额度已使用，不能自动全额退款");
    if (mapper.markRefundSucceeded(refund.id(), "mock-refund-" + refund.id()) != 1 || mapper.markOrderRefunded(orderNo) != 1)
      throw bad("REFUND_STATE_CONFLICT", "退款状态更新失败");
    RefundRecord completed = mapper.findRefundByKey(idempotencyKey);
    audit(actor.id(), "REFUND_ORDER", "ORDER", order.id(), order, completed, text);
    return refund(completed, orderNo);
  }

  public Page<AuditView> auditEvents(String subjectType, String subjectId, int page, int pageSize) {
    int p = range(page, 1, 1_000_000); int size = range(pageSize, 1, 100);
    String type = subjectType == null || subjectType.isBlank() ? null : bounded(subjectType.trim().toUpperCase(Locale.ROOT), 40);
    String id = subjectId == null || subjectId.isBlank() ? null : bounded(subjectId.trim(), 160);
    long total = mapper.countAudit(type, id);
    return new Page<>(mapper.listAudit(type, id, size, (p - 1) * size).stream().map(this::auditView).toList(), total, p, size, (int) Math.ceil(total / (double) size));
  }

  private BillingUserRecord requiredUser(String id) {
    if (id == null || id.isBlank()) throw bad("NOT_FOUND", "用户不存在");
    BillingUserRecord user = mapper.findUser(id);
    if (user == null || "DELETED".equals(user.status())) throw bad("NOT_FOUND", "用户不存在");
    return user;
  }
  private BillingUserRecord requiredActiveUser(String id) {
    BillingUserRecord user = requiredUser(id);
    if (!"ACTIVE".equals(user.status())) throw bad("ACCOUNT_DISABLED", "账号已被限制，暂不能创建订单");
    return user;
  }
  private Account account(BillingUserRecord user, QuotaAccountRecord value) { return new Account(user.id(), mask(user.phone()), user.status(), user.displayName(), value.total(), value.available(), value.reserved(), value.used(), user.createdAt(), user.lastLoginAt()); }
  private UserItem user(BillingUserRecord value) { return new UserItem(value.id(), mask(value.phone()), value.status(), value.displayName(), value.createdAt(), value.lastLoginAt(), value.disabledAt(), value.disabledReason()); }
  private LedgerItem ledger(BillingLedgerRecord value) { return new LedgerItem(value.id(), value.type(), value.amount(), value.balanceAfter(), value.sourceType(), value.sourceId(), value.taskId(), value.ruleId(), value.ruleVersion(), value.reasonCode(), value.createdAt()); }
  private PricingRuleView pricing(PricingRuleRecord value) { return new PricingRuleView(value.id(), value.code(), value.version(), value.operation(), value.modelPattern(), value.resolution(), value.minWidth(), value.maxWidth(), value.minHeight(), value.maxHeight(), value.unitCreditCost(), value.formula(), value.effectiveFrom(), value.effectiveTo(), value.status(), value.createdBy(), value.createdAt(), value.updatedAt()); }
  private ProductView product(CreditProductRecord value) { return new ProductView(value.id(), value.code(), value.name(), value.creditAmount(), value.amountMinor(), value.currency(), value.validityDays(), value.status(), value.sortOrder(), value.createdAt(), value.updatedAt()); }
  private OrderView order(BillingOrderRecord value) { return new OrderView(value.orderNo(), value.productCode(), value.productName(), value.quantity(), value.creditAmount(), value.amountMinor(), value.currency(), value.status(), value.provider(), value.expiresAt(), value.paidAt(), value.createdAt()); }
  private AdminOrderView adminOrder(AdminBillingOrderRecord value) { return new AdminOrderView(value.orderNo(), value.userId(), mask(value.phone()), value.productCode(), value.productName(), value.quantity(), value.creditAmount(), value.amountMinor(), value.currency(), value.status(), value.provider(), value.expiresAt(), value.paidAt(), value.createdAt()); }
  private RefundView refund(RefundRecord value, String orderNo) { return new RefundView(value.id(), orderNo, value.amountMinor(), value.reason(), value.status(), value.providerRefundId(), value.idempotencyKey(), value.createdAt(), value.completedAt()); }
  private AuditView auditView(BillingAuditRecord value) { return new AuditView(value.id(), value.actorId(), value.actorType(), value.action(), value.subjectType(), value.subjectId(), value.beforeJson(), value.afterJson(), value.reason(), value.createdAt()); }
  private void audit(String actorId, String action, String subjectType, String subjectId, Object before, Object after, String reason) { try { mapper.insertAudit(UUID.randomUUID().toString(), actorId, "ADMIN", action, subjectType, subjectId, json.writeValueAsString(before), json.writeValueAsString(after), reason); } catch (Exception e) { throw new IllegalStateException("billing audit serialization failed", e); } }
  private static String requiredReason(String value) { String result = value == null ? "" : value.trim(); if (result.isEmpty() || result.length() > 500) throw bad("REASON_REQUIRED", "请填写 1-500 字符的操作原因"); return result; }
  private static String optional(String value, String fallback) { return value == null || value.isBlank() ? fallback : value.trim(); }
  private static String optionalType(String value) { if (value == null || value.isBlank()) return null; String v = value.trim().toUpperCase(Locale.ROOT); if (!List.of("GRANT", "RESERVE", "CONSUME", "RELEASE").contains(v)) throw bad("LEDGER_TYPE_INVALID", "流水类型无效"); return v; }
  private static boolean overlaps(PricingRuleRecord left, PricingRuleRecord right) {
    Instant leftEnd = left.effectiveTo() == null ? Instant.MAX : left.effectiveTo();
    Instant rightEnd = right.effectiveTo() == null ? Instant.MAX : right.effectiveTo();
    return left.effectiveFrom().isBefore(rightEnd) && right.effectiveFrom().isBefore(leftEnd);
  }
  private static int range(int value, int min, int max) { if (value < min || value > max) throw bad("VALIDATION_ERROR", "分页参数无效"); return value; }
  private static String bounded(String value, int max) { String v = value.trim(); if (v.length() > max) throw bad("VALIDATION_ERROR", "搜索关键词过长"); return v; }
  private static String mask(String phone) { return phone == null || phone.length() < 7 ? "***" : phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4); }
  private static ApiException bad(String code, String message) { return new ApiException(HttpStatus.BAD_REQUEST, code, message); }
}
