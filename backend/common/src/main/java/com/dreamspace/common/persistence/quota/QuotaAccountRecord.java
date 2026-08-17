package com.dreamspace.common.persistence.quota;

import java.time.Instant;

public record QuotaAccountRecord(String userId, int total, int available, int reserved,
    Instant createdAt, Instant updatedAt) {
  public int used() { return total - available - reserved; }
  public boolean invariantHolds() { return total >= 0 && available >= 0 && reserved >= 0 && used() >= 0; }
}
