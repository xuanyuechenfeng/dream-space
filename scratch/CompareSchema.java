import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public final class CompareSchema {
  private static final String ACTUAL = "dream_space";
  private static final String BASELINE = "codex_schema_baseline_20260826";

  public static void main(String[] args) throws Exception {
    Path directory = Paths.get(args[0]);
    try (Connection c = DriverManager.getConnection(
        "jdbc:postgresql://localhost:5432/dream_space", "dream_space", "123456")) {
      c.setAutoCommit(false);
      try {
        try (Statement statement = c.createStatement()) {
          statement.execute("CREATE SCHEMA \"" + BASELINE + "\"");
          statement.execute("SET LOCAL search_path TO \"" + BASELINE + "\"");
        }
        for (Path migration : migrations(directory)) {
          try (Statement statement = c.createStatement()) {
            statement.execute(new String(Files.readAllBytes(migration), StandardCharsets.UTF_8));
          }
        }
        ensureSchemaScopedConstraints(c);
        compare(c, "ENUM", enumQuery(), ACTUAL, BASELINE);
        compare(c, "TABLE", tableQuery(), ACTUAL, BASELINE);
        compare(c, "COLUMN", columnQuery(), ACTUAL, BASELINE);
        compare(c, "CONSTRAINT", constraintQuery(), ACTUAL, BASELINE);
        compare(c, "INDEX", indexQuery(), ACTUAL, BASELINE);
        compare(c, "SEQUENCE", sequenceQuery(), ACTUAL, BASELINE);
        compare(c, "TRIGGER", triggerQuery(), ACTUAL, BASELINE);
      } finally {
        c.rollback();
      }
    }
  }

  private static void ensureSchemaScopedConstraints(Connection c) throws Exception {
    String[][] constraints = {
        {"AdminUser", "AdminUser_status_check", "CHECK (\"status\" IN ('INVITED', 'ACTIVE', 'DISABLED'))"},
        {"AdminUser", "AdminUser_status_active_check", "CHECK ((\"status\" = 'ACTIVE' AND \"active\" = TRUE) OR (\"status\" IN ('INVITED', 'DISABLED') AND \"active\" = FALSE))"},
        {"AdminUser", "AdminUser_version_check", "CHECK (\"version\" > 0)"},
        {"AdminOperationIdempotency", "AdminOperationIdempotency_scope_key_check", "CHECK (length(trim(\"scope\")) > 0 AND length(trim(\"idempotencyKey\")) > 0)"}
    };
    for (String[] definition : constraints) {
      try (PreparedStatement check = c.prepareStatement(
          "SELECT 1 FROM pg_constraint x JOIN pg_namespace n ON n.oid=x.connamespace "
              + "WHERE n.nspname=? AND x.conname=?")) {
        check.setString(1, BASELINE);
        check.setString(2, definition[1]);
        try (ResultSet result = check.executeQuery()) {
          if (!result.next()) {
            try (Statement add = c.createStatement()) {
              add.execute("ALTER TABLE \"" + BASELINE + "\".\"" + definition[0]
                  + "\" ADD CONSTRAINT \"" + definition[1] + "\" " + definition[2]);
            }
          }
        }
      }
    }
  }

  private static List<Path> migrations(Path directory) throws Exception {
    List<Path> result = new ArrayList<Path>();
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.sql")) {
      for (Path path : stream) result.add(path);
    }
    Collections.sort(result);
    return result;
  }

  private static void compare(Connection c, String label, String sql, String actual, String expected)
      throws Exception {
    Set<String> actualValues = values(c, sql, actual);
    Set<String> expectedValues = values(c, sql, expected);
    Set<String> missing = new TreeSet<String>(expectedValues);
    missing.removeAll(actualValues);
    Set<String> extra = new TreeSet<String>(actualValues);
    extra.removeAll(expectedValues);
    System.out.println(label + " actual=" + actualValues.size() + " expected=" + expectedValues.size()
        + " missing=" + missing.size() + " extra=" + extra.size());
    for (String value : missing) System.out.println("MISSING " + label + " " + value);
    for (String value : extra) System.out.println("EXTRA " + label + " " + value);
  }

  private static Set<String> values(Connection c, String sql, String schema) throws Exception {
    Set<String> result = new TreeSet<String>();
    try (PreparedStatement statement = c.prepareStatement(sql)) {
      statement.setString(1, schema);
      try (ResultSet rows = statement.executeQuery()) {
        ResultSetMetaData metadata = rows.getMetaData();
        while (rows.next()) {
          StringBuilder value = new StringBuilder();
          for (int i = 1; i <= metadata.getColumnCount(); i++) {
            if (i > 1) value.append('|');
            String part = rows.getString(i);
            if (part != null) {
              part = part.replace("\"" + schema + "\".", "").replace(schema + ".", "");
            }
            value.append(part);
          }
          result.add(value.toString());
        }
      }
    }
    return result;
  }

  private static String enumQuery() {
    return "SELECT t.typname,e.enumlabel,e.enumsortorder FROM pg_type t "
        + "JOIN pg_enum e ON e.enumtypid=t.oid JOIN pg_namespace n ON n.oid=t.typnamespace "
        + "WHERE n.nspname=? ORDER BY t.typname,e.enumsortorder";
  }

  private static String tableQuery() {
    return "SELECT table_name,table_type FROM information_schema.tables "
        + "WHERE table_schema=? AND table_name<>'schema_migrations' ORDER BY table_name";
  }

  private static String columnQuery() {
    return "SELECT table_name,column_name,ordinal_position,data_type,udt_name,is_nullable,column_default,"
        + "character_maximum_length,numeric_precision,numeric_scale,datetime_precision "
        + "FROM information_schema.columns WHERE table_schema=? AND table_name<>'schema_migrations' "
        + "ORDER BY table_name,ordinal_position";
  }

  private static String constraintQuery() {
    return "SELECT c.conrelid::regclass::text,c.conname,c.contype,pg_get_constraintdef(c.oid) "
        + "FROM pg_constraint c JOIN pg_namespace n ON n.oid=c.connamespace "
        + "WHERE n.nspname=? AND c.conrelid::regclass::text NOT LIKE '%schema_migrations' ORDER BY 1,2";
  }

  private static String indexQuery() {
    return "SELECT t.relname,i.relname,pg_get_indexdef(i.oid) FROM pg_index x "
        + "JOIN pg_class i ON i.oid=x.indexrelid JOIN pg_class t ON t.oid=x.indrelid "
        + "JOIN pg_namespace n ON n.oid=t.relnamespace "
        + "WHERE n.nspname=? AND t.relname<>'schema_migrations' ORDER BY t.relname,i.relname";
  }

  private static String sequenceQuery() {
    return "SELECT sequence_name,data_type,start_value,minimum_value,maximum_value,increment,cycle_option "
        + "FROM information_schema.sequences WHERE sequence_schema=? ORDER BY sequence_name";
  }

  private static String triggerQuery() {
    return "SELECT event_object_table,trigger_name,event_manipulation,action_timing,action_orientation,action_statement "
        + "FROM information_schema.triggers WHERE trigger_schema=? ORDER BY event_object_table,trigger_name,event_manipulation";
  }
}
