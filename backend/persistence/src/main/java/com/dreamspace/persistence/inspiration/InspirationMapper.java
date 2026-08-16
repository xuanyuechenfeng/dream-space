package com.dreamspace.persistence.inspiration;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface InspirationMapper {
  @Select("""
      SELECT "id", "slug", "title", "prompt", "category", "imagePath", "thumbnailPath",
             "width", "height", "modelName", "ratio", "resolutionLabel", "authorDisplayName",
             "sourceType", "sourceName", "sourceUrl", "licenseBasis", "isAiGenerated",
             "likeCount", "sortOrder", "status", "publishedAt", "createdAt", "updatedAt",
             "isAiGenerated" AS "aiGenerated"
      FROM "Inspiration" WHERE "status" = 'PUBLISHED'::"InspirationStatus"
      ORDER BY "sortOrder" ASC, "publishedAt" DESC LIMIT #{limit} OFFSET #{offset}
      """)
  List<InspirationRecord> listPublished(@Param("limit") int limit, @Param("offset") int offset);

  @Select("SELECT \"id\", \"slug\", \"title\", \"prompt\", \"category\", \"imagePath\", \"thumbnailPath\", \"width\", \"height\", \"modelName\", \"ratio\", \"resolutionLabel\", \"authorDisplayName\", \"sourceType\", \"sourceName\", \"sourceUrl\", \"licenseBasis\", \"isAiGenerated\" AS \"aiGenerated\", \"likeCount\", \"sortOrder\", \"status\", \"publishedAt\", \"createdAt\", \"updatedAt\" FROM \"Inspiration\" WHERE \"slug\" = #{slug} AND \"status\" = 'PUBLISHED'::\"InspirationStatus\" LIMIT 1")
  InspirationRecord findBySlug(String slug);
}
