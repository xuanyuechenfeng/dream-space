package com.dreamspace.api.persistence.inspiration;

import com.dreamspace.common.persistence.database.DatabaseEnums.InspirationCategory;
import com.dreamspace.common.persistence.database.DatabaseEnums.InspirationSourceType;
import com.dreamspace.common.persistence.database.DatabaseEnums.InspirationStatus;
import java.time.Instant;

public record InspirationRecord(String id, String slug, String title, String prompt, InspirationCategory category,
    String imagePath, String thumbnailPath, int width, int height, String modelName, String ratio,
    String resolutionLabel, String authorDisplayName, InspirationSourceType sourceType, String sourceName,
    String sourceUrl, String licenseBasis, boolean aiGenerated, int likeCount, int sortOrder,
    InspirationStatus status, Instant publishedAt, Instant createdAt, Instant updatedAt) {}
