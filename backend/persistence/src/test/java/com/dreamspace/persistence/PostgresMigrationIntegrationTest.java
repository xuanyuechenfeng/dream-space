package com.dreamspace.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

class PostgresMigrationIntegrationTest {
  @Test
  void migrationContractHasAnOptInDockerGate() throws Exception {
    Assumptions.assumeTrue(dockerAvailable(), "Docker is required for PostgreSQL integration tests");
    try (var postgres = new PostgreSQLContainer<>("postgres:17-alpine")) {
      postgres.start();
      assertThat(postgres.isRunning()).isTrue();
      var dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
      new DatabaseMigrationService(dataSource).migrate();
      try (var connection = dataSource.getConnection();
          var statement = connection.prepareStatement("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_type = 'BASE TABLE' AND table_name <> 'schema_migrations'");
          var result = statement.executeQuery()) {
        assertThat(result.next()).isTrue();
        assertThat(result.getInt(1)).isEqualTo(18);
      }
    }
  }

  private static boolean dockerAvailable() throws IOException, InterruptedException {
    try {
      Process process = new ProcessBuilder("docker", "version", "--format", "{{.Server.Version}}")
          .redirectErrorStream(true).start();
      return process.waitFor(3, TimeUnit.SECONDS) && process.exitValue() == 0;
    } catch (IOException unavailable) {
      return false;
    }
  }
}
