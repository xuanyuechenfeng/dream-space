package com.dreamspace.persistence.generation;

import com.dreamspace.persistence.database.DatabaseEnums.ModerationStatus;
import java.time.Instant;

public record GenerationResultRecord(String id, String taskId, int index, String imagePath,
    String objectKey, String thumbnailObjectKey, String checksumSha256, int width, int height,
    String mimeType, int byteSize, Integer thumbnailWidth, Integer thumbnailHeight,
    Integer thumbnailByteSize, ModerationStatus moderationStatus, boolean isAiGenerated,
    Instant createdAt) {}
