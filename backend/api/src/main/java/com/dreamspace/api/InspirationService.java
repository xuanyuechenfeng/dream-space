package com.dreamspace.api;

import com.dreamspace.persistence.inspiration.*;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.util.Set;

@Service
public class InspirationService {
  private final InspirationMapper mapper;
  private static final Set<String> CATEGORIES = Set.of("portrait", "photography", "anime", "illustration", "design");
  public InspirationService(InspirationMapper mapper) { this.mapper = mapper; }
  public record Page(List<Item> items, long total, int page, int pageSize, int pageCount) {}
  public record Item(String id, String slug, String title, String promptSummary, String category, String imageUrl, String thumbnailUrl, int width, int height, String authorDisplayName, int likeCount, String modelName, String ratio, String resolutionLabel, boolean isAiGenerated, String prompt, String sourceName, String sourceUrl, Instant publishedAt) {}
  public Page list(String category, String query, int page, int pageSize) {
    if (category != null && !category.isBlank() && !CATEGORIES.contains(category.toLowerCase())) throw new ApiException(HttpStatus.BAD_REQUEST, "INSPIRATION_CATEGORY_INVALID", "分类无效");
    String normalizedCategory = category == null ? null : category.toUpperCase(java.util.Locale.ROOT);
    int p = Math.max(1, page), size = Math.min(100, Math.max(1, pageSize)); long total = mapper.countPublished(normalizedCategory, query); List<InspirationRecord> rows = mapper.searchPublished(normalizedCategory, query, size, (p - 1) * size); List<Item> items = rows.stream().map(this::item).toList(); return new Page(items, total, p, size, (int) Math.ceil(total / (double) size));
  }
  public Item detail(String slug) { InspirationRecord row = mapper.findBySlug(slug); if (row == null) throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "灵感不存在"); return item(row); }
  private Item item(InspirationRecord r) { String image = r.imagePath(); String thumb = r.thumbnailPath(); return new Item(r.id(), r.slug(), r.title(), summary(r.prompt()), r.category().name().toLowerCase(), image, thumb, r.width(), r.height(), r.authorDisplayName(), r.likeCount(), r.modelName(), r.ratio(), r.resolutionLabel(), r.aiGenerated(), r.prompt(), r.sourceName(), r.sourceUrl(), r.publishedAt()); }
  private String summary(String p) { return p == null ? "" : p.length() <= 180 ? p : p.substring(0, 177) + "..."; }
}
