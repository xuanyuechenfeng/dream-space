package com.dreamspace.api.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.dreamspace.api.persistence.admin.AdminUserRecord;
import com.dreamspace.api.persistence.auth.UserRecord;
import java.time.Instant;
import java.util.Arrays;
import org.apache.ibatis.annotations.AutomapConstructor;
import org.junit.jupiter.api.Test;

class UserRecordConstructorMappingTest {
  @Test
  void marksOnlyTheCanonicalEmailAwareConstructorForMyBatisAutomapping() {
    var mappedConstructors = Arrays.stream(UserRecord.class.getDeclaredConstructors())
        .filter(constructor -> constructor.isAnnotationPresent(AutomapConstructor.class))
        .toList();

    assertThat(mappedConstructors).singleElement().satisfies(constructor -> {
      assertThat(constructor.getParameterTypes()).containsExactly(
          String.class, String.class, String.class, String.class, Instant.class, Instant.class);
    });
  }

  @Test
  void keepsTheLegacyFiveArgumentConstructorCompatible() {
    Instant now = Instant.parse("2026-08-26T00:00:00Z");

    UserRecord user = new UserRecord("user-1", "13800138000", "hash", now, now);

    assertThat(user.email()).isNull();
    assertThat(user.passwordHash()).isEqualTo("hash");
  }

  @Test
  void marksTheCanonicalAdminUserConstructorForMyBatisAutomapping() {
    var mappedConstructors = Arrays.stream(AdminUserRecord.class.getDeclaredConstructors())
        .filter(constructor -> constructor.isAnnotationPresent(AutomapConstructor.class))
        .toList();

    assertThat(mappedConstructors).singleElement().satisfies(constructor ->
        assertThat(constructor.getParameterCount()).isEqualTo(15));
  }
}
