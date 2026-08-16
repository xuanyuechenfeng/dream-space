package com.dreamspace.persistence.quota;

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
  public boolean reserve(String userId, String taskId, int amount, String idempotencyKey) {
    requirePositive(amount);
    accounts.lockAccount(userId);
    if (ledger.countByIdempotencyKey(idempotencyKey) > 0) return true;
    if (accounts.reserve(userId, amount) != 1) return false;
    QuotaAccountRecord account = accounts.lockAccount(userId);
    ledger.insert(UUID.randomUUID().toString(), userId, taskId, "RESERVE", amount, account.available(), idempotencyKey);
    return true;
  }

  @Transactional
  public boolean settle(String userId, String taskId, int amount, String type, String idempotencyKey) {
    requirePositive(amount);
    if (!"CONSUME".equals(type) && !"RELEASE".equals(type)) throw new IllegalArgumentException("invalid settlement type");
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
