package com.dreamspace.persistence.generation;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface GenerationMapper {
  @Select("SELECT * FROM \"GenerationTask\" WHERE \"id\" = #{id} LIMIT 1") GenerationTaskRecord findTask(String id);
  @Select("SELECT * FROM \"GenerationTask\" WHERE \"userId\" = #{userId} AND \"idempotencyKey\" = #{key} LIMIT 1")
  GenerationTaskRecord findByIdempotencyKey(@Param("userId") String userId, @Param("key") String key);
  @Select("SELECT * FROM \"GenerationTaskEvent\" WHERE \"taskId\" = #{taskId} AND \"id\" > #{afterId} ORDER BY \"id\" ASC LIMIT #{limit}")
  List<GenerationTaskEventRecord> listEvents(@Param("taskId") String taskId, @Param("afterId") long afterId, @Param("limit") int limit);

  @Update("UPDATE \"GenerationTask\" SET \"status\" = 'GENERATING', \"attempts\" = \"attempts\" + 1, \"lastAttemptKey\" = #{attemptKey}, \"startedAt\" = COALESCE(\"startedAt\", CURRENT_TIMESTAMP), \"updatedAt\" = CURRENT_TIMESTAMP WHERE \"id\" = #{taskId} AND \"status\" = 'QUEUED' AND (\"lastAttemptKey\" IS NULL OR \"lastAttemptKey\" <> #{attemptKey})")
  int claimQueuedTask(@Param("taskId") String taskId, @Param("attemptKey") String attemptKey);
  @Update("UPDATE \"GenerationTask\" SET \"queueJobId\" = #{messageId}, \"updatedAt\" = CURRENT_TIMESTAMP WHERE \"id\" = #{taskId}")
  int setQueueMessageId(@Param("taskId") String taskId, @Param("messageId") String messageId);
  @Insert("INSERT INTO \"GenerationTaskEvent\" (\"taskId\",\"type\",\"status\",\"payload\",\"createdAt\") VALUES (#{taskId},#{type},#{status}::\"GenerationTaskStatus\",CAST(#{payload} AS JSONB),CURRENT_TIMESTAMP)")
  int insertEvent(@Param("taskId") String taskId, @Param("type") String type, @Param("status") String status, @Param("payload") String payload);
}
