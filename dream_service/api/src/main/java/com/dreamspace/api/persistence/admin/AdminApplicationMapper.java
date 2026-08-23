package com.dreamspace.api.persistence.admin;

import com.dreamspace.common.persistence.generation.GenerationResultRecord;
import com.dreamspace.api.persistence.inspiration.InspirationRecord;
import com.dreamspace.common.persistence.reconciliation.QuotaReconciliationRunRecord;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectProvider;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AdminApplicationMapper {
  @SelectProvider(type = AdminApplicationSql.class, method = "listTasks")
  List<AdminTaskRecord> listTasks(@Param("status") String status, @Param("model") String model,
      @Param("query") String query, @Param("createdFrom") Instant createdFrom,
      @Param("createdTo") Instant createdTo, @Param("limit") int limit, @Param("offset") int offset);

  @SelectProvider(type = AdminApplicationSql.class, method = "countTasks")
  long countTasks(@Param("status") String status, @Param("model") String model,
      @Param("query") String query, @Param("createdFrom") Instant createdFrom,
      @Param("createdTo") Instant createdTo);

  @Select(AdminApplicationSql.TASK_SELECT + " WHERE t.\"id\"=#{id} LIMIT 1")
  AdminTaskRecord findTask(String id);

  @Select("SELECT * FROM \"GenerationResult\" WHERE \"taskId\"=#{taskId} ORDER BY \"index\"")
  List<GenerationResultRecord> listTaskResults(String taskId);

  @Select("SELECT * FROM \"GenerationResult\" WHERE \"id\"=#{id} LIMIT 1")
  GenerationResultRecord findResult(String id);

  @Select("SELECT \"errorCode\",\"errorMessage\",\"attempts\",\"createdAt\",\"resolvedAt\" FROM \"GenerationDeadLetter\" WHERE \"taskId\"=#{taskId} LIMIT 1")
  AdminDeadLetterRecord findDeadLetter(String taskId);

  @Select("SELECT * FROM \"QuotaReconciliationRun\" ORDER BY \"createdAt\" DESC LIMIT #{limit}")
  List<QuotaReconciliationRunRecord> listReconciliationRuns(int limit);

  @Select("SELECT \"id\",\"runId\",\"userId\",\"taskId\",\"kind\",\"status\",\"expectedAmount\",\"actualAmount\",\"repairedAt\",\"createdAt\" FROM \"QuotaReconciliationFinding\" WHERE \"runId\"=#{runId} ORDER BY \"createdAt\" DESC LIMIT 100")
  List<AdminReconciliationFindingRecord> listReconciliationFindings(String runId);

  @SelectProvider(type = AdminApplicationSql.class, method = "listInspirations")
  List<InspirationRecord> listInspirations(@Param("status") String status,
      @Param("category") String category, @Param("query") String query,
      @Param("limit") int limit, @Param("offset") int offset);

  @SelectProvider(type = AdminApplicationSql.class, method = "countInspirations")
  long countInspirations(@Param("status") String status, @Param("category") String category,
      @Param("query") String query);

  @Select(AdminApplicationSql.INSPIRATION_SELECT + " WHERE \"id\"=#{id} LIMIT 1")
  InspirationRecord findInspiration(String id);

  @Select("SELECT COUNT(*) FROM \"Inspiration\" WHERE \"slug\"=#{slug} AND (#{excludeId} IS NULL OR \"id\"<>#{excludeId})")
  int countSlug(@Param("slug") String slug, @Param("excludeId") String excludeId);

  @Insert("INSERT INTO \"Inspiration\" (\"id\",\"slug\",\"title\",\"prompt\",\"category\",\"imagePath\",\"thumbnailPath\",\"width\",\"height\",\"modelName\",\"ratio\",\"resolutionLabel\",\"authorDisplayName\",\"sourceType\",\"sourceName\",\"sourceUrl\",\"licenseBasis\",\"isAiGenerated\",\"likeCount\",\"sortOrder\",\"status\",\"publishedAt\",\"createdAt\",\"updatedAt\") VALUES (#{id},#{slug},#{title},#{prompt},#{category}::\"InspirationCategory\",#{imagePath},#{thumbnailPath},#{width},#{height},#{modelName},#{ratio},#{resolutionLabel},#{authorDisplayName},#{sourceType}::\"InspirationSourceType\",#{sourceName},#{sourceUrl},#{licenseBasis},#{aiGenerated},#{likeCount},#{sortOrder},'DRAFT',NULL,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)")
  int insertInspiration(@Param("id") String id, @Param("slug") String slug,
      @Param("title") String title, @Param("prompt") String prompt, @Param("category") String category,
      @Param("imagePath") String imagePath, @Param("thumbnailPath") String thumbnailPath,
      @Param("width") int width, @Param("height") int height, @Param("modelName") String modelName,
      @Param("ratio") String ratio, @Param("resolutionLabel") String resolutionLabel,
      @Param("authorDisplayName") String authorDisplayName, @Param("sourceType") String sourceType,
      @Param("sourceName") String sourceName, @Param("sourceUrl") String sourceUrl,
      @Param("licenseBasis") String licenseBasis, @Param("aiGenerated") boolean aiGenerated,
      @Param("likeCount") int likeCount, @Param("sortOrder") int sortOrder);

  @Update("UPDATE \"Inspiration\" SET \"slug\"=#{slug},\"title\"=#{title},\"prompt\"=#{prompt},\"category\"=#{category}::\"InspirationCategory\",\"imagePath\"=#{imagePath},\"thumbnailPath\"=#{thumbnailPath},\"width\"=#{width},\"height\"=#{height},\"modelName\"=#{modelName},\"ratio\"=#{ratio},\"resolutionLabel\"=#{resolutionLabel},\"authorDisplayName\"=#{authorDisplayName},\"sourceType\"=#{sourceType}::\"InspirationSourceType\",\"sourceName\"=#{sourceName},\"sourceUrl\"=#{sourceUrl},\"licenseBasis\"=#{licenseBasis},\"isAiGenerated\"=#{aiGenerated},\"likeCount\"=#{likeCount},\"sortOrder\"=#{sortOrder},\"updatedAt\"=CURRENT_TIMESTAMP WHERE \"id\"=#{id} AND \"updatedAt\"=#{expectedUpdatedAt}")
  int updateInspiration(@Param("id") String id, @Param("expectedUpdatedAt") Instant expectedUpdatedAt,
      @Param("slug") String slug, @Param("title") String title, @Param("prompt") String prompt,
      @Param("category") String category, @Param("imagePath") String imagePath,
      @Param("thumbnailPath") String thumbnailPath, @Param("width") int width,
      @Param("height") int height, @Param("modelName") String modelName,
      @Param("ratio") String ratio, @Param("resolutionLabel") String resolutionLabel,
      @Param("authorDisplayName") String authorDisplayName, @Param("sourceType") String sourceType,
      @Param("sourceName") String sourceName, @Param("sourceUrl") String sourceUrl,
      @Param("licenseBasis") String licenseBasis, @Param("aiGenerated") boolean aiGenerated,
      @Param("likeCount") int likeCount, @Param("sortOrder") int sortOrder);

  @Update("UPDATE \"Inspiration\" SET \"status\"=#{status}::\"InspirationStatus\",\"publishedAt\"=CASE WHEN #{status}='PUBLISHED' THEN CURRENT_TIMESTAMP ELSE NULL END,\"updatedAt\"=CURRENT_TIMESTAMP WHERE \"id\"=#{id} AND \"updatedAt\"=#{expectedUpdatedAt}")
  int transitionInspiration(@Param("id") String id, @Param("status") String status,
      @Param("expectedUpdatedAt") Instant expectedUpdatedAt);
}
