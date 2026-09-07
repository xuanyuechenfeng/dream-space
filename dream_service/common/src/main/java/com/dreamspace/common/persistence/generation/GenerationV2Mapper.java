package com.dreamspace.common.persistence.generation;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface GenerationV2Mapper {
  @Select("SELECT * FROM \"GenerationTask\" WHERE \"userId\"=#{userId} AND \"idempotencyKey\"=#{key} LIMIT 1")
  GenerationTaskRecord findTaskByIdempotency(@Param("userId") String userId, @Param("key") String key);
  /** Locks the task row so continuation and cancellation make one decision about its active execution. */
  @Select("SELECT * FROM \"GenerationTask\" WHERE \"userId\"=#{userId} AND \"id\"=#{taskId} LIMIT 1 FOR UPDATE")
  GenerationTaskRecord lockTask(@Param("userId") String userId, @Param("taskId") String taskId);
  @Select("SELECT * FROM \"GenerationTask\" WHERE \"id\"=#{taskId} LIMIT 1")
  GenerationTaskRecord findTaskForWorker(String taskId);
  @Insert("INSERT INTO \"GenerationTask\" (\"id\",\"sessionId\",\"userId\",\"status\",\"prompt\",\"model\",\"ratio\",\"resolution\",\"width\",\"height\",\"imageCount\",\"imageIds\",\"unitCost\",\"totalCost\",\"idempotencyKey\",\"settlementVersion\",\"preflightId\",\"consumedCost\",\"pricingRuleId\",\"pricingRuleVersion\",\"createdAt\",\"updatedAt\") VALUES (#{id},#{sessionId},#{userId},'QUEUED',#{prompt},#{model},#{ratio}::\"GenerationRatio\",#{resolution}::\"GenerationResolution\",#{width},#{height},#{imageCount},CAST(#{imageIds} AS JSONB),#{unitCost},#{totalCost},#{idempotencyKey},2,#{preflightId},0,#{ruleId},#{ruleVersion},CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)")
  int insertTaskV2(@Param("id") String id, @Param("sessionId") String sessionId, @Param("userId") String userId,
      @Param("prompt") String prompt, @Param("model") String model, @Param("ratio") String ratio,
      @Param("resolution") String resolution, @Param("width") Integer width, @Param("height") Integer height,
      @Param("imageCount") int imageCount, @Param("imageIds") String imageIds, @Param("unitCost") int unitCost,
      @Param("totalCost") int totalCost, @Param("idempotencyKey") String idempotencyKey, @Param("preflightId") String preflightId,
      @Param("ruleId") String ruleId, @Param("ruleVersion") Integer ruleVersion);
  @Select("SELECT * FROM \"GenerationPreflight\" WHERE \"id\"=#{id} AND \"userId\"=#{userId} LIMIT 1")
  GenerationPreflightRecord findPreflight(@Param("userId") String userId, @Param("id") String id);
  @Select("SELECT * FROM \"GenerationPreflight\" WHERE \"id\"=#{id} LIMIT 1") GenerationPreflightRecord findPreflightForWorker(String id);
  @Select("SELECT * FROM \"GenerationPreflight\" WHERE \"userId\"=#{userId} AND \"idempotencyKey\"=#{key} LIMIT 1")
  GenerationPreflightRecord findPreflightByIdempotency(@Param("userId") String userId, @Param("key") String key);
  @Select("SELECT * FROM \"GenerationPreflight\" WHERE \"userId\"=#{userId} AND \"id\"=#{id} LIMIT 1 FOR UPDATE")
  GenerationPreflightRecord lockPreflight(@Param("userId") String userId, @Param("id") String id);
  @Insert("INSERT INTO \"GenerationPreflight\" (\"id\",\"userId\",\"sessionId\",\"status\",\"idempotencyKey\",\"draftKey\",\"inputHash\",\"inputJson\",\"ratio\",\"resolution\",\"width\",\"height\",\"pricingRuleId\",\"pricingRuleVersion\",\"unitCost\") VALUES (#{id},#{userId},#{sessionId},'QUEUED',#{idempotencyKey},#{draftKey},#{inputHash},CAST(#{inputJson} AS JSONB),#{ratio},#{resolution},#{width},#{height},#{ruleId},#{ruleVersion},#{unitCost})")
  int insertPreflight(@Param("id") String id, @Param("userId") String userId, @Param("sessionId") String sessionId,
      @Param("idempotencyKey") String idempotencyKey, @Param("draftKey") String draftKey, @Param("inputHash") String inputHash,
      @Param("inputJson") String inputJson, @Param("ratio") String ratio, @Param("resolution") String resolution,
      @Param("width") Integer width, @Param("height") Integer height, @Param("ruleId") String ruleId,
      @Param("ruleVersion") Integer ruleVersion, @Param("unitCost") int unitCost);
  @Insert("INSERT INTO \"GenerationSession\" (\"id\",\"userId\",\"title\",\"draft\",\"createdAt\",\"updatedAt\") VALUES (#{id},#{userId},#{title},CAST(#{draft} AS JSONB),CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)")
  int insertSession(@Param("id") String id, @Param("userId") String userId, @Param("title") String title, @Param("draft") String draft);
  @Update("UPDATE \"GenerationPreflight\" SET \"status\"='PLANNING',\"updatedAt\"=CURRENT_TIMESTAMP WHERE \"id\"=#{id} AND \"status\"='QUEUED'") int claimPreflight(String id);
  @Update("UPDATE \"GenerationPreflight\" SET \"status\"='SUPERSEDED',\"updatedAt\"=CURRENT_TIMESTAMP WHERE \"userId\"=#{userId} AND \"draftKey\"=#{draftKey} AND \"inputHash\"<>#{inputHash} AND \"status\" IN ('QUEUED','PLANNING','READY')")
  int supersedePreflights(@Param("userId") String userId, @Param("draftKey") String draftKey, @Param("inputHash") String inputHash);
  @Update("UPDATE \"GenerationPreflight\" SET \"status\"=#{status}::\"GenerationPreflightStatus\",\"planJson\"=CAST(#{planJson} AS JSONB),\"planSchemaVersion\"=#{schemaVersion},\"collectionMode\"=#{collectionMode}::\"GenerationCollectionMode\",\"imageCount\"=#{imageCount},\"estimatedCost\"=#{estimatedCost},\"errorCode\"=#{errorCode},\"errorDetails\"=#{errorDetails},\"readyAt\"=CASE WHEN #{status}='READY' THEN CURRENT_TIMESTAMP ELSE \"readyAt\" END,\"expiresAt\"=CASE WHEN #{status}='READY' THEN CURRENT_TIMESTAMP + INTERVAL '10 minutes' ELSE \"expiresAt\" END,\"updatedAt\"=CURRENT_TIMESTAMP WHERE \"id\"=#{id} AND \"status\" IN ('QUEUED','PLANNING')")
  int finishPreflight(@Param("id") String id, @Param("status") String status, @Param("planJson") String planJson,
      @Param("schemaVersion") String schemaVersion, @Param("collectionMode") String collectionMode,
      @Param("imageCount") Integer imageCount, @Param("estimatedCost") Integer estimatedCost,
      @Param("errorCode") String errorCode, @Param("errorDetails") String errorDetails);
  @Update("UPDATE \"GenerationPreflight\" SET \"ratio\"=#{ratio},\"width\"=#{width},\"height\"=#{height},\"updatedAt\"=CURRENT_TIMESTAMP WHERE \"id\"=#{id} AND \"status\"='PLANNING'")
  int updatePreflightOutput(@Param("id") String id, @Param("ratio") String ratio,
      @Param("width") Integer width, @Param("height") Integer height);
  @Update("UPDATE \"GenerationPreflight\" SET \"status\"='CONSUMED',\"consumedAt\"=CURRENT_TIMESTAMP,\"taskId\"=#{taskId},\"updatedAt\"=CURRENT_TIMESTAMP WHERE \"id\"=#{id} AND \"userId\"=#{userId} AND \"status\"='READY' AND \"expiresAt\">CURRENT_TIMESTAMP AND \"taskId\" IS NULL")
  int consumePreflight(@Param("id") String id, @Param("userId") String userId, @Param("taskId") String taskId);
  @Insert("INSERT INTO \"GenerationPreflightEvent\" (\"preflightId\",\"type\",\"status\",\"payload\") VALUES (#{id},#{type},#{status}::\"GenerationPreflightStatus\",CAST(#{payload} AS JSONB))")
  int insertPreflightEvent(@Param("id") String id, @Param("type") String type, @Param("status") String status, @Param("payload") String payload);
  @Select("SELECT \"id\",\"preflightId\",\"type\",\"status\",\"payload\",\"createdAt\" FROM \"GenerationPreflightEvent\" WHERE \"preflightId\"=#{id} AND \"id\">#{afterId} ORDER BY \"id\" LIMIT #{limit}")
  List<Map<String,Object>> listPreflightEvents(@Param("id") String id, @Param("afterId") long afterId, @Param("limit") int limit);

  @Insert("INSERT INTO \"GenerationPlan\" (\"id\",\"taskId\",\"schemaVersion\",\"status\",\"inputHash\",\"collectionJson\",\"collectionMode\",\"createdAt\",\"updatedAt\") VALUES (#{id},#{taskId},#{schemaVersion},'RUNNABLE'::\"GenerationPlanStatus\",#{inputHash},CAST(#{collectionJson} AS JSONB),#{collectionMode}::\"GenerationCollectionMode\",CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)")
  int insertCollectionPlan(@Param("id") String id, @Param("taskId") String taskId, @Param("schemaVersion") String schemaVersion,
      @Param("inputHash") String inputHash, @Param("collectionJson") String collectionJson, @Param("collectionMode") String collectionMode);
  @Insert("INSERT INTO \"GenerationResultSlot\" (\"taskId\",\"slotIndex\",\"label\",\"role\",\"intentSummary\",\"promptHash\") VALUES (#{taskId},#{index},#{label},#{role},#{intent},#{promptHash})")
  int insertResultSlot(@Param("taskId") String taskId, @Param("index") int index, @Param("label") String label,
      @Param("role") String role, @Param("intent") String intent, @Param("promptHash") String promptHash);
  @Select("SELECT * FROM \"GenerationResultSlot\" WHERE \"taskId\"=#{taskId} ORDER BY \"slotIndex\"") List<GenerationResultSlotRecord> listResultSlots(String taskId);
  @Select("SELECT COUNT(*) FROM \"GenerationResultSlot\" WHERE \"taskId\"=#{taskId} AND \"status\" <> 'SUCCEEDED'") int countIncompleteSlots(String taskId);

  @Insert("INSERT INTO \"GenerationExecution\" (\"id\",\"taskId\",\"kind\",\"status\",\"idempotencyKey\",\"slotIndexes\",\"reservedAmount\",\"pricingRuleId\",\"pricingRuleVersion\") VALUES (#{id},#{taskId},#{kind}::\"GenerationExecutionKind\",'QUEUED',#{key},CAST(#{slots} AS JSONB),#{amount},#{ruleId},#{ruleVersion})")
  int insertExecution(@Param("id") String id, @Param("taskId") String taskId, @Param("kind") String kind,
      @Param("key") String key, @Param("slots") String slots, @Param("amount") int amount,
      @Param("ruleId") String ruleId, @Param("ruleVersion") Integer ruleVersion);
  @Select("SELECT * FROM \"GenerationExecution\" WHERE \"id\"=#{id} LIMIT 1") GenerationExecutionRecord findExecution(String id);
  @Select("SELECT * FROM \"GenerationExecution\" WHERE \"id\"=#{id} FOR UPDATE") GenerationExecutionRecord lockExecution(String id);
  @Select("SELECT * FROM \"GenerationExecution\" WHERE \"taskId\"=#{taskId} AND \"status\" IN ('QUEUED','GENERATING') LIMIT 1") GenerationExecutionRecord findActiveExecution(String taskId);
  @Select("SELECT * FROM \"GenerationExecution\" WHERE \"status\" = 'GENERATING' AND \"updatedAt\" < #{cutoff} ORDER BY \"updatedAt\" LIMIT #{limit}")
  List<GenerationExecutionRecord> listStaleExecutions(@Param("cutoff") java.time.Instant cutoff, @Param("limit") int limit);
  @Select("SELECT * FROM \"GenerationExecution\" WHERE \"taskId\"=#{taskId} AND \"idempotencyKey\"=#{key} LIMIT 1") GenerationExecutionRecord findExecutionByIdempotency(@Param("taskId") String taskId, @Param("key") String key);
  @Select("SELECT * FROM \"GenerationExecution\" WHERE \"status\"='QUEUED' AND \"queueMessageId\" IS NULL ORDER BY \"createdAt\" LIMIT #{limit}") List<GenerationExecutionRecord> listPendingExecutions(int limit);
  @Select("SELECT * FROM \"GenerationPreflight\" WHERE \"status\"='QUEUED' ORDER BY \"createdAt\" LIMIT #{limit}") List<GenerationPreflightRecord> listPendingPreflights(int limit);
  @Select("SELECT * FROM \"GenerationPreflight\" WHERE \"status\"='PLANNING' AND \"updatedAt\" < #{cutoff} ORDER BY \"updatedAt\" LIMIT #{limit}")
  List<GenerationPreflightRecord> listStalePlanningPreflights(@Param("cutoff") java.time.Instant cutoff, @Param("limit") int limit);
  @Update("UPDATE \"GenerationPreflight\" SET \"status\"='FAILED',\"errorCode\"='GENERATION_PREFLIGHT_TIMEOUT',\"errorDetails\"='生成准备超时，请重试',\"updatedAt\"=CURRENT_TIMESTAMP WHERE \"id\"=#{id} AND \"status\"='PLANNING' AND \"updatedAt\" < #{cutoff}")
  int timeoutPreflight(@Param("id") String id, @Param("cutoff") java.time.Instant cutoff);
  @Update("UPDATE \"GenerationExecution\" SET \"status\"='GENERATING',\"attempts\"=\"attempts\"+1,\"updatedAt\"=CURRENT_TIMESTAMP WHERE \"id\"=#{id} AND \"status\" IN ('QUEUED','GENERATING') AND \"attempts\" < #{attemptNumber}") int claimExecution(@Param("id") String id, @Param("attemptNumber") int attemptNumber);
  @Update("UPDATE \"GenerationExecution\" SET \"queueMessageId\"=#{messageId},\"updatedAt\"=CURRENT_TIMESTAMP WHERE \"id\"=#{id}") int setExecutionQueueMessage(@Param("id") String id, @Param("messageId") String messageId);
  @Update("UPDATE \"GenerationResultSlot\" SET \"status\"='GENERATING',\"activeExecutionId\"=#{executionId},\"slotAttempt\"=\"slotAttempt\"+1,\"startedAt\"=COALESCE(\"startedAt\",CURRENT_TIMESTAMP),\"updatedAt\"=CURRENT_TIMESTAMP WHERE \"taskId\"=#{taskId} AND \"slotIndex\"=#{index} AND (\"status\"='WAITING' OR (\"status\"='GENERATING' AND \"activeExecutionId\"=#{executionId}))")
  int claimResultSlot(@Param("taskId") String taskId, @Param("index") int index, @Param("executionId") String executionId);
  @Select("SELECT * FROM \"GenerationResultSlot\" WHERE \"taskId\"=#{taskId} AND \"slotIndex\"=#{index} FOR UPDATE") GenerationResultSlotRecord lockResultSlot(@Param("taskId") String taskId, @Param("index") int index);
  @Update("UPDATE \"GenerationResultSlot\" SET \"status\"=#{status}::\"GenerationSlotStatus\",\"resultId\"=#{resultId},\"errorCode\"=#{errorCode},\"errorMessage\"=#{errorMessage},\"completedAt\"=CURRENT_TIMESTAMP,\"updatedAt\"=CURRENT_TIMESTAMP WHERE \"taskId\"=#{taskId} AND \"slotIndex\"=#{index} AND \"status\"='GENERATING' AND \"activeExecutionId\"=#{executionId}")
  int finishResultSlot(@Param("taskId") String taskId, @Param("index") int index, @Param("status") String status,
      @Param("resultId") String resultId, @Param("errorCode") String errorCode, @Param("errorMessage") String errorMessage,
      @Param("executionId") String executionId);
  @Select("SELECT COUNT(*) FROM \"GenerationResultSlot\" WHERE \"taskId\"=#{taskId} AND \"status\"='SUCCEEDED'")
  int countSucceededSlots(String taskId);
  @Select("SELECT COUNT(*) FROM \"GenerationResultSlot\" WHERE \"taskId\"=#{taskId} AND \"status\"='FAILED'")
  int countFailedSlots(String taskId);
  @Update("UPDATE \"GenerationExecution\" SET \"consumedAmount\"=\"consumedAmount\"+#{amount},\"updatedAt\"=CURRENT_TIMESTAMP WHERE \"id\"=#{id} AND \"status\"='GENERATING' AND \"reservedAmount\">=\"consumedAmount\"+\"releasedAmount\"+#{amount}") int addExecutionConsumed(@Param("id") String id, @Param("amount") int amount);
  @Update("UPDATE \"GenerationExecution\" SET \"releasedAmount\"=\"releasedAmount\"+#{amount},\"status\"=#{status}::\"GenerationExecutionStatus\",\"errorCode\"=#{errorCode},\"updatedAt\"=CURRENT_TIMESTAMP WHERE \"id\"=#{id} AND \"status\" IN ('QUEUED','GENERATING')") int finishExecution(@Param("id") String id, @Param("amount") int amount, @Param("status") String status, @Param("errorCode") String errorCode);
  @Update("UPDATE \"GenerationTask\" SET \"consumedCost\"=\"consumedCost\"+#{amount},\"updatedAt\"=CURRENT_TIMESTAMP WHERE \"id\"=#{taskId} AND \"settlementVersion\"=2 AND \"consumedCost\"+#{amount}<=\"totalCost\"") int addTaskConsumed(@Param("taskId") String taskId, @Param("amount") int amount);
  @Update("UPDATE \"GenerationTask\" SET \"status\"=#{status}::\"GenerationTaskStatus\",\"errorCode\"=#{errorCode},\"errorMessage\"=#{errorMessage},\"completedAt\"=CASE WHEN #{terminal} THEN CURRENT_TIMESTAMP ELSE NULL END,\"updatedAt\"=CURRENT_TIMESTAMP WHERE \"id\"=#{taskId} AND \"settlementVersion\"=2") int setTaskV2Status(@Param("taskId") String taskId, @Param("status") String status, @Param("errorCode") String errorCode, @Param("errorMessage") String errorMessage, @Param("terminal") boolean terminal);
  @Update("UPDATE \"GenerationTask\" SET \"status\"='QUEUED',\"errorCode\"=NULL,\"errorMessage\"=NULL,\"completedAt\"=NULL,\"updatedAt\"=CURRENT_TIMESTAMP WHERE \"id\"=#{taskId} AND \"settlementVersion\"=2 AND \"status\" IN ('FAILED','PARTIALLY_SUCCEEDED','CANCELLED')") int queueTaskForContinuation(@Param("taskId") String taskId);
  @Update("UPDATE \"GenerationTask\" SET \"status\"='CANCELLED',\"errorCode\"='TASK_CANCELLED',\"errorMessage\"='任务已取消',\"completedAt\"=CURRENT_TIMESTAMP,\"updatedAt\"=CURRENT_TIMESTAMP WHERE \"id\"=#{taskId} AND \"userId\"=#{userId} AND \"settlementVersion\"=2 AND \"status\" IN ('QUEUED','GENERATING') AND NOT EXISTS (SELECT 1 FROM \"GenerationExecution\" WHERE \"taskId\"=#{taskId} AND \"status\" IN ('QUEUED','GENERATING'))") int cancelTaskV2(@Param("userId") String userId, @Param("taskId") String taskId);
  @Update("UPDATE \"GenerationResultSlot\" SET \"status\"='WAITING',\"activeExecutionId\"=#{executionId},\"errorCode\"=NULL,\"errorMessage\"=NULL,\"startedAt\"=NULL,\"completedAt\"=NULL,\"updatedAt\"=CURRENT_TIMESTAMP WHERE \"taskId\"=#{taskId} AND \"status\"<>'SUCCEEDED'") int resetMissingSlots(@Param("taskId") String taskId, @Param("executionId") String executionId);
  @Update("UPDATE \"GenerationResultSlot\" SET \"status\"='CANCELLED',\"completedAt\"=CURRENT_TIMESTAMP,\"updatedAt\"=CURRENT_TIMESTAMP WHERE \"taskId\"=#{taskId} AND \"status\"<>'SUCCEEDED'") int cancelMissingSlots(String taskId);
  @Update("UPDATE \"GenerationResultSlot\" SET \"status\"='FAILED',\"activeExecutionId\"=#{executionId},\"errorCode\"='STALE_EXECUTION_RECOVERED',\"errorMessage\"='生成任务超时，已自动结束',\"completedAt\"=CURRENT_TIMESTAMP,\"updatedAt\"=CURRENT_TIMESTAMP WHERE \"taskId\"=#{taskId} AND \"status\"<>'SUCCEEDED'")
  int failStaleExecutionSlots(@Param("taskId") String taskId, @Param("executionId") String executionId);
  @Insert("INSERT INTO \"GenerationTaskEvent\" (\"taskId\",\"type\",\"status\",\"payload\",\"createdAt\") VALUES (#{taskId},#{type},#{status}::\"GenerationTaskStatus\",CAST(#{payload} AS JSONB),CURRENT_TIMESTAMP)")
  int insertTaskEvent(@Param("taskId") String taskId, @Param("type") String type,
      @Param("status") String status, @Param("payload") String payload);
}
