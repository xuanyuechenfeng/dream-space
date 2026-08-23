package com.dreamspace.api.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PasswordHashingTest {
  @Test
  void encodesAndMatchesWithoutStoringPlaintext() {
    String encoded = PasswordHashing.encode("correct-horse-battery");

    assertThat(encoded).startsWith("pbkdf2-sha256$210000$")
        .doesNotContain("correct-horse-battery");
    assertThat(PasswordHashing.matches("correct-horse-battery", encoded)).isTrue();
    assertThat(PasswordHashing.matches("wrong-password", encoded)).isFalse();
  }

  @Test
  void missingHashUsesDummyVerificationAndFails() {
    assertThat(PasswordHashing.matches("any-password", null)).isFalse();
    assertThat(PasswordHashing.matches("any-password", "")).isFalse();
  }
}
