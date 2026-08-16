package com.dreamspace.persistence.database;

import static org.assertj.core.api.Assertions.assertThat;

import com.dreamspace.persistence.database.DatabaseEnums.GenerationRatio;
import com.dreamspace.persistence.database.DatabaseEnums.GenerationResolution;
import com.dreamspace.persistence.database.DatabaseEnums.QuotaReconciliationFindingKind;
import org.junit.jupiter.api.Test;

class DatabaseEnumsTest {
  @Test void preservesSpecialPrismaMappings() {
    assertThat(GenerationRatio.SMART.databaseValue()).isEqualTo("smart");
    assertThat(GenerationRatio.RATIO_21_9.databaseValue()).isEqualTo("21:9");
    assertThat(GenerationResolution.K2.databaseValue()).isEqualTo("2K");
    assertThat(GenerationResolution.K4.databaseValue()).isEqualTo("4K");
  }

  @Test void includesAllReconciliationFindingKinds() {
    assertThat(QuotaReconciliationFindingKind.values()).extracting(DatabaseValue::databaseValue)
        .containsExactly("MISSING_RESERVE", "MISSING_RELEASE", "MISSING_CONSUME",
            "SETTLEMENT_AMOUNT_MISMATCH", "TOTAL_DRIFT", "RESERVED_DRIFT", "AVAILABLE_DRIFT");
  }
}
