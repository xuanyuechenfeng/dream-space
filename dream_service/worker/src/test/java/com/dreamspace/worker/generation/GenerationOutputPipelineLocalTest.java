package com.dreamspace.worker.generation;

import static org.assertj.core.api.Assertions.assertThat;

import com.dreamspace.common.image.PngImageWriter;
import com.dreamspace.common.persistence.config.DreamSpaceProperties;
import com.dreamspace.common.persistence.database.DatabaseEnums.GenerationInputMode;
import com.dreamspace.common.persistence.database.DatabaseEnums.GenerationRatio;
import com.dreamspace.common.persistence.database.DatabaseEnums.GenerationResolution;
import com.dreamspace.common.persistence.storage.LocalObjectStorage;
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
    var pipeline = new GenerationOutputPipeline(storage, new PngImageWriter(), metrics);
    var task = new WorkerTaskSnapshot("task-1", "user-1", "session-1", "prompt",
        GenerationInputMode.AUTO, List.of(), "image-model", GenerationRatio.RATIO_1_1,
        GenerationResolution.K2, 512, 512, 1, 1, 0);

    BufferedImage source = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
    ByteArrayOutputStream png = new ByteArrayOutputStream();
    ImageIO.write(source, "png", png);

    StoredGenerationResult result = pipeline.persist(task,
        List.of(new ProviderImage(0, png.toByteArray(), "image/png", "real-provider"))).getFirst();

    assertThat(result.objectKey()).endsWith(".png");
    assertThat(result.thumbnailObjectKey()).endsWith(".png");
    assertThat(Files.readAllBytes(root.resolve(result.objectKey()))).startsWith((byte) 0x89, (byte) 'P', (byte) 'N', (byte) 'G');
    assertThat(Files.readAllBytes(root.resolve(result.thumbnailObjectKey()))).startsWith((byte) 0x89, (byte) 'P', (byte) 'N', (byte) 'G');
    assertThat(storage.get(result.objectKey())).get()
        .extracting(value -> value.contentType()).isEqualTo("image/png");
    try (var paths = Files.walk(root)) { paths.sorted(Comparator.reverseOrder()).forEach(path -> {
      try { Files.deleteIfExists(path); } catch (Exception ignored) { }
    }); }
  }
}
