package com.dreamspace.api.persistence.admin;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Param;
import java.time.Instant;
import java.util.List;

@Mapper
public interface AdminMapper {
  @Select("SELECT * FROM \"AdminUser\" WHERE \"phone\" = #{phone} AND \"active\" = TRUE LIMIT 1") AdminUserRecord findActiveByPhone(String phone);
  @Select("SELECT * FROM \"AdminUser\" WHERE \"id\" = #{id} AND \"active\" = TRUE LIMIT 1") AdminUserRecord findActiveById(String id);
  @Select("SELECT * FROM \"AdminSession\" WHERE \"tokenHash\" = #{tokenHash} AND \"expiresAt\" > CURRENT_TIMESTAMP LIMIT 1") AdminSessionRecord findActiveSession(String tokenHash);
  @Select("SELECT \"code\" FROM \"AdminPermissionDefinition\" ORDER BY \"code\"") List<String> listPermissionCodes();
  @Select("""
      SELECT permission_definition.*
      FROM "AdminPermissionDefinition" permission_definition
      JOIN "AdminRolePermission" role_permission ON role_permission."permissionId" = permission_definition."id"
      JOIN "AdminRoleDefinition" role_definition ON role_definition."id" = role_permission."roleId"
      JOIN "AdminUserRole" user_role ON user_role."roleId" = role_definition."id"
      WHERE user_role."adminUserId" = #{adminUserId}
        AND role_definition."status" = 'ACTIVE'
        AND permission_definition."status" = 'ACTIVE'
      ORDER BY permission_definition."code"
      """) List<AdminPermissionDefinitionRecord> listPermissionDefinitions(String adminUserId);
  @Select("""
      SELECT role_definition.*
      FROM "AdminRoleDefinition" role_definition
      JOIN "AdminUserRole" user_role ON user_role."roleId" = role_definition."id"
      WHERE user_role."adminUserId" = #{adminUserId} AND role_definition."status" = 'ACTIVE'
      ORDER BY role_definition."code"
      """) List<AdminRoleDefinitionRecord> listRoleDefinitions(String adminUserId);
  @Select("SELECT * FROM \"AdminUserRole\" WHERE \"adminUserId\" = #{adminUserId} ORDER BY \"assignedAt\"") List<AdminUserRoleRecord> listUserRoles(String adminUserId);
  @Select("SELECT * FROM \"AdminRolePermission\" WHERE \"roleId\" = #{roleId} ORDER BY \"grantedAt\"") List<AdminRolePermissionRecord> listRolePermissions(String roleId);
  @Select("SELECT * FROM \"AdminVerificationCode\" WHERE \"id\" = #{id} LIMIT 1") AdminVerificationCodeRecord findCodeById(String id);
  @Select("SELECT * FROM \"AdminVerificationCode\" WHERE \"phone\" = #{phone} AND \"consumedAt\" IS NULL AND \"expiresAt\" > CURRENT_TIMESTAMP AND \"attempts\" < 5 ORDER BY \"createdAt\" DESC LIMIT 1") AdminVerificationCodeRecord findActiveCode(String phone);
  @Insert("INSERT INTO \"AdminVerificationCode\" (\"id\",\"phone\",\"codeHash\",\"expiresAt\",\"createdAt\") VALUES (#{id},#{phone},#{codeHash},#{expiresAt},CURRENT_TIMESTAMP)") int insertCode(@Param("id") String id, @Param("phone") String phone, @Param("codeHash") String codeHash, @Param("expiresAt") Instant expiresAt);
  @Update("UPDATE \"AdminVerificationCode\" SET \"attempts\" = \"attempts\" + 1 WHERE \"id\" = #{id} AND \"consumedAt\" IS NULL AND \"attempts\" < 5") int incrementAttempts(String id);
  @Update("UPDATE \"AdminVerificationCode\" SET \"consumedAt\" = CURRENT_TIMESTAMP WHERE \"id\" = #{id} AND \"consumedAt\" IS NULL AND \"expiresAt\" > CURRENT_TIMESTAMP") int consumeCode(String id);
  @Insert("INSERT INTO \"AdminSession\" (\"id\",\"tokenHash\",\"adminUserId\",\"expiresAt\",\"createdAt\",\"lastSeenAt\") VALUES (#{id},#{tokenHash},#{adminUserId},#{expiresAt},CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)") int insertSession(@Param("id") String id, @Param("tokenHash") String tokenHash, @Param("adminUserId") String adminUserId, @Param("expiresAt") Instant expiresAt);
  @Update("DELETE FROM \"AdminSession\" WHERE \"tokenHash\" = #{tokenHash}") int deleteSession(String tokenHash);
  @Update("DELETE FROM \"AdminSession\" WHERE \"adminUserId\" = #{adminUserId}") int deleteSessionsForAdmin(String adminUserId);
}
