package com.dreamspace.persistence.admin;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AdminMapper {
  @Select("SELECT * FROM \"AdminUser\" WHERE \"phone\" = #{phone} AND \"active\" = TRUE LIMIT 1") AdminUserRecord findActiveByPhone(String phone);
  @Select("SELECT * FROM \"AdminSession\" WHERE \"tokenHash\" = #{tokenHash} AND \"expiresAt\" > CURRENT_TIMESTAMP LIMIT 1") AdminSessionRecord findActiveSession(String tokenHash);
}
