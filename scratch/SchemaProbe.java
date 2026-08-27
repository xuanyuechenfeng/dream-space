import java.sql.*;

public final class SchemaProbe {
  public static void main(String[] args) throws Exception {
    String url = args.length > 0 ? args[0] : "jdbc:postgresql://localhost:5432/dream_space";
    String user = args.length > 1 ? args[1] : "dream_space";
    String password = args.length > 2 ? args[2] : "123456";
    try (Connection c = DriverManager.getConnection(url, user, password)) {
      System.out.println("DATABASE=" + c.getMetaData().getURL());
      dump(c, "SELECT current_database(), current_user, current_schema(), current_setting('search_path')", "SESSION");
      dump(c, "SELECT \"userId\",\"idempotencyKey\",COUNT(*) FROM \"GenerationTask\" GROUP BY \"userId\",\"idempotencyKey\" HAVING COUNT(*)>1", "GENERATION_TASK_DUPLICATES");
      dump(c, "SELECT version FROM schema_migrations ORDER BY version", "MIGRATION");
      dump(c, "SELECT n.nspname, t.typname, e.enumlabel FROM pg_type t JOIN pg_enum e ON e.enumtypid=t.oid JOIN pg_namespace n ON n.oid=t.typnamespace WHERE n.nspname=current_schema() ORDER BY t.typname,e.enumsortorder", "ENUM");
      dump(c, "SELECT table_schema,table_name,table_type FROM information_schema.tables WHERE table_schema NOT IN ('pg_catalog','information_schema') ORDER BY table_schema,table_name", "TABLE");
      dump(c, "SELECT table_name,column_name,udt_name,is_nullable,column_default FROM information_schema.columns WHERE table_schema=current_schema() ORDER BY table_name,ordinal_position", "COLUMN");
      dump(c, "SELECT conrelid::regclass::text AS table_name, conname, contype, pg_get_constraintdef(oid) FROM pg_constraint WHERE connamespace=current_schema()::regnamespace ORDER BY 1,2", "CONSTRAINT");
      dump(c, "SELECT tablename,indexname,indexdef FROM pg_indexes WHERE schemaname=current_schema() ORDER BY tablename,indexname", "INDEX");
      dump(c, "SELECT event_object_table,trigger_name,event_manipulation,action_statement FROM information_schema.triggers WHERE trigger_schema=current_schema() ORDER BY event_object_table,trigger_name", "TRIGGER");
    }
  }
  private static void dump(Connection c, String sql, String label) throws Exception {
    System.out.println("##" + label);
    try (Statement s = c.createStatement(); ResultSet r = s.executeQuery(sql)) {
      ResultSetMetaData m = r.getMetaData();
      while (r.next()) {
        StringBuilder b = new StringBuilder();
        for (int i = 1; i <= m.getColumnCount(); i++) {
          if (i > 1) b.append(" | ");
          b.append(m.getColumnLabel(i)).append('=').append(r.getString(i));
        }
        System.out.println(b);
      }
    } catch (SQLException e) {
      System.out.println("ERROR=" + e.getMessage());
    }
  }
}
