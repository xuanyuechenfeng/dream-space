package com.dreamspace.worker.persistence.reconciliation;

import com.dreamspace.common.persistence.generation.GenerationTaskRecord;
import com.dreamspace.common.persistence.quota.QuotaAccountRecord;
import com.dreamspace.common.persistence.reconciliation.QuotaReconciliationRunRecord;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface QuotaReconciliationMapper {
  @Insert("INSERT INTO \"QuotaReconciliationRun\" (\"id\",\"windowKey\",\"status\",\"startedAt\",\"createdAt\") VALUES (#{id},#{windowKey},'RUNNING',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP) ON CONFLICT (\"windowKey\") DO NOTHING")
  int insertRun(@Param("id") String id, @Param("windowKey") String windowKey);

  @Select("SELECT * FROM \"QuotaReconciliationRun\" WHERE \"windowKey\" = #{windowKey} LIMIT 1")
  QuotaReconciliationRunRecord findRun(String windowKey);

  @Select("SELECT * FROM \"QuotaAccount\" ORDER BY \"userId\"")
  List<QuotaAccountRecord> listAccounts();

  @Select("SELECT * FROM \"GenerationTask\" WHERE \"userId\" = #{userId} ORDER BY \"createdAt\"")
  List<GenerationTaskRecord> listTasks(String userId);

  @Select("SELECT \"amount\" FROM \"QuotaLedgerEntry\" WHERE \"taskId\" = #{taskId} AND \"type\" = #{type}::\"QuotaLedgerType\" ORDER BY \"createdAt\" LIMIT 1")
  Integer findLedgerAmount(@Param("taskId") String taskId, @Param("type") String type);

  @Select("SELECT COALESCE(SUM(\"amount\"),0) FROM \"QuotaLedgerEntry\" WHERE \"userId\" = #{userId} AND \"type\" = #{type}::\"QuotaLedgerType\"")
  int sumLedger(@Param("userId") String userId, @Param("type") String type);

  @Insert("INSERT INTO \"QuotaReconciliationFinding\" (\"id\",\"runId\",\"userId\",\"taskId\",\"kind\",\"status\",\"idempotencyKey\",\"expectedAmount\",\"actualAmount\",\"details\",\"createdAt\") VALUES (#{id},#{runId},#{userId},#{taskId},#{kind}::\"QuotaReconciliationFindingKind\",'OPEN',#{key},#{expected},#{actual},CAST(#{details} AS JSONB),CURRENT_TIMESTAMP) ON CONFLICT (\"runId\",\"idempotencyKey\") DO UPDATE SET \"expectedAmount\"=EXCLUDED.\"expectedAmount\",\"actualAmount\"=EXCLUDED.\"actualAmount\",\"details\"=EXCLUDED.\"details\"")
  int upsertFinding(@Param("id") String id, @Param("runId") String runId,
      @Param("userId") String userId, @Param("taskId") String taskId, @Param("kind") String kind,
      @Param("key") String key, @Param("expected") int expected, @Param("actual") int actual,
      @Param("details") String details);

  @Update("UPDATE \"QuotaReconciliationFinding\" SET \"status\"=#{status}::\"QuotaReconciliationFindingStatus\", \"repairedAt\"=CASE WHEN #{status}='REPAIRED' THEN CURRENT_TIMESTAMP ELSE NULL END WHERE \"runId\"=#{runId} AND \"idempotencyKey\"=#{key}")
  int finishFinding(@Param("runId") String runId, @Param("key") String key, @Param("status") String status);

  @Insert("INSERT INTO \"QuotaLedgerEntry\" (\"id\",\"userId\",\"taskId\",\"type\",\"amount\",\"balanceAfter\",\"idempotencyKey\",\"createdAt\") SELECT #{id},#{userId},t.\"id\",'CONSUME',#{amount},a.\"available\",#{key},CURRENT_TIMESTAMP FROM \"GenerationTask\" t JOIN \"QuotaAccount\" a ON a.\"userId\"=t.\"userId\" WHERE t.\"id\"=#{taskId} AND t.\"userId\"=#{userId} AND t.\"status\" IN ('SUCCEEDED','PARTIALLY_SUCCEEDED') AND NOT EXISTS (SELECT 1 FROM \"QuotaLedgerEntry\" l WHERE l.\"taskId\"=t.\"id\" AND l.\"type\"='CONSUME') ON CONFLICT (\"idempotencyKey\") DO NOTHING")
  int insertMissingConsume(@Param("id") String id, @Param("userId") String userId,
      @Param("taskId") String taskId, @Param("amount") int amount, @Param("key") String key);

  @Update("UPDATE \"QuotaAccount\" SET \"available\"=\"available\"+#{amount},\"reserved\"=\"reserved\"-#{amount},\"updatedAt\"=CURRENT_TIMESTAMP WHERE \"userId\"=#{userId} AND \"reserved\">=#{minimumReserved}")
  int releaseExcessReserved(@Param("userId") String userId, @Param("amount") int amount,
      @Param("minimumReserved") int minimumReserved);

  @Insert("INSERT INTO \"QuotaLedgerEntry\" (\"id\",\"userId\",\"taskId\",\"type\",\"amount\",\"balanceAfter\",\"idempotencyKey\",\"createdAt\") SELECT #{id},#{userId},#{taskId},'RELEASE',#{amount},\"available\",#{key},CURRENT_TIMESTAMP FROM \"QuotaAccount\" WHERE \"userId\"=#{userId} ON CONFLICT (\"idempotencyKey\") DO NOTHING")
  int insertMissingRelease(@Param("id") String id, @Param("userId") String userId,
      @Param("taskId") String taskId, @Param("amount") int amount, @Param("key") String key);

  @Update("UPDATE \"QuotaReconciliationRun\" SET \"status\"='COMPLETED',\"completedAt\"=CURRENT_TIMESTAMP,\"scannedUsers\"=#{users},\"scannedTasks\"=#{tasks},\"mismatchCount\"=#{mismatches},\"repairedCount\"=#{repaired} WHERE \"id\"=#{id} AND \"status\"='RUNNING'")
  int completeRun(@Param("id") String id, @Param("users") int users, @Param("tasks") int tasks,
      @Param("mismatches") int mismatches, @Param("repaired") int repaired);

  @Update("UPDATE \"QuotaReconciliationRun\" SET \"status\"='FAILED',\"completedAt\"=CURRENT_TIMESTAMP,\"errorMessage\"=#{message} WHERE \"id\"=#{id} AND \"status\"='RUNNING'")
  int failRun(@Param("id") String id, @Param("message") String message);
}
