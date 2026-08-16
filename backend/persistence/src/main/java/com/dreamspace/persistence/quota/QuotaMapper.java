package com.dreamspace.persistence.quota;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface QuotaMapper {
  @Select("SELECT * FROM \"QuotaAccount\" WHERE \"userId\" = #{userId} FOR UPDATE") QuotaAccountRecord lockAccount(String userId);
  @Update("UPDATE \"QuotaAccount\" SET \"available\" = \"available\" - #{amount}, \"reserved\" = \"reserved\" + #{amount}, \"updatedAt\" = CURRENT_TIMESTAMP WHERE \"userId\" = #{userId} AND \"available\" >= #{amount}")
  int reserve(@Param("userId") String userId, @Param("amount") int amount);
  @Update("UPDATE \"QuotaAccount\" SET \"reserved\" = \"reserved\" - #{amount}, \"updatedAt\" = CURRENT_TIMESTAMP WHERE \"userId\" = #{userId} AND \"reserved\" >= #{amount}")
  int consume(@Param("userId") String userId, @Param("amount") int amount);
  @Update("UPDATE \"QuotaAccount\" SET \"available\" = \"available\" + #{amount}, \"reserved\" = \"reserved\" - #{amount}, \"updatedAt\" = CURRENT_TIMESTAMP WHERE \"userId\" = #{userId} AND \"reserved\" >= #{amount}")
  int release(@Param("userId") String userId, @Param("amount") int amount);
}
