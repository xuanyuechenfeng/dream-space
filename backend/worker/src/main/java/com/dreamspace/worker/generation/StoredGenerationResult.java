package com.dreamspace.worker.generation;

public record StoredGenerationResult(String id, int index, String imagePath, String objectKey,
    String thumbnailObjectKey, String checksumSha256, int width, int height, String mimeType,
    int byteSize, int thumbnailWidth, int thumbnailHeight, int thumbnailByteSize) {}
