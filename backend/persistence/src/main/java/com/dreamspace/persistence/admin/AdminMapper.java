package com.dreamspace.persistence.admin;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Param;
import java.time.Instant;

@Mapper
public interface AdminMapper {
  @Select("SELECT * FROM \"AdminUser\" WHERE \"phone\" = #{phone} AND \"active\" = TRUE LIMIT 1") AdminUserRecord findActiveByPhone(String phone);
  @Select("SELECT * FROM \"AdminUser\" WHERE \"id\" = #{id} AND \"active\" = TRUE LIMIT 1") AdminUserRecord findActiveById(String id);
  @Select("SELECT * FROM \"AdminSession\" WHERE \"tokenHash\" = #{tokenHash} AND \"expiresAt\" > CURRENT_TIMESTAMP LIMIT 1") AdminSessionRecord findActiveSession(String tokenHash);
  @Select("SELECT * FROM \"AdminVerificationCode\" WHERE \"id\" = #{id} LIMIT 1") AdminVerificationCodeRecord findCodeById(String id);
  @Select("SELECT * FROM \"AdminVerificationCode\" WHERE \"phone\" = #{phone} AND \"consumedAt\" IS NULL AND \"expiresAt\" > CURRENT_TIMESTAMP AND \"attempts\" < 5 ORDER BY \"createdAt\" DESC LIMIT 1") AdminVerificationCodeRecord findActiveCode(String phone);
  @Insert("INSERT INTO \"AdminVerificationCode\" (\"id\",\"phone\",\"codeHash\",\"expiresAt\",\"createdAt\") VALUES (#{id},#{phone},#{codeHash},#{expiresAt},CURRENT_TIMESTAMP)") int insertCode(@Param("id") String id, @Param("phone") String phone, @Param("codeHash") String codeHash, @Param("expiresAt") Instant expiresAt);
  @Update("UPDATE \"AdminVerificationCode\" SET \"attempts\" = \"attempts\" + 1 WHERE \"id\" = #{id} AND \"consumedAt\" IS NULL AND \"attempts\" < 5") int incrementAttempts(String id);
  @Update("UPDATE \"AdminVerificationCode\" SET \"consumedAt\" = CURRENT_TIMESTAMP WHERE \"id\" = #{id} AND \"consumedAt\" IS NULL AND \"expiresAt\" > CURRENT_TIMESTAMP") int consumeCode(String id);
  @Insert("INSERT INTO \"AdminSession\" (\"id\",\"tokenHash\",\"adminUserId\",\"expiresAt\",\"createdAt\",\"lastSeenAt\") VALUES (#{id},#{tokenHash},#{adminUserId},#{expiresAt},CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)") int insertSession(@Param("id") String id, @Param("tokenHash") String tokenHash, @Param("adminUserId") String adminUserId, @Param("expiresAt") Instant expiresAt);
  @Update("DELETE FROM \"AdminSession\" WHERE \"tokenHash\" = #{tokenHash}") int deleteSession(String tokenHash);
}
