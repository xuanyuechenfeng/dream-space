package com.dreamspace.api;

import com.dreamspace.persistence.admin.AdminApplicationMapper;
import com.dreamspace.persistence.inspiration.InspirationRecord;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminInspirationsService {
  private static final Set<String> CATEGORIES = Set.of(
      "portrait", "photography", "anime", "illustration", "design");
  private static final Set<String> STATUSES = Set.of("draft", "published", "archived");
  private static final Set<String> SOURCE_TYPES = Set.of(
      "ai_public_gallery", "licensed", "internal");
  private final AdminApplicationMapper mapper;

  public AdminInspirationsService(AdminApplicationMapper mapper) { this.mapper = mapper; }

  public record Page(List<Item> items, long total, int page, int pageSize, int pageCount) {}
  public record Item(String id, String slug, String title, String prompt, String category,
      String imageUrl, String thumbnailUrl, int width, int height, String modelName, String ratio,
      String resolutionLabel, String authorDisplayName, String sourceType, String sourceName,
      String sourceUrl, String licenseBasis, boolean isAiGenerated, int likeCount, int sortOrder,
      String status, Instant publishedAt, Instant createdAt, Instant updatedAt) {}
  public record Input(String slug, String title, String prompt, String category, String imageUrl,
      String thumbnailUrl, Integer width, Integer height, String modelName, String ratio,
      String resolutionLabel, String authorDisplayName, String sourceType, String sourceName,
      String sourceUrl, String licenseBasis, Boolean isAiGenerated, Integer likeCount,
      Integer sortOrder, Instant updatedAt) {}
  public record Transition(Instant updatedAt) {}

  public Page list(String status, String category, String query, int page, int pageSize) {
    String normalizedStatus = choice(status, STATUSES, "灵感状态无效");
    String normalizedCategory = choice(category, CATEGORIES, "灵感分类无效");
    String normalizedQuery = optional(query);
    if (normalizedQuery != null && normalizedQuery.length() > 100) {
      throw bad("VALIDATION_ERROR", "搜索关键词过长");
    }
    int normalizedPage = range(page, 1, 1_000_000, "页码无效");
    int normalizedSize = range(pageSize, 1, 100, "每页数量无效");
    String databaseStatus = upper(normalizedStatus);
    String databaseCategory = upper(normalizedCategory);
    long total = mapper.countInspirations(databaseStatus, databaseCategory, normalizedQuery);
    List<Item> items = mapper.listInspirations(databaseStatus, databaseCategory, normalizedQuery,
        normalizedSize, (normalizedPage - 1) * normalizedSize).stream().map(this::item).toList();
    return new Page(items, total, normalizedPage, normalizedSize,
        (int) Math.ceil(total / (double) normalizedSize));
  }

  public Item get(String id) { return item(find(id)); }

  @Transactional
  public Item create(Input raw) {
    Validated input = validate(raw, false);
    if (mapper.countSlug(input.slug(), null) > 0) throw conflict("灵感 slug 已存在");
    String id = UUID.randomUUID().toString();
    mapper.insertInspiration(id, input.slug(), input.title(), input.prompt(), upper(input.category()),
        input.imageUrl(), input.thumbnailUrl(), input.width(), input.height(), input.modelName(),
        input.ratio(), input.resolutionLabel(), input.authorDisplayName(), upper(input.sourceType()),
        input.sourceName(), input.sourceUrl(), input.licenseBasis(), input.isAiGenerated(),
        input.likeCount(), input.sortOrder());
    return item(find(id));
  }

  @Transactional
  public Item update(String id, Input raw) {
    InspirationRecord existing = find(id);
    Validated input = validate(raw, true);
    if (mapper.countSlug(input.slug(), existing.id()) > 0) throw conflict("灵感 slug 已存在");
    int changed = mapper.updateInspiration(existing.id(), input.expectedUpdatedAt(), input.slug(),
        input.title(), input.prompt(), upper(input.category()), input.imageUrl(), input.thumbnailUrl(),
        input.width(), input.height(), input.modelName(), input.ratio(), input.resolutionLabel(),
        input.authorDisplayName(), upper(input.sourceType()), input.sourceName(), input.sourceUrl(),
        input.licenseBasis(), input.isAiGenerated(), input.likeCount(), input.sortOrder());
    if (changed != 1) throw conflict("灵感已被其他管理员修改，请刷新后重试");
    return item(find(existing.id()));
  }

  @Transactional
  public Item transition(String id, String status, Transition request) {
    InspirationRecord existing = find(id);
    if (request == null || request.updatedAt() == null) throw bad("VERSION_REQUIRED", "缺少 updatedAt");
    int changed = mapper.transitionInspiration(existing.id(), upper(status), request.updatedAt());
    if (changed != 1) throw conflict("灵感状态已被其他管理员修改，请刷新后重试");
    return item(find(existing.id()));
  }

  private InspirationRecord find(String id) {
    String normalized = optional(id);
    if (normalized == null || normalized.length() > 100) throw bad("ID_INVALID", "灵感 ID 无效");
    InspirationRecord item = mapper.findInspiration(normalized);
    if (item == null) throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "灵感不存在");
    return item;
  }

  private Validated validate(Input raw, boolean versionRequired) {
    if (raw == null) throw bad("VALIDATION_ERROR", "请求参数无效");
    String slug = text(raw.slug(), "slug", 2, 80).toLowerCase(Locale.ROOT);
    if (!slug.matches("^[a-z0-9]+(?:-[a-z0-9]+)*$")) {
      throw bad("SLUG_INVALID", "slug 只能包含小写字母、数字和中划线");
    }
    String category = choice(raw.category(), CATEGORIES, "灵感分类无效");
    String sourceType = choice(raw.sourceType(), SOURCE_TYPES, "素材来源类型无效");
    if (raw.isAiGenerated() == null) throw bad("AI_MARK_REQUIRED", "请标记是否为 AI 生成内容");
    if (versionRequired && raw.updatedAt() == null) throw bad("VERSION_REQUIRED", "缺少 updatedAt");
    return new Validated(slug, text(raw.title(), "标题", 2, 100),
        text(raw.prompt(), "提示词", 1, 4_000), category,
        asset(raw.imageUrl(), "原图地址"), asset(raw.thumbnailUrl(), "缩略图地址"),
        number(raw.width(), 1, 10_000, "图片宽度"),
        number(raw.height(), 1, 10_000, "图片高度"),
        text(raw.modelName(), "模型名称", 1, 64), text(raw.ratio(), "图片比例", 1, 16),
        text(raw.resolutionLabel(), "分辨率", 1, 64),
        text(raw.authorDisplayName(), "作者名称", 1, 64), sourceType,
        text(raw.sourceName(), "来源名称", 1, 120), nullableHttp(raw.sourceUrl(), "来源链接"),
        text(raw.licenseBasis(), "授权依据", 1, 500), raw.isAiGenerated(),
        number(raw.likeCount(), 0, 1_000_000, "点赞数"),
        number(raw.sortOrder(), 0, 1_000_000, "排序值"), raw.updatedAt());
  }

  private Item item(InspirationRecord item) {
    return new Item(item.id(), item.slug(), item.title(), item.prompt(), value(item.category()),
        item.imagePath(), item.thumbnailPath(), item.width(), item.height(), item.modelName(),
        item.ratio(), item.resolutionLabel(), item.authorDisplayName(), value(item.sourceType()),
        item.sourceName(), item.sourceUrl(), item.licenseBasis(), item.aiGenerated(), item.likeCount(),
        item.sortOrder(), value(item.status()), item.publishedAt(), item.createdAt(), item.updatedAt());
  }

  private record Validated(String slug, String title, String prompt, String category,
      String imageUrl, String thumbnailUrl, int width, int height, String modelName, String ratio,
      String resolutionLabel, String authorDisplayName, String sourceType, String sourceName,
      String sourceUrl, String licenseBasis, boolean isAiGenerated, int likeCount, int sortOrder,
      Instant expectedUpdatedAt) {}

  private static String text(String value, String label, int min, int max) {
    String normalized = value == null ? null : value.replaceAll("\\s+", " ").trim();
    if (normalized == null || normalized.length() < min || normalized.length() > max) {
      throw bad("VALIDATION_ERROR", label + "长度应为 " + min + "-" + max + " 个字符");
    }
    return normalized;
  }
  private static int number(Integer value, int min, int max, String label) {
    if (value == null || value < min || value > max) throw bad("VALIDATION_ERROR", label + "无效");
    return value;
  }
  private static String choice(String value, Set<String> allowed, String message) {
    String normalized = optional(value);
    if (normalized == null) return null;
    normalized = normalized.toLowerCase(Locale.ROOT);
    if (!allowed.contains(normalized)) throw bad("VALIDATION_ERROR", message);
    return normalized;
  }
  private static String asset(String value, String label) {
    String normalized = text(value, label, 1, 500);
    if (normalized.startsWith("/") && !normalized.startsWith("//") && !normalized.contains("..")) {
      return normalized;
    }
    return http(normalized, label);
  }
  private static String nullableHttp(String value, String label) {
    String normalized = optional(value);
    return normalized == null ? null : http(normalized, label);
  }
  private static String http(String value, String label) {
    try {
      URI uri = URI.create(value);
      if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
          || uri.getHost() == null) throw new IllegalArgumentException();
      return uri.toString();
    } catch (IllegalArgumentException invalid) {
      throw bad("URL_INVALID", label + "无效");
    }
  }
  private static int range(int value, int min, int max, String message) {
    if (value < min || value > max) throw bad("VALIDATION_ERROR", message);
    return value;
  }
  private static String optional(String value) {
    return value == null || value.trim().isEmpty() ? null : value.trim();
  }
  private static String upper(String value) {
    return value == null ? null : value.toUpperCase(Locale.ROOT);
  }
  private static String value(Enum<?> value) {
    return value == null ? null : value.name().toLowerCase(Locale.ROOT);
  }
  private static ApiException bad(String code, String message) {
    return new ApiException(HttpStatus.BAD_REQUEST, code, message);
  }
  private static ApiException conflict(String message) {
    return new ApiException(HttpStatus.CONFLICT, "OPTIMISTIC_CONFLICT", message);
  }
}
