package com.dreamspace.worker.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dreamspace.common.persistence.database.DatabaseEnums.CollectionMode;
import com.dreamspace.common.persistence.database.DatabaseEnums.GenerationExecutionKind;
import com.dreamspace.common.persistence.database.DatabaseEnums.GenerationExecutionStatus;
import com.dreamspace.common.persistence.database.DatabaseEnums.GenerationInputMode;
import com.dreamspace.common.persistence.database.DatabaseEnums.GenerationPlanStatus;
import com.dreamspace.common.persistence.database.DatabaseEnums.GenerationRatio;
import com.dreamspace.common.persistence.database.DatabaseEnums.GenerationResolution;
import com.dreamspace.common.persistence.database.DatabaseEnums.GenerationSlotStatus;
import com.dreamspace.common.persistence.database.DatabaseEnums.GenerationTaskStatus;
import com.dreamspace.common.persistence.database.DatabaseEnums.ModerationStatus;
import com.dreamspace.common.persistence.generation.GenerationExecutionRecord;
import com.dreamspace.common.persistence.generation.GenerationMapper;
import com.dreamspace.common.persistence.generation.GenerationPlanRecord;
import com.dreamspace.common.persistence.generation.GenerationPromptHash;
import com.dreamspace.common.persistence.generation.GenerationResultSlotRecord;
import com.dreamspace.common.persistence.generation.GenerationTaskRecord;
import com.dreamspace.common.persistence.generation.GenerationV2Mapper;
import com.dreamspace.common.persistence.generation.ImageCollectionPlan;
import com.dreamspace.common.persistence.generation.ResultSlotPlan;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class GenerationV2SlotProcessorTest {
  private static final String TASK_ID = "task-1";
  private static final String EXECUTION_ID = "execution-1";

  private final GenerationMapper generation = mock(GenerationMapper.class);
  private final GenerationV2Mapper v2 = mock(GenerationV2Mapper.class);
  private final GenerationV2SettlementService settlement = mock(GenerationV2SettlementService.class);
  private final ImageGenerationModel imageModel = mock(ImageGenerationModel.class);
  private final GenerationOutputPipeline output = mock(GenerationOutputPipeline.class);
  private final ContentModerator moderator = mock(ContentModerator.class);
  private final QualityEvaluationModel quality = mock(QualityEvaluationModel.class);
  private final ObjectMapper json = new ObjectMapper();
  private final GenerationV2SlotProcessor processor = new GenerationV2SlotProcessor(
      generation, v2, settlement, imageModel, output, moderator, json);

  @Test
  void runsIndependentSlotsConcurrentlyAndWaitsForAllBeforeAggregating() throws Exception {
    GenerationAttempt attempt = new GenerationAttempt(EXECUTION_ID + ":1", 1, 3);
    givenExecution(List.of(0, 1), 4, List.of(
        slotRecord(0, GenerationSlotStatus.WAITING, null),
        slotRecord(1, GenerationSlotStatus.WAITING, null)));
    GenerationV2SlotProcessor concurrent = new GenerationV2SlotProcessor(
        generation, v2, settlement, imageModel, output, moderator, json, null, 1, 0.0, 2);
    CountDownLatch started = new CountDownLatch(2);
    CountDownLatch release = new CountDownLatch(1);
    when(imageModel.generate(any(), eq(attempt))).thenAnswer(invocation -> {
      started.countDown();
      assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
      assertThat(release.await(2, TimeUnit.SECONDS)).isTrue();
      return response();
    });
    when(output.persist(any(), any())).thenAnswer(invocation -> {
      List<ProviderImage> images = invocation.getArgument(1);
      return List.of(stored(images.getFirst().index()));
    });
    when(settlement.publishSlot(anyString(), anyString(), anyInt(), any(), anyInt())).thenReturn(true);
    when(settlement.completeExecution(EXECUTION_ID, TASK_ID, 2)).thenReturn(true);

    var future = java.util.concurrent.CompletableFuture.supplyAsync(() -> concurrent.process(EXECUTION_ID, attempt));
    assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
    release.countDown();
    assertThat(future.get(3, TimeUnit.SECONDS).status()).isEqualTo(GenerationProcessor.Status.SUCCEEDED);
    verify(imageModel, times(2)).generate(any(), eq(attempt));
    verify(settlement, times(2)).publishSlot(anyString(), eq(TASK_ID), anyInt(), any(), eq(2));
    verify(settlement).completeExecution(EXECUTION_ID, TASK_ID, 2);
    concurrent.shutdownExecutorForTest();
  }

  @Test
  void generatesAndSettlesSlotsStrictlySerialOneAtATime() {
    GenerationAttempt attempt = new GenerationAttempt(EXECUTION_ID + ":1", 1, 3);
    givenExecution(List.of(0, 1, 2), 6, List.of(
        slotRecord(0, GenerationSlotStatus.WAITING, null),
        slotRecord(1, GenerationSlotStatus.WAITING, null),
        slotRecord(2, GenerationSlotStatus.WAITING, null)));
    List<String> timeline = new ArrayList<>();
    when(imageModel.generate(any(), eq(attempt))).thenAnswer(invocation -> {
      int index = requestSlot(invocation.getArgument(0));
      timeline.add("generate-" + index);
      return response();
    });
    when(output.persist(any(), any())).thenAnswer(invocation -> {
      List<ProviderImage> images = invocation.getArgument(1);
      assertThat(images).hasSize(1);
      int index = images.getFirst().index();
      timeline.add("persist-" + index);
      return List.of(stored(index));
    });
    when(settlement.publishSlot(anyString(), anyString(), anyInt(), any(), anyInt()))
        .thenAnswer(invocation -> {
          timeline.add("settle-" + invocation.getArgument(2));
          return true;
        });
    when(settlement.completeExecution(EXECUTION_ID, TASK_ID, 3)).thenReturn(true);

    GenerationProcessor.Outcome outcome = processor.process(EXECUTION_ID, attempt);

    assertThat(outcome.status()).isEqualTo(GenerationProcessor.Status.SUCCEEDED);
    assertThat(timeline).containsExactly(
        "generate-0", "persist-0", "settle-0",
        "generate-1", "persist-1", "settle-1",
        "generate-2", "persist-2", "settle-2");
    ArgumentCaptor<ImageGenerationModel.ImageGenerationRequest> requests =
        ArgumentCaptor.forClass(ImageGenerationModel.ImageGenerationRequest.class);
    verify(imageModel, times(3)).generate(requests.capture(), eq(attempt));
    assertThat(requests.getAllValues()).allSatisfy(request -> {
      assertThat(request.iteration()).isEqualTo(1);
      assertThat(request.executionId()).isEqualTo(EXECUTION_ID);
      assertThat(request.slotAttempt()).isEqualTo(1);
    });
  }

  @Test
  void isolatesSlotFailureFromSiblingSlots() {
    GenerationAttempt attempt = new GenerationAttempt(EXECUTION_ID + ":1", 1, 3);
    givenExecution(List.of(0, 1, 2), 6, List.of(
        slotRecord(0, GenerationSlotStatus.WAITING, null),
        slotRecord(1, GenerationSlotStatus.WAITING, null),
        slotRecord(2, GenerationSlotStatus.WAITING, null)));
    List<Integer> generated = new ArrayList<>();
    when(imageModel.generate(any(), eq(attempt))).thenAnswer(invocation -> {
      int index = requestSlot(invocation.getArgument(0));
      generated.add(index);
      if (index == 1) {
        throw new GenerationProviderException("PROVIDER_REJECTED", "rejected", false);
      }
      return response();
    });
    when(output.persist(any(), any())).thenAnswer(invocation -> {
      List<ProviderImage> images = invocation.getArgument(1);
      return List.of(stored(images.getFirst().index()));
    });
    when(settlement.publishSlot(anyString(), eq(TASK_ID), anyInt(), any(), eq(2))).thenReturn(true);
    when(settlement.recordSlotFailure(EXECUTION_ID, TASK_ID, 1, "PROVIDER_REJECTED",
        "该图片生成失败")).thenReturn(true);
    when(settlement.completeExecution(EXECUTION_ID, TASK_ID, 3)).thenReturn(true);

    GenerationProcessor.Outcome outcome = processor.process(EXECUTION_ID, attempt);

    assertThat(outcome.status()).isEqualTo(GenerationProcessor.Status.SUCCEEDED);
    assertThat(generated).containsExactlyInAnyOrder(0, 1, 2);
    verify(settlement, times(2)).publishSlot(eq(EXECUTION_ID), eq(TASK_ID), anyInt(), any(), eq(2));
    verify(settlement).recordSlotFailure(EXECUTION_ID, TASK_ID, 1, "PROVIDER_REJECTED", "该图片生成失败");
    verify(settlement).completeExecution(EXECUTION_ID, TASK_ID, 3);
    verify(settlement, never()).failExecution(anyString(), anyString(), anyInt(), anyString(), anyString());
  }

  @Test
  void claimLossDoesNotPreventSiblingSlotFromCompleting() {
    GenerationAttempt attempt = new GenerationAttempt(EXECUTION_ID + ":1", 1, 3);
    givenExecution(List.of(0, 1), 4, List.of(
        slotRecord(0, GenerationSlotStatus.WAITING, null),
        slotRecord(1, GenerationSlotStatus.WAITING, null)));
    when(v2.claimResultSlot(TASK_ID, 0, EXECUTION_ID)).thenReturn(0);
    when(imageModel.generate(any(), eq(attempt))).thenReturn(response());
    when(output.persist(any(), any())).thenReturn(List.of(stored(1)));
    when(settlement.publishSlot(EXECUTION_ID, TASK_ID, 1, stored(1), 2)).thenReturn(true);

    GenerationProcessor.Outcome outcome = processor.process(EXECUTION_ID, attempt);

    assertThat(outcome.status()).isEqualTo(GenerationProcessor.Status.IGNORED);
    verify(v2).claimResultSlot(TASK_ID, 1, EXECUTION_ID);
    verify(imageModel).generate(any(), eq(attempt));
    verify(settlement).publishSlot(EXECUTION_ID, TASK_ID, 1, stored(1), 2);
    verify(settlement, never()).completeExecution(anyString(), anyString(), anyInt());
  }

  @Test
  void redeliverySkipsSucceededSlotsAndResumesAtFirstMissingSlot() {
    GenerationAttempt attempt = new GenerationAttempt(EXECUTION_ID + ":2", 2, 3);
    givenExecution(List.of(0, 1), 4, List.of(
        slotRecord(0, GenerationSlotStatus.SUCCEEDED, EXECUTION_ID),
        slotRecord(1, GenerationSlotStatus.WAITING, null)));
    when(imageModel.generate(any(), eq(attempt))).thenReturn(response());
    when(output.persist(any(), any())).thenReturn(List.of(stored(1)));
    when(settlement.publishSlot(EXECUTION_ID, TASK_ID, 1, stored(1), 2)).thenReturn(true);
    when(settlement.completeExecution(EXECUTION_ID, TASK_ID, 2)).thenReturn(true);

    assertThat(processor.process(EXECUTION_ID, attempt).status())
        .isEqualTo(GenerationProcessor.Status.SUCCEEDED);

    ArgumentCaptor<ImageGenerationModel.ImageGenerationRequest> request =
        ArgumentCaptor.forClass(ImageGenerationModel.ImageGenerationRequest.class);
    verify(imageModel).generate(request.capture(), eq(attempt));
    assertThat(requestSlot(request.getValue())).isEqualTo(1);
    verify(v2, never()).claimResultSlot(TASK_ID, 0, EXECUTION_ID);
    verify(settlement, never()).publishSlot(eq(EXECUTION_ID), eq(TASK_ID), eq(0), any(), eq(2));
  }

  @Test
  void redeliverySkipsRecordedFailureAndCompletesRemainingSlots() {
    GenerationAttempt attempt = new GenerationAttempt(EXECUTION_ID + ":2", 2, 3);
    givenExecution(List.of(0, 1), 4, List.of(
        slotRecord(0, GenerationSlotStatus.FAILED, EXECUTION_ID),
        slotRecord(1, GenerationSlotStatus.WAITING, null)));
    when(imageModel.generate(any(), eq(attempt))).thenReturn(response());
    when(output.persist(any(), any())).thenReturn(List.of(stored(1)));
    when(settlement.publishSlot(EXECUTION_ID, TASK_ID, 1, stored(1), 2)).thenReturn(true);
    when(settlement.completeExecution(EXECUTION_ID, TASK_ID, 2)).thenReturn(true);

    assertThat(processor.process(EXECUTION_ID, attempt).status())
        .isEqualTo(GenerationProcessor.Status.SUCCEEDED);

    verify(v2, never()).claimResultSlot(TASK_ID, 0, EXECUTION_ID);
    verify(settlement, never()).recordSlotFailure(eq(EXECUTION_ID), eq(TASK_ID), eq(0), anyString(), anyString());
    verify(settlement).completeExecution(EXECUTION_ID, TASK_ID, 2);
  }

  @Test
  void cleansPersistedObjectWhenSlotSettlementLosesOwnership() {
    GenerationAttempt attempt = new GenerationAttempt(EXECUTION_ID + ":1", 1, 3);
    givenExecution(List.of(0), 2, List.of(slotRecord(0, GenerationSlotStatus.WAITING, null)));
    StoredGenerationResult result = stored(0);
    when(imageModel.generate(any(), eq(attempt))).thenReturn(response());
    when(output.persist(any(), any())).thenReturn(List.of(result));
    when(settlement.publishSlot(EXECUTION_ID, TASK_ID, 0, result, 2)).thenReturn(false);

    assertThat(processor.process(EXECUTION_ID, attempt).status())
        .isEqualTo(GenerationProcessor.Status.IGNORED);

    verify(output).cleanup(List.of(result));
    verify(settlement, never()).completeExecution(anyString(), anyString(), anyInt());
    verify(settlement, never()).failExecution(anyString(), anyString(), anyInt(), anyString(), anyString());
  }

  @Test
  void rejectsAStoredPromptWhoseFrozenHashNoLongerMatches() {
    GenerationAttempt attempt = new GenerationAttempt(EXECUTION_ID + ":1", 1, 3);
    givenExecution(List.of(0), 2, List.of(new GenerationResultSlotRecord(
        TASK_ID, 0, "slot 0", "VARIATION", "intent 0", "different-prompt-hash",
        GenerationSlotStatus.WAITING, null, null, 0, null, null, null, null, null, null)));
    when(settlement.failExecution(EXECUTION_ID, TASK_ID, -1,
        "GENERATION_COLLECTION_PLAN_INVALID", "冻结的槽位提示词校验失败")).thenReturn(true);

    assertThat(processor.process(EXECUTION_ID, attempt).status())
        .isEqualTo(GenerationProcessor.Status.FAILED);

    verify(imageModel, never()).generate(any(), any());
    verify(settlement).failExecution(EXECUTION_ID, TASK_ID, -1,
        "GENERATION_COLLECTION_PLAN_INVALID", "冻结的槽位提示词校验失败");
  }

  @Test
  void rejectsAFrozenPlanWithMissingPromptWithoutCallingProvider() {
    GenerationAttempt attempt = new GenerationAttempt(EXECUTION_ID + ":1", 1, 3);
    givenExecution(List.of(0), 2, List.of(slotRecord(0, GenerationSlotStatus.WAITING, null)));
    ResultSlotPlan valid = slotPlan(0);
    ImageCollectionPlan invalid = new ImageCollectionPlan("collection-v2", CollectionMode.VARIATIONS,
        json.createObjectNode(), List.of(new ResultSlotPlan(valid.index(), valid.label(), valid.role(),
            valid.intent(), valid.contentScope(), valid.variationConstraints(), null, valid.acceptance())),
        0.95, List.of());
    when(generation.findPlan(TASK_ID)).thenReturn(new GenerationPlanRecord("plan-1", TASK_ID,
        "collection-v2", GenerationPlanStatus.RUNNABLE, "hash", null, null, null, null, null, null,
        json.valueToTree(invalid), CollectionMode.VARIATIONS));
    when(settlement.failExecution(EXECUTION_ID, TASK_ID, -1,
        "GENERATION_COLLECTION_PLAN_INVALID", "冻结的槽位提示词校验失败")).thenReturn(true);

    assertThat(processor.process(EXECUTION_ID, attempt).status())
        .isEqualTo(GenerationProcessor.Status.FAILED);

    verify(imageModel, never()).generate(any(), any());
  }

  @Test
  void doesNotReReadTaskOrCleanObjectAfterSettlementCommits() {
    GenerationAttempt attempt = new GenerationAttempt(EXECUTION_ID + ":1", 1, 3);
    GenerationTaskRecord task = task(1);
    givenExecution(task, List.of(0), 2, List.of(slotRecord(0, GenerationSlotStatus.WAITING, null)));
    when(generation.findTask(TASK_ID)).thenReturn(task, task).thenThrow(new IllegalStateException("late read failed"));
    StoredGenerationResult result = stored(0);
    when(imageModel.generate(any(), eq(attempt))).thenReturn(response());
    when(output.persist(any(), any())).thenReturn(List.of(result));
    when(settlement.publishSlot(EXECUTION_ID, TASK_ID, 0, result, 2)).thenReturn(true);
    when(settlement.completeExecution(EXECUTION_ID, TASK_ID, 1)).thenReturn(true);

    assertThat(processor.process(EXECUTION_ID, attempt).status())
        .isEqualTo(GenerationProcessor.Status.SUCCEEDED);

    verify(generation, times(2)).findTask(TASK_ID);
    verify(output, never()).cleanup(any());
  }

  @Test
  void refinesOneSlotUntilItsFrozenQualityContractPasses() {
    GenerationV2SlotProcessor qualityProcessor = new GenerationV2SlotProcessor(
        generation, v2, settlement, imageModel, output, moderator, json, quality, 2, 0.8);
    GenerationAttempt attempt = new GenerationAttempt(EXECUTION_ID + ":1", 1, 3);
    givenExecution(List.of(0), 2, List.of(slotRecord(0, GenerationSlotStatus.WAITING, null)));
    when(imageModel.generate(any(), eq(attempt))).thenReturn(response());
    RefinementPatch patch = new RefinementPatch("strengthen the slot intent", List.of(),
        List.of("make the required season visible"), List.of("shared subject"), List.of("SLOT_INTENT"));
    when(quality.evaluate(any(), any(), any(), eq(1))).thenReturn(
        new QualityEvaluationModel.EvaluationResult(
            new EvaluationReport(false, 0.6, List.of("SLOT_INTENT"), true, List.of(), "quality-v1"), patch));
    when(quality.evaluate(any(), any(), any(), eq(2))).thenReturn(
        new QualityEvaluationModel.EvaluationResult(
            new EvaluationReport(true, 0.95, List.of(), false, List.of(), "quality-v1"), null));
    when(output.persist(any(), any())).thenReturn(List.of(stored(0)));
    when(settlement.publishSlot(EXECUTION_ID, TASK_ID, 0, stored(0), 2)).thenReturn(true);
    when(settlement.completeExecution(EXECUTION_ID, TASK_ID, 1)).thenReturn(true);

    assertThat(qualityProcessor.process(EXECUTION_ID, attempt).status())
        .isEqualTo(GenerationProcessor.Status.SUCCEEDED);

    ArgumentCaptor<ImageGenerationModel.ImageGenerationRequest> requests =
        ArgumentCaptor.forClass(ImageGenerationModel.ImageGenerationRequest.class);
    verify(imageModel, times(2)).generate(requests.capture(), eq(attempt));
    assertThat(requests.getAllValues()).extracting(ImageGenerationModel.ImageGenerationRequest::iteration)
        .containsExactly(1, 2);
    assertThat(requests.getAllValues().get(0).refinement()).isNull();
    assertThat(requests.getAllValues().get(1).refinement()).isEqualTo(patch);
    verify(settlement, times(1)).publishSlot(EXECUTION_ID, TASK_ID, 0, stored(0), 2);
  }

  @Test
  void exhaustedDeliveryFailsItsActiveSlotThroughV2Settlement() {
    GenerationExecutionRecord execution = new GenerationExecutionRecord(EXECUTION_ID, TASK_ID,
        GenerationExecutionKind.INITIAL, GenerationExecutionStatus.GENERATING, "execution-key",
        json.valueToTree(List.of(0, 1)), 4, 0, 0, "rule-1", 1, null, 3, null, null, null);
    when(v2.findExecution(EXECUTION_ID)).thenReturn(execution);
    when(generation.findTask(TASK_ID)).thenReturn(task(2));
    when(v2.listResultSlots(TASK_ID)).thenReturn(List.of(
        slotRecord(0, GenerationSlotStatus.GENERATING, EXECUTION_ID),
        slotRecord(1, GenerationSlotStatus.WAITING, null)));
    when(settlement.failExecution(EXECUTION_ID, TASK_ID, 0, "QUEUE_ATTEMPTS_EXHAUSTED",
        "该图片多次生成失败，后续图片已停止")).thenReturn(true);

    GenerationProcessor.Outcome outcome = processor.rejectExecution(EXECUTION_ID,
        "QUEUE_ATTEMPTS_EXHAUSTED", "该图片多次生成失败，后续图片已停止");

    assertThat(outcome.status()).isEqualTo(GenerationProcessor.Status.FAILED);
    verify(settlement).failExecution(EXECUTION_ID, TASK_ID, 0, "QUEUE_ATTEMPTS_EXHAUSTED",
        "该图片多次生成失败，后续图片已停止");
    verify(imageModel, never()).generate(any(), any());
  }

  @Test
  void passesOnlyFrozenTargetAndReferenceAssignmentsToTheSlotRequest() {
    GenerationAttempt attempt = new GenerationAttempt(EXECUTION_ID + ":1", 1, 3);
    givenExecution(List.of(0), 2, List.of(slotRecord(0, GenerationSlotStatus.WAITING, null)));
    ObjectNode shared = json.createObjectNode();
    var assignments = shared.putArray("imageAssignments");
    assignments.addObject().put("imageId", "target-a").put("role", "TARGET_A");
    assignments.addObject().put("imageId", "reference-b").put("role", "REFERENCE_B");
    assignments.addObject().put("imageId", "unused-c").put("role", "UNUSED");
    when(generation.findPlan(TASK_ID)).thenReturn(plan(1, shared));
    when(imageModel.generate(any(), eq(attempt))).thenReturn(response());
    when(output.persist(any(), any())).thenReturn(List.of(stored(0)));
    when(settlement.publishSlot(EXECUTION_ID, TASK_ID, 0, stored(0), 2)).thenReturn(true);
    when(settlement.completeExecution(EXECUTION_ID, TASK_ID, 1)).thenReturn(true);

    assertThat(processor.process(EXECUTION_ID, attempt).status())
        .isEqualTo(GenerationProcessor.Status.SUCCEEDED);

    ArgumentCaptor<ImageGenerationModel.ImageGenerationRequest> request =
        ArgumentCaptor.forClass(ImageGenerationModel.ImageGenerationRequest.class);
    verify(imageModel).generate(request.capture(), eq(attempt));
    assertThat(request.getValue().targetImageId()).isEqualTo("target-a");
    assertThat(request.getValue().referenceImageId()).isEqualTo("reference-b");
    assertThat(request.getValue().inputImageIds()).isEmpty();
  }

  private void givenExecution(List<Integer> indexes, int reserved,
      List<GenerationResultSlotRecord> slots) {
    givenExecution(task(slots.size()), indexes, reserved, slots);
  }

  private void givenExecution(GenerationTaskRecord task, List<Integer> indexes, int reserved,
      List<GenerationResultSlotRecord> slots) {
    GenerationExecutionRecord execution = new GenerationExecutionRecord(EXECUTION_ID, TASK_ID,
        GenerationExecutionKind.INITIAL, GenerationExecutionStatus.QUEUED, "execution-key",
        json.valueToTree(indexes), reserved, 0, 0, "rule-1", 1, null, 0, null, null, null);
    when(v2.findExecution(EXECUTION_ID)).thenReturn(execution);
    when(v2.claimExecution(EXECUTION_ID, 1)).thenReturn(1);
    when(v2.claimExecution(EXECUTION_ID, 2)).thenReturn(1);
    when(generation.findTask(TASK_ID)).thenReturn(task);
    when(generation.claimQueuedTask(eq(TASK_ID), anyString())).thenReturn(1);
    when(generation.findPlan(TASK_ID)).thenReturn(plan(slots.size()));
    when(v2.listResultSlots(TASK_ID)).thenReturn(slots);
    when(v2.claimResultSlot(eq(TASK_ID), anyInt(), eq(EXECUTION_ID))).thenReturn(1);
    when(moderator.moderateOutput(any(), any())).thenReturn(new ContentModerator.Decision(true, null));
  }

  private GenerationPlanRecord plan(int count) {
    return plan(count, json.createObjectNode());
  }

  private GenerationPlanRecord plan(int count, com.fasterxml.jackson.databind.JsonNode shared) {
    List<ResultSlotPlan> slots = java.util.stream.IntStream.range(0, count)
        .mapToObj(this::slotPlan)
        .toList();
    ImageCollectionPlan plan = new ImageCollectionPlan("collection-v2", CollectionMode.VARIATIONS,
        shared, slots, 0.95, List.of());
    return new GenerationPlanRecord("plan-1", TASK_ID, "collection-v2", GenerationPlanStatus.RUNNABLE,
        "hash", null, null, null, null, null, null, json.valueToTree(plan), CollectionMode.VARIATIONS);
  }

  private ResultSlotPlan slotPlan(int index) {
    PromptPackage prompt = new PromptPackage("slot " + index, "", Map.of("slotIndex", index),
        "preserve supplied text", "collection-v2");
    return new ResultSlotPlan(index, "slot " + index, "VARIATION", "intent " + index,
        List.of("intent " + index), List.of(), json.valueToTree(prompt), json.createObjectNode());
  }

  private GenerationResultSlotRecord slotRecord(int index, GenerationSlotStatus status,
      String activeExecutionId) {
    return new GenerationResultSlotRecord(TASK_ID, index, "slot " + index, "VARIATION",
        "intent " + index, GenerationPromptHash.sha256(slotPlan(index).prompt()), status,
        status == GenerationSlotStatus.SUCCEEDED ? "result-" + index : null,
        activeExecutionId, 0, null, null, null, null, null, null);
  }

  private GenerationTaskRecord task(int count) {
    return new GenerationTaskRecord(TASK_ID, "session-1", "user-1", GenerationTaskStatus.GENERATING,
        "prompt", GenerationInputMode.TEXT_TO_IMAGE, json.createArrayNode(), "image-model",
        GenerationRatio.RATIO_1_1, GenerationResolution.K2, 1024, 1024, count, 2, count * 2,
        "task-key", null, 1, EXECUTION_ID + ":1", null, null,
        ModerationStatus.APPROVED, ModerationStatus.PENDING, null, null, null, null,
        "rule-1", 1, (short) 2, "preflight-1", 0);
  }

  private static ImageGenerationModel.ImageGenerationResponse response() {
    return new ImageGenerationModel.ImageGenerationResponse(
        List.of(new ProviderImage(0, new byte[] {1}, "image/png", "provider-image")),
        "provider", "model", "request-1");
  }

  private static StoredGenerationResult stored(int index) {
    return new StoredGenerationResult("result-" + index, index, "/result/" + index,
        "object-" + index, "thumbnail-" + index, "checksum-" + index,
        1024, 1024, "image/png", 10, 480, 480, 5);
  }

  private static int requestSlot(ImageGenerationModel.ImageGenerationRequest request) {
    return ((Number) request.promptPackage().modelInput().get("slotIndex")).intValue();
  }
}
