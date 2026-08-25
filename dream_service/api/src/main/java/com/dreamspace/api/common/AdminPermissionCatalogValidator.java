package com.dreamspace.api.common;

import com.dreamspace.api.persistence.admin.AdminMapper;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class AdminPermissionCatalogValidator implements SmartInitializingSingleton {
  private final AdminMapper mapper;

  public AdminPermissionCatalogValidator(AdminMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public void afterSingletonsInstantiated() {
    validate(mapper.listPermissionCodes());
  }

  void validate(Collection<String> databaseCodes) {
    Set<String> missing = new TreeSet<>(AdminPermissions.ALL);
    missing.removeAll(databaseCodes == null ? Set.of() : new HashSet<>(databaseCodes));
    if (!missing.isEmpty()) {
      throw new IllegalStateException("Missing registered admin permissions: " + missing);
    }
  }
}
