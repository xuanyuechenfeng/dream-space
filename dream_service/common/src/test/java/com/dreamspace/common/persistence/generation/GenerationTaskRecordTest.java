package com.dreamspace.common.persistence.generation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.apache.ibatis.annotations.AutomapConstructor;
import org.junit.jupiter.api.Test;

class GenerationTaskRecordTest {
  @Test
  void marksTheFullTaskConstructorForMyBatisAutomapping() {
    var mappedConstructors = Arrays.stream(GenerationTaskRecord.class.getDeclaredConstructors())
        .filter(constructor -> constructor.isAnnotationPresent(AutomapConstructor.class))
        .toList();

    assertThat(mappedConstructors).singleElement().satisfies(constructor ->
        assertThat(constructor.getParameterCount()).isEqualTo(32));
  }
}
