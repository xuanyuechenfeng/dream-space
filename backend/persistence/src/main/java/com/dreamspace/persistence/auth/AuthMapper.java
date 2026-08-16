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
  @Select("SELECT * FROM \"User\" WHERE \"id\" = #{id} LIMIT 1") UserRecord findUserById(String id);
  @Select("SELECT * FROM \"UserSession\" WHERE \"tokenHash\" = #{tokenHash} AND \"expiresAt\" > CURRENT_TIMESTAMP LIMIT 1") UserSessionRecord findActiveSession(String tokenHash);
  @Select("SELECT * FROM \"VerificationCode\" WHERE \"phone\" = #{phone} AND \"consumedAt\" IS NULL AND \"expiresAt\" > CURRENT_TIMESTAMP AND \"attempts\" < 5 ORDER BY \"createdAt\" DESC LIMIT 1") VerificationCodeRecord findActiveCode(String phone);
  @Select("SELECT * FROM \"VerificationCode\" WHERE \"id\" = #{id} LIMIT 1") VerificationCodeRecord findCodeById(String id);

  @Insert("INSERT INTO \"User\" (\"id\",\"phone\",\"createdAt\",\"updatedAt\") VALUES (#{id},#{phone},CURRENT_TIMESTAMP,CURRENT_TIMESTAMP) ON CONFLICT (\"phone\") DO UPDATE SET \"updatedAt\" = CURRENT_TIMESTAMP")
  int upsertUser(@Param("id") String id, @Param("phone") String phone);
  @Select("SELECT \"id\" FROM \"User\" WHERE \"phone\" = #{phone} LIMIT 1") String findUserIdByPhone(String phone);

  @Insert("INSERT INTO \"VerificationCode\" (\"id\",\"phone\",\"codeHash\",\"expiresAt\",\"createdAt\") VALUES (#{id},#{phone},#{codeHash},#{expiresAt},CURRENT_TIMESTAMP)")
  int insertCode(@Param("id") String id, @Param("phone") String phone, @Param("codeHash") String codeHash, @Param("expiresAt") Instant expiresAt);
  @Update("UPDATE \"VerificationCode\" SET \"attempts\" = \"attempts\" + 1 WHERE \"id\" = #{id} AND \"consumedAt\" IS NULL") int incrementAttempts(String id);
  @Update("UPDATE \"VerificationCode\" SET \"consumedAt\" = CURRENT_TIMESTAMP WHERE \"id\" = #{id} AND \"consumedAt\" IS NULL AND \"expiresAt\" > CURRENT_TIMESTAMP") int consumeCode(String id);
  @Insert("INSERT INTO \"UserSession\" (\"id\",\"tokenHash\",\"userId\",\"expiresAt\",\"createdAt\",\"lastSeenAt\") VALUES (#{id},#{tokenHash},#{userId},#{expiresAt},CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)")
  int insertSession(@Param("id") String id, @Param("tokenHash") String tokenHash, @Param("userId") String userId, @Param("expiresAt") Instant expiresAt);
  @Update("DELETE FROM \"UserSession\" WHERE \"tokenHash\" = #{tokenHash}") int deleteSession(String tokenHash);
  @Update("UPDATE \"UserSession\" SET \"lastSeenAt\" = CURRENT_TIMESTAMP WHERE \"tokenHash\" = #{tokenHash} AND \"expiresAt\" > CURRENT_TIMESTAMP") int touchSession(String tokenHash);
  @Insert("INSERT INTO \"AgreementAcceptance\" (\"id\",\"userId\",\"version\",\"termsAccepted\",\"privacyAccepted\",\"aiTermsAccepted\",\"acceptedAt\") VALUES (#{id},#{userId},#{version},#{termsAccepted},#{privacyAccepted},#{aiTermsAccepted},CURRENT_TIMESTAMP) ON CONFLICT (\"userId\",\"version\") DO UPDATE SET \"termsAccepted\" = EXCLUDED.\"termsAccepted\", \"privacyAccepted\" = EXCLUDED.\"privacyAccepted\", \"aiTermsAccepted\" = EXCLUDED.\"aiTermsAccepted\", \"acceptedAt\" = CURRENT_TIMESTAMP")
  int upsertAgreement(@Param("id") String id, @Param("userId") String userId, @Param("version") String version,
      @Param("termsAccepted") boolean termsAccepted, @Param("privacyAccepted") boolean privacyAccepted,
      @Param("aiTermsAccepted") boolean aiTermsAccepted);
}
