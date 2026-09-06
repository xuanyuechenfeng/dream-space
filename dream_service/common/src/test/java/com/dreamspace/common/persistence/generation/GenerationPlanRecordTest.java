package com.dreamspace.common.persistence.generation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.apache.ibatis.annotations.AutomapConstructor;
import org.junit.jupiter.api.Test;

class GenerationPlanRecordTest {
  @Test
  void marksTheFullPlanConstructorForMyBatisAutomapping() {
    var mappedConstructors = Arrays.stream(GenerationPlanRecord.class.getDeclaredConstructors())
        .filter(constructor -> constructor.isAnnotationPresent(AutomapConstructor.class))
        .toList();

    assertThat(mappedConstructors).singleElement().satisfies(constructor ->
        assertThat(constructor.getParameterCount()).isEqualTo(13));
  }
}
