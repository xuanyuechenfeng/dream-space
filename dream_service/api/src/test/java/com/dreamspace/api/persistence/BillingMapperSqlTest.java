package com.dreamspace.api.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.dreamspace.api.persistence.admin.BillingMapper;
import java.util.Arrays;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

class BillingMapperSqlTest {
  @Test
  void ledgerQueriesCastNullableTypeBeforeCheckingForNull() {
    for (String methodName : new String[] {"listLedger", "countLedger", "countUserLedger"}) {
      var method = Arrays.stream(BillingMapper.class.getDeclaredMethods())
          .filter(candidate -> candidate.getName().equals(methodName))
          .findFirst()
          .orElseThrow();
      String sql = String.join(" ", method.getAnnotation(Select.class).value());

      assertThat(sql).contains("CAST(#{type} AS \"QuotaLedgerType\") IS NULL");
      assertThat(sql).doesNotContain("#{type} IS NULL");
    }
  }
}
