package com.dreamspace.common.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.dreamspace.common.persistence.database.DatabaseMigrationService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

class PostgresMigrationIntegrationTest {
  @Test
  void migrationContractHasAnOptInDockerGate() throws Exception {
    DockerTestSupport.requireDocker();
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
}
