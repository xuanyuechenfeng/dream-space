package com.dreamspace.worker.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dreamspace.common.persistence.config.DreamSpaceProperties;
import com.dreamspace.common.persistence.database.DatabaseEnums.GenerationInputMode;
import com.dreamspace.common.persistence.database.DatabaseEnums.GenerationRatio;
import com.dreamspace.common.persistence.database.DatabaseEnums.GenerationResolution;
import com.dreamspace.worker.observability.WorkerMetrics;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;

class ChatQualityEvaluationModelTest {
  private final ChatModel model = mock(ChatModel.class);
  private final ReferenceImageLoader references = mock(ReferenceImageLoader.class);
  private final ChatQualityEvaluationModel evaluator = new ChatQualityEvaluationModel(model, new ObjectMapper(), references,
      new WorkerMetrics(new SimpleMeterRegistry(), properties()), "test-model");

  @Test
  void normalizesObjectEvidenceIntoTheDocumentedStringArray() throws Exception {
    JsonNode root = new ObjectMapper().readTree("""
        {"accepted":false,"score":0.7,"violations":[],"repairable":false,
         "evidence":{"layout":["headline is clear"],"policy":{"checked":true}},
         "evaluatorVersion":"quality-v1"}
        """);

    JsonNode normalized = ChatQualityEvaluationModel.normalizeReport(root);

    assertThat(normalized.path("evidence").isArray()).isTrue();
    assertThat(normalized.path("evidence")).extracting(JsonNode::asText)
        .containsExactly("layout: headline is clear", "policy: {\"checked\":true}");
  }

  @Test
  void evaluatesCompatibilityEvidenceAndUsesNinetySecondDetectionTimeout() {
    when(model.call(any(Prompt.class))).thenReturn(response("""
        {"accepted":true,"score":0.95,"violations":[],"repairable":false,
         "evidence":{"technical":"valid"},"evaluatorVersion":"quality-v1"}
        """));

    QualityEvaluationModel.EvaluationResult result = evaluator.evaluate(task(),
        new GenerationPlanBundle(null, null, null, null),
        List.of(new ProviderImage(0, new byte[] {1, 2, 3}, "image/png", "provider-0")), 1);

    assertThat(result.report().evidence()).containsExactly("technical: valid");
    var prompt = org.mockito.ArgumentCaptor.forClass(Prompt.class);
    verify(model).call(prompt.capture());
    assertThat(((OpenAiChatOptions) prompt.getValue().getOptions()).getTimeout())
        .isEqualTo(Duration.ofSeconds(90));
  }


  @Test
  void parsesRequiredRefinementInstructionAndCompatibleMissingArrays() {
    when(model.call(any(Prompt.class))).thenReturn(response("""
        {"accepted":false,"score":0.84,"violations":["OUTPUT_DIMENSION_MISMATCH"],"repairable":true,
         "evidence":["provider image is 1548x1016"],"evaluatorVersion":"quality-v1",
         "refinement":{"instruction":"Regenerate at exactly 2048x1344 while preserving approved content.",
         "preserve":["requestedRatio=3:2","literal N倍"]}}
        """));

    QualityEvaluationModel.EvaluationResult result = evaluator.evaluate(task(),
        new GenerationPlanBundle(null, null, null, null),
        List.of(new ProviderImage(0, new byte[] {1, 2, 3}, "image/png", "provider-0")), 1);

    assertThat(result.refinement()).isNotNull();
    assertThat(result.refinement().instruction()).startsWith("Regenerate at exactly");
    assertThat(result.refinement().changes()).containsExactly(result.refinement().instruction());
    assertThat(result.refinement().reasonCodes()).containsExactly("OUTPUT_DIMENSION_MISMATCH");
  }
  private static ChatResponse response(String text) {
    return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
  }

  private static WorkerTaskSnapshot task() {
    return new WorkerTaskSnapshot("task-1", "user-1", "session-1", "safe prompt", GenerationInputMode.AUTO,
        List.of(), "test-model", GenerationRatio.RATIO_1_1, GenerationResolution.K2, 1024, 1024, 1, 1, 0);
  }

  private static DreamSpaceProperties properties() {
    return new DreamSpaceProperties(new DreamSpaceProperties.Redis("redis://localhost:6379", "generation",
        "generation-workers", Duration.ofSeconds(30)), null, null, null, null);
  }
}
