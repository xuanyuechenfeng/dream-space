package com.dreamspace.common.persistence.database;

import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** Explicit operations entry point for applying pending database migrations. */
public final class DatabaseMigrationCommand {
  private static final String DEFAULT_URL = "jdbc:postgresql://localhost:5432/dream_space";
  private static final String DEFAULT_USER = "dream_space";
  private static final String DEFAULT_PASSWORD = "123456";

  private DatabaseMigrationCommand() {}

  public static void main(String[] args) {
    migrate();
  }

  public static void migrate() {
    String url = firstNonBlank(
        System.getProperty("database.jdbc.url"),
        System.getenv("DATABASE_JDBC_URL"),
        System.getenv("DATABASE_URL"),
        DEFAULT_URL);
    String user = firstNonBlank(
        System.getProperty("database.user"),
        System.getenv("DATABASE_USER"),
        DEFAULT_USER);
    String password = firstNonBlank(
        System.getProperty("database.password"),
        System.getenv("DATABASE_PASSWORD"),
        DEFAULT_PASSWORD);

    var dataSource = new DriverManagerDataSource(url, user, password);
    new DatabaseMigrationService(dataSource).migrate();
    System.out.println("Database migrations applied successfully.");
  }

  static String firstNonBlank(String... values) {
    for (String value : values) {
      if (value != null && !value.isBlank()) return value;
    }
    throw new IllegalArgumentException("At least one database configuration value is required");
  }
}
