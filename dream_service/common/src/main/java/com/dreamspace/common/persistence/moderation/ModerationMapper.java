package com.dreamspace.common.persistence.moderation;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ModerationMapper {
  @Insert("INSERT INTO \"ModerationReviewCase\" (\"id\",\"taskId\",\"resultId\",\"userId\",\"stage\",\"status\",\"reasonCode\",\"evidenceJson\",\"model\",\"modelVersion\") VALUES (#{id},#{taskId},#{resultId},#{userId},#{stage},'REJECTED',#{reasonCode},CAST(#{evidenceJson} AS JSONB),#{model},#{modelVersion}) ON CONFLICT (\"taskId\",\"stage\") DO NOTHING")
  int insertRejectedCase(@Param("id") String id, @Param("taskId") String taskId,
      @Param("resultId") String resultId, @Param("userId") String userId,
      @Param("stage") String stage, @Param("reasonCode") String reasonCode,
      @Param("evidenceJson") String evidenceJson, @Param("model") String model,
      @Param("modelVersion") String modelVersion);

  @Select("SELECT * FROM \"ModerationReviewCase\" WHERE \"id\"=#{id}")
  ModerationReviewCaseRecord findCase(@Param("id") String id);

  @Select("SELECT * FROM \"ModerationReviewCase\" WHERE \"id\"=#{id} AND \"userId\"=#{userId}")
  ModerationReviewCaseRecord findOwnedCase(@Param("id") String id, @Param("userId") String userId);

  @Select("SELECT * FROM \"ModerationReviewCase\" WHERE \"userId\"=#{userId} ORDER BY \"createdAt\" DESC LIMIT #{limit}")
  List<ModerationReviewCaseRecord> listOwned(@Param("userId") String userId, @Param("limit") int limit);

  @Select("<script>SELECT * FROM \"ModerationReviewCase\" <if test='status != null'>WHERE \"status\"=#{status}</if> ORDER BY \"createdAt\" DESC LIMIT #{limit} OFFSET #{offset}</script>")
  List<ModerationReviewCaseRecord> listCases(@Param("status") String status,
      @Param("limit") int limit, @Param("offset") int offset);

  @Select("<script>SELECT COUNT(*) FROM \"ModerationReviewCase\" <if test='status != null'>WHERE \"status\"=#{status}</if></script>")
  long countCases(@Param("status") String status);

  @Select("SELECT COUNT(*) FROM \"ModerationReviewCase\" WHERE \"status\" IN ('PENDING','APPEALED')")
  long countPending();

  @Insert("INSERT INTO \"ModerationAppeal\" (\"id\",\"caseId\",\"userId\",\"reason\") VALUES (#{id},#{caseId},#{userId},#{reason})")
  int insertAppeal(@Param("id") String id, @Param("caseId") String caseId,
      @Param("userId") String userId, @Param("reason") String reason);

  @Select("SELECT * FROM \"ModerationAppeal\" WHERE \"caseId\"=#{caseId}")
  ModerationAppealRecord findAppeal(@Param("caseId") String caseId);

  @Update("UPDATE \"ModerationReviewCase\" SET \"status\"='APPEALED',\"version\"=\"version\"+1 WHERE \"id\"=#{id} AND \"userId\"=#{userId} AND \"status\"='REJECTED'")
  int markAppealed(@Param("id") String id, @Param("userId") String userId);

  @Update("UPDATE \"ModerationReviewCase\" SET \"status\"=#{status},\"resolvedAt\"=CURRENT_TIMESTAMP,\"version\"=\"version\"+1 WHERE \"id\"=#{id} AND \"version\"=#{version} AND \"status\" IN ('PENDING','REJECTED','APPEALED')")
  int resolveCase(@Param("id") String id, @Param("version") int version,
      @Param("status") String status);

  @Update("UPDATE \"ModerationAppeal\" SET \"status\"=#{status},\"resolvedAt\"=CURRENT_TIMESTAMP WHERE \"caseId\"=#{caseId} AND \"status\"='PENDING'")
  int resolveAppeal(@Param("caseId") String caseId, @Param("status") String status);

  @Insert("INSERT INTO \"ModerationAuditEvent\" (\"id\",\"caseId\",\"actorId\",\"actorType\",\"action\",\"beforeJson\",\"afterJson\") VALUES (#{id},#{caseId},#{actorId},#{actorType},#{action},CAST(#{beforeJson} AS JSONB),CAST(#{afterJson} AS JSONB))")
  int insertAudit(@Param("id") String id, @Param("caseId") String caseId,
      @Param("actorId") String actorId, @Param("actorType") String actorType,
      @Param("action") String action, @Param("beforeJson") String beforeJson,
      @Param("afterJson") String afterJson);

  @Select("SELECT * FROM \"ModerationAuditEvent\" WHERE \"caseId\"=#{caseId} ORDER BY \"createdAt\" ASC")
  List<ModerationAuditEventRecord> listAudit(@Param("caseId") String caseId);
}
