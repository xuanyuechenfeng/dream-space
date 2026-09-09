package com.dreamspace.worker.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.endsWith;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.dreamspace.common.image.PngImageWriter;
import com.dreamspace.common.image.WebpImageWriter;
import com.dreamspace.common.persistence.config.DreamSpaceProperties;
import com.dreamspace.common.persistence.database.DatabaseEnums.GenerationInputMode;
import com.dreamspace.common.persistence.database.DatabaseEnums.GenerationRatio;
import com.dreamspace.common.persistence.database.DatabaseEnums.GenerationResolution;
import com.dreamspace.common.persistence.storage.LocalObjectStorage;
import com.dreamspace.common.persistence.storage.ObjectStorage;
import com.dreamspace.worker.observability.WorkerMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Comparator;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class GenerationOutputPipelineLocalTest {
  @Test
  void persistsRealProviderOutputToLocalStorage() throws Exception {
    Path root = Path.of("target", "test-local-objects").toAbsolutePath().normalize();
    if (Files.exists(root)) {
      try (var paths = Files.walk(root)) { paths.sorted(Comparator.reverseOrder()).forEach(path -> {
        try { Files.deleteIfExists(path); } catch (Exception ignored) { }
      }); }
    }
    Files.createDirectories(root);
    var properties = new DreamSpaceProperties(null,
        new DreamSpaceProperties.Storage("local", root.toString(), null), null, null, null);
    var storage = new LocalObjectStorage(root);
    var metrics = new WorkerMetrics(new SimpleMeterRegistry(), properties);
    var pipeline = new GenerationOutputPipeline(storage, new PngImageWriter(), new WebpImageWriter(), metrics);
    var task = new WorkerTaskSnapshot("task-1", "user-1", "session-1", "prompt",
        GenerationInputMode.AUTO, List.of(), "image-model", GenerationRatio.RATIO_1_1,
        GenerationResolution.K2, 512, 512, 1, 1, 0);

    BufferedImage source = new BufferedImage(64, 32, BufferedImage.TYPE_INT_RGB);
    ByteArrayOutputStream png = new ByteArrayOutputStream();
    ImageIO.write(source, "png", png);

    StoredGenerationResult result = pipeline.persist(task,
        List.of(new ProviderImage(0, png.toByteArray(), "image/png", "real-provider"))).getFirst();

    assertThat(result.objectKey()).endsWith(".png");
    assertThat(result.thumbnailObjectKey()).endsWith("-v2.webp");
    assertThat(result.width()).isEqualTo(64);
    assertThat(result.height()).isEqualTo(32);
    assertThat(result.thumbnailWidth()).isEqualTo(64);
    assertThat(result.thumbnailHeight()).isEqualTo(32);
    assertThat(Files.readAllBytes(root.resolve(result.objectKey()))).startsWith((byte) 0x89, (byte) 'P', (byte) 'N', (byte) 'G');
    assertThat(Files.readAllBytes(root.resolve(result.thumbnailObjectKey())))
        .startsWith((byte) 'R', (byte) 'I', (byte) 'F', (byte) 'F');
    BufferedImage persisted;
    try (var input = Files.newInputStream(root.resolve(result.objectKey()))) {
      persisted = ImageIO.read(input);
    }
    assertThat(persisted.getWidth()).isEqualTo(64);
    assertThat(persisted.getHeight()).isEqualTo(32);
    assertThat(storage.get(result.objectKey())).get()
        .extracting(value -> value.contentType()).isEqualTo("image/png");
    assertThat(storage.get(result.thumbnailObjectKey())).get()
        .extracting(value -> value.contentType()).isEqualTo("image/webp");
    try (var paths = Files.walk(root)) { paths.sorted(Comparator.reverseOrder()).forEach(path -> {
      try { Files.deleteIfExists(path); } catch (Exception ignored) { }
    }); }
  }

  @Test
  void removesOriginalWhenPreviewUploadFails() throws Exception {
    ObjectStorage storage = mock(ObjectStorage.class);
    doThrow(new IllegalStateException("preview upload failed"))
        .when(storage).put(endsWith(".webp"), any(byte[].class), eq("image/webp"));
    var properties = new DreamSpaceProperties(null, null, null, null, null);
    var metrics = new WorkerMetrics(new SimpleMeterRegistry(), properties);
    var pipeline = new GenerationOutputPipeline(storage, new PngImageWriter(), new WebpImageWriter(), metrics);
    var task = new WorkerTaskSnapshot("task-1", "user-1", "session-1", "prompt",
        GenerationInputMode.AUTO, List.of(), "image-model", GenerationRatio.RATIO_1_1,
        GenerationResolution.K2, 512, 512, 1, 1, 0);
    BufferedImage source = new BufferedImage(64, 32, BufferedImage.TYPE_INT_RGB);
    ByteArrayOutputStream png = new ByteArrayOutputStream();
    ImageIO.write(source, "png", png);

    assertThatThrownBy(() -> pipeline.persist(task,
        List.of(new ProviderImage(0, png.toByteArray(), "image/png", "real-provider"))))
        .isInstanceOf(IllegalStateException.class);

    verify(storage).delete(endsWith(".webp"));
    verify(storage).delete(endsWith(".png"));
  }
}
