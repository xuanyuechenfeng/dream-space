package com.dreamspace.api.service;

import com.dreamspace.api.common.ApiException;
import com.dreamspace.common.persistence.config.DreamSpaceProperties;
import com.dreamspace.common.persistence.database.DatabaseEnums.GenerationTaskStatus;
import com.dreamspace.common.persistence.database.DatabaseEnums.GenerationInputMode;
import com.dreamspace.common.persistence.generation.GenerationPlanRecord;
import com.dreamspace.common.persistence.generation.GenerationIterationRecord;
import com.dreamspace.common.persistence.generation.GenerationMapper;
import com.dreamspace.common.persistence.generation.GenerationResultRecord;
import com.dreamspace.common.persistence.generation.GenerationSessionRecord;
import com.dreamspace.common.persistence.generation.GenerationTaskEventRecord;
import com.dreamspace.common.persistence.generation.GenerationTaskRecord;
import com.dreamspace.common.persistence.generation.GenerationResultSlotRecord;
import com.dreamspace.common.persistence.generation.GenerationV2Mapper;
import com.dreamspace.common.persistence.generation.GenerationExecutionRecord;
import com.dreamspace.common.persistence.quota.QuotaAccountRecord;
import com.dreamspace.common.persistence.quota.QuotaTransactionService;
import com.dreamspace.common.persistence.storage.ObjectStorage;
import com.dreamspace.common.persistence.storage.ObjectStorageFactory;
import com.dreamspace.api.persistence.admin.BillingMapper;
import com.dreamspace.api.persistence.admin.PricingRuleRecord;
import com.dreamspace.api.persistence.upload.ReferenceUploadMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class GenerationService {
  private static final List<String> MODELS = List.of("image-4.7", "image-5-lite");
  private static final List<String> RATIOS = List.of("smart", "21:9", "16:9", "3:2", "4:3", "1:1", "3:4", "2:3", "9:16", "custom");
  private static final List<String> RESOLUTIONS = List.of("2K", "4K");
  private static final List<String> REFERENCE_MIMES = List.of("image/jpeg", "image/png", "image/webp");
  private final GenerationMapper mapper;
  private final QuotaTransactionService quota;
  private final GenerationQueuePublisher queuePublisher;
  private final ObjectStorageFactory storage;
  private final DreamSpaceProperties properties;
  private final ObjectMapper objectMapper;
  private final ReferenceUploadMapper uploads;
  private final BillingMapper billing;
  private final GenerationV2Mapper v2;
  private final TransactionTemplate transactions;
  private final ExecutorService sseExecutor = Executors.newCachedThreadPool(r -> {
    Thread thread = new Thread(r, "dream-space-generation-sse");
    thread.setDaemon(true);
    return thread;
  });

  public GenerationService(GenerationMapper mapper, QuotaTransactionService quota, GenerationQueuePublisher queuePublisher,
      ObjectStorageFactory storage, DreamSpaceProperties properties, ObjectMapper objectMapper,
      PlatformTransactionManager transactionManager, ReferenceUploadMapper uploads) {
    this(mapper, quota, queuePublisher, storage, properties, objectMapper, transactionManager, uploads, null, null);
  }
  public GenerationService(GenerationMapper mapper, QuotaTransactionService quota, GenerationQueuePublisher queuePublisher,
      ObjectStorageFactory storage, DreamSpaceProperties properties, ObjectMapper objectMapper,
      PlatformTransactionManager transactionManager, ReferenceUploadMapper uploads, BillingMapper billing) {
    this(mapper, quota, queuePublisher, storage, properties, objectMapper, transactionManager, uploads, billing, null);
  }
  @org.springframework.beans.factory.annotation.Autowired
  public GenerationService(GenerationMapper mapper, QuotaTransactionService quota, GenerationQueuePublisher queuePublisher,
      ObjectStorageFactory storage, DreamSpaceProperties properties, ObjectMapper objectMapper,
      PlatformTransactionManager transactionManager, ReferenceUploadMapper uploads, BillingMapper billing, GenerationV2Mapper v2) {
    this.mapper = mapper;
    this.quota = quota;
    this.queuePublisher = queuePublisher;
    this.storage = storage;
    this.properties = properties;
    this.objectMapper = objectMapper;
    this.uploads = uploads;
    this.billing = billing;
    this.v2 = v2;
    this.transactions = new TransactionTemplate(transactionManager);
  }

  @JsonIgnoreProperties(ignoreUnknown = false)
  public record Draft(String mode, String prompt, List<String> imageIds, String ratio,
      String resolution, Integer width, Integer height, String imageCountMode, Integer imageCount) {
    public Draft(String mode, String prompt, List<String> imageIds, String ratio,
        String resolution, Integer width, Integer height) {
      this(mode, prompt, imageIds, ratio, resolution, width, height, "AUTO", null);
    }
  }
  @JsonIgnoreProperties(ignoreUnknown = false)
  public record TaskRequest(String idempotencyKey, String sessionId, String mode, String prompt,
      List<String> imageIds, String ratio, String resolution, Integer width, Integer height, String planToken,
      String imageCountMode, Integer imageCount) {
    public TaskRequest(String idempotencyKey, String sessionId, String mode, String prompt,
        List<String> imageIds, String ratio, String resolution, Integer width, Integer height) {
      this(idempotencyKey, sessionId, mode, prompt, imageIds, ratio, resolution, width, height, null, "AUTO", null);
    }
    public TaskRequest(String idempotencyKey, String sessionId, String mode, String prompt,
        List<String> imageIds, String ratio, String resolution, Integer width, Integer height, String planToken) {
      this(idempotencyKey, sessionId, mode, prompt, imageIds, ratio, resolution, width, height, planToken, "AUTO", null);
    }
  }
  public record Options(List<String> modes, List<RatioOption> ratios, List<ResolutionOption> resolutions,
      DimensionLimits dimensions, ReferenceLimits referenceImages, ImageCountOptions imageCounts) {
    public Options(List<String> modes, List<RatioOption> ratios, List<ResolutionOption> resolutions,
        DimensionLimits dimensions, ReferenceLimits referenceImages) {
      this(modes, ratios, resolutions, dimensions, referenceImages, new ImageCountOptions("AUTO", 1, 4, List.of(1,2,3,4)));
    }
  }
  public record ImageCountOptions(String defaultMode, int min, int max, List<Integer> values) {}
  public record RatioOption(String value, String label) {}
  public record ResolutionOption(String value, String label, int maxEdge, long maxPixels, int unitCost,
      boolean enabled, String disabledReason) {}
  public record DimensionLimits(int minEdge, int step) {}
  public record ReferenceLimits(int max, int maxBytes, List<String> mimeTypes) {}
  public record QuotaView(int total, int available, int reserved, int used, int remainingPercent) {}
  public record SessionSummary(String id, String title, String thumbnailUrl, Instant createdAt, Instant updatedAt) {}
  public record SessionDetail(String id, String title, Draft draft, Instant createdAt, Instant updatedAt,
      List<TaskView> tasks) {}
  public record ResultView(String id, int index, String contentUrl, String thumbnailUrl, int width, int height,
      String mimeType, int byteSize, boolean isAiGenerated, String moderationStatus) {}
  public record SlotView(int index, String label, String role, String status, ResultView result,
      String errorCode, String errorMessage) {}
  public record TaskView(String id, String sessionId, String status, String mode, String prompt,
      List<String> imageIds, String model, String ratio, String resolution, Integer width, Integer height,
      int imageCount, int unitCost, int totalCost,
      String errorCode, String errorMessage, Instant startedAt, Instant completedAt, Instant createdAt,
      Instant updatedAt, String planStatus, String currentStage, int currentIteration, Double evaluationScore,
      List<ResultView> results, int consumedCost, int successfulCount, int missingCount,
      String collectionMode, List<SlotView> slots) {
    public TaskView(String id, String sessionId, String status, String mode, String prompt,
        List<String> imageIds, String model, String ratio, String resolution, Integer width, Integer height,
        int imageCount, int unitCost, int totalCost, String errorCode, String errorMessage, Instant startedAt,
        Instant completedAt, Instant createdAt, Instant updatedAt, String planStatus, String currentStage,
        int currentIteration, Double evaluationScore, List<ResultView> results) {
      this(id, sessionId, status, mode, prompt, imageIds, model, ratio, resolution, width, height, imageCount,
          unitCost, totalCost, errorCode, errorMessage, startedAt, completedAt, createdAt, updatedAt, planStatus,
          currentStage, currentIteration, evaluationScore, results, 0, results == null ? 0 : results.size(),
          Math.max(0, imageCount - (results == null ? 0 : results.size())), null, List.of());
    }
  }
  public record SubmitResponse(SessionDetail session, TaskView task, QuotaView quota, boolean replayed) {}
  public record EventView(long id, String taskId, String type, String status, JsonNode payload, Instant createdAt) {}
  public record PlanView(String taskId, String status, JsonNode requirement, JsonNode structure, JsonNode visual,
      JsonNode promptPackage, List<GenerationIterationRecord> iterations) {}

  public Options options() {
    List<RatioOption> ratios = RATIOS.stream().filter(value -> !"custom".equals(value))
        .map(value -> new RatioOption(value, "smart".equals(value) ? "智能" : value)).toList();
    return new Options(List.of("AUTO"), ratios, List.of(
        new ResolutionOption("2K", "高清 2K", 2048, 2048L * 2048L, 1, true, null),
        new ResolutionOption("4K", "超清 4K", 4096, 4096L * 4096L, 2, true, null)),
        new DimensionLimits(512, 64), new ReferenceLimits(2, 10 * 1024 * 1024, REFERENCE_MIMES));
  }

  public QuotaView quota(String userId) {
    return quotaView(quota.ensureAndRead(userId, properties.quota().initialTotal()));
  }

  public List<SessionSummary> listSessions(String userId) {
    return mapper.listSessions(userId).stream().map(this::summary).toList();
  }

  public SessionDetail getSession(String userId, String sessionId) {
    GenerationSessionRecord session = ownedSession(userId, sessionId);
    return detail(userId, session);
  }

  public SessionDetail createSession(String userId, Draft draft) {
    Draft normalized = normalizeDraft(draft == null ? defaultDraft() : draft);
    String id = UUID.randomUUID().toString();
    transactions.executeWithoutResult(status -> {
      quota.ensureAndRead(userId, properties.quota().initialTotal());
      mapper.insertSession(id, userId, titleFor(normalized.prompt()), json(normalized));
    });
    return getSession(userId, id);
  }

  public SessionDetail renameSession(String userId, String sessionId, String title) {
    ownedSession(userId, sessionId);
    String normalized = title == null ? "" : title.trim();
    if (normalized.isEmpty() || normalized.length() > 80) throw bad("SESSION_TITLE_INVALID", "会话名称长度应为 1-80 个字符");
    transactions.executeWithoutResult(status -> mapper.renameSession(userId, sessionId, normalized));
    return getSession(userId, sessionId);
  }

  public SessionDetail updateDraft(String userId, String sessionId, Draft draft) {
    ownedSession(userId, sessionId);
    Draft normalized = normalizeDraft(draft);
    transactions.executeWithoutResult(status -> mapper.updateDraft(userId, sessionId, json(normalized)));
    return getSession(userId, sessionId);
  }

  public void deleteSession(String userId, String sessionId) {
    ownedSession(userId, sessionId);
    transactions.executeWithoutResult(status -> {
      int activeTasks = mapper.countActiveTasks(sessionId);
      int activePreflights = mapper.countActivePreflights(sessionId);
      if (activeTasks > 0 || activePreflights > 0) {
        throw bad("SESSION_ACTIVE", "生成任务进行中，暂不能删除会话");
      }
      if (mapper.deleteSession(userId, sessionId) == 1) return;
      // The delete SQL repeats the activity guard so a task/preflight that
      // starts between the checks cannot be removed accidentally.
      if (mapper.findSession(userId, sessionId) != null) {
        throw bad("SESSION_ACTIVE", "生成任务进行中，暂不能删除会话");
      }
      throw bad("NOT_FOUND", "会话不存在");
    });
  }

  public SubmitResponse submit(String userId, TaskRequest request) {
    Validated input = validate(userId, request);
    GenerationTaskRecord existing = mapper.findByIdempotencyKey(userId, input.idempotencyKey());
    if (existing != null) {
      if (!sameInput(existing, input)) throw bad("GENERATION_IDEMPOTENCY_CONFLICT", "幂等键已用于其他生成参数");
      return new SubmitResponse(detail(userId, ownedSession(userId, existing.sessionId())), taskView(existing), quota(userId), true);
    }
    CreatedTask created;
    try {
      created = transactions.execute(status -> createTask(userId, input));
    } catch (DuplicateKeyException duplicate) {
      GenerationTaskRecord replay = mapper.findByIdempotencyKey(userId, input.idempotencyKey());
      if (replay == null) throw duplicate;
      if (!sameInput(replay, input)) throw bad("GENERATION_IDEMPOTENCY_CONFLICT", "幂等键已用于其他生成参数");
      return new SubmitResponse(detail(userId, ownedSession(userId, replay.sessionId())), taskView(replay), quota(userId), true);
    }
    queuePublisher.publish(created.task());
    GenerationTaskRecord task = mapper.findTask(created.task().id());
    return new SubmitResponse(detail(userId, ownedSession(userId, task.sessionId())), taskView(task), quota(userId), false);
  }

  public TaskView getTask(String userId, String taskId) {
    return taskView(ownedTask(userId, taskId));
  }

  /** Validates and normalizes reference IDs for the asynchronous preflight path. */
  public List<String> validateReferenceIds(String userId, List<String> values) {
    return normalizeImageIds(userId, values);
  }

  /** Public projection used by the v2 preflight/create workflow. */
  public TaskView viewTask(String userId, String taskId) { return getTask(userId, taskId); }

  /** Keeps v2 submission behavior aligned with the legacy task path. */
  void clearSubmittedDraft(String userId, String sessionId) {
    if (mapper.updateDraft(userId, sessionId, json(defaultDraft())) != 1) {
      throw new IllegalStateException("generation session draft was not cleared");
    }
  }

  public PlanView getPlan(String userId, String taskId) {
    ownedTask(userId, taskId);
    GenerationPlanRecord plan = mapper.findPlan(taskId);
    if (plan == null) throw new ApiException(HttpStatus.NOT_FOUND, "PLAN_NOT_READY", "生成计划尚未就绪");
    List<GenerationIterationRecord> iterations = mapper.listIterations(taskId);
    return new PlanView(taskId, apiValue(plan.status()), plan.requirementJson(), plan.structureJson(),
        plan.visualJson(), plan.promptJson(), iterations == null ? List.of() : iterations);
  }

  public TaskView cancel(String userId, String taskId) {
    GenerationTaskRecord task = ownedTask(userId, taskId);
    if (task.status() != GenerationTaskStatus.QUEUED && task.status() != GenerationTaskStatus.GENERATING)
      throw bad("TASK_NOT_CANCELLABLE", "当前任务不能取消");
    if (task.settlementVersionValue() == 2 && v2 != null) {
      transactions.executeWithoutResult(status -> cancelV2(userId, taskId));
      return getTask(userId, taskId);
    }
    transactions.executeWithoutResult(status -> {
      if (mapper.cancel(userId, taskId) != 1) throw bad("TASK_NOT_CANCELLABLE", "当前任务不能取消");
      if (!quota.settle(userId, taskId, task.totalCost(), "RELEASE", taskId + ":release"))
        throw bad("QUOTA_SETTLEMENT_FAILED", "额度结算失败");
      insertEvent(taskId, "task.cancelled", GenerationTaskStatus.CANCELLED, "TASK_CANCELLED");
    });
    return getTask(userId, taskId);
  }

  public SubmitResponse retry(String userId, String taskId) {
    GenerationTaskRecord task = ownedTask(userId, taskId);
    if (task.status() != GenerationTaskStatus.FAILED && task.status() != GenerationTaskStatus.CANCELLED
        && task.status() != GenerationTaskStatus.PARTIALLY_SUCCEEDED)
      throw bad("TASK_NOT_RETRYABLE", "当前任务不能重试");
    if (task.settlementVersionValue() == 2)
      throw bad("TASK_USE_CONTINUE_OR_REGENERATE", "多图片任务请使用继续生成缺失图片或全部重新生成");
    return submit(userId, new TaskRequest("retry-" + task.id() + "-" + UUID.randomUUID(), task.sessionId(),
        "AUTO", task.prompt(), imageIds(task), task.ratio().databaseValue(),
        task.resolution().databaseValue(), task.width(), task.height()));
  }

  public ObjectStorage.ObjectData result(String userId, String resultId, boolean thumbnail) {
    GenerationResultRecord result = mapper.findOwnedResult(userId, resultId);
    if (result == null) throw badNotFound();
    String key = thumbnail ? result.thumbnailObjectKey() : result.objectKey();
    if (thumbnail && (key == null || key.isBlank())) {
      throw new ApiException(HttpStatus.NOT_FOUND, "THUMBNAIL_NOT_FOUND", "预览图不存在");
    }
    if (!thumbnail && (key == null || key.isBlank())) key = result.imagePath();
    try {
      return storage.selected().get(key).orElseThrow(() -> thumbnail
          ? new ApiException(HttpStatus.NOT_FOUND, "THUMBNAIL_NOT_FOUND", "预览图不存在")
          : badNotFound());
    } catch (IllegalArgumentException invalidKey) {
      if (thumbnail) throw new ApiException(HttpStatus.NOT_FOUND, "THUMBNAIL_NOT_FOUND", "预览图不存在");
      throw badNotFound();
    }
  }

  public SseEmitter events(String userId, String taskId, long afterId) {
    ownedTask(userId, taskId);
    SseEmitter emitter = new SseEmitter(0L);
    sseExecutor.submit(() -> stream(userId, taskId, Math.max(0L, afterId), emitter));
    return emitter;
  }

  @PreDestroy
  void stopSseExecutor() { sseExecutor.shutdownNow(); }

  private void stream(String userId, String taskId, long cursor, SseEmitter emitter) {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(120);
    try {
      while (System.nanoTime() < deadline) {
        GenerationTaskRecord task = mapper.findTask(taskId);
        if (task == null || !userId.equals(task.userId())) { emitter.complete(); return; }
        List<GenerationTaskEventRecord> events = mapper.listEvents(taskId, cursor, 100);
        for (GenerationTaskEventRecord event : events) {
          emitter.send(SseEmitter.event().id(Long.toString(event.id())).name(event.type())
              .data(new EventView(event.id(), event.taskId(), event.type(), apiValue(event.status()), event.payload(), event.createdAt())));
          cursor = event.id();
          if (terminal(event.status())) { emitter.complete(); return; }
        }
        if (terminal(task.status())) { emitter.complete(); return; }
        emitter.send(SseEmitter.event().comment("keep-alive"));
        Thread.sleep(250L);
      }
      emitter.complete();
    } catch (Exception ignored) {
      emitter.complete();
    }
  }

  private CreatedTask createTask(String userId, Validated input) {
    String sessionId = input.sessionId();
    if (sessionId == null || sessionId.isBlank()) {
      sessionId = UUID.randomUUID().toString();
      mapper.insertSession(sessionId, userId, titleFor(input.prompt()), json(input.draft()));
    } else {
      ownedSession(userId, sessionId);
    }
    String taskId = UUID.randomUUID().toString();
    if (mapper.insertTask(taskId, sessionId, userId, input.prompt(), MODELS.getFirst(), input.ratio(), input.resolution(),
        input.width(), input.height(), json(input.imageIds()), input.unitCost(), input.totalCost(), input.idempotencyKey()) != 1)
      throw new IllegalStateException("generation task was not inserted");
    if (input.ruleId() != null) mapper.updatePricingSnapshot(taskId, input.ruleId(), input.ruleVersion());
    boolean reserved = input.ruleId() == null
        ? quota.reserve(userId, taskId, input.totalCost(), taskId + ":reserve", properties.quota().initialTotal())
        : quota.reserve(userId, taskId, input.totalCost(), taskId + ":reserve", properties.quota().initialTotal(), input.ruleId(), input.ruleVersion());
    if (!reserved)
      throw bad("QUOTA_INSUFFICIENT", "额度不足");
    insertEvent(taskId, "task.queued", GenerationTaskStatus.QUEUED, null);
    // A submitted prompt and its references belong to the task history, not
    // to the next composer draft. Clear the persisted draft in the same
    // transaction so a refresh cannot restore stale user input.
    mapper.updateDraft(userId, sessionId, json(defaultDraft()));
    GenerationTaskRecord task = mapper.findTask(taskId);
    return new CreatedTask(task);
  }

  private void insertEvent(String taskId, String type, GenerationTaskStatus status, String errorCode) {
    try {
      var payload = objectMapper.createObjectNode().put("taskId", taskId).put("type", type).put("status", status.name());
      if (errorCode != null) payload.put("errorCode", errorCode);
      mapper.insertEvent(taskId, type, status.name(), payload.toString());
    } catch (RuntimeException e) {
      throw e;
    }
  }

  private Validated validate(String userId, TaskRequest request) {
    if (request == null) throw bad("VALIDATION_ERROR", "请求参数无效");
    if (request.sessionId() == null || request.sessionId().isBlank())
      throw bad("GENERATION_SESSION_REQUIRED", "请先创建会话再提交生成任务");
    ownedSession(userId, request.sessionId());
    String key = request.idempotencyKey() == null ? "" : request.idempotencyKey().trim();
    if (!key.matches("[A-Za-z0-9:_-]{8,128}")) throw bad("VALIDATION_ERROR", "幂等键格式无效");
    String prompt = request.prompt() == null ? "" : request.prompt().trim();
    if (prompt.isEmpty() || prompt.length() > 4000) throw bad("VALIDATION_ERROR", "提示词长度应为 1-4000 个字符");
    GenerationInputMode mode = requireAutoMode(request.mode());
    List<String> imageIds = normalizeImageIds(userId, request.imageIds());
    OutputParameters output = validateOutput(request.ratio(), request.resolution(), request.width(), request.height());
    PricingRuleRecord rule = billing == null ? null : billing.findActivePricingRule("IMAGE_GENERATION", output.resolution());
    if (billing != null && rule == null) throw bad("PRICING_RULE_NOT_FOUND", "当前生成参数没有可用计费规则");
    int cost = rule == null ? ("4K".equals(output.resolution()) ? 2 : 1) : rule.unitCreditCost();
    Draft draft = new Draft(mode.name(), prompt, imageIds, output.ratio(), output.resolution(), output.width(), output.height());
    return new Validated(key, request.sessionId(), mode, prompt, imageIds, output.ratio(), output.resolution(),
        output.width(), output.height(), cost, cost, draft, rule == null ? null : rule.id(), rule == null ? null : rule.version());
  }

  private Draft normalizeDraft(Draft draft) {
    if (draft == null) draft = defaultDraft();
    String prompt = draft.prompt() == null ? "" : draft.prompt().trim();
    if (prompt.length() > 4000) throw bad("VALIDATION_ERROR", "提示词长度应为 0-4000 个字符");
    requireAutoMode(draft.mode());
    List<String> ids = draft.imageIds() == null ? List.of() : draft.imageIds().stream()
        .filter(java.util.Objects::nonNull).map(String::trim).filter(value -> !value.isEmpty()).distinct().toList();
    if (ids.size() > 2) throw bad("GENERATION_IMAGES_INVALID", "最多添加两张图片");
    OutputParameters output = validateOutput(draft.ratio(), draft.resolution(), draft.width(), draft.height());
    return new Draft("AUTO", prompt, ids, output.ratio(), output.resolution(), output.width(), output.height(),
        draft.imageCountMode() == null ? "AUTO" : draft.imageCountMode(), draft.imageCount());
  }

  private static Draft defaultDraft() {
    return new Draft("AUTO", "", List.of(), "1:1", "2K", 2048, 2048);
  }

  private static GenerationInputMode requireAutoMode(String value) {
    String normalized = value == null || value.isBlank() ? "AUTO" : value.trim().toUpperCase(Locale.ROOT);
    if (!"AUTO".equals(normalized)) throw bad("GENERATION_MODE_INVALID", "图片生成意图由 AI 自动识别，mode 仅支持 AUTO");
    return GenerationInputMode.AUTO;
  }

  private boolean sameInput(GenerationTaskRecord existing, Validated input) {
    return existing.sessionId().equals(input.sessionId())
        && existing.prompt().equals(input.prompt()) && existing.mode() == input.mode()
        && imageIds(existing).equals(input.imageIds())
        && existing.ratio().databaseValue().equals(input.ratio())
        && existing.resolution().databaseValue().equals(input.resolution())
        && java.util.Objects.equals(existing.width(), input.width())
        && java.util.Objects.equals(existing.height(), input.height());
  }

  private GenerationSessionRecord ownedSession(String userId, String id) {
    if (id == null || id.isBlank()) throw bad("NOT_FOUND", "会话不存在");
    GenerationSessionRecord session = mapper.findSession(userId, id);
    if (session == null) throw bad("NOT_FOUND", "会话不存在");
    return session;
  }

  private GenerationTaskRecord ownedTask(String userId, String id) {
    GenerationTaskRecord task = mapper.findTask(id);
    if (task == null || !userId.equals(task.userId())) throw badNotFound();
    return task;
  }

  private SessionSummary summary(GenerationSessionRecord session) {
    String thumbnail = null;
    List<GenerationTaskRecord> tasks = mapper.listTasks(session.id());
    if (!tasks.isEmpty()) {
      List<GenerationResultRecord> results = mapper.listResults(tasks.get(0).id());
      if (!results.isEmpty()) thumbnail = thumbnailUrl(results.get(0));
    }
    return new SessionSummary(session.id(), session.title(), thumbnail, session.createdAt(), session.updatedAt());
  }

  private SessionDetail detail(String userId, GenerationSessionRecord session) {
    return new SessionDetail(session.id(), session.title(), draftView(session.draft()), session.createdAt(), session.updatedAt(),
        mapper.listTasks(session.id()).stream().map(this::taskView).toList());
  }

  private Draft draftView(JsonNode value) {
    if (value == null || value.isNull()) return defaultDraft();
    try {
      Draft draft = objectMapper.treeToValue(value, Draft.class);
      return draft.imageCountMode() == null
          ? new Draft(draft.mode(), draft.prompt(), draft.imageIds(), draft.ratio(), draft.resolution(), draft.width(), draft.height(), "AUTO", draft.imageCount())
          : draft;
    } catch (com.fasterxml.jackson.core.JsonProcessingException error) {
      throw new IllegalStateException("generation session draft is invalid", error);
    }
  }

  private TaskView taskView(GenerationTaskRecord task) {
    List<ResultView> results = mapper.listResults(task.id()).stream().map(result -> new ResultView(result.id(), result.index(),
        "/dream_web/generation/results/" + result.id() + "/content", thumbnailUrl(result),
        result.width(), result.height(), result.mimeType(), result.byteSize(), result.isAiGenerated(),
        apiValue(result.moderationStatus()))).toList();
    List<String> refs = imageIds(task);
    GenerationPlanRecord plan = mapper.findPlan(task.id());
    List<GenerationIterationRecord> iterations = mapper.listIterations(task.id());
    if (iterations == null) iterations = List.of();
    GenerationIterationRecord latest = iterations.isEmpty() ? null : iterations.getLast();
    Double score = latest == null || latest.evaluationJson() == null ? null : latest.evaluationJson().path("score").isNumber()
        ? latest.evaluationJson().path("score").asDouble() : null;
    String stage = currentStage(task, plan, latest);
    java.util.Map<Integer, ResultView> resultsByIndex = results.stream()
        .collect(java.util.stream.Collectors.toMap(ResultView::index, value -> value));
    List<GenerationResultSlotRecord> slotRecords = task.settlementVersionValue() == 2
        ? mapper.listResultSlots(task.id()) : List.of();
    List<SlotView> slots = slotRecords == null ? List.of() : slotRecords.stream()
        .map(slot -> new SlotView(slot.slotIndex(), slot.label(), slot.role(), apiValue(slot.status()),
            resultsByIndex.get(slot.slotIndex()), slot.errorCode(), slot.errorMessage())).toList();
    int successful = task.settlementVersionValue() == 2
        ? (int) slots.stream().filter(slot -> "succeeded".equals(slot.status())).count() : results.size();
    int consumed = task.settlementVersionValue() == 2 ? task.consumedCost() : successful * task.unitCost();
    return new TaskView(task.id(), task.sessionId(), apiValue(task.status()), task.mode().name(), task.prompt(),
        refs, task.model(), task.ratio().databaseValue(), task.resolution().databaseValue(), task.width(), task.height(),
        task.imageCount(), task.unitCost(), task.totalCost(), task.errorCode(), task.errorMessage(),
        task.startedAt(), task.completedAt(), task.createdAt(), task.updatedAt(), plan == null ? null : apiValue(plan.status()),
        stage, latest == null ? 0 : latest.iteration(), score, results, consumed, successful,
        Math.max(0, task.imageCount() - successful), plan == null ? null : apiValue(plan.collectionMode()), slots);
  }

  private static String thumbnailUrl(GenerationResultRecord result) {
    int version = java.util.Objects.hash(result.thumbnailObjectKey(), result.thumbnailByteSize());
    return "/dream_web/generation/results/" + result.id() + "/thumbnail?v="
        + Integer.toUnsignedString(version, 36);
  }

  public SubmitResponse continueMissing(String userId, String taskId, String idempotencyKey) {
    if (v2 == null) throw bad("TASK_CONTINUE_UNAVAILABLE", "当前服务不支持继续生成");
    GenerationTaskRecord task = ownedTask(userId, taskId);
    if (task.settlementVersionValue() != 2 || (task.status() != GenerationTaskStatus.PARTIALLY_SUCCEEDED
        && task.status() != GenerationTaskStatus.FAILED && task.status() != GenerationTaskStatus.CANCELLED))
      throw bad("TASK_NOT_CONTINUABLE", "当前任务没有可继续生成的缺失图片");
    String key = idempotencyKey == null ? "continue-" + task.id() + "-" + UUID.randomUUID() : idempotencyKey.trim();
    if (!key.matches("[A-Za-z0-9:_-]{8,128}")) throw bad("VALIDATION_ERROR", "幂等键格式无效");
    GenerationExecutionRecord replay = v2.findExecutionByIdempotency(task.id(), key);
    if (replay != null) return new SubmitResponse(getSession(userId, task.sessionId()), getTask(userId, task.id()), quota(userId), true);
    String executionId = UUID.randomUUID().toString();
    String queuedExecutionId;
    try {
      queuedExecutionId = transactions.execute(status -> {
        // Terminal v2 tasks are the only continuable state. Lock the row before
        // checking the active execution so two continuation requests cannot both
        // reserve the same missing slots or race a state transition.
        GenerationTaskRecord currentTask = v2.lockTask(userId, task.id());
        GenerationExecutionRecord duplicate = v2.findExecutionByIdempotency(task.id(), key);
        if (duplicate != null) return null;
        if (currentTask == null || currentTask.settlementVersionValue() != 2
            || (currentTask.status() != GenerationTaskStatus.PARTIALLY_SUCCEEDED
                && currentTask.status() != GenerationTaskStatus.FAILED
                && currentTask.status() != GenerationTaskStatus.CANCELLED))
          throw bad("TASK_NOT_CONTINUABLE", "当前任务没有可继续生成的缺失图片");
        if (v2.findActiveExecution(task.id()) != null) throw bad("TASK_ALREADY_RUNNING", "任务正在生成中");
        List<Integer> missing = v2.listResultSlots(task.id()).stream()
            .filter(s -> s.status() != null && !"SUCCEEDED".equals(s.status().name()))
            .map(GenerationResultSlotRecord::slotIndex).toList();
        if (missing.isEmpty()) return null;
        int amount = missing.size() * currentTask.unitCost();
        if (v2.insertExecution(executionId, task.id(), "CONTINUATION", key, json(missing), amount,
            currentTask.pricingRuleId(), currentTask.pricingRuleVersion()) != 1)
          throw new IllegalStateException("continuation execution was not inserted");
        if (v2.resetMissingSlots(task.id(), executionId) != missing.size())
          throw bad("TASK_NOT_CONTINUABLE", "缺失图片状态已变化");
        if (!quota.reserve(userId, task.id(), amount, "reserve:" + executionId, properties.quota().initialTotal(),
            currentTask.pricingRuleId(), currentTask.pricingRuleVersion(), executionId)) throw bad("QUOTA_INSUFFICIENT", "额度不足");
        if (v2.queueTaskForContinuation(task.id()) != 1) throw bad("TASK_NOT_CONTINUABLE", "任务状态已变化");
        insertEvent(task.id(), "task.execution.queued", GenerationTaskStatus.QUEUED, null);
        return executionId;
      });
    } catch (DuplicateKeyException duplicate) {
      GenerationExecutionRecord raceReplay = v2.findExecutionByIdempotency(task.id(), key);
      if (raceReplay != null) return new SubmitResponse(getSession(userId, task.sessionId()), getTask(userId, task.id()), quota(userId), true);
      throw bad("TASK_ALREADY_RUNNING", "任务正在生成中");
    }
    if (queuedExecutionId == null) return new SubmitResponse(getSession(userId, task.sessionId()), getTask(userId, task.id()), quota(userId), true);
    queuePublisher.publishExecution(task.id(), queuedExecutionId, "execution:" + queuedExecutionId);
    return new SubmitResponse(getSession(userId, task.sessionId()), getTask(userId, task.id()), quota(userId), false);
  }

  private void cancelV2(String userId, String taskId) {
    // All v2 mutations lock task -> execution -> slot -> quota. Re-read the
    // task under the lock because the request-level snapshot may be stale.
    GenerationTaskRecord task = v2.lockTask(userId, taskId);
    if (task == null || task.settlementVersionValue() != 2
        || (task.status() != GenerationTaskStatus.QUEUED && task.status() != GenerationTaskStatus.GENERATING))
      throw bad("TASK_NOT_CANCELLABLE", "当前任务不能取消");
    GenerationExecutionRecord execution = v2.findActiveExecution(task.id());
    if (execution != null) {
      execution = v2.lockExecution(execution.id());
      if (execution == null || (execution.status() != com.dreamspace.common.persistence.database.DatabaseEnums.GenerationExecutionStatus.QUEUED
          && execution.status() != com.dreamspace.common.persistence.database.DatabaseEnums.GenerationExecutionStatus.GENERATING)) {
        execution = null;
      }
    }
    if (execution != null) {
      // Mark slots cancelled while holding task and execution locks before
      // taking the account lock. Late provider responses then fail the same
      // execution/slot ownership check and cannot consume quota.
      v2.cancelMissingSlots(task.id());
      int release = Math.max(0, execution.reservedAmount() - execution.consumedAmount() - execution.releasedAmount());
      if (release > 0 && !quota.settle(userId, task.id(), release, "RELEASE", "release:" + execution.id(), execution.id(), null)) throw bad("QUOTA_SETTLEMENT_FAILED", "额度结算失败");
      if (v2.finishExecution(execution.id(), release, "CANCELLED", "TASK_CANCELLED") != 1) throw bad("TASK_NOT_CANCELLABLE", "当前任务不能取消");
      v2.insertTaskEvent(task.id(), "task.execution.released", "GENERATING",
          json(java.util.Map.of("executionId", execution.id(), "releasedCost", release)));
    }
    if (execution == null) v2.cancelMissingSlots(task.id());
    if (v2.cancelTaskV2(userId, task.id()) != 1) throw bad("TASK_NOT_CANCELLABLE", "当前任务不能取消");
    insertEvent(task.id(), "task.cancelled", GenerationTaskStatus.CANCELLED, "TASK_CANCELLED");
  }

  private static String currentStage(GenerationTaskRecord task, GenerationPlanRecord plan, GenerationIterationRecord latest) {
    if (terminal(task.status())) return task.status().name().toLowerCase(Locale.ROOT);
    if (latest != null) return latest.status().name().toLowerCase(Locale.ROOT);
    if (plan != null && plan.promptJson() != null) return "prompt_constructed";
    if (plan != null && plan.visualJson() != null) return "visual_constraints";
    if (plan != null && plan.structureJson() != null) return "structure_planning";
    if (plan != null && plan.requirementJson() != null) return "requirement_understanding";
    return task.status() == GenerationTaskStatus.QUEUED ? "queued" : "planning";
  }

  private List<String> normalizeImageIds(String userId, List<String> values) {
    if (values == null || values.isEmpty()) return List.of();
    if (values.size() > 2) throw bad("GENERATION_IMAGES_INVALID", "最多添加两张图片");
    List<String> normalized = values.stream().map(value -> normalizeImageId(userId, value)).distinct().toList();
    if (normalized.size() != values.size()) throw bad("GENERATION_IMAGES_INVALID", "不能重复添加同一张图片");
    return normalized;
  }

  private String normalizeImageId(String userId, String value) {
    if (value == null || value.isBlank()) throw bad("GENERATION_IMAGES_INVALID", "图片 ID 不能为空");
    String normalized = value.trim();
    if (normalized.startsWith("/dream_web/uploads/references/")) {
      String[] segments = normalized.split("/");
      normalized = segments.length > 1 ? segments[segments.length - 2] : "";
    }
    if (!normalized.matches("[A-Za-z0-9-]{16,64}") || uploads == null || uploads.findOwned(userId, normalized) == null)
      throw bad("GENERATION_REFERENCE_INVALID", "图片地址无效");
    return normalized;
  }

  private static OutputParameters validateOutput(String ratioValue, String resolutionValue, Integer width, Integer height) {
    String ratio = ratioValue == null || ratioValue.isBlank() ? "1:1" : ratioValue.trim().toLowerCase(Locale.ROOT);
    String resolution = resolutionValue == null || resolutionValue.isBlank() ? "2K" : resolutionValue.trim().toUpperCase(Locale.ROOT);
    if (!RATIOS.contains(ratio)) throw bad("GENERATION_RATIO_INVALID", "图片比例无效");
    if (!RESOLUTIONS.contains(resolution)) throw bad("GENERATION_RESOLUTION_INVALID", "图片分辨率无效");
    if ("smart".equals(ratio)) {
      if (width != null || height != null) throw bad("GENERATION_DIMENSION_INVALID", "智能比例不能指定宽高");
      return new OutputParameters(ratio, resolution, null, null);
    }
    if (width == null || height == null || width < 512 || height < 512 || width % 64 != 0 || height % 64 != 0)
      throw bad("GENERATION_DIMENSION_INVALID", "宽高必须不小于 512 且为 64 的整数倍");
    int maxEdge = "4K".equals(resolution) ? 4096 : 2048;
    long maxPixels = (long) maxEdge * maxEdge;
    if (width > maxEdge || height > maxEdge || (long) width * height > maxPixels)
      throw bad("GENERATION_DIMENSION_INVALID", "宽高超过当前分辨率限制");
    if (!"custom".equals(ratio)) {
      String[] parts = ratio.split(":");
      int ratioWidth = Integer.parseInt(parts[0]);
      int ratioHeight = Integer.parseInt(parts[1]);
      long difference = Math.abs((long) width * ratioHeight - (long) height * ratioWidth);
      if (difference > 64L * Math.max(ratioWidth, ratioHeight))
        throw bad("GENERATION_RATIO_INVALID", "宽高与所选比例不一致");
    }
    return new OutputParameters(ratio, resolution, width, height);
  }

  private List<String> imageIds(GenerationTaskRecord task) {
    return task.imageIds() == null || !task.imageIds().isArray() ? List.of()
        : objectMapper.convertValue(task.imageIds(), objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
  }

  private static String apiValue(Enum<?> value) {
    return value == null ? null : value.name().toLowerCase(java.util.Locale.ROOT);
  }

  private QuotaView quotaView(QuotaAccountRecord account) {
    int used = account.used();
    int percent = account.total() == 0 ? 0 : Math.max(0, Math.min(100, (int) Math.round(account.available() * 100.0 / account.total())));
    return new QuotaView(account.total(), account.available(), account.reserved(), used, percent);
  }

  private String json(Object value) {
    try { return objectMapper.writeValueAsString(value); } catch (IOException e) { throw new IllegalStateException(e); }
  }

  static String titleFor(String prompt) {
    String title = prompt == null ? "新的创作" : prompt.trim().replaceAll("\\s+", " ");
    if (title.isEmpty()) title = "新的创作";
    return title.codePointCount(0, title.length()) > 20
        ? title.substring(0, title.offsetByCodePoints(0, 20))
        : title;
  }

  private static boolean terminal(GenerationTaskStatus status) {
    return status == GenerationTaskStatus.SUCCEEDED || status == GenerationTaskStatus.PARTIALLY_SUCCEEDED
        || status == GenerationTaskStatus.FAILED || status == GenerationTaskStatus.CANCELLED;
  }

  private static ApiException bad(String code, String message) { return new ApiException(HttpStatus.BAD_REQUEST, code, message); }
  private static ApiException badNotFound() { return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "资源不存在"); }

  private record OutputParameters(String ratio, String resolution, Integer width, Integer height) {}
  private record Validated(String idempotencyKey, String sessionId, GenerationInputMode mode, String prompt,
      List<String> imageIds, String ratio, String resolution, Integer width, Integer height,
      int unitCost, int totalCost, Draft draft, String ruleId, Integer ruleVersion) {}
  private record CreatedTask(GenerationTaskRecord task) {}
}
