package com.dreamspace.worker.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dreamspace.common.image.WebpImageWriter;
import com.dreamspace.common.persistence.database.DatabaseEnums.ModerationStatus;
import com.dreamspace.common.persistence.generation.GenerationMapper;
import com.dreamspace.common.persistence.generation.GenerationResultRecord;
import com.dreamspace.common.persistence.storage.ObjectStorage;
import com.dreamspace.common.persistence.config.DreamSpaceProperties;
import com.dreamspace.worker.observability.WorkerMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class GenerationPreviewBackfillTest {
  @Test
  void replacesLegacyPreviewAndDeletesItAfterCompareAndSet() throws Exception {
    GenerationMapper mapper = mock(GenerationMapper.class);
    ObjectStorage storage = mock(ObjectStorage.class);
    GenerationResultRecord result = result("thumbnails/task-1/result-1.png");
    when(mapper.listPreviewBackfillCandidates(null, 10, 640)).thenReturn(List.of(result));
    when(storage.get(result.objectKey())).thenReturn(Optional.of(new ObjectStorage.ObjectData(png(1200, 600), "image/png")));
    when(mapper.replacePreview(eq("result-1"), eq(result.thumbnailObjectKey()),
        eq("thumbnails/task-1/result-1-v2.webp"), eq(640), eq(320), anyInt())).thenReturn(1);
    var backfill = backfill(mapper, storage, new WebpImageWriter());

    GenerationPreviewBackfill.Summary summary = backfill.run(
        new GenerationPreviewBackfill.Options(10, 10, null, false));

    assertThat(summary.succeeded()).isEqualTo(1);
    verify(storage).put(eq("thumbnails/task-1/result-1-v2.webp"),
        org.mockito.ArgumentMatchers.argThat(bytes -> bytes.length > 12
            && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'),
        eq("image/webp"));
    verify(storage).delete("thumbnails/task-1/result-1.png");
  }

  @Test
  void dryRunScansWithoutReadingOrWritingObjects() {
    GenerationMapper mapper = mock(GenerationMapper.class);
    ObjectStorage storage = mock(ObjectStorage.class);
    when(mapper.listPreviewBackfillCandidates(null, 10, 640))
        .thenReturn(List.of(result("thumbnails/task-1/result-1.png")));
    var backfill = backfill(mapper, storage, mock(WebpImageWriter.class));

    GenerationPreviewBackfill.Summary summary = backfill.run(
        new GenerationPreviewBackfill.Options(10, 10, null, true));

    assertThat(summary.scanned()).isEqualTo(1);
    assertThat(summary.dryRun()).isTrue();
    verify(storage, never()).get(org.mockito.ArgumentMatchers.anyString());
    verify(storage, never()).put(org.mockito.ArgumentMatchers.anyString(),
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString());
  }

  @Test
  void failedConcurrentRunDoesNotDeletePreviewAlreadyReferencedByDatabase() throws Exception {
    GenerationMapper mapper = mock(GenerationMapper.class);
    ObjectStorage storage = mock(ObjectStorage.class);
    GenerationResultRecord result = result("thumbnails/task-1/result-1.png");
    String newKey = "thumbnails/task-1/result-1-v2.webp";
    when(mapper.listPreviewBackfillCandidates(null, 10, 640)).thenReturn(List.of(result));
    when(storage.get(result.objectKey()))
        .thenReturn(Optional.of(new ObjectStorage.ObjectData(png(1200, 600), "image/png")));
    doThrow(new IllegalStateException("database response lost"))
        .when(mapper).replacePreview(eq("result-1"), eq(result.thumbnailObjectKey()),
            eq(newKey), eq(640), eq(320), anyInt());
    when(mapper.findResult("result-1")).thenReturn(new GenerationResultRecord(
        result.id(), result.taskId(), result.index(), result.imagePath(), result.objectKey(), newKey,
        result.checksumSha256(), result.width(), result.height(), result.mimeType(), result.byteSize(),
        640, 320, 100, result.moderationStatus(), result.isAiGenerated(), result.createdAt()));
    var backfill = backfill(mapper, storage, new WebpImageWriter());

    GenerationPreviewBackfill.Summary summary = backfill.run(
        new GenerationPreviewBackfill.Options(10, 10, null, false));

    assertThat(summary.failed()).isEqualTo(1);
    verify(storage, never()).delete(newKey);
  }

  @Test
  void compareAndSetConflictPreservesDeterministicPreviewForConcurrentRunner() throws Exception {
    GenerationMapper mapper = mock(GenerationMapper.class);
    ObjectStorage storage = mock(ObjectStorage.class);
    GenerationResultRecord result = result("thumbnails/task-1/result-1.png");
    String newKey = "thumbnails/task-1/result-1-v2.webp";
    when(mapper.listPreviewBackfillCandidates(null, 10, 640)).thenReturn(List.of(result));
    when(storage.get(result.objectKey()))
        .thenReturn(Optional.of(new ObjectStorage.ObjectData(png(1200, 600), "image/png")));
    when(mapper.replacePreview(eq("result-1"), eq(result.thumbnailObjectKey()),
        eq(newKey), eq(640), eq(320), anyInt())).thenReturn(0);
    when(mapper.findResult("result-1")).thenReturn(result);
    var backfill = backfill(mapper, storage, new WebpImageWriter());

    GenerationPreviewBackfill.Summary summary = backfill.run(
        new GenerationPreviewBackfill.Options(10, 10, null, false));

    assertThat(summary.conflicts()).isEqualTo(1);
    verify(storage, never()).delete(newKey);
    verify(storage, never()).delete(result.thumbnailObjectKey());
  }

  @Test
  void failedRowDoesNotPreventLaterRowsFromBeingConverted() throws Exception {
    GenerationMapper mapper = mock(GenerationMapper.class);
    ObjectStorage storage = mock(ObjectStorage.class);
    GenerationResultRecord first = result("result-1", "thumbnails/task-1/result-1.png");
    GenerationResultRecord second = result("result-2", "thumbnails/task-1/result-2.png");
    when(mapper.listPreviewBackfillCandidates(null, 10, 640)).thenReturn(List.of(first, second));
    when(storage.get(first.objectKey())).thenReturn(Optional.empty());
    when(storage.get(second.objectKey()))
        .thenReturn(Optional.of(new ObjectStorage.ObjectData(png(1200, 600), "image/png")));
    when(mapper.replacePreview(eq("result-2"), eq(second.thumbnailObjectKey()),
        eq("thumbnails/task-1/result-2-v2.webp"), eq(640), eq(320), anyInt())).thenReturn(1);
    var backfill = backfill(mapper, storage, new WebpImageWriter());

    GenerationPreviewBackfill.Summary summary = backfill.run(
        new GenerationPreviewBackfill.Options(10, 10, null, false));

    assertThat(summary.scanned()).isEqualTo(2);
    assertThat(summary.failed()).isEqualTo(1);
    assertThat(summary.succeeded()).isEqualTo(1);
    verify(storage).put(eq("thumbnails/task-1/result-2-v2.webp"),
        org.mockito.ArgumentMatchers.any(), eq("image/webp"));
  }

  private static GenerationResultRecord result(String thumbnailKey) {
    return result("result-1", thumbnailKey);
  }

  private static GenerationPreviewBackfill backfill(GenerationMapper mapper,
      ObjectStorage storage, WebpImageWriter webp) {
    DreamSpaceProperties properties = new DreamSpaceProperties(null, null, null, null, null);
    return new GenerationPreviewBackfill(mapper, storage, webp,
        new WorkerMetrics(new SimpleMeterRegistry(), properties));
  }

  private static GenerationResultRecord result(String id, String thumbnailKey) {
    return new GenerationResultRecord(id, "task-1", 0, "/result/" + id,
        "results/task-1/" + id + ".png", thumbnailKey, "checksum", 1200, 600, "image/png", 100,
        480, 240, 50, ModerationStatus.APPROVED, true, Instant.EPOCH);
  }

  private static byte[] png(int width, int height) throws Exception {
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    ImageIO.write(image, "png", output);
    return output.toByteArray();
  }
}
