package com.dreamspace.common.persistence.quota;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuotaTransactionService {
  private final QuotaMapper accounts;
  private final QuotaLedgerMapper ledger;

  public QuotaTransactionService(QuotaMapper accounts, QuotaLedgerMapper ledger) {
    this.accounts = accounts; this.ledger = ledger;
  }

  @Transactional
  public QuotaAccountRecord ensureAndRead(String userId, int initialTotal) {
    accounts.ensureAccount(userId, initialTotal);
    ledger.insertInitialGrant(UUID.randomUUID().toString(), userId, "initial-grant:" + userId);
    QuotaAccountRecord account = accounts.findAccount(userId);
    if (account == null) throw new IllegalStateException("quota account was not created");
    if (!account.invariantHolds()) throw new IllegalStateException("quota account invariant violated");
    return account;
  }

  @Transactional
  public boolean grant(String userId, int amount, String sourceType, String sourceId, String reason) {
    return grant(userId, amount, sourceType, sourceId, reason, 100);
  }

  @Transactional
  public boolean grant(String userId, int amount, String sourceType, String sourceId, String reason, int initialTotal) {
    requirePositive(amount);
    accounts.ensureAccount(userId, initialTotal);
    accounts.lockAccount(userId);
    String key = "grant:" + sourceType.toLowerCase(java.util.Locale.ROOT) + ":" + sourceId;
    if (ledger.countByIdempotencyKey(key) > 0) return true;
    if (accounts.grant(userId, amount) != 1) return false;
    QuotaAccountRecord account = accounts.lockAccount(userId);
    ledger.insertWithSource(UUID.randomUUID().toString(), userId, null, "GRANT", amount, account.available(), key,
        sourceType, sourceId, reason);
    return true;
  }

  @Transactional
  public boolean revoke(String userId, int amount, String sourceType, String sourceId, String reason) {
    return revoke(userId, amount, sourceType, sourceId, reason, 100);
  }

  @Transactional
  public boolean revoke(String userId, int amount, String sourceType, String sourceId, String reason, int initialTotal) {
    requirePositive(amount);
    accounts.ensureAccount(userId, initialTotal);
    accounts.lockAccount(userId);
    String key = "revoke:" + sourceType.toLowerCase(java.util.Locale.ROOT) + ":" + sourceId;
    if (ledger.countByIdempotencyKey(key) > 0) return true;
    if (accounts.revoke(userId, amount) != 1) return false;
    QuotaAccountRecord account = accounts.lockAccount(userId);
    ledger.insertWithSource(UUID.randomUUID().toString(), userId, null, "CONSUME", amount,
        account.available(), key, sourceType, sourceId, reason);
    return true;
  }

  @Transactional
  public boolean reserve(String userId, String taskId, int amount, String idempotencyKey, int initialTotal) {
    return reserve(userId, taskId, amount, idempotencyKey, initialTotal, null, null);
  }

  @Transactional
  public boolean reserve(String userId, String taskId, int amount, String idempotencyKey, int initialTotal,
      String ruleId, Integer ruleVersion) {
    requirePositive(amount);
    accounts.ensureAccount(userId, initialTotal);
    accounts.lockAccount(userId);
    if (ledger.countByIdempotencyKey(idempotencyKey) > 0) return true;
    if (accounts.reserve(userId, amount) != 1) return false;
    QuotaAccountRecord account = accounts.lockAccount(userId);
    if (ruleId == null) ledger.insert(UUID.randomUUID().toString(), userId, taskId, "RESERVE", amount, account.available(), idempotencyKey);
    else ledger.insertGenerationReserve(UUID.randomUUID().toString(), userId, taskId, amount, account.available(), idempotencyKey, ruleId, ruleVersion);
    return true;
  }

  @Transactional
  public boolean settle(String userId, String taskId, int amount, String type, String idempotencyKey) {
    requirePositive(amount);
    if (!"CONSUME".equals(type) && !"RELEASE".equals(type)) throw new IllegalArgumentException("invalid settlement type");
    accounts.ensureAccount(userId, 100);
    accounts.lockAccount(userId);
    if (ledger.countByIdempotencyKey(idempotencyKey) > 0) return true;
    int changed = "CONSUME".equals(type) ? accounts.consume(userId, amount) : accounts.release(userId, amount);
    if (changed != 1) return false;
    QuotaAccountRecord account = accounts.lockAccount(userId);
    ledger.insert(UUID.randomUUID().toString(), userId, taskId, type, amount, account.available(), idempotencyKey);
    return true;
  }

  private static void requirePositive(int amount) { if (amount <= 0) throw new IllegalArgumentException("amount must be positive"); }
}
