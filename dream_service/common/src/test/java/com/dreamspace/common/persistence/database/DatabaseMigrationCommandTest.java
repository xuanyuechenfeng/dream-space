package com.dreamspace.common.persistence.database;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DatabaseMigrationCommandTest {
  @Test
  void selectsTheFirstNonBlankConfigurationValue() {
    assertThat(DatabaseMigrationCommand.firstNonBlank(null, " ", "configured", "fallback"))
        .isEqualTo("configured");
  }
}
