package com.dreamspace.api.persistence.admin;

import java.util.Map;

/** Public MyBatis provider shared by the admin application mapper methods. */
public final class AdminApplicationSql {
  static final String TASK_SELECT = "SELECT t.\"id\",t.\"sessionId\",s.\"title\" AS \"sessionTitle\",t.\"userId\",u.\"phone\" AS \"userPhone\",t.\"status\",t.\"prompt\",t.\"model\",t.\"ratio\",t.\"resolution\",t.\"imageCount\",(SELECT COUNT(*) FROM \"GenerationResult\" r WHERE r.\"taskId\"=t.\"id\") AS \"resultCount\",t.\"totalCost\",t.\"attempts\",t.\"imageIds\",t.\"errorCode\",t.\"errorMessage\",t.\"inputModerationStatus\",t.\"outputModerationStatus\",t.\"createdAt\",t.\"startedAt\",t.\"completedAt\" FROM \"GenerationTask\" t JOIN \"GenerationSession\" s ON s.\"id\"=t.\"sessionId\" JOIN \"User\" u ON u.\"id\"=t.\"userId\"";
  static final String INSPIRATION_SELECT = "SELECT \"id\",\"slug\",\"title\",\"prompt\",\"category\",\"imagePath\",\"thumbnailPath\",\"width\",\"height\",\"modelName\",\"ratio\",\"resolutionLabel\",\"authorDisplayName\",\"sourceType\",\"sourceName\",\"sourceUrl\",\"licenseBasis\",\"isAiGenerated\" AS \"aiGenerated\",\"likeCount\",\"sortOrder\",\"status\",\"publishedAt\",\"createdAt\",\"updatedAt\" FROM \"Inspiration\"";

  private AdminApplicationSql() {}

  public static String listTasks(Map<String, Object> params) {
    return TASK_SELECT + taskFilters(params) + " ORDER BY t.\"createdAt\" DESC LIMIT #{limit} OFFSET #{offset}";
  }

  public static String countTasks(Map<String, Object> params) {
    return "SELECT COUNT(*) FROM \"GenerationTask\" t JOIN \"GenerationSession\" s ON s.\"id\"=t.\"sessionId\" JOIN \"User\" u ON u.\"id\"=t.\"userId\"" + taskFilters(params);
  }

  private static String taskFilters(Map<String, Object> params) {
    StringBuilder sql = new StringBuilder(" WHERE 1=1");
    if (present(params, "status")) sql.append(" AND t.\"status\"=#{status}::\"GenerationTaskStatus\"");
    if (present(params, "model")) sql.append(" AND t.\"model\"=#{model}");
    if (present(params, "query")) sql.append(" AND (LOWER(t.\"prompt\") LIKE LOWER(CONCAT('%',#{query},'%')) OR u.\"phone\" LIKE CONCAT('%',#{query},'%') OR LOWER(s.\"title\") LIKE LOWER(CONCAT('%',#{query},'%')))");
    if (params.get("createdFrom") != null) sql.append(" AND t.\"createdAt\">=#{createdFrom}");
    if (params.get("createdTo") != null) sql.append(" AND t.\"createdAt\"<=#{createdTo}");
    return sql.toString();
  }

  public static String listInspirations(Map<String, Object> params) {
    return INSPIRATION_SELECT + inspirationFilters(params) + " ORDER BY \"updatedAt\" DESC,\"id\" LIMIT #{limit} OFFSET #{offset}";
  }

  public static String countInspirations(Map<String, Object> params) {
    return "SELECT COUNT(*) FROM \"Inspiration\"" + inspirationFilters(params);
  }

  private static String inspirationFilters(Map<String, Object> params) {
    StringBuilder sql = new StringBuilder(" WHERE 1=1");
    if (present(params, "status")) sql.append(" AND \"status\"=#{status}::\"InspirationStatus\"");
    if (present(params, "category")) sql.append(" AND \"category\"=#{category}::\"InspirationCategory\"");
    if (present(params, "query")) sql.append(" AND (LOWER(\"slug\") LIKE LOWER(CONCAT('%',#{query},'%')) OR LOWER(\"title\") LIKE LOWER(CONCAT('%',#{query},'%')) OR LOWER(\"prompt\") LIKE LOWER(CONCAT('%',#{query},'%')) OR LOWER(\"sourceName\") LIKE LOWER(CONCAT('%',#{query},'%'))) ");
    return sql.toString();
  }

  private static boolean present(Map<String, Object> params, String key) {
    return params.get(key) != null && !params.get(key).toString().isBlank();
  }
}
