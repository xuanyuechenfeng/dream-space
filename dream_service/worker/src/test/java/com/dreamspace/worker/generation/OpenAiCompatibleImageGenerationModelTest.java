package com.dreamspace.worker.generation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class OpenAiCompatibleImageGenerationModelTest {
  private final ObjectMapper json = new ObjectMapper();

  @Test
  void treatsProviderGenerationTimeoutAsRetryable() throws Exception {
    var root = json.readTree("""
        {"error":{"code":"IMAGE_GENERATION_TIMEOUT","message":"Image generation timed out, please retry","type":"timeout_error"}}
        """);

    GenerationProviderException error = OpenAiCompatibleImageGenerationModel.providerFailure(root);

    assertThat(error).isNotNull();
    assertThat(error.code()).isEqualTo("IMAGE_PROVIDER_TEMPORARILY_UNAVAILABLE");
    assertThat(error.retryable()).isTrue();
  }

  @Test
  void keepsNonTimeoutProviderErrorPermanent() throws Exception {
    var root = json.readTree("""
        {"error":{"code":"INVALID_IMAGE_SIZE","message":"Unsupported size","type":"invalid_request_error"}}
        """);

    GenerationProviderException error = OpenAiCompatibleImageGenerationModel.providerFailure(root);

    assertThat(error).isNotNull();
    assertThat(error.code()).isEqualTo("IMAGE_PROVIDER_REJECTED");
    assertThat(error.retryable()).isFalse();
  }

  @Test
  void ignoresSuccessfulProviderResponse() throws Exception {
    var root = json.readTree("{\"data\":[{\"url\":\"https://image.example/result.png\"}]}");

    assertThat(OpenAiCompatibleImageGenerationModel.providerFailure(root)).isNull();
  }

  @Test
  void redactsImagePayloadsFromTheLoggedPreview() {
    String preview = OpenAiCompatibleImageGenerationModel.preview("{\"data\":[{\"b64_json\":\"sensitive-image-data\",\"url\":\"https://image.example/result.png\"}]}");

    assertThat(preview).contains("<redacted-image-payload>", "https://image.example/result.png")
        .doesNotContain("sensitive-image-data");
  }

  @Test
  void derivesStableButDistinctRequestIdsForEachQualityIteration() {
    String first = OpenAiCompatibleImageGenerationModel.deterministicRequestId(
        "task-1", "execution-1", 2, 3, 1, "execution-1:2");
    String replay = OpenAiCompatibleImageGenerationModel.deterministicRequestId(
        "task-1", "execution-1", 2, 3, 1, "execution-1:2");
    String refined = OpenAiCompatibleImageGenerationModel.deterministicRequestId(
        "task-1", "execution-1", 2, 3, 2, "execution-1:2");
    String nextSlot = OpenAiCompatibleImageGenerationModel.deterministicRequestId(
        "task-1", "execution-1", 3, 1, 1, "execution-1:2");

    assertThat(first).isEqualTo(replay)
        .startsWith("dream-")
        .hasSize(38);
    assertThat(refined).isNotEqualTo(first);
    assertThat(nextSlot).isNotEqualTo(first);
  }

}
