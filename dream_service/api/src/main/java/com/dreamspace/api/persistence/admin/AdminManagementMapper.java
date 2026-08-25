package com.dreamspace.api.persistence.admin;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface AdminManagementMapper {
  String ACCOUNT_SELECT = "SELECT \"id\",\"phone\",\"displayName\",\"role\"::TEXT AS \"role\",\"active\",\"status\",\"version\",\"createdAt\",\"updatedAt\",\"lastLoginAt\",\"createdBy\",\"disabledAt\",\"disabledBy\",\"disabledReason\" FROM \"AdminUser\"";
  @Select("""
      SELECT \"id\",\"phone\",\"displayName\",\"role\"::TEXT AS \"role\",\"active\",\"status\",\"version\",\"createdAt\",\"updatedAt\",\"lastLoginAt\",\"createdBy\",\"disabledAt\",\"disabledBy\",\"disabledReason\" FROM \"AdminUser\"
      WHERE (#{query} IS NULL OR \"phone\" ILIKE '%' || #{query} || '%' OR \"displayName\" ILIKE '%' || #{query} || '%')
        AND (#{status} IS NULL OR \"status\" = #{status})
        AND (#{roleId} IS NULL OR EXISTS (SELECT 1 FROM \"AdminUserRole\" ur WHERE ur.\"adminUserId\"=\"AdminUser\".\"id\" AND ur.\"roleId\"=#{roleId}))
      ORDER BY \"createdAt\" DESC LIMIT #{limit} OFFSET #{offset}
      """) List<AdminAccountRecord> listAccounts(@Param("query") String query, @Param("status") String status,
      @Param("roleId") String roleId, @Param("limit") int limit, @Param("offset") int offset);
  @Select("SELECT COUNT(*) FROM \"AdminUser\" WHERE (#{query} IS NULL OR \"phone\" ILIKE '%' || #{query} || '%' OR \"displayName\" ILIKE '%' || #{query} || '%') AND (#{status} IS NULL OR \"status\"=#{status}) AND (#{roleId} IS NULL OR EXISTS (SELECT 1 FROM \"AdminUserRole\" ur WHERE ur.\"adminUserId\"=\"AdminUser\".\"id\" AND ur.\"roleId\"=#{roleId}))") long countAccounts(@Param("query") String query, @Param("status") String status, @Param("roleId") String roleId);
  @Select(ACCOUNT_SELECT + " WHERE \"id\"=#{id} FOR UPDATE") AdminAccountRecord lockAccount(String id);
  @Insert("INSERT INTO \"AdminUser\" (\"id\",\"phone\",\"displayName\",\"role\",\"active\",\"status\",\"version\",\"createdBy\",\"updatedAt\") VALUES (#{id},#{phone},#{displayName},CAST(#{role} AS \"AdminRole\"),#{active},#{status},1,#{createdBy},CURRENT_TIMESTAMP)") int insertAccount(@Param("id") String id, @Param("phone") String phone, @Param("displayName") String displayName, @Param("role") String role, @Param("active") boolean active, @Param("status") String status, @Param("createdBy") String createdBy);
  @Select("SELECT \"subjectId\" FROM \"AdminOperationIdempotency\" WHERE \"scope\"=#{scope} AND \"idempotencyKey\"=#{key}") String findIdempotentSubject(@Param("scope") String scope, @Param("key") String key);
  @Insert("INSERT INTO \"AdminOperationIdempotency\" (\"scope\",\"idempotencyKey\",\"subjectId\") VALUES (#{scope},#{key},#{subjectId})") int insertIdempotency(@Param("scope") String scope, @Param("key") String key, @Param("subjectId") String subjectId);
  @Update("UPDATE \"AdminUser\" SET \"displayName\"=#{displayName}, \"status\"=#{status}, \"active\"=#{active}, \"disabledAt\"=CASE WHEN #{active}=false THEN CURRENT_TIMESTAMP ELSE NULL END, \"disabledBy\"=CASE WHEN #{active}=false THEN #{actorId} ELSE NULL END, \"disabledReason\"=CASE WHEN #{active}=false THEN #{reason} ELSE NULL END, \"version\"=\"version\"+1, \"updatedAt\"=CURRENT_TIMESTAMP WHERE \"id\"=#{id} AND \"version\"=#{version}") int updateAccount(@Param("id") String id, @Param("displayName") String displayName, @Param("status") String status, @Param("active") boolean active, @Param("actorId") String actorId, @Param("reason") String reason, @Param("version") int version);
  @Select("SELECT r.*, (SELECT COUNT(*) FROM \"AdminUserRole\" ur WHERE ur.\"roleId\"=r.\"id\") AS \"accountCount\", (SELECT COUNT(*) FROM \"AdminRolePermission\" rp WHERE rp.\"roleId\"=r.\"id\") AS \"permissionCount\" FROM \"AdminRoleDefinition\" r ORDER BY r.\"system\" DESC, r.\"code\"") List<AdminRoleManagementRecord> listRoles();
  @Select("SELECT * FROM \"AdminRoleDefinition\" WHERE \"id\"=#{id} FOR UPDATE") AdminRoleDefinitionRecord lockRole(String id);
  @Insert("INSERT INTO \"AdminRoleDefinition\" (\"id\",\"code\",\"name\",\"description\",\"system\") VALUES (#{id},#{code},#{name},#{description},false)") int insertRole(@Param("id") String id, @Param("code") String code, @Param("name") String name, @Param("description") String description);
  @Update("UPDATE \"AdminRoleDefinition\" SET \"name\"=#{name},\"description\"=#{description},\"status\"=COALESCE(#{status}, \"status\"),\"version\"=\"version\"+1,\"updatedAt\"=CURRENT_TIMESTAMP WHERE \"id\"=#{id} AND \"version\"=#{version} AND \"system\"=false") int updateRole(@Param("id") String id, @Param("name") String name, @Param("description") String description, @Param("status") String status, @Param("version") int version);
  @Update("UPDATE \"AdminRoleDefinition\" SET \"version\"=\"version\"+1,\"updatedAt\"=CURRENT_TIMESTAMP WHERE \"id\"=#{id} AND \"version\"=#{version}") int bumpRoleVersion(@Param("id") String id, @Param("version") int version);
  @Select("SELECT \"id\",\"code\",\"resource\",\"action\",\"description\",\"riskLevel\",\"status\",\"createdAt\",\"updatedAt\" FROM \"AdminPermissionDefinition\" WHERE \"status\"='ACTIVE' ORDER BY \"resource\",\"action\"") List<AdminPermissionDefinitionRecord> listPermissions();
  @Select("SELECT \"permissionId\" FROM \"AdminRolePermission\" WHERE \"roleId\"=#{roleId} ORDER BY \"permissionId\"") List<String> listRolePermissionIds(String roleId);
  @Delete("DELETE FROM \"AdminRolePermission\" WHERE \"roleId\"=#{roleId}") int deleteRolePermissions(String roleId);
  @Insert("<script>INSERT INTO \"AdminRolePermission\" (\"roleId\",\"permissionId\",\"grantedBy\") VALUES <foreach collection='permissionIds' item='permissionId' separator=','>(#{roleId},#{permissionId},#{actorId})</foreach></script>") int insertRolePermissions(@Param("roleId") String roleId, @Param("permissionIds") List<String> permissionIds, @Param("actorId") String actorId);
  @Select("<script>SELECT COUNT(*) FROM \"AdminPermissionDefinition\" WHERE \"id\" IN (<foreach collection='permissionIds' item='id' separator=','>#{id}</foreach>) AND \"status\"='ACTIVE'</script>") long countActivePermissions(@Param("permissionIds") List<String> permissionIds);
  @Select("<script>SELECT COUNT(*) FROM \"AdminRoleDefinition\" WHERE \"id\" IN (<foreach collection='roleIds' item='id' separator=','>#{id}</foreach>) AND \"status\"='ACTIVE'</script>") long countActiveRoles(@Param("roleIds") List<String> roleIds);
  @Delete("DELETE FROM \"AdminUserRole\" WHERE \"adminUserId\"=#{adminUserId}") int deleteUserRoles(String adminUserId);
  @Insert("<script>INSERT INTO \"AdminUserRole\" (\"adminUserId\",\"roleId\",\"assignedBy\") VALUES <foreach collection='roleIds' item='roleId' separator=','>(#{adminUserId},#{roleId},#{actorId})</foreach></script>") int insertUserRoles(@Param("adminUserId") String adminUserId, @Param("roleIds") List<String> roleIds, @Param("actorId") String actorId);
  @Select("SELECT \"roleId\" FROM \"AdminUserRole\" WHERE \"adminUserId\"=#{adminUserId} ORDER BY \"roleId\"") List<String> listUserRoleIds(String adminUserId);
  @Select("SELECT COUNT(*) FROM \"AdminUser\" u JOIN \"AdminUserRole\" ur ON ur.\"adminUserId\"=u.\"id\" JOIN \"AdminRoleDefinition\" r ON r.\"id\"=ur.\"roleId\" WHERE u.\"active\"=true AND r.\"code\"='ADMIN' AND r.\"status\"='ACTIVE'") long countActiveAdmins();
  @Select("<script>SELECT \"code\" FROM \"AdminRoleDefinition\" WHERE \"id\" IN (<foreach collection='roleIds' item='id' separator=','>#{id}</foreach>)</script>") List<String> roleCodes(@Param("roleIds") List<String> roleIds);
  @Update("UPDATE \"AdminUser\" SET \"lastLoginAt\"=CURRENT_TIMESTAMP, \"updatedAt\"=CURRENT_TIMESTAMP WHERE \"id\"=#{id}") int markLogin(String id);
  @Update("DELETE FROM \"AdminSession\" WHERE \"adminUserId\"=#{id}") int revokeSessions(String id);
  @Update("UPDATE \"AdminUser\" SET \"role\"=CAST(#{role} AS \"AdminRole\") WHERE \"id\"=#{id}") int updateLegacyRole(@Param("id") String id, @Param("role") String role);
}
