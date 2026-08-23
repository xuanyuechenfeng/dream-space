package com.dreamspace.common.persistence.quota;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface QuotaLedgerMapper {
  @Select("SELECT COUNT(*) FROM \"QuotaLedgerEntry\" WHERE \"idempotencyKey\" = #{key}") int countByIdempotencyKey(String key);
  @Insert("INSERT INTO \"QuotaLedgerEntry\" (\"id\",\"userId\",\"taskId\",\"type\",\"amount\",\"balanceAfter\",\"idempotencyKey\",\"createdAt\") VALUES (#{id},#{userId},#{taskId},#{type}::\"QuotaLedgerType\",#{amount},#{balanceAfter},#{key},CURRENT_TIMESTAMP)")
  int insert(@Param("id") String id, @Param("userId") String userId, @Param("taskId") String taskId,
      @Param("type") String type, @Param("amount") int amount, @Param("balanceAfter") int balanceAfter,
      @Param("key") String idempotencyKey);
}
