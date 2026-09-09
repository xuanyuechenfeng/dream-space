package com.dreamspace.worker.generation;

import com.dreamspace.common.image.ImageProcessingException;
import com.dreamspace.common.image.PngImageWriter;
import com.dreamspace.common.image.WebpImageWriter;
import com.dreamspace.common.persistence.storage.ObjectStorage;
import com.dreamspace.worker.observability.WorkerMetrics;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class GenerationOutputPipeline {
  private static final Logger log = LoggerFactory.getLogger(GenerationOutputPipeline.class);
  private static final String PNG_MIME = "image/png";
  private static final String WEBP_MIME = "image/webp";
  private static final int PREVIEW_MAX_EDGE = 640;
  private static final int PREVIEW_TARGET_BYTES = 150 * 1024;
  private static final float PREVIEW_QUALITY = 0.80f;
  private static final long MAX_PIXELS = 40_000_000L;
  private final ObjectStorage storage;
  private final PngImageWriter png;
  private final WebpImageWriter webp;
  private final WorkerMetrics metrics;

  public GenerationOutputPipeline(ObjectStorage storage, PngImageWriter png, WebpImageWriter webp,
      WorkerMetrics metrics) {
    this.storage = storage;
    this.png = png;
    this.webp = webp;
    this.metrics = metrics;
  }

  public List<StoredGenerationResult> persist(WorkerTaskSnapshot task, List<ProviderImage> images) {
    validateProviderOutput(task, images);
    var timer = metrics.startImageProcessing();
    List<StoredGenerationResult> stored = new ArrayList<>();
    try {
      for (ProviderImage image : images) stored.add(persistOne(task, image));
      log.atInfo().addKeyValue("taskId", task.id()).addKeyValue("resultCount", stored.size())
          .log("generation output storage completed");
      return List.copyOf(stored);
    } catch (RuntimeException error) {
      cleanup(stored);
      log.atError().addKeyValue("taskId", task.id()).addKeyValue("persistedCount", stored.size())
          .log("generation output storage failed and partial objects were cleaned", error);
      throw error;
    } finally {
      metrics.stopImageProcessing(timer, "persist");
    }
  }

  public void cleanup(List<StoredGenerationResult> results) {
    for (StoredGenerationResult result : results) {
      deleteQuietly(result.thumbnailObjectKey());
      deleteQuietly(result.objectKey());
    }
  }

  private StoredGenerationResult persistOne(WorkerTaskSnapshot task, ProviderImage image) {
    PngImageWriter.NormalizedImage original;
    WebpImageWriter.EncodedPreview preview;
    try {
      original = png.normalizeOriginal(image.data(), MAX_PIXELS);
      long previewStarted = metrics.startImageProcessing();
      try {
        preview = webp.preview(image.data(), PREVIEW_MAX_EDGE, PREVIEW_QUALITY, MAX_PIXELS);
      } finally {
        metrics.recordPreviewEncoding(previewStarted);
      }
    } catch (ImageProcessingException error) {
      throw new GenerationProviderException(error.code(), "provider image processing failed", false, error);
    }
    byte[] output = original.data();
    byte[] thumbnail = preview.data();

    String resultId = UUID.randomUUID().toString();
    String objectKey = "results/" + task.id() + "/" + resultId + ".png";
    String thumbnailObjectKey = "thumbnails/" + task.id() + "/" + resultId + "-v2.webp";
    storage.put(objectKey, output, PNG_MIME);
    try {
      storage.put(thumbnailObjectKey, thumbnail, WEBP_MIME);
    } catch (RuntimeException error) {
      deleteQuietly(thumbnailObjectKey);
      deleteQuietly(objectKey);
      throw error;
    }
    metrics.recordPreview(output.length, thumbnail.length);
    if (thumbnail.length > PREVIEW_TARGET_BYTES) {
      log.atWarn().addKeyValue("taskId", task.id()).addKeyValue("resultId", resultId)
          .addKeyValue("previewBytes", thumbnail.length).addKeyValue("targetBytes", PREVIEW_TARGET_BYTES)
          .log("generated preview exceeds target size");
    }
    return new StoredGenerationResult(resultId, image.index(),
        "/dream_web/generation/results/" + resultId + "/content", objectKey, thumbnailObjectKey,
        original.checksumSha256(), original.width(), original.height(), PNG_MIME, output.length,
        preview.width(), preview.height(), thumbnail.length);
  }

  private static void validateProviderOutput(WorkerTaskSnapshot task, List<ProviderImage> images) {
    if (images == null || images.isEmpty() || images.size() > task.imageCount()) {
      throw new GenerationProviderException("PROVIDER_OUTPUT_INVALID",
          "provider returned an invalid image count", false);
    }
    Set<Integer> indexes = new HashSet<>();
    for (ProviderImage image : images) {
      if (image.index() >= task.imageCount() || !indexes.add(image.index())) {
        throw new GenerationProviderException("PROVIDER_OUTPUT_INVALID",
            "provider returned an invalid image index", false);
      }
    }
  }

  private void deleteQuietly(String key) {
    try { storage.delete(key); } catch (RuntimeException ignored) { metrics.recordCleanupFailure(); }
  }

}
