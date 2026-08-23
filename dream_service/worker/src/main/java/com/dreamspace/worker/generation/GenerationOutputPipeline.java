package com.dreamspace.worker.generation;

import com.dreamspace.common.image.ImageProcessingException;
import com.dreamspace.common.image.PngImageWriter;
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
  private final ObjectStorage storage;
  private final PngImageWriter png;
  private final WorkerMetrics metrics;

  public GenerationOutputPipeline(ObjectStorage storage, PngImageWriter png, WorkerMetrics metrics) {
    this.storage = storage;
    this.png = png;
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
    OutputDimensions dimensions = OutputDimensions.resolve(task.ratio(), task.resolution(), task.width(), task.height());
    PngImageWriter.EncodedImage encoded;
    try {
      encoded = png.cover(image.data(), dimensions.width(), dimensions.height(), 480, 40_000_000L);
    } catch (ImageProcessingException error) {
      throw new GenerationProviderException(error.code(), "provider image processing failed", false, error);
    }
    byte[] output = encoded.data();
    byte[] thumbnail = encoded.thumbnail();
    int thumbnailWidth = encoded.thumbnailWidth();
    int thumbnailHeight = encoded.thumbnailHeight();

    String resultId = UUID.randomUUID().toString();
    String objectKey = "results/" + task.id() + "/" + resultId + ".png";
    String thumbnailObjectKey = "thumbnails/" + task.id() + "/" + resultId + ".png";
    storage.put(objectKey, output, PNG_MIME);
    try {
      storage.put(thumbnailObjectKey, thumbnail, PNG_MIME);
    } catch (RuntimeException error) {
      deleteQuietly(thumbnailObjectKey);
      deleteQuietly(objectKey);
      throw error;
    }
    return new StoredGenerationResult(resultId, image.index(),
        "/dream_web/generation/results/" + resultId + "/content", objectKey, thumbnailObjectKey,
        encoded.checksumSha256(), dimensions.width(), dimensions.height(), PNG_MIME, output.length,
        thumbnailWidth, thumbnailHeight, thumbnail.length);
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
