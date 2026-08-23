package com.dreamspace.worker.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dreamspace.common.persistence.config.DreamSpaceProperties;
import com.dreamspace.common.persistence.database.DatabaseEnums.GenerationInputMode;
import com.dreamspace.common.persistence.database.DatabaseEnums.GenerationRatio;
import com.dreamspace.common.persistence.database.DatabaseEnums.GenerationResolution;
import com.dreamspace.worker.observability.WorkerMetrics;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.mockito.ArgumentCaptor;

class ChatPlanningModelTest {
  private final ChatModel model = mock(ChatModel.class);
  private final ReferenceImageLoader references = mock(ReferenceImageLoader.class);
  private final ChatPlanningModel planning = new ChatPlanningModel(model, new ObjectMapper(), references,
      new WorkerMetrics(new SimpleMeterRegistry(), properties()), "test-model");

  @Test
  void acceptsObjectShapeAndNestedArrays() {
    when(model.call(any(Prompt.class))).thenReturn(response("""
        {"intent":"TEXT_TO_IMAGE","imageAssignments":[],"imageType":"poster","industry":"tech","coreSubject":"product","displayGoal":"show","targetAudience":"users","contentFacts":[],"constraints":[],"inferredVisualPreferences":{"style":"editorial","palette":["teal"]},"inferredLoopStrategy":{"maxIterations":3,"focus":["layout"]},"unknowns":[],"confidence":0.9,"needsClarification":false}
        """));

    RequirementBrief result = planning.understand(task(), context());

    assertThat(result.inferredVisualPreferences()).containsEntry("style", "editorial");
    assertThat(result.inferredVisualPreferences()).containsKey("palette");
  }

  @Test
  void acceptsJsonCodeFenceAroundPlanningResponse() {
    when(model.call(any(Prompt.class))).thenReturn(response("""
        ```json
        {"intent":"TEXT_TO_IMAGE","imageAssignments":[],"imageType":"poster","industry":"tech","coreSubject":"product","displayGoal":"show","targetAudience":"users","contentFacts":[],"constraints":[],"inferredVisualPreferences":{},"inferredLoopStrategy":{},"unknowns":[],"confidence":0.9,"needsClarification":false}
        ```
        """));

    assertThat(planning.understand(task(), context()).intent())
        .isEqualTo(RequirementBrief.GenerationIntent.TEXT_TO_IMAGE);
  }

  @Test
  void normalizesConfidenceLabelToCanonicalNumber() {
    when(model.call(any(Prompt.class))).thenReturn(response("""
        {"intent":"TEXT_TO_IMAGE","imageAssignments":[],"imageType":"poster","industry":"tech","coreSubject":"product","displayGoal":"show","targetAudience":"users","contentFacts":[],"constraints":[],"inferredVisualPreferences":{},"inferredLoopStrategy":{},"unknowns":[],"confidence":"high","needsClarification":false}
        """));

    RequirementBrief result = planning.understand(task(), context());

    assertThat(result.confidence()).isEqualTo(0.9);
  }

  @Test
  void ignoresModelOwnedCanvasDimensions() {
    when(model.call(any(Prompt.class))).thenReturn(response("""
        {"canvas":{"aspectRatio":"1:1","composition":"structured","resolution":"4K","width":4096,"height":4096},"readingOrder":[],"modules":[],"textBlocks":[],"chartSpecs":[],"layoutRules":{},"density":"balanced"}
        """));

    StructurePlan result = planning.structure(task(), requirement(), context());

    assertThat(result.canvas().aspectRatio()).isEqualTo("1:1");
    assertThat(result.canvas().composition()).isEqualTo("structured");
    assertThat(result.canvas().resolution()).isNull();
    assertThat(result.canvas().width()).isNull();
    assertThat(result.canvas().height()).isNull();
  }

  @Test
  void wrapsProviderArrayForObjectFieldWithoutDroppingValues() {
    when(model.call(any(Prompt.class))).thenReturn(response("""
        {"intent":"TEXT_TO_IMAGE","imageAssignments":[],"imageType":"poster","industry":"tech","coreSubject":"product","displayGoal":"show","targetAudience":"users","contentFacts":[],"constraints":[],"inferredVisualPreferences":["editorial","teal"],"inferredLoopStrategy":[],"unknowns":[],"confidence":0.9,"needsClarification":false}
        """));

    RequirementBrief result = planning.understand(task(), context());

    assertThat(result.inferredVisualPreferences()).containsKey("items");
    assertThat(result.inferredVisualPreferences().get("items")).isEqualTo(List.of("editorial", "teal"));
    assertThat(result.inferredLoopStrategy()).containsEntry("items", List.of());
  }

  @Test
  void normalizesObjectShapeForLayoutRules() {
    when(model.call(any(Prompt.class))).thenReturn(response("""
        {"canvas":{"aspectRatio":null,"composition":"structured"},"readingOrder":[],"modules":[],"textBlocks":[],"chartSpecs":[],"layoutRules":{"spacing":"consistent","alignment":"grid"},"density":"balanced"}
        """));

    StructurePlan result = planning.structure(task(), requirement(), context());

    assertThat(result.layoutRules().get("spacing").asText()).isEqualTo("consistent");
    assertThat(result.layoutRules().get("alignment").asText()).isEqualTo("grid");
  }

  @Test
  void normalizesObjectShapeForScalarVisualFields() {
    when(model.call(any(Prompt.class))).thenReturn(response("""
        {"style":{"name":"editorial-tech","tone":"calm"},"palette":{},"layout":{"aspectRatio":"3:2","resolution":"K2","width":2048,"height":1344,"readingOrder":["title","flow"]},"typography":{"heading":"sans"},"contrast":{"value":"WCAG-AA"},"negativeConstraints":[]}
        """));

    VisualSpec result = planning.visualize(task(), requirement(),
        new StructurePlan(new StructureCanvas("1:1", "structured", "2K", 1024, 1024),
            JsonNodeFactory.instance.arrayNode(), List.of(), List.of(), List.of(), JsonNodeFactory.instance.objectNode(), "balanced"),
        context());

    assertThat(result.style()).isEqualTo("editorial-tech");
    assertThat(result.contrast()).isEqualTo("WCAG-AA");
    assertThat(result.layout().get("readingOrder")).isEqualTo(List.of("title", "flow"));
    assertThat(result.layout().get("width")).isEqualTo(2048);
  }
  @Test
  void overridesPromptOutputParametersWithTaskValues() {
    when(model.call(any(Prompt.class))).thenReturn(response("""
        {"positivePrompt":"prompt","negativePrompt":"negative","modelInput":{"aspectRatio":"4:3","resolution":"4K","width":4096,"height":3072},"textPolicy":"exact","promptVersion":"v1","promptRelation":"EXPANDED","alignmentScore":0.9,"expansionReason":"added lighting"}
        """));

    PromptPackage result = planning.prompt(task(), requirement(),
        new StructurePlan(new StructureCanvas("1:1", "structured", "2K", 1024, 1024),
            JsonNodeFactory.instance.arrayNode(), List.of(), List.of(), List.of(), JsonNodeFactory.instance.objectNode(), "balanced"),
        new VisualSpec("editorial", Map.of(), Map.of(), Map.of(), "high", JsonNodeFactory.instance.arrayNode()), context());

    assertThat(result.modelInput()).containsEntry("aspectRatio", "1:1");
    assertThat(result.modelInput()).containsEntry("resolution", "2K");
    assertThat(result.modelInput()).containsEntry("width", 1024);
    assertThat(result.modelInput()).containsEntry("height", 1024);
  }

  @Test
  void acceptsObjectShapeForJsonFields() {
    when(model.call(any(Prompt.class))).thenReturn(response("""
        {"intent":"TEXT_TO_IMAGE","imageAssignments":[],"imageType":"poster","industry":"tech","coreSubject":"product","displayGoal":"show","targetAudience":"users","contentFacts":{"items":["fact"]},"constraints":[],"inferredVisualPreferences":{},"inferredLoopStrategy":{},"unknowns":[],"confidence":0.9,"needsClarification":false}
        """));

    RequirementBrief result = planning.understand(task(), context());

    assertThat(result.contentFacts().get("items").get(0).asText()).isEqualTo("fact");
  }
  @Test
  void ignoresUnknownFieldsAfterNormalization() {
    when(model.call(any(Prompt.class))).thenReturn(response("""
        {"intent":"TEXT_TO_IMAGE","imageAssignments":[],"imageType":"poster","industry":"tech","coreSubject":"product","displayGoal":"show","targetAudience":"users","contentFacts":[],"constraints":[],"inferredVisualPreferences":{},"inferredLoopStrategy":{},"unknowns":[],"confidence":0.9,"needsClarification":false,"unexpected":true}
        """));

    assertThat(planning.understand(task(), context()).intent())
        .isEqualTo(RequirementBrief.GenerationIntent.TEXT_TO_IMAGE);
    verify(model).call(any(Prompt.class));
  }

  @Test
  void rejectsMissingContractFields() {
    when(model.call(any(Prompt.class))).thenReturn(response("""
        {"intent":"TEXT_TO_IMAGE","imageAssignments":[],"needsClarification":false}
        """));

    assertThatThrownBy(() -> planning.understand(task(), context()))
        .isInstanceOf(GenerationProviderException.class)
        .hasMessage("planning model output is invalid");
    verify(model).call(any(Prompt.class));
  }

  @Test
  void acceptsMissingNonCriticalFieldsWithSafeDefaults() {
    when(model.call(any(Prompt.class))).thenReturn(response("""
        {"intent":"TEXT_TO_IMAGE","imageAssignments":[],"confidence":0.9,"needsClarification":false}
        """));

    RequirementBrief result = planning.understand(task(), context());

    assertThat(result.intent()).isEqualTo(RequirementBrief.GenerationIntent.TEXT_TO_IMAGE);
    assertThat(result.imageType()).isEmpty();
    assertThat(result.contentFacts()).isEmpty();
  }

  @Test
  void repairsObservedExtraObjectStartOnce() {
    when(model.call(any(Prompt.class))).thenReturn(response("""
        {"canvas":{"aspectRatio":"1:1","composition":"structured"}},{{"readingOrder":[],"modules":[],"textBlocks":[],"chartSpecs":[],"layoutRules":{},"density":"balanced"}
        """), response("""
        {"canvas":{"aspectRatio":"1:1","composition":"structured"},"readingOrder":[],"modules":[],"textBlocks":[],"chartSpecs":[],"layoutRules":{},"density":"balanced"}
        """));

    StructurePlan result = planning.structure(task(), requirement(), context());

    assertThat(result.canvas().composition()).isEqualTo("structured");
    ArgumentCaptor<Prompt> prompts = ArgumentCaptor.forClass(Prompt.class);
    verify(model, times(2)).call(prompts.capture());
    assertThat(prompts.getAllValues()).allSatisfy(prompt -> {
      assertThat(prompt.getOptions()).isInstanceOf(OpenAiChatOptions.class);
      assertThat(((OpenAiChatOptions) prompt.getOptions()).getResponseFormat().getType())
          .isEqualTo(OpenAiChatModel.ResponseFormat.Type.JSON_OBJECT);
      assertThat(((OpenAiChatOptions) prompt.getOptions()).getTemperature()).isNull();
    });
  }

  @Test
  void stopsAfterOneUnsuccessfulSyntaxRepair() {
    when(model.call(any(Prompt.class))).thenReturn(response("{invalid"), response("{still-invalid"));

    assertThatThrownBy(() -> planning.understand(task(), context()))
        .isInstanceOf(GenerationProviderException.class)
        .hasMessage("planning model output is invalid");
    verify(model, times(2)).call(any(Prompt.class));
  }

  private static ChatResponse response(String text) {
    return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
  }

  private static RequirementBrief requirement() {
    return new RequirementBrief(RequirementBrief.GenerationIntent.TEXT_TO_IMAGE, List.of(), "poster", "tech",
        "product", "show", "users", JsonNodeFactory.instance.arrayNode(), JsonNodeFactory.instance.arrayNode(), Map.of(), Map.of(), JsonNodeFactory.instance.arrayNode(), 0.9, false);
  }
  private static WorkerTaskSnapshot task() {
    return new WorkerTaskSnapshot("task-1", "user-1", "session-1", "safe prompt", GenerationInputMode.AUTO,
        List.of(), "test-model", GenerationRatio.RATIO_1_1, GenerationResolution.K2, 1024, 1024, 1, 1, 0);
  }

  private static StageContext context() { return new StageContext("trace", "task-1", "attempt", "stage"); }

  private static DreamSpaceProperties properties() {
    return new DreamSpaceProperties(new DreamSpaceProperties.Redis("redis://localhost:6379", "generation",
        "generation-workers", Duration.ofSeconds(30)), null, null, null, null);
  }
}
