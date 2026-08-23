package com.dreamspace.api.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.dreamspace.api.persistence.admin.AdminApplicationMapper;
import com.dreamspace.api.persistence.inspiration.InspirationMapper;
import java.lang.reflect.Modifier;
import java.util.List;
import org.apache.ibatis.annotations.SelectProvider;
import org.junit.jupiter.api.Test;

class SqlProviderAccessibilityTest {
  @Test
  void allSelectProvidersArePublicAndInvocableByMyBatis() {
    for (Class<?> mapper : List.of(InspirationMapper.class, AdminApplicationMapper.class)) {
      for (var method : mapper.getDeclaredMethods()) {
        SelectProvider annotation = method.getAnnotation(SelectProvider.class);
        if (annotation == null) continue;
        Class<?> provider = annotation.type() == void.class ? annotation.value() : annotation.type();

        assertThat(Modifier.isPublic(provider.getModifiers()))
            .as("provider class for %s#%s", mapper.getName(), method.getName())
            .isTrue();
        assertThat(List.of(provider.getMethods()).stream()
            .filter(candidate -> candidate.getName().equals(annotation.method()))
            .anyMatch(candidate -> Modifier.isPublic(candidate.getModifiers())
                && Modifier.isStatic(candidate.getModifiers())))
            .as("public static provider method for %s#%s", mapper.getName(), method.getName())
            .isTrue();
      }
    }
  }
}
