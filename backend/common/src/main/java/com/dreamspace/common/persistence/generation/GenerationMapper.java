package com.dreamspace.common.persistence.generation;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Delete;

@Mapper
public interface GenerationMapper {
  @Select("SELECT * FROM \"GenerationSession\" WHERE \"id\" = #{id} AND \"userId\" = #{userId} LIMIT 1")
  GenerationSessionRecord findSession(@Param("userId") String userId, @Param("id") String id);
  @Select("SELECT * FROM \"GenerationSession\" WHERE \"userId\" = #{userId} ORDER BY \"updatedAt\" DESC")
  List<GenerationSessionRecord> listSessions(String userId);
  @Insert("INSERT INTO \"GenerationSession\" (\"id\",\"userId\",\"title\",\"draft\",\"createdAt\",\"updatedAt\") VALUES (#{id},#{userId},#{title},CAST(#{draft} AS JSONB),CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)")
  int insertSession(@Param("id") String id, @Param("userId") String userId, @Param("title") String title, @Param("draft") String draft);
  @Update("UPDATE \"GenerationSession\" SET \"title\" = #{title}, \"updatedAt\" = CURRENT_TIMESTAMP WHERE \"id\" = #{id} AND \"userId\" = #{userId}")
  int renameSession(@Param("userId") String userId, @Param("id") String id, @Param("title") String title);
  @Update("UPDATE \"GenerationSession\" SET \"draft\" = CAST(#{draft} AS JSONB), \"updatedAt\" = CURRENT_TIMESTAMP WHERE \"id\" = #{id} AND \"userId\" = #{userId}")
  int updateDraft(@Param("userId") String userId, @Param("id") String id, @Param("draft") String draft);
  @Delete("DELETE FROM \"GenerationSession\" WHERE \"id\" = #{id} AND \"userId\" = #{userId} AND NOT EXISTS (SELECT 1 FROM \"GenerationTask\" WHERE \"sessionId\" = #{id} AND \"status\" IN ('QUEUED','GENERATING'))")
  int deleteSession(@Param("userId") String userId, @Param("id") String id);
  @Select("SELECT COUNT(*) FROM \"GenerationTask\" WHERE \"sessionId\" = #{id} AND \"status\" IN ('QUEUED','GENERATING')")
  int countActiveTasks(String id);

  @Select("SELECT * FROM \"GenerationTask\" WHERE \"id\" = #{id} LIMIT 1") GenerationTaskRecord findTask(String id);
  @Select("SELECT * FROM \"GenerationTask\" WHERE \"userId\" = #{userId} AND \"idempotencyKey\" = #{key} LIMIT 1")
  GenerationTaskRecord findByIdempotencyKey(@Param("userId") String userId, @Param("key") String key);
  @Select("SELECT * FROM \"GenerationTask\" WHERE \"sessionId\" = #{sessionId} ORDER BY \"createdAt\" DESC")
  List<GenerationTaskRecord> listTasks(String sessionId);
  @Select("SELECT * FROM \"GenerationTask\" WHERE \"status\" = 'QUEUED' AND \"queueJobId\" IS NULL ORDER BY \"createdAt\" ASC LIMIT #{limit}")
  List<GenerationTaskRecord> listPendingQueuePublish(int limit);
  @Insert("INSERT INTO \"GenerationTask\" (\"id\",\"sessionId\",\"userId\",\"status\",\"prompt\",\"model\",\"ratio\",\"resolution\",\"imageCount\",\"referenceImageUrls\",\"unitCost\",\"totalCost\",\"idempotencyKey\",\"createdAt\",\"updatedAt\") VALUES (#{id},#{sessionId},#{userId},'QUEUED',#{prompt},#{model},#{ratio}::\"GenerationRatio\",#{resolution}::\"GenerationResolution\",#{imageCount},CAST(#{referenceImageUrls} AS JSONB),#{unitCost},#{totalCost},#{idempotencyKey},CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)")
  int insertTask(@Param("id") String id, @Param("sessionId") String sessionId, @Param("userId") String userId,
      @Param("prompt") String prompt, @Param("model") String model, @Param("ratio") String ratio,
      @Param("resolution") String resolution, @Param("imageCount") int imageCount,
      @Param("referenceImageUrls") String referenceImageUrls, @Param("unitCost") int unitCost,
      @Param("totalCost") int totalCost, @Param("idempotencyKey") String idempotencyKey);
  @Update("UPDATE \"GenerationTask\" SET \"status\" = #{status}::\"GenerationTaskStatus\", \"errorCode\" = #{errorCode}, \"errorMessage\" = #{errorMessage}, \"completedAt\" = CASE WHEN #{terminal} THEN CURRENT_TIMESTAMP ELSE \"completedAt\" END, \"updatedAt\" = CURRENT_TIMESTAMP WHERE \"id\" = #{taskId} AND \"status\" = #{fromStatus}::\"GenerationTaskStatus\"")
  int transition(@Param("taskId") String taskId, @Param("fromStatus") String fromStatus, @Param("status") String status,
      @Param("errorCode") String errorCode, @Param("errorMessage") String errorMessage, @Param("terminal") boolean terminal);
  @Update("UPDATE \"GenerationTask\" SET \"status\" = 'CANCELLED', \"errorCode\" = 'TASK_CANCELLED', \"errorMessage\" = '任务已取消', \"completedAt\" = CURRENT_TIMESTAMP, \"updatedAt\" = CURRENT_TIMESTAMP WHERE \"id\" = #{taskId} AND \"userId\" = #{userId} AND \"status\" IN ('QUEUED','GENERATING')")
  int cancel(@Param("userId") String userId, @Param("taskId") String taskId);
  @Select("SELECT * FROM \"GenerationResult\" WHERE \"taskId\" = #{taskId} ORDER BY \"index\" ASC")
  List<GenerationResultRecord> listResults(String taskId);
  @Select("SELECT r.* FROM \"GenerationResult\" r JOIN \"GenerationTask\" t ON t.\"id\" = r.\"taskId\" WHERE r.\"id\" = #{resultId} AND t.\"userId\" = #{userId} LIMIT 1")
  GenerationResultRecord findOwnedResult(@Param("userId") String userId, @Param("resultId") String resultId);
  @Select("SELECT * FROM \"GenerationTaskEvent\" WHERE \"taskId\" = #{taskId} AND \"id\" > #{afterId} ORDER BY \"id\" ASC LIMIT #{limit}")
  List<GenerationTaskEventRecord> listEvents(@Param("taskId") String taskId, @Param("afterId") long afterId, @Param("limit") int limit);

  @Update("UPDATE \"GenerationTask\" SET \"status\" = 'GENERATING', \"attempts\" = \"attempts\" + 1, \"lastAttemptKey\" = #{attemptKey}, \"startedAt\" = COALESCE(\"startedAt\", CURRENT_TIMESTAMP), \"updatedAt\" = CURRENT_TIMESTAMP WHERE \"id\" = #{taskId} AND \"status\" IN ('QUEUED','GENERATING') AND (\"lastAttemptKey\" IS NULL OR \"lastAttemptKey\" <> #{attemptKey})")
  int claimQueuedTask(@Param("taskId") String taskId, @Param("attemptKey") String attemptKey);
  @Update("UPDATE \"GenerationTask\" SET \"inputModerationStatus\" = #{status}::\"ModerationStatus\", \"updatedAt\" = CURRENT_TIMESTAMP WHERE \"id\" = #{taskId} AND \"status\" = 'GENERATING'")
  int updateInputModeration(@Param("taskId") String taskId, @Param("status") String status);
  @Update("UPDATE \"GenerationTask\" SET \"outputModerationStatus\" = #{status}::\"ModerationStatus\", \"updatedAt\" = CURRENT_TIMESTAMP WHERE \"id\" = #{taskId} AND \"status\" = 'GENERATING'")
  int updateOutputModeration(@Param("taskId") String taskId, @Param("status") String status);
  @Insert("INSERT INTO \"GenerationResult\" (\"id\",\"taskId\",\"index\",\"imagePath\",\"objectKey\",\"thumbnailObjectKey\",\"checksumSha256\",\"width\",\"height\",\"mimeType\",\"byteSize\",\"thumbnailWidth\",\"thumbnailHeight\",\"thumbnailByteSize\",\"moderationStatus\",\"isAiGenerated\",\"createdAt\") VALUES (#{id},#{taskId},#{index},#{imagePath},#{objectKey},#{thumbnailObjectKey},#{checksumSha256},#{width},#{height},#{mimeType},#{byteSize},#{thumbnailWidth},#{thumbnailHeight},#{thumbnailByteSize},'APPROVED',#{aiGenerated},CURRENT_TIMESTAMP) ON CONFLICT (\"taskId\",\"index\") DO NOTHING")
  int insertResult(@Param("id") String id, @Param("taskId") String taskId, @Param("index") int index,
      @Param("imagePath") String imagePath, @Param("objectKey") String objectKey,
      @Param("thumbnailObjectKey") String thumbnailObjectKey, @Param("checksumSha256") String checksumSha256,
      @Param("width") int width, @Param("height") int height, @Param("mimeType") String mimeType,
      @Param("byteSize") int byteSize, @Param("thumbnailWidth") int thumbnailWidth,
      @Param("thumbnailHeight") int thumbnailHeight, @Param("thumbnailByteSize") int thumbnailByteSize,
      @Param("aiGenerated") boolean aiGenerated);
  @Insert("INSERT INTO \"GenerationDeadLetter\" (\"id\",\"taskId\",\"errorCode\",\"errorMessage\",\"attempts\",\"payload\",\"createdAt\") VALUES (#{id},#{taskId},#{errorCode},#{errorMessage},#{attempts},CAST(#{payload} AS JSONB),CURRENT_TIMESTAMP) ON CONFLICT (\"taskId\") DO UPDATE SET \"errorCode\" = EXCLUDED.\"errorCode\", \"errorMessage\" = EXCLUDED.\"errorMessage\", \"attempts\" = EXCLUDED.\"attempts\", \"payload\" = EXCLUDED.\"payload\"")
  int upsertDeadLetter(@Param("id") String id, @Param("taskId") String taskId,
      @Param("errorCode") String errorCode, @Param("errorMessage") String errorMessage,
      @Param("attempts") int attempts, @Param("payload") String payload);
  @Update("UPDATE \"GenerationSession\" SET \"updatedAt\" = CURRENT_TIMESTAMP WHERE \"id\" = #{sessionId}")
  int touchSession(String sessionId);
  @Update("UPDATE \"GenerationTask\" SET \"queueJobId\" = #{messageId}, \"updatedAt\" = CURRENT_TIMESTAMP WHERE \"id\" = #{taskId}")
  int setQueueMessageId(@Param("taskId") String taskId, @Param("messageId") String messageId);
  @Insert("INSERT INTO \"GenerationTaskEvent\" (\"taskId\",\"type\",\"status\",\"payload\",\"createdAt\") VALUES (#{taskId},#{type},#{status}::\"GenerationTaskStatus\",CAST(#{payload} AS JSONB),CURRENT_TIMESTAMP)")
  int insertEvent(@Param("taskId") String taskId, @Param("type") String type, @Param("status") String status, @Param("payload") String payload);
}
