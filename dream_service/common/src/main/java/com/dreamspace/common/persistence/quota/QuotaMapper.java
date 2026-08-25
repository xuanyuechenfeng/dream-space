package com.dreamspace.common.persistence.quota;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface QuotaMapper {
  @Select("SELECT * FROM \"QuotaAccount\" WHERE \"userId\" = #{userId} LIMIT 1") QuotaAccountRecord findAccount(String userId);
  @org.apache.ibatis.annotations.Insert("INSERT INTO \"QuotaAccount\" (\"userId\",\"total\",\"available\",\"reserved\",\"createdAt\",\"updatedAt\") VALUES (#{userId},#{total},#{total},0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP) ON CONFLICT (\"userId\") DO NOTHING")
  int ensureAccount(@Param("userId") String userId, @Param("total") int total);
  @Select("SELECT * FROM \"QuotaAccount\" WHERE \"userId\" = #{userId} FOR UPDATE") QuotaAccountRecord lockAccount(String userId);
  @Update("UPDATE \"QuotaAccount\" SET \"available\" = \"available\" - #{amount}, \"reserved\" = \"reserved\" + #{amount}, \"updatedAt\" = CURRENT_TIMESTAMP WHERE \"userId\" = #{userId} AND \"available\" >= #{amount}")
  int reserve(@Param("userId") String userId, @Param("amount") int amount);
  @Update("UPDATE \"QuotaAccount\" SET \"reserved\" = \"reserved\" - #{amount}, \"updatedAt\" = CURRENT_TIMESTAMP WHERE \"userId\" = #{userId} AND \"reserved\" >= #{amount}")
  int consume(@Param("userId") String userId, @Param("amount") int amount);
  @Update("UPDATE \"QuotaAccount\" SET \"available\" = \"available\" + #{amount}, \"reserved\" = \"reserved\" - #{amount}, \"updatedAt\" = CURRENT_TIMESTAMP WHERE \"userId\" = #{userId} AND \"reserved\" >= #{amount}")
  int release(@Param("userId") String userId, @Param("amount") int amount);

  @Update("UPDATE \"QuotaAccount\" SET \"total\"=\"total\"+#{amount},\"available\"=\"available\"+#{amount},\"updatedAt\"=CURRENT_TIMESTAMP WHERE \"userId\"=#{userId}")
  int grant(@Param("userId") String userId, @Param("amount") int amount);

  @Update("UPDATE \"QuotaAccount\" SET \"total\"=\"total\"-#{amount},\"available\"=\"available\"-#{amount},\"updatedAt\"=CURRENT_TIMESTAMP WHERE \"userId\"=#{userId} AND \"available\" >= #{amount} AND \"total\"-#{amount} >= 0")
  int revoke(@Param("userId") String userId, @Param("amount") int amount);
}
