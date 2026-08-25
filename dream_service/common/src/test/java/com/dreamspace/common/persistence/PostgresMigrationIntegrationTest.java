package com.dreamspace.common.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.dreamspace.common.persistence.database.DatabaseMigrationService;
import org.springframework.core.io.ClassPathResource;
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
      try (var connection = dataSource.getConnection()) {
        connection.createStatement().executeUpdate("""
            INSERT INTO "AdminUser" ("id", "phone", "displayName", "role", "active", "updatedAt")
            VALUES
              ('legacy-operator', '18812340000', 'Legacy Operator', 'OPERATOR', true, CURRENT_TIMESTAMP),
              ('legacy-viewer', '18812340001', 'Legacy Viewer', 'VIEWER', true, CURRENT_TIMESTAMP)
            """);
        try (var statement = connection.createStatement()) {
          statement.execute(new ClassPathResource("db/migration/20260825090000_add_admin_rbac.sql")
              .getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        }
        connection.createStatement().executeUpdate("""
            INSERT INTO "AdminSession" ("id", "tokenHash", "adminUserId", "expiresAt", "createdAt", "lastSeenAt")
            VALUES
              ('operator-session', 'operator-token-hash', 'legacy-operator', CURRENT_TIMESTAMP + INTERVAL '1 hour', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
              ('viewer-session', 'viewer-token-hash', 'legacy-viewer', CURRENT_TIMESTAMP + INTERVAL '1 hour', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """);
      }
      try (var connection = dataSource.getConnection();
          var statement = connection.prepareStatement(
              "SELECT "
                  + "(SELECT COUNT(*) FROM information_schema.tables "
                  + "WHERE table_schema = 'public' AND table_type = 'BASE TABLE' "
                  + "AND table_name <> 'schema_migrations'), "
                  + "(SELECT COUNT(*) FROM schema_migrations), "
                  + "(SELECT COUNT(*) FROM information_schema.columns "
                  + "WHERE table_schema = 'public' AND table_name = 'User' "
                  + "AND column_name = 'status'), "
                  + "(SELECT COUNT(*) FROM \"AdminRoleDefinition\"), "
                  + "(SELECT COUNT(*) FROM \"AdminPermissionDefinition\"), "
                  + "(SELECT COUNT(*) FROM \"AdminUserRole\" user_role "
                  + "JOIN \"AdminRoleDefinition\" role_definition "
                  + "ON role_definition.\"id\" = user_role.\"roleId\" "
                  + "WHERE user_role.\"adminUserId\" = 'legacy-operator' "
                  + "AND role_definition.\"code\" = 'OPERATOR'), "
                  + "(SELECT COUNT(*) FROM \"AdminPermissionDefinition\" permission_definition "
                  + "JOIN \"AdminRolePermission\" role_permission "
                  + "ON role_permission.\"permissionId\" = permission_definition.\"id\" "
                  + "JOIN \"AdminRoleDefinition\" role_definition "
                  + "ON role_definition.\"id\" = role_permission.\"roleId\" "
                  + "WHERE role_definition.\"code\" = 'OPERATOR' "
                  + "AND permission_definition.\"code\" = 'tasks:write'), "
                  + "(SELECT COUNT(*) FROM \"AdminRolePermission\" role_permission "
                  + "WHERE role_permission.\"roleId\" = 'admin-role-viewer'), "
                  + "(SELECT COUNT(*) FROM \"AdminRolePermission\" role_permission "
                  + "WHERE role_permission.\"roleId\" = 'admin-role-operator'), "
                  + "(SELECT COUNT(*) FROM \"AdminRolePermission\" role_permission "
                  + "WHERE role_permission.\"roleId\" = 'admin-role-admin'), "
                  + "(SELECT string_agg(permission_definition.\"code\", ',' "
                  + "ORDER BY permission_definition.\"code\") "
                  + "FROM \"AdminRolePermission\" role_permission "
                  + "JOIN \"AdminPermissionDefinition\" permission_definition "
                  + "ON permission_definition.\"id\" = role_permission.\"permissionId\" "
                  + "WHERE role_permission.\"roleId\" = 'admin-role-viewer'), "
                  + "(SELECT string_agg(permission_definition.\"code\", ',' "
                  + "ORDER BY permission_definition.\"code\") "
                  + "FROM \"AdminRolePermission\" role_permission "
                  + "JOIN \"AdminPermissionDefinition\" permission_definition "
                  + "ON permission_definition.\"id\" = role_permission.\"permissionId\" "
                  + "WHERE role_permission.\"roleId\" = 'admin-role-operator'), "
                  + "(SELECT string_agg(permission_definition.\"code\", ',' "
                  + "ORDER BY permission_definition.\"code\") "
                  + "FROM \"AdminRolePermission\" role_permission "
                  + "JOIN \"AdminPermissionDefinition\" permission_definition "
                  + "ON permission_definition.\"id\" = role_permission.\"permissionId\" "
                  + "WHERE role_permission.\"roleId\" = 'admin-role-admin')");
          var result = statement.executeQuery()) {
        assertThat(result.next()).isTrue();
        assertThat(result.getInt(1)).isEqualTo(34);
        assertThat(result.getInt(2)).isEqualTo(23);
        assertThat(result.getInt(3)).isEqualTo(1);
        assertThat(result.getInt(4)).isEqualTo(3);
        assertThat(result.getInt(5)).isEqualTo(11);
        assertThat(result.getInt(6)).isEqualTo(1);
        assertThat(result.getInt(7)).isEqualTo(1);
        assertThat(result.getInt(8)).isEqualTo(5);
        assertThat(result.getInt(9)).isEqualTo(9);
        assertThat(result.getInt(10)).isEqualTo(11);
        assertThat(result.getString(11)).isEqualTo(
            "billing:read,inspirations:read,pricing:read,tasks:read,users:read");
        assertThat(result.getString(12)).isEqualTo(
            "billing:read,billing:write,inspirations:read,inspirations:write,pricing:read,"
                + "tasks:read,tasks:write,users:read,users:write");
        assertThat(result.getString(13)).isEqualTo(
            "audit:read,billing:read,billing:write,inspirations:read,inspirations:write,"
                + "pricing:read,pricing:write,tasks:read,tasks:write,users:read,users:write");
      }
      try (var connection = dataSource.getConnection()) {
        long operatorRevisionBefore;
        long viewerRevisionBefore;
        try (var revision = connection.createStatement().executeQuery(
            "SELECT \"id\", \"permissionRevision\" FROM \"AdminUser\" "
                + "WHERE \"id\" IN ('legacy-operator', 'legacy-viewer') ORDER BY \"id\"")) {
          assertThat(revision.next()).isTrue();
          assertThat(revision.getString(1)).isEqualTo("legacy-operator");
          operatorRevisionBefore = revision.getLong(2);
          assertThat(revision.next()).isTrue();
          assertThat(revision.getString(1)).isEqualTo("legacy-viewer");
          viewerRevisionBefore = revision.getLong(2);
        }
        connection.createStatement().executeUpdate("""
            UPDATE "AdminRolePermission"
            SET "roleId" = 'admin-role-viewer'
            WHERE "roleId" = 'admin-role-operator'
              AND "permissionId" = 'admin-permission-tasks-write'
            """);
        try (var result = connection.createStatement().executeQuery("""
            SELECT
              (SELECT "permissionRevision" FROM "AdminUser" WHERE "id" = 'legacy-operator'),
              (SELECT "permissionRevision" FROM "AdminUser" WHERE "id" = 'legacy-viewer'),
              (SELECT COUNT(*) FROM "AdminSession" WHERE "adminUserId" IN ('legacy-operator', 'legacy-viewer'))
            """)) {
          assertThat(result.next()).isTrue();
          assertThat(result.getLong(1)).isEqualTo(operatorRevisionBefore + 1);
          assertThat(result.getLong(2)).isEqualTo(viewerRevisionBefore + 1);
          assertThat(result.getInt(3)).isZero();
        }

        connection.createStatement().executeUpdate("""
            INSERT INTO "AdminSession" ("id", "tokenHash", "adminUserId", "expiresAt", "createdAt", "lastSeenAt")
            VALUES ('definition-session', 'definition-token-hash', 'legacy-viewer', CURRENT_TIMESTAMP + INTERVAL '1 hour', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """);
        connection.createStatement().executeUpdate("""
            UPDATE "AdminPermissionDefinition" SET "status" = 'DISABLED'
            WHERE "id" = 'admin-permission-tasks-write'
            """);
        try (var result = connection.createStatement().executeQuery(
            "SELECT COUNT(*) FROM \"AdminSession\" WHERE \"adminUserId\" = 'legacy-viewer'")) {
          assertThat(result.next()).isTrue();
          assertThat(result.getInt(1)).isZero();
        }

        connection.createStatement().executeUpdate("""
            INSERT INTO "AdminSession" ("id", "tokenHash", "adminUserId", "expiresAt", "createdAt", "lastSeenAt")
            VALUES ('disable-session', 'disable-token-hash', 'legacy-viewer', CURRENT_TIMESTAMP + INTERVAL '1 hour', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """);
        connection.createStatement().executeUpdate(
            "UPDATE \"AdminUser\" SET \"active\" = false WHERE \"id\" = 'legacy-viewer'");
        try (var result = connection.createStatement().executeQuery(
            "SELECT COUNT(*) FROM \"AdminSession\" WHERE \"adminUserId\" = 'legacy-viewer'")) {
          assertThat(result.next()).isTrue();
          assertThat(result.getInt(1)).isZero();
        }
      }
    }
  }
}
