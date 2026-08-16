package com.dreamspace.persistence.upload;

import java.time.Instant;

public record ReferenceUploadRecord(String id, String userId, String objectKey, String originalFilename,
    String mimeType, int byteSize, int width, int height, String checksumSha256, Instant createdAt, Instant deletedAt) {}
