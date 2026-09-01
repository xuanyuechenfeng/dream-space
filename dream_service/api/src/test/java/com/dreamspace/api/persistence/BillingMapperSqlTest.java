package com.dreamspace.api.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.dreamspace.api.persistence.admin.BillingMapper;
import java.util.Arrays;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

class BillingMapperSqlTest {
  @Test
  void ledgerQueriesCastNullableTypeBeforeCheckingForNull() {
    for (String methodName : new String[] {"listAccountLedger", "countAccountLedger", "listUserLedger", "countUserLedger"}) {
      String sql = sql(methodName);

      assertThat(sql).contains("CAST(#{type} AS \"QuotaLedgerType\") IS NULL");
      assertThat(sql).doesNotContain("#{type} IS NULL");
    }
  }

  @Test
  void accountLedgerQueriesExcludeTechnicalEntriesBeforePagination() {
    String listSql = sql("listAccountLedger");
    String countSql = sql("countAccountLedger");

    for (String sql : new String[] {listSql, countSql}) {
      assertThat(sql)
          .contains("'RESERVE'::\"QuotaLedgerType\"")
          .contains("'RELEASE'::\"QuotaLedgerType\"");
    }
    assertThat(listSql.indexOf(BillingMapper.ACCOUNT_LEDGER_VISIBILITY_FILTER))
        .isLessThan(listSql.indexOf("LIMIT #{limit}"));
  }

  @Test
  void adminLedgerQueriesRetainTechnicalEntries() {
    assertThat(sql("listUserLedger")).doesNotContain(BillingMapper.ACCOUNT_LEDGER_VISIBILITY_FILTER);
    assertThat(sql("countUserLedger")).doesNotContain(BillingMapper.ACCOUNT_LEDGER_VISIBILITY_FILTER);
  }

  private static String sql(String methodName) {
    var method = Arrays.stream(BillingMapper.class.getDeclaredMethods())
        .filter(candidate -> candidate.getName().equals(methodName))
        .findFirst()
        .orElseThrow();
    return String.join(" ", method.getAnnotation(Select.class).value());
  }
}
