package com.dreamspace.common.persistence.database;

import java.sql.Connection;
import java.sql.ResultSet;
import java.util.Arrays;
import javax.sql.DataSource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

@Service
public class DatabaseMigrationService {
  private final DataSource dataSource;
  public DatabaseMigrationService(DataSource dataSource) { this.dataSource = dataSource; }

  /** Invoked explicitly by deployment tooling; application startup never calls this method. */
  public void migrate() {
    try (Connection connection = dataSource.getConnection()) {
      connection.setAutoCommit(false);
      try {
        connection.createStatement().execute("CREATE TABLE IF NOT EXISTS schema_migrations (version TEXT PRIMARY KEY, applied_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        connection.commit();
        Resource[] resources = new PathMatchingResourcePatternResolver().getResources("classpath*:db/migration/*.sql");
        Arrays.sort(resources, java.util.Comparator.comparing(Resource::getFilename));
        for (Resource resource : resources) {
          String version = resource.getFilename();
          if (!applied(connection, version)) {
            try {
              // PostgreSQL must parse the complete script so PL/pgSQL dollar-quoted
              // function bodies are not split at their internal semicolons.
              try (var scriptStatement = connection.createStatement()) {
                scriptStatement.execute(resource.getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
              }
              try (var statement = connection.prepareStatement("INSERT INTO schema_migrations(version) VALUES (?)")) {
                statement.setString(1, version); statement.executeUpdate();
              }
              connection.commit();
            } catch (Exception e) {
              connection.rollback();
              throw e;
            }
          }
        }
      } catch (Exception e) { connection.rollback(); throw e; }
    } catch (Exception e) { throw new IllegalStateException("database migration failed", e); }
  }

  private boolean applied(Connection connection, String version) throws Exception {
    try (var statement = connection.prepareStatement("SELECT 1 FROM schema_migrations WHERE version = ?")) {
      statement.setString(1, version);
      try (ResultSet result = statement.executeQuery()) { return result.next(); }
    }
  }
}
