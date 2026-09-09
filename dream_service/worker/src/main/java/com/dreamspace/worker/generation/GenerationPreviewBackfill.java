package com.dreamspace.worker.generation;

import com.dreamspace.common.image.WebpImageWriter;
import com.dreamspace.common.persistence.generation.GenerationMapper;
import com.dreamspace.common.persistence.generation.GenerationResultRecord;
import com.dreamspace.common.persistence.storage.ObjectStorage;
import com.dreamspace.worker.observability.WorkerMetrics;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class GenerationPreviewBackfill {
  private static final Logger log = LoggerFactory.getLogger(GenerationPreviewBackfill.class);
  static final int MAX_EDGE = 640;
  static final float QUALITY = 0.80f;
  static final long MAX_PIXELS = 40_000_000L;
  private final GenerationMapper mapper;
  private final ObjectStorage storage;
  private final WebpImageWriter webp;
  private final WorkerMetrics metrics;

  public GenerationPreviewBackfill(GenerationMapper mapper, ObjectStorage storage,
      WebpImageWriter webp, WorkerMetrics metrics) {
    this.mapper = mapper;
    this.storage = storage;
    this.webp = webp;
    this.metrics = metrics;
  }

  public Summary run(Options options) {
    options.validate();
    int scanned = 0;
    int succeeded = 0;
    int failed = 0;
    int conflicts = 0;
    String cursor = options.afterId();
    while (scanned < options.maxItems()) {
      int limit = Math.min(options.batchSize(), options.maxItems() - scanned);
      List<GenerationResultRecord> candidates = mapper.listPreviewBackfillCandidates(cursor, limit, MAX_EDGE);
      if (candidates.isEmpty()) break;
      for (GenerationResultRecord result : candidates) {
        cursor = result.id();
        scanned++;
        if (options.dryRun()) continue;
        Outcome outcome = convert(result);
        if (outcome == Outcome.SUCCEEDED) succeeded++;
        else if (outcome == Outcome.CONFLICT) conflicts++;
        else failed++;
      }
      if (candidates.size() < limit) break;
    }
    Summary summary = new Summary(scanned, succeeded, failed, conflicts, options.dryRun(), cursor);
    metrics.recordPreviewBackfill(scanned, succeeded, failed,
        conflicts + (options.dryRun() ? scanned : 0));
    log.atInfo().addKeyValue("scanned", scanned).addKeyValue("succeeded", succeeded)
        .addKeyValue("failed", failed).addKeyValue("conflicts", conflicts)
        .addKeyValue("dryRun", options.dryRun()).addKeyValue("cursor", cursor)
        .log("generation preview backfill completed");
    return summary;
  }

  private Outcome convert(GenerationResultRecord result) {
    String newKey = "thumbnails/" + result.taskId() + "/" + result.id() + "-v2.webp";
    try {
      ObjectStorage.ObjectData original = storage.get(result.objectKey())
          .orElseThrow(() -> new IllegalStateException("original object is missing"));
      WebpImageWriter.EncodedPreview preview;
      long previewStarted = metrics.startImageProcessing();
      try {
        preview = webp.preview(original.bytes(), MAX_EDGE, QUALITY, MAX_PIXELS);
      } finally {
        metrics.recordPreviewEncoding(previewStarted);
      }
      metrics.recordPreview(original.bytes().length, preview.data().length);
      storage.put(newKey, preview.data(), WebpImageWriter.MIME_TYPE);
      int updated = mapper.replacePreview(result.id(), result.thumbnailObjectKey(), newKey,
          preview.width(), preview.height(), preview.data().length);
      if (updated != 1) {
        GenerationResultRecord current = mapper.findResult(result.id());
        if (current == null) deleteQuietly(newKey);
        log.atWarn().addKeyValue("resultId", result.id()).log("preview backfill skipped after concurrent update");
        return Outcome.CONFLICT;
      }
      if (result.thumbnailObjectKey() != null && !result.thumbnailObjectKey().isBlank()
          && !newKey.equals(result.thumbnailObjectKey())) deleteQuietly(result.thumbnailObjectKey());
      return Outcome.SUCCEEDED;
    } catch (RuntimeException error) {
      deleteIfUnreferenced(result.id(), newKey);
      log.atError().addKeyValue("resultId", result.id()).addKeyValue("taskId", result.taskId())
          .log("preview backfill failed", error);
      return Outcome.FAILED;
    }
  }

  private void deleteIfUnreferenced(String resultId, String key) {
    try {
      GenerationResultRecord current = mapper.findResult(resultId);
      // The versioned key is deterministic and may be shared by another in-flight runner.
      // Preserve it while the row exists so a concurrent successful CAS cannot reference a deleted object.
      if (current == null) deleteQuietly(key);
    } catch (RuntimeException lookupError) {
      log.atWarn().addKeyValue("resultId", resultId).addKeyValue("objectKey", key)
          .log("preview backfill cleanup deferred because current metadata could not be read", lookupError);
    }
  }

  private void deleteQuietly(String key) {
    try { storage.delete(key); }
    catch (RuntimeException error) {
      log.atWarn().addKeyValue("objectKey", key).log("preview backfill cleanup failed", error);
    }
  }

  private enum Outcome { SUCCEEDED, FAILED, CONFLICT }

  public record Options(int batchSize, int maxItems, String afterId, boolean dryRun) {
    void validate() {
      if (batchSize < 1 || batchSize > 1_000) throw new IllegalArgumentException("batchSize must be between 1 and 1000");
      if (maxItems < 1) throw new IllegalArgumentException("maxItems must be positive");
    }
  }

  public record Summary(int scanned, int succeeded, int failed, int conflicts,
      boolean dryRun, String lastId) {}
}
