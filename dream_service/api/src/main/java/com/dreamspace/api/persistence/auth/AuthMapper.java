package com.dreamspace.api.persistence.auth;

import java.time.Instant;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AuthMapper {
  @Select("SELECT \"id\",\"phone\",\"email\",\"passwordHash\",\"createdAt\",\"updatedAt\" FROM \"User\" WHERE \"phone\" = #{phone} LIMIT 1") UserRecord findUserByPhone(String phone);
  @Select("SELECT \"id\",\"phone\",\"email\",\"passwordHash\",\"createdAt\",\"updatedAt\" FROM \"User\" WHERE \"id\" = #{id} LIMIT 1") UserRecord findUserById(String id);
  @Select("SELECT \"id\",\"phone\",\"email\",\"passwordHash\",\"createdAt\",\"updatedAt\" FROM \"User\" WHERE \"email\" = #{email} LIMIT 1") UserRecord findUserByEmail(String email);
  @Select("SELECT * FROM \"UserSession\" WHERE \"tokenHash\" = #{tokenHash} AND \"expiresAt\" > CURRENT_TIMESTAMP LIMIT 1") UserSessionRecord findActiveSession(String tokenHash);
  @Select("SELECT * FROM \"VerificationCode\" WHERE \"phone\" = #{phone} AND \"consumedAt\" IS NULL AND \"expiresAt\" > CURRENT_TIMESTAMP AND \"attempts\" < 5 ORDER BY \"createdAt\" DESC LIMIT 1") VerificationCodeRecord findActiveCode(String phone);
  @Select("SELECT * FROM \"VerificationCode\" WHERE \"id\" = #{id} LIMIT 1") VerificationCodeRecord findCodeById(String id);

  @Insert("INSERT INTO \"User\" (\"id\",\"phone\",\"createdAt\",\"updatedAt\") VALUES (#{id},#{phone},CURRENT_TIMESTAMP,CURRENT_TIMESTAMP) ON CONFLICT (\"phone\") DO UPDATE SET \"updatedAt\" = CURRENT_TIMESTAMP")
  int upsertUser(@Param("id") String id, @Param("phone") String phone);
  @Insert("INSERT INTO \"User\" (\"id\",\"email\",\"passwordHash\",\"createdAt\",\"updatedAt\") VALUES (#{id},#{email},#{passwordHash},CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)")
  int insertEmailUser(@Param("id") String id, @Param("email") String email, @Param("passwordHash") String passwordHash);
  @Select("SELECT \"id\" FROM \"User\" WHERE \"phone\" = #{phone} LIMIT 1") String findUserIdByPhone(String phone);
  @Select("SELECT \"status\" FROM \"User\" WHERE \"id\" = #{id} LIMIT 1") String findUserStatusById(String id);
  @Update("UPDATE \"User\" SET \"lastLoginAt\"=CURRENT_TIMESTAMP,\"updatedAt\"=CURRENT_TIMESTAMP WHERE \"id\"=#{id}") int touchLogin(String id);

  @Select("SELECT * FROM \"LoginCaptcha\" WHERE \"id\" = #{id} LIMIT 1")
  LoginCaptchaRecord findLoginCaptcha(String id);

  @Select("SELECT COUNT(*) FROM \"LoginCaptcha\" WHERE \"clientKeyHash\" = #{clientKeyHash} AND \"createdAt\" > #{since}")
  long countRecentLoginCaptchas(@Param("clientKeyHash") String clientKeyHash, @Param("since") Instant since);

  @Insert("INSERT INTO \"LoginCaptcha\" (\"id\",\"clientKeyHash\",\"codeHash\",\"expiresAt\",\"createdAt\") VALUES (#{id},#{clientKeyHash},#{codeHash},#{expiresAt},CURRENT_TIMESTAMP)")
  int insertLoginCaptcha(@Param("id") String id, @Param("clientKeyHash") String clientKeyHash,
      @Param("codeHash") String codeHash, @Param("expiresAt") Instant expiresAt);

  @Update("UPDATE \"LoginCaptcha\" SET \"attempts\" = \"attempts\" + 1, \"consumedAt\" = CASE WHEN \"attempts\" + 1 >= #{maxAttempts} THEN CURRENT_TIMESTAMP ELSE \"consumedAt\" END WHERE \"id\" = #{id} AND \"consumedAt\" IS NULL AND \"expiresAt\" > CURRENT_TIMESTAMP AND \"attempts\" < #{maxAttempts}")
  int incrementLoginCaptchaAttempts(@Param("id") String id, @Param("maxAttempts") int maxAttempts);

  @Update("UPDATE \"LoginCaptcha\" SET \"consumedAt\" = CURRENT_TIMESTAMP WHERE \"id\" = #{id} AND \"consumedAt\" IS NULL AND \"expiresAt\" > CURRENT_TIMESTAMP AND \"attempts\" < #{maxAttempts}")
  int consumeLoginCaptcha(@Param("id") String id, @Param("maxAttempts") int maxAttempts);

  @Select("SELECT * FROM \"RegistrationEmailCode\" WHERE \"id\" = #{id} LIMIT 1")
  RegistrationEmailCodeRecord findRegistrationEmailCode(String id);
  @Select("SELECT COUNT(*) FROM \"RegistrationEmailCode\" WHERE \"emailHash\" = #{emailHash} AND \"createdAt\" > #{since}")
  long countRecentRegistrationCodesByEmail(@Param("emailHash") String emailHash, @Param("since") Instant since);
  @Select("SELECT COUNT(*) FROM \"RegistrationEmailCode\" WHERE \"clientKeyHash\" = #{clientKeyHash} AND \"createdAt\" > #{since}")
  long countRecentRegistrationCodesByClient(@Param("clientKeyHash") String clientKeyHash, @Param("since") Instant since);
  @Insert("INSERT INTO \"RegistrationEmailCode\" (\"id\",\"emailHash\",\"codeHash\",\"clientKeyHash\",\"expiresAt\",\"createdAt\") VALUES (#{id},#{emailHash},#{codeHash},#{clientKeyHash},#{expiresAt},CURRENT_TIMESTAMP)")
  int insertRegistrationEmailCode(@Param("id") String id, @Param("emailHash") String emailHash,
      @Param("codeHash") String codeHash, @Param("clientKeyHash") String clientKeyHash,
      @Param("expiresAt") Instant expiresAt);
  @Update("DELETE FROM \"RegistrationEmailCode\" WHERE \"id\" = #{id} AND \"consumedAt\" IS NULL")
  int deleteRegistrationEmailCode(String id);
  @Update("UPDATE \"RegistrationEmailCode\" SET \"attempts\" = \"attempts\" + 1, \"consumedAt\" = CASE WHEN \"attempts\" + 1 >= #{maxAttempts} THEN CURRENT_TIMESTAMP ELSE \"consumedAt\" END WHERE \"id\" = #{id} AND \"consumedAt\" IS NULL AND \"expiresAt\" > CURRENT_TIMESTAMP AND \"attempts\" < #{maxAttempts}")
  int incrementRegistrationEmailCodeAttempts(@Param("id") String id, @Param("maxAttempts") int maxAttempts);
  @Update("UPDATE \"RegistrationEmailCode\" SET \"consumedAt\" = CURRENT_TIMESTAMP WHERE \"id\" = #{id} AND \"consumedAt\" IS NULL AND \"expiresAt\" > CURRENT_TIMESTAMP AND \"attempts\" < #{maxAttempts}")
  int consumeRegistrationEmailCode(@Param("id") String id, @Param("maxAttempts") int maxAttempts);

  @Insert("INSERT INTO \"VerificationCode\" (\"id\",\"phone\",\"codeHash\",\"expiresAt\",\"createdAt\") VALUES (#{id},#{phone},#{codeHash},#{expiresAt},CURRENT_TIMESTAMP)")
  int insertCode(@Param("id") String id, @Param("phone") String phone, @Param("codeHash") String codeHash, @Param("expiresAt") Instant expiresAt);
  @Update("UPDATE \"VerificationCode\" SET \"attempts\" = \"attempts\" + 1 WHERE \"id\" = #{id} AND \"consumedAt\" IS NULL AND \"attempts\" < 5") int incrementAttempts(String id);
  @Update("UPDATE \"VerificationCode\" SET \"consumedAt\" = CURRENT_TIMESTAMP WHERE \"id\" = #{id} AND \"consumedAt\" IS NULL AND \"expiresAt\" > CURRENT_TIMESTAMP") int consumeCode(String id);
  @Insert("INSERT INTO \"UserSession\" (\"id\",\"tokenHash\",\"userId\",\"expiresAt\",\"createdAt\",\"lastSeenAt\") VALUES (#{id},#{tokenHash},#{userId},#{expiresAt},CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)")
  int insertSession(@Param("id") String id, @Param("tokenHash") String tokenHash, @Param("userId") String userId, @Param("expiresAt") Instant expiresAt);
  @Update("DELETE FROM \"UserSession\" WHERE \"tokenHash\" = #{tokenHash}") int deleteSession(String tokenHash);
  @Update("UPDATE \"UserSession\" SET \"lastSeenAt\" = CURRENT_TIMESTAMP WHERE \"tokenHash\" = #{tokenHash} AND \"expiresAt\" > CURRENT_TIMESTAMP") int touchSession(String tokenHash);
  @Insert("INSERT INTO \"AgreementAcceptance\" (\"id\",\"userId\",\"version\",\"termsAccepted\",\"privacyAccepted\",\"aiTermsAccepted\",\"acceptedAt\") VALUES (#{id},#{userId},#{version},#{termsAccepted},#{privacyAccepted},#{aiTermsAccepted},CURRENT_TIMESTAMP) ON CONFLICT (\"userId\",\"version\") DO NOTHING")
  int upsertAgreement(@Param("id") String id, @Param("userId") String userId, @Param("version") String version,
      @Param("termsAccepted") boolean termsAccepted, @Param("privacyAccepted") boolean privacyAccepted,
      @Param("aiTermsAccepted") boolean aiTermsAccepted);
}
