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
}
