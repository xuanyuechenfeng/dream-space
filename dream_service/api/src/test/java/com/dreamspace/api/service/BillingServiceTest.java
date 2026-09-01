package com.dreamspace.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dreamspace.api.persistence.admin.BillingLedgerRecord;
import com.dreamspace.api.persistence.admin.BillingMapper;
import com.dreamspace.api.persistence.admin.BillingUserRecord;
import com.dreamspace.common.persistence.quota.QuotaTransactionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BillingServiceTest {
  private static final String USER_ID = "user-1";

  @Test
  void accountLedgerUsesFilteredProjectionAndFilteredCount() {
    BillingMapper mapper = mapperWithUser();
    when(mapper.countAccountLedger(USER_ID, "CONSUME")).thenReturn(21L);
    when(mapper.listAccountLedger(USER_ID, "CONSUME", 10, 10)).thenReturn(List.of(ledger("CONSUME")));

    var page = service(mapper).ledger(USER_ID, "consume", 2, 10);

    assertThat(page.total()).isEqualTo(21);
    assertThat(page.page()).isEqualTo(2);
    assertThat(page.pageSize()).isEqualTo(10);
    assertThat(page.pageCount()).isEqualTo(3);
    assertThat(page.items()).extracting(BillingService.LedgerItem::type).containsExactly("CONSUME");
    verify(mapper).countAccountLedger(USER_ID, "CONSUME");
    verify(mapper).listAccountLedger(USER_ID, "CONSUME", 10, 10);
    verify(mapper, never()).listUserLedger(USER_ID, "CONSUME", 10, 10);
  }

  @ParameterizedTest
  @ValueSource(strings = {"RESERVE", "RELEASE"})
  void adminLedgerUsesFullProjectionIncludingTechnicalEntries(String type) {
    BillingMapper mapper = mapperWithUser();
    when(mapper.countUserLedger(USER_ID, type)).thenReturn(1L);
    when(mapper.listUserLedger(USER_ID, type, 20, 0)).thenReturn(List.of(ledger(type)));

    var page = service(mapper).userLedger(USER_ID, type.toLowerCase(), 1, 20);

    assertThat(page.total()).isEqualTo(1);
    assertThat(page.pageCount()).isEqualTo(1);
    assertThat(page.items()).extracting(BillingService.LedgerItem::type).containsExactly(type);
    verify(mapper).countUserLedger(USER_ID, type);
    verify(mapper).listUserLedger(USER_ID, type, 20, 0);
    verify(mapper, never()).listAccountLedger(USER_ID, type, 20, 0);
  }

  @ParameterizedTest
  @ValueSource(strings = {"RESERVE", "RELEASE"})
  void accountLedgerReturnsAnEmptyPageForTechnicalTypeFilters(String type) {
    BillingMapper mapper = mapperWithUser();
    when(mapper.countAccountLedger(USER_ID, type)).thenReturn(0L);
    when(mapper.listAccountLedger(USER_ID, type, 20, 0)).thenReturn(List.of());

    var page = service(mapper).ledger(USER_ID, type.toLowerCase(), 1, 20);

    assertThat(page.items()).isEmpty();
    assertThat(page.total()).isZero();
    assertThat(page.pageCount()).isZero();
    verify(mapper).countAccountLedger(USER_ID, type);
    verify(mapper).listAccountLedger(USER_ID, type, 20, 0);
    verify(mapper, never()).listUserLedger(USER_ID, type, 20, 0);
  }

  private static BillingService service(BillingMapper mapper) {
    return new BillingService(mapper, mock(QuotaTransactionService.class), new ObjectMapper());
  }

  private static BillingMapper mapperWithUser() {
    BillingMapper mapper = mock(BillingMapper.class);
    when(mapper.findUser(USER_ID)).thenReturn(new BillingUserRecord(
        USER_ID, "13800000000", "ACTIVE", "User", Instant.EPOCH, null, null, null));
    return mapper;
  }

  private static BillingLedgerRecord ledger(String type) {
    return new BillingLedgerRecord("ledger-1", USER_ID, "task-1", type, 1, 9,
        "GENERATION", "task-1", null, null, null, Instant.EPOCH);
  }
}
