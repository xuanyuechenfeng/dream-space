package com.dreamspace.persistence.auth;

import java.time.Instant;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AuthMapper {
  @Select("SELECT * FROM \"User\" WHERE \"phone\" = #{phone} LIMIT 1") UserRecord findUserByPhone(String phone);
  @Select("SELECT * FROM \"UserSession\" WHERE \"tokenHash\" = #{tokenHash} AND \"expiresAt\" > CURRENT_TIMESTAMP LIMIT 1") UserSessionRecord findActiveSession(String tokenHash);
  @Select("SELECT * FROM \"VerificationCode\" WHERE \"phone\" = #{phone} AND \"consumedAt\" IS NULL AND \"expiresAt\" > CURRENT_TIMESTAMP AND \"attempts\" < 5 ORDER BY \"createdAt\" DESC LIMIT 1") VerificationCodeRecord findActiveCode(String phone);

  @Insert("INSERT INTO \"VerificationCode\" (\"id\",\"phone\",\"codeHash\",\"expiresAt\",\"createdAt\") VALUES (#{id},#{phone},#{codeHash},#{expiresAt},CURRENT_TIMESTAMP)")
  int insertCode(@Param("id") String id, @Param("phone") String phone, @Param("codeHash") String codeHash, @Param("expiresAt") Instant expiresAt);
  @Update("UPDATE \"VerificationCode\" SET \"attempts\" = \"attempts\" + 1 WHERE \"id\" = #{id} AND \"consumedAt\" IS NULL") int incrementAttempts(String id);
  @Update("UPDATE \"VerificationCode\" SET \"consumedAt\" = CURRENT_TIMESTAMP WHERE \"id\" = #{id} AND \"consumedAt\" IS NULL AND \"expiresAt\" > CURRENT_TIMESTAMP") int consumeCode(String id);
  @Insert("INSERT INTO \"UserSession\" (\"id\",\"tokenHash\",\"userId\",\"expiresAt\",\"createdAt\",\"lastSeenAt\") VALUES (#{id},#{tokenHash},#{userId},#{expiresAt},CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)")
  int insertSession(@Param("id") String id, @Param("tokenHash") String tokenHash, @Param("userId") String userId, @Param("expiresAt") Instant expiresAt);
}
