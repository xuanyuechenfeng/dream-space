import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

public final class ApplyMigration {
  public static void main(String[] args) throws Exception {
    Path migration = Paths.get(args[0]);
    String version = migration.getFileName().toString();
    String url = args.length > 1 ? args[1] : "jdbc:postgresql://localhost:5432/dream_space";
    String user = args.length > 2 ? args[2] : "dream_space";
    String password = args.length > 3 ? args[3] : "123456";
    try (Connection c = DriverManager.getConnection(
        url, user, password)) {
      c.setAutoCommit(false);
      try (PreparedStatement check = c.prepareStatement("SELECT 1 FROM schema_migrations WHERE version=?")) {
        check.setString(1, version);
        try (ResultSet result = check.executeQuery()) {
          if (result.next()) {
            System.out.println("Already applied: " + version);
            c.rollback();
            return;
          }
        }
      }
      try {
        try (Statement statement = c.createStatement()) {
          statement.execute(new String(Files.readAllBytes(migration), StandardCharsets.UTF_8));
        }
        try (PreparedStatement record = c.prepareStatement("INSERT INTO schema_migrations(version) VALUES (?)")) {
          record.setString(1, version);
          record.executeUpdate();
        }
        c.commit();
        System.out.println("Applied: " + version);
      } catch (Exception e) {
        c.rollback();
        throw e;
      }
    }
  }
}
