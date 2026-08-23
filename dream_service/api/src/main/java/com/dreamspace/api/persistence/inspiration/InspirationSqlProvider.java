package com.dreamspace.api.persistence.inspiration;

import java.util.Map;

/** Public MyBatis provider so Java reflection can invoke its SQL methods on JDK 21. */
public final class InspirationSqlProvider {
  private InspirationSqlProvider() {}

  private static String base() {
    return "SELECT \"id\",\"slug\",\"title\",\"prompt\",\"category\",\"imagePath\",\"thumbnailPath\",\"width\",\"height\",\"modelName\",\"ratio\",\"resolutionLabel\",\"authorDisplayName\",\"sourceType\",\"sourceName\",\"sourceUrl\",\"licenseBasis\",\"isAiGenerated\" AS \"aiGenerated\",\"likeCount\",\"sortOrder\",\"status\",\"publishedAt\",\"createdAt\",\"updatedAt\" FROM \"Inspiration\" WHERE \"status\" = 'PUBLISHED'::\"InspirationStatus\"";
  }

  public static String list(Map<String, Object> p) {
    return base() + filters(p)
        + " ORDER BY \"sortOrder\" ASC, \"publishedAt\" DESC LIMIT #{limit} OFFSET #{offset}";
  }

  public static String count(Map<String, Object> p) {
    return "SELECT COUNT(*) FROM \"Inspiration\" WHERE \"status\" = 'PUBLISHED'::\"InspirationStatus\""
        + filters(p);
  }

  private static String filters(Map<String, Object> p) {
    StringBuilder sql = new StringBuilder();
    if (p.get("category") != null && !p.get("category").toString().isBlank()) {
      sql.append(" AND \"category\" = #{category}::\"InspirationCategory\"");
    }
    if (p.get("query") != null && !p.get("query").toString().isBlank()) {
      sql.append(" AND (LOWER(\"title\") LIKE LOWER(CONCAT('%',#{query},'%'))"
          + " OR LOWER(\"prompt\") LIKE LOWER(CONCAT('%',#{query},'%'))) ");
    }
    return sql.toString();
  }
}
