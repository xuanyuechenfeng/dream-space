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

  @org.apache.ibatis.annotations.Insert("INSERT INTO \"QuotaLedgerEntry\" (\"id\",\"userId\",\"type\",\"amount\",\"balanceAfter\",\"idempotencyKey\",\"sourceType\",\"sourceId\",\"reasonCode\",\"createdAt\") SELECT #{id},a.\"userId\",'GRANT'::\"QuotaLedgerType\",a.\"total\",a.\"total\",#{key},'INITIAL_GRANT',a.\"userId\",'INITIAL_ALLOWANCE',CURRENT_TIMESTAMP FROM \"QuotaAccount\" a WHERE a.\"userId\"=#{userId} AND NOT EXISTS (SELECT 1 FROM \"QuotaLedgerEntry\" l WHERE l.\"userId\"=#{userId} AND l.\"type\"='GRANT'::\"QuotaLedgerType\") ON CONFLICT (\"idempotencyKey\") DO NOTHING")
  int insertInitialGrant(@Param("id") String id, @Param("userId") String userId, @Param("key") String key);

  @org.apache.ibatis.annotations.Insert("INSERT INTO \"QuotaLedgerEntry\" (\"id\",\"userId\",\"taskId\",\"type\",\"amount\",\"balanceAfter\",\"idempotencyKey\",\"sourceType\",\"sourceId\",\"reasonCode\",\"createdAt\") VALUES (#{id},#{userId},#{taskId},#{type}::\"QuotaLedgerType\",#{amount},#{balanceAfter},#{key},#{sourceType},#{sourceId},#{reason},CURRENT_TIMESTAMP)")
  int insertWithSource(@Param("id") String id, @Param("userId") String userId, @Param("taskId") String taskId,
      @Param("type") String type, @Param("amount") int amount, @Param("balanceAfter") int balanceAfter,
      @Param("key") String key, @Param("sourceType") String sourceType, @Param("sourceId") String sourceId,
      @Param("reason") String reason);

  @org.apache.ibatis.annotations.Insert("INSERT INTO \"QuotaLedgerEntry\" (\"id\",\"userId\",\"taskId\",\"type\",\"amount\",\"balanceAfter\",\"idempotencyKey\",\"sourceType\",\"sourceId\",\"ruleId\",\"ruleVersion\",\"reasonCode\",\"createdAt\") VALUES (#{id},#{userId},#{taskId},'RESERVE'::\"QuotaLedgerType\",#{amount},#{balanceAfter},#{key},'GENERATION',#{taskId},#{ruleId},#{ruleVersion},'GENERATION_RESERVE',CURRENT_TIMESTAMP)")
  int insertGenerationReserve(@Param("id") String id, @Param("userId") String userId, @Param("taskId") String taskId,
      @Param("amount") int amount, @Param("balanceAfter") int balanceAfter, @Param("key") String key,
      @Param("ruleId") String ruleId, @Param("ruleVersion") Integer ruleVersion);
}
