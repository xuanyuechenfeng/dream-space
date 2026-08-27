package com.dreamspace.common.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class MigrationResourceTest {
  @Test void includesTheCompletePrismaMigrationSequence() throws IOException {
    try (Stream<Path> files = Files.list(Path.of("src/main/resources/db/migration"))) {
      var names = files.map(path -> path.getFileName().toString()).sorted().toList();
      assertThat(names).hasSize(25).allMatch(name -> name.endsWith(".sql"));
      assertThat(names.get(0)).startsWith("20260803030753_");
      assertThat(names.get(names.size() - 1)).startsWith("20260826100000_");
      Path seed = Path.of("src/main/resources/db/migration/20260818100000_seed_inspirations.sql");
      try (Stream<String> lines = Files.lines(seed)) {
        assertThat(lines.filter(line -> line.startsWith("  (")).count()).isEqualTo(52);
      }
    }
  }
}
