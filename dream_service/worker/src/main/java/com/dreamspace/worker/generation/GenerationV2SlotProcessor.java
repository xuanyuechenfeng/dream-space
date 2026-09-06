package com.dreamspace.worker.generation;

import com.dreamspace.common.persistence.config.DreamSpaceProperties;
import com.dreamspace.common.persistence.generation.GenerationMapper;
import com.dreamspace.common.persistence.generation.GenerationPlanRecord;
import com.dreamspace.common.persistence.generation.GenerationPromptHash;
import com.dreamspace.common.persistence.generation.GenerationResultSlotRecord;
import com.dreamspace.common.persistence.generation.GenerationTaskRecord;
import com.dreamspace.common.persistence.generation.GenerationV2Mapper;
import com.dreamspace.common.persistence.generation.ImageCollectionPlan;
import com.dreamspace.common.persistence.generation.ResultSlotPlan;
import com.dreamspace.common.persistence.database.DatabaseEnums.GenerationSlotStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Executes v2 image slots independently and settles each result exactly once. */
@Service
public class GenerationV2SlotProcessor {
  private static final Logger log = LoggerFactory.getLogger(GenerationV2SlotProcessor.class);
  private final GenerationMapper generation;
  private final GenerationV2Mapper v2;
  private final GenerationV2SettlementService settlement;
  private final ImageGenerationModel imageModel;
  private final GenerationOutputPipeline output;
  private final ContentModerator moderator;
  private final ObjectMapper json;
  private final QualityEvaluationModel quality;
  private final int maxQualityIterations;
  private final double acceptScore;
  private final int slotConcurrency;
  private final ExecutorService executor;

  public GenerationV2SlotProcessor(GenerationMapper generation, GenerationV2Mapper v2,
      GenerationV2SettlementService settlement, ImageGenerationModel imageModel, GenerationOutputPipeline output,
      ContentModerator moderator, ObjectMapper json) {
    this(generation, v2, settlement, imageModel, output, moderator, json, null, 1, 0.0, 1);
  }

  @org.springframework.beans.factory.annotation.Autowired
  public GenerationV2SlotProcessor(GenerationMapper generation, GenerationV2Mapper v2,
      GenerationV2SettlementService settlement, ImageGenerationModel imageModel, GenerationOutputPipeline output,
      ContentModerator moderator, ObjectMapper json, QualityEvaluationModel quality,
      DreamSpaceProperties properties,
      @org.springframework.beans.factory.annotation.Value("${dream-space.worker.slot-concurrency:4}") int slotConcurrency) {
    this(generation, v2, settlement, imageModel, output, moderator, json, quality,
        properties.ai().harness().maxLoopIterations(), properties.ai().harness().acceptScore(), slotConcurrency);
  }

  public GenerationV2SlotProcessor(GenerationMapper generation, GenerationV2Mapper v2,
      GenerationV2SettlementService settlement, ImageGenerationModel imageModel, GenerationOutputPipeline output,
      ContentModerator moderator, ObjectMapper json, QualityEvaluationModel quality,
      DreamSpaceProperties properties) {
    this(generation, v2, settlement, imageModel, output, moderator, json, quality,
        properties.ai().harness().maxLoopIterations(), properties.ai().harness().acceptScore(), 4);
  }

  GenerationV2SlotProcessor(GenerationMapper generation, GenerationV2Mapper v2,
      GenerationV2SettlementService settlement, ImageGenerationModel imageModel, GenerationOutputPipeline output,
      ContentModerator moderator, ObjectMapper json, QualityEvaluationModel quality,
      int maxQualityIterations, double acceptScore) {
    this(generation, v2, settlement, imageModel, output, moderator, json, quality,
        maxQualityIterations, acceptScore, 1);
  }

  GenerationV2SlotProcessor(GenerationMapper generation, GenerationV2Mapper v2,
      GenerationV2SettlementService settlement, ImageGenerationModel imageModel, GenerationOutputPipeline output,
      ContentModerator moderator, ObjectMapper json, QualityEvaluationModel quality,
      int maxQualityIterations, double acceptScore, int slotConcurrency) {
    this.generation = generation; this.v2 = v2; this.settlement = settlement; this.imageModel = imageModel;
    this.output = output; this.moderator = moderator; this.json = json;
    this.quality = quality;
    this.maxQualityIterations = Math.max(1, maxQualityIterations);
    this.acceptScore = Math.max(0.0, Math.min(1.0, acceptScore));
    this.slotConcurrency = Math.max(1, Math.min(4, slotConcurrency));
    this.executor = Executors.newFixedThreadPool(this.slotConcurrency, runnable -> {
      Thread thread = new Thread(runnable, "dream-space-generation-slot");
      thread.setDaemon(true);
      return thread;
    });
  }

  @jakarta.annotation.PreDestroy
  void shutdown() { executor.shutdownNow(); }

  void shutdownExecutorForTest() { executor.shutdownNow(); }

  public GenerationProcessor.Outcome process(String executionId, GenerationAttempt attempt) {
    var execution = v2.findExecution(executionId);
    if (execution == null || v2.claimExecution(executionId, attempt.number()) != 1) return new GenerationProcessor.Outcome(executionId, GenerationProcessor.Status.IGNORED);
    GenerationTaskRecord task = generation.findTask(execution.taskId());
    if (task == null || task.settlementVersionValue() != 2) return new GenerationProcessor.Outcome(execution.taskId(), GenerationProcessor.Status.IGNORED);
    if (generation.claimQueuedTask(task.id(), executionId + ":" + attempt.number()) != 1)
      return new GenerationProcessor.Outcome(task.id(), GenerationProcessor.Status.IGNORED);
    task = generation.findTask(task.id());
    GenerationPlanRecord planRecord = generation.findPlan(task.id());
    ImageCollectionPlan plan = read(planRecord == null ? null : planRecord.collectionJson());
    if (plan == null || plan.slots().isEmpty()) return fail(executionId, task, "GENERATION_COLLECTION_PLAN_MISSING", "集合计划缺失");
    List<GenerationResultSlotRecord> slots = v2.listResultSlots(task.id());
    if (execution.slotIndexes() == null || !execution.slotIndexes().isArray())
      return fail(executionId, task, "GENERATION_EXECUTION_SLOTS_INVALID", "执行批次槽位范围缺失");
    final Set<Integer> executionSlots;
    try {
      List<Integer> slotIndexes = json.convertValue(execution.slotIndexes(),
          json.getTypeFactory().constructCollectionType(List.class, Integer.class));
      if (slotIndexes.stream().anyMatch(java.util.Objects::isNull))
        return fail(executionId, task, "GENERATION_EXECUTION_SLOTS_INVALID", "执行批次槽位范围无效");
      executionSlots = new java.util.HashSet<>(slotIndexes);
      if (executionSlots.size() != slotIndexes.size())
        return fail(executionId, task, "GENERATION_EXECUTION_SLOTS_INVALID", "执行批次槽位范围包含重复项");
    } catch (IllegalArgumentException error) {
      return fail(executionId, task, "GENERATION_EXECUTION_SLOTS_INVALID", "执行批次槽位范围无效");
    }
    Set<Integer> planSlots = plan.slots().stream().map(ResultSlotPlan::index).collect(Collectors.toSet());
    Set<Integer> storedSlots = slots.stream().map(GenerationResultSlotRecord::slotIndex).collect(Collectors.toSet());
    boolean contiguousPlan = java.util.stream.IntStream.range(0, plan.slots().size()).allMatch(planSlots::contains);
    if (!contiguousPlan || planSlots.size() != plan.slots().size() || slots.size() != plan.slots().size()
        || !storedSlots.equals(planSlots)
        || executionSlots.isEmpty() || !planSlots.containsAll(executionSlots)
        || execution.reservedAmount() != executionSlots.size() * task.unitCost())
      return fail(executionId, task, "GENERATION_EXECUTION_SLOTS_INVALID", "执行批次槽位范围无效");
    java.util.Map<Integer, ResultSlotPlan> planByIndex = plan.slots().stream()
        .collect(Collectors.toMap(ResultSlotPlan::index, value -> value));
    boolean promptHashesMatch = slots.stream().allMatch(slot -> {
      ResultSlotPlan planned = planByIndex.get(slot.slotIndex());
      return planned != null && planned.prompt() != null && slot.promptHash() != null
          && slot.promptHash().equals(GenerationPromptHash.sha256(planned.prompt()));
    });
    if (!promptHashesMatch)
      return fail(executionId, task, "GENERATION_COLLECTION_PLAN_INVALID", "冻结的槽位提示词校验失败");
    final GenerationTaskRecord claimedTask = task;
    List<Future<SlotOutcome>> futures = new ArrayList<>();
    GenerationProviderException retryable = null;
    RuntimeException processingFailure = null;
    boolean ownershipLost = false;
    for (ResultSlotPlan slot : plan.slots()) {
      if (!executionSlots.contains(slot.index())) continue;
      GenerationResultSlotRecord current = slots.stream().filter(s -> s.slotIndex() == slot.index()).findFirst().orElse(null);
      if (current != null && (current.status() == GenerationSlotStatus.SUCCEEDED
          || current.status() == GenerationSlotStatus.FAILED)) {
        continue;
      }
      if (current != null && current.status() == GenerationSlotStatus.CANCELLED) {
        ownershipLost = true;
        continue;
      }
      futures.add(executor.submit(() -> processSlot(executionId, claimedTask, plan, slot, current, attempt)));
    }
    for (Future<SlotOutcome> future : futures) {
      try {
        SlotOutcome result = future.get();
        if (result.retryableError() != null && retryable == null) retryable = result.retryableError();
        ownershipLost |= result.ownershipLost();
      } catch (InterruptedException error) {
        Thread.currentThread().interrupt();
        if (retryable == null) retryable = new GenerationProviderException("GENERATION_INTERRUPTED", "生成被中断", true, error);
      } catch (ExecutionException error) {
        Throwable cause = error.getCause();
        if (cause instanceof GenerationProviderException provider && provider.retryable() && retryable == null) retryable = provider;
        else {
          log.warn("generation slot future failed", cause);
          if (processingFailure == null) {
            processingFailure = cause instanceof RuntimeException runtime
                ? runtime : new IllegalStateException("generation slot execution failed", cause);
          }
        }
      }
    }
    if (processingFailure != null) throw processingFailure;
    if (retryable != null) throw retryable;
    if (ownershipLost) return new GenerationProcessor.Outcome(task.id(), GenerationProcessor.Status.IGNORED);
    boolean completed = settlement.completeExecution(executionId, claimedTask.id(), executionSlots.size());
    return new GenerationProcessor.Outcome(claimedTask.id(), completed
        ? GenerationProcessor.Status.SUCCEEDED : GenerationProcessor.Status.IGNORED);
  }

  private SlotOutcome processSlot(String executionId, GenerationTaskRecord task, ImageCollectionPlan plan,
      ResultSlotPlan slot, GenerationResultSlotRecord current, GenerationAttempt attempt) {
    if (v2.claimResultSlot(task.id(), slot.index(), executionId) != 1) return SlotOutcome.ignored();
    v2.insertTaskEvent(task.id(), "task.slot.started", "GENERATING",
        json(java.util.Map.of("executionId", executionId, "slotIndex", slot.index(), "attempt", attempt.number(),
            "concurrency", slotConcurrency)));
    List<StoredGenerationResult> stored = List.of();
    try {
      WorkerTaskSnapshot snapshot = snapshot(task);
      PromptPackage prompt = readPrompt(slot);
      int slotAttempt = current == null ? 1 : current.slotAttempt() + 1;
      String targetImageId = imageAssignment(plan, "TARGET_A");
      String referenceImageId = imageAssignment(plan, "REFERENCE_B");
      ProviderImage image = null;
      RefinementPatch refinement = null;
      for (int iteration = 1; iteration <= maxQualityIterations; iteration++) {
        var response = imageModel.generate(new ImageGenerationModel.ImageGenerationRequest(snapshot, prompt,
            targetImageId, referenceImageId, refinement, iteration, executionId, slot.index(), slotAttempt,
            List.of()), attempt);
        if (response.images() == null || response.images().isEmpty())
          throw new GenerationProviderException("PROVIDER_OUTPUT_INVALID", "provider returned no image", false);
        ProviderImage candidate = response.images().getFirst();
        image = new ProviderImage(slot.index(), candidate.data(), candidate.mimeType(), candidate.sourceName());
        if (quality == null) break;
        QualityEvaluationModel.EvaluationResult evaluation = quality.evaluate(snapshot,
            new GenerationPlanBundle(null, null, null, prompt), List.of(image), iteration);
        EvaluationReport report = evaluation == null ? null : evaluation.report();
        if (report == null) throw new GenerationProviderException("EVALUATION_OUTPUT_INVALID", "slot quality evaluator returned no report", false);
        if (report.accepted() && report.score() >= acceptScore) break;
        if (report.repairable() && evaluation.refinement() != null && iteration < maxQualityIterations) {
          refinement = evaluation.refinement();
          v2.insertTaskEvent(task.id(), "task.slot.retrying", "GENERATING",
              json(java.util.Map.of("executionId", executionId, "slotIndex", slot.index(),
                  "attempt", attempt.number(), "iteration", iteration, "errorCode", "IMAGE_QUALITY_REFINEMENT")));
          image = null;
          continue;
        }
        throw new GenerationProviderException("IMAGE_EVALUATION_REJECTED", "slot output did not satisfy its frozen acceptance contract", false);
      }
      if (image == null) throw new GenerationProviderException("IMAGE_EVALUATION_REJECTED", "slot quality refinement was exhausted", false);
      ContentModerator.Decision decision = moderator.moderateOutput(snapshot, image);
      if (!decision.approved()) throw new GenerationProviderException("OUTPUT_MODERATION_REJECTED", "slot output rejected", false);
      stored = output.persist(snapshot, List.of(image));
      StoredGenerationResult result = stored.getFirst();
      if (!settlement.publishSlot(executionId, task.id(), slot.index(), result, task.unitCost())) {
        output.cleanup(stored);
        return SlotOutcome.ignored();
      }
      return SlotOutcome.success();
    } catch (GenerationProviderException error) {
      output.cleanup(stored);
      if (error.retryable() && attempt.number() < attempt.maxAttempts()) {
        v2.insertTaskEvent(task.id(), "task.slot.retrying", "GENERATING",
            json(java.util.Map.of("executionId", executionId, "slotIndex", slot.index(), "attempt", attempt.number(), "errorCode", error.code())));
        return SlotOutcome.retry(error);
      }
      boolean recorded = settlement.recordSlotFailure(executionId, task.id(), slot.index(), error.code(), "该图片生成失败");
      return SlotOutcome.failure(recorded);
    } catch (RuntimeException error) {
      output.cleanup(stored);
      boolean recorded = settlement.recordSlotFailure(executionId, task.id(), slot.index(), "GENERATION_FAILED", "该图片生成失败");
      return SlotOutcome.failure(recorded);
    }
  }

  private record SlotOutcome(GenerationProviderException retryableError, boolean ownershipLost) {
    static SlotOutcome success() { return new SlotOutcome(null, false); }
    static SlotOutcome failure(boolean recorded) { return new SlotOutcome(null, !recorded); }
    static SlotOutcome ignored() { return new SlotOutcome(null, true); }
    static SlotOutcome retry(GenerationProviderException error) { return new SlotOutcome(error, false); }
  }

  /** Terminalizes an execution delivery that cannot be processed again. */
  public GenerationProcessor.Outcome rejectExecution(String executionId, String code, String message) {
    var execution = v2.findExecution(executionId);
    if (execution == null) {
      return new GenerationProcessor.Outcome(executionId, GenerationProcessor.Status.IGNORED);
    }
    GenerationTaskRecord task = generation.findTask(execution.taskId());
    if (task == null || task.settlementVersionValue() != 2) {
      return new GenerationProcessor.Outcome(execution.taskId(), GenerationProcessor.Status.IGNORED);
    }
    int activeSlot = v2.listResultSlots(task.id()).stream()
        .filter(slot -> slot.status() == GenerationSlotStatus.GENERATING)
        .filter(slot -> executionId.equals(slot.activeExecutionId()))
        .mapToInt(GenerationResultSlotRecord::slotIndex)
        .min()
        .orElse(-1);
    return failSlot(executionId, task, activeSlot, code, message);
  }

  private GenerationProcessor.Outcome failSlot(String executionId, GenerationTaskRecord task, int index, String code, String message) {
    boolean failed = settlement.failExecution(executionId, task.id(), index, code, message);
    return new GenerationProcessor.Outcome(task.id(), failed
        ? GenerationProcessor.Status.FAILED : GenerationProcessor.Status.IGNORED);
  }
  private GenerationProcessor.Outcome fail(String id, GenerationTaskRecord task, String code, String message) {
    return failSlot(id, task, -1, code, message);
  }
  private WorkerTaskSnapshot snapshot(GenerationTaskRecord task) {
    List<String> imageIds = task.imageIds() == null || !task.imageIds().isArray() ? List.of() : json.convertValue(task.imageIds(), json.getTypeFactory().constructCollectionType(List.class, String.class));
    return new WorkerTaskSnapshot(task.id(), task.userId(), task.sessionId(), task.prompt(), task.mode(), imageIds,
        task.model(), task.ratio(), task.resolution(), task.width(), task.height(), task.imageCount(), task.totalCost(), task.attempts());
  }
  private ImageCollectionPlan read(com.fasterxml.jackson.databind.JsonNode value) { try { return value == null ? null : json.treeToValue(value, ImageCollectionPlan.class); } catch (Exception e) { return null; } }
  private PromptPackage readPrompt(ResultSlotPlan slot) {
    try { return json.treeToValue(slot.prompt(), PromptPackage.class); }
    catch (Exception error) {
      throw new GenerationProviderException("GENERATION_COLLECTION_PLAN_INVALID", "slot prompt is invalid", false, error);
    }
  }

  private static String imageAssignment(ImageCollectionPlan plan, String expectedRole) {
    if (plan == null || plan.shared() == null || !plan.shared().path("imageAssignments").isArray()) return null;
    for (var assignment : plan.shared().path("imageAssignments")) {
      if (expectedRole.equalsIgnoreCase(assignment.path("role").asText())) {
        String imageId = assignment.path("imageId").asText(null);
        return imageId == null || imageId.isBlank() ? null : imageId;
      }
    }
    return null;
  }

  private String json(Object value) {
    try { return json.writeValueAsString(value); }
    catch (Exception error) { throw new IllegalStateException("failed to serialize generation event", error); }
  }
}
