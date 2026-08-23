package com.dreamspace.worker.generation;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

import com.dreamspace.common.persistence.config.DreamSpaceProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;

class GenerationWorkerConfigurationTest {
  private final GenerationWorkerConfiguration configuration = new GenerationWorkerConfiguration();

  @Test
  void rejectsIncompleteImageConfiguration() {
    var properties = propertiesWithImage(new DreamSpaceProperties.Image(true, "openai-compatible",
        "https://image.example", "", "", "/v1/images/generations", Duration.ofSeconds(60), 3));

    assertThatThrownBy(() -> configuration.imageGenerationModel(properties, null, null, null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("AI_IMAGE_API_KEY");
  }

  @Test
  void rejectsDisabledImageConfiguration() {
    var properties = propertiesWithImage(new DreamSpaceProperties.Image(false, "openai-compatible",
        "https://image.example", "image-key", "image-model", "/v1/images/generations",
        Duration.ofSeconds(60), 3));

    assertThatThrownBy(() -> configuration.imageGenerationModel(properties, null, null, null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("AI_IMAGE_ENABLED");
  }

  @Test
  void localStorageUsesTheRealImageModel() {
    var properties = new DreamSpaceProperties(null,
        new DreamSpaceProperties.Storage("local", "./var/test-objects", null),
        null, null, null,
        new DreamSpaceProperties.Ai(
            new DreamSpaceProperties.Planning(true, 2),
            new DreamSpaceProperties.Image(true, "openai-compatible", "https://image.example",
                "image-key", "image-model", "/v1/images/generations", Duration.ofSeconds(60), 3),
            new DreamSpaceProperties.Harness(3, 0.8, false, 7)));

    assertThat(configuration.imageGenerationModel(properties, new ObjectMapper(), null, null))
        .isInstanceOf(OpenAiCompatibleImageGenerationModel.class);
  }

  @Test
  void rejectsUnknownStorageMode() {
    var properties = new DreamSpaceProperties(null,
        new DreamSpaceProperties.Storage("unknown", null, null), null, null, null,
        new DreamSpaceProperties.Ai(
            new DreamSpaceProperties.Planning(true, 2),
            new DreamSpaceProperties.Image(true, "openai-compatible", "https://image.example",
                "image-key", "image-model", "/v1/images/generations", Duration.ofSeconds(60), 3),
            new DreamSpaceProperties.Harness(3, 0.8, false, 7)));

    assertThatThrownBy(() -> configuration.imageGenerationModel(properties, new ObjectMapper(), null, null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("local or sftp");
  }

  private static DreamSpaceProperties propertiesWithImage(DreamSpaceProperties.Image image) {
    return new DreamSpaceProperties(null,
        new DreamSpaceProperties.Storage("sftp", null,
            new DreamSpaceProperties.Sftp("sftp.example", 22, "worker", "password", null, null,
                "/tmp/known_hosts", true, "/dream-space", Duration.ofSeconds(10), Duration.ofSeconds(60), 3)),
        null, null, null,
        new DreamSpaceProperties.Ai(
            new DreamSpaceProperties.Planning(true, 2),
            image,
            new DreamSpaceProperties.Harness(3, 0.8, false, 7)));
  }
}
