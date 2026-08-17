package com.dreamspace.common.persistence.quota;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class QuotaAccountRecordTest {
  @Test void derivesUsedBalanceAndChecksInvariant() {
    var account = new QuotaAccountRecord("user-1", 100, 70, 20, null, null);
    assertThat(account.used()).isEqualTo(10);
    assertThat(account.invariantHolds()).isTrue();
    assertThat(new QuotaAccountRecord("user-1", 10, 8, 5, null, null).invariantHolds()).isFalse();
  }
}
