package com.dreamspace.api.common;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.dreamspace.api.persistence.admin.AdminMapper;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class AdminPermissionCatalogValidatorTest {
  private final AdminPermissionCatalogValidator validator =
      new AdminPermissionCatalogValidator(mock(AdminMapper.class));

  @Test
  void acceptsCompleteCatalogAndIgnoresForwardCompatibleExtraCodes() {
    var codes = new ArrayList<>(AdminPermissions.ALL);
    codes.add("future:read");

    assertThatCode(() -> validator.validate(codes)).doesNotThrowAnyException();
  }

  @Test
  void rejectsCatalogMissingAnApplicationPermission() {
    var codes = new ArrayList<>(AdminPermissions.ALL);
    codes.remove(AdminPermissions.TASKS_WRITE);

    assertThatThrownBy(() -> validator.validate(codes))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(AdminPermissions.TASKS_WRITE);
  }
}
