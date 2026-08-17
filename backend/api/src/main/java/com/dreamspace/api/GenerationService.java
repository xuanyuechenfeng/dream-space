package com.dreamspace.api;

import com.dreamspace.common.persistence.config.DreamSpaceProperties;
import com.dreamspace.common.persistence.database.DatabaseEnums.GenerationTaskStatus;
import com.dreamspace.common.persistence.generation.GenerationMapper;
import com.dreamspace.common.persistence.generation.GenerationResultRecord;
import com.dreamspace.common.persistence.generation.GenerationSessionRecord;
import com.dreamspace.common.persistence.generation.GenerationTaskEventRecord;
import com.dreamspace.common.persistence.generation.GenerationTaskRecord;
import com.dreamspace.common.persistence.quota.QuotaAccountRecord;
import com.dreamspace.common.persistence.quota.QuotaTransactionService;
import com.dreamspace.common.persistence.storage.ObjectStorage;
import com.dreamspace.common.persistence.storage.ObjectStorageFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
  private static final List<String> RATIOS = List.of("smart", "21:9", "16:9", "3:2", "4:3", "1:1", "3:4", "2:3", "9:16");
  private static final List<String> RESOLUTIONS = List.of("2K", "4K");
  private static final List<String> REFERENCE_MIMES = List.of("image/jpeg", "image/png", "image/webp");
  private final GenerationMapper mapper;
  private final QuotaTransactionService quota;
  private final GenerationQueuePublisher queuePublisher;
  private final ObjectStorageFactory storage;
  private final DreamSpaceProperties properties;
  private final ObjectMapper objectMapper;
  private final TransactionTemplate transactions;
  private final ExecutorService sseExecutor = Executors.newCachedThreadPool(r -> {
    Thread thread = new Thread(r, "dream-space-generation-sse");
    thread.setDaemon(true);
    return thread;
  });

  public GenerationService(GenerationMapper mapper, QuotaTransactionService quota, GenerationQueuePublisher queuePublisher,
      ObjectStorageFactory storage, DreamSpaceProperties properties, ObjectMapper objectMapper,
      PlatformTransactionManager transactionManager) {
    this.mapper = mapper;
    this.quota = quota;
    this.queuePublisher = queuePublisher;
    this.storage = storage;
    this.properties = properties;
    this.objectMapper = objectMapper;
    this.transactions = new TransactionTemplate(transactionManager);
  }

  public record Draft(String prompt, String model, String ratio, String resolution, int imageCount,
      List<String> referenceImageUrls) {}
  public record TaskRequest(String idempotencyKey, String sessionId, String prompt, String model,
      String ratio, String resolution, int imageCount, List<String> referenceImageUrls) {}
  public record Options(List<String> models, List<String> ratios, List<String> resolutions,
      CountRange imageCount, ReferenceLimits referenceImages, int costPerImage, String externalServicesMode) {}
  public record CountRange(int min, int max) {}
  public record ReferenceLimits(int max, int maxBytes, List<String> mimeTypes) {}
  public record QuotaView(int total, int available, int reserved, int used, int remainingPercent) {}
  public record SessionSummary(String id, String title, String thumbnailUrl, Instant createdAt, Instant updatedAt) {}
  public record SessionDetail(String id, String title, JsonNode draft, Instant createdAt, Instant updatedAt,
      List<TaskView> tasks) {}
  public record ResultView(String id, int index, String contentUrl, String thumbnailUrl, int width, int height,
      String mimeType, int byteSize, boolean isAiGenerated, String moderationStatus) {}
  public record TaskView(String id, String sessionId, String status, String prompt, String model, String ratio,
      String resolution, int imageCount, List<String> referenceImageUrls, int unitCost, int totalCost,
      String errorCode, String errorMessage, Instant startedAt, Instant completedAt, Instant createdAt,
      Instant updatedAt, List<ResultView> results) {}
  public record SubmitResponse(SessionDetail session, TaskView task, QuotaView quota, boolean replayed) {}
  public record EventView(long id, String taskId, String type, String status, JsonNode payload, Instant createdAt) {}

  public Options options() {
    return new Options(MODELS, RATIOS, RESOLUTIONS, new CountRange(1, 8),
        new ReferenceLimits(4, 10 * 1024 * 1024, REFERENCE_MIMES), 1, properties.externalServicesMode());
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
    Draft normalized = normalizeDraft(draft == null ? new Draft("", MODELS.get(0), "1:1", "2K", 1, List.of()) : draft);
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
    if (mapper.countActiveTasks(sessionId) > 0) throw bad("SESSION_ACTIVE", "生成任务进行中，暂不能删除会话");
    transactions.executeWithoutResult(status -> {
      if (mapper.deleteSession(userId, sessionId) != 1) throw bad("NOT_FOUND", "会话不存在");
    });
  }

  public SubmitResponse submit(String userId, TaskRequest request) {
    Validated input = validate(request);
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

  public TaskView cancel(String userId, String taskId) {
    GenerationTaskRecord task = ownedTask(userId, taskId);
    if (task.status() != GenerationTaskStatus.QUEUED && task.status() != GenerationTaskStatus.GENERATING)
      throw bad("TASK_NOT_CANCELLABLE", "当前任务不能取消");
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
    List<String> refs = task.referenceImageUrls() == null || !task.referenceImageUrls().isArray()
        ? List.of() : objectMapper.convertValue(task.referenceImageUrls(), objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
    return submit(userId, new TaskRequest("retry-" + task.id() + "-" + UUID.randomUUID(), task.sessionId(), task.prompt(),
        task.model(), task.ratio().databaseValue(), task.resolution().databaseValue(), task.imageCount(), refs));
  }

  public ObjectStorage.ObjectData result(String userId, String resultId, boolean thumbnail) {
    GenerationResultRecord result = mapper.findOwnedResult(userId, resultId);
    if (result == null) throw badNotFound();
    String key = thumbnail ? result.thumbnailObjectKey() : result.objectKey();
    if (key == null || key.isBlank()) key = result.imagePath();
    return storage.selected().get(key).orElseThrow(GenerationService::badNotFound);
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
    if (mapper.insertTask(taskId, sessionId, userId, input.prompt(), input.model(), input.ratio(), input.resolution(),
        input.imageCount(), json(input.referenceImageUrls()), input.unitCost(), input.totalCost(), input.idempotencyKey()) != 1)
      throw new IllegalStateException("generation task was not inserted");
    if (!quota.reserve(userId, taskId, input.totalCost(), taskId + ":reserve", properties.quota().initialTotal()))
      throw bad("QUOTA_INSUFFICIENT", "额度不足");
    insertEvent(taskId, "task.queued", GenerationTaskStatus.QUEUED, null);
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

  private Validated validate(TaskRequest request) {
    if (request == null) throw bad("VALIDATION_ERROR", "请求参数无效");
    String key = request.idempotencyKey() == null ? "" : request.idempotencyKey().trim();
    if (!key.matches("[A-Za-z0-9:_-]{8,128}")) throw bad("VALIDATION_ERROR", "幂等键格式无效");
    String prompt = request.prompt() == null ? "" : request.prompt().trim();
    if (prompt.isEmpty() || prompt.length() > 4000) throw bad("VALIDATION_ERROR", "提示词长度应为 1-4000 个字符");
    String model = request.model() == null ? "" : request.model().trim();
    if (!MODELS.contains(model)) throw bad("GENERATION_MODEL_INVALID", "模型不受支持");
    String ratio = request.ratio() == null ? "" : request.ratio().trim();
    if (!RATIOS.contains(ratio)) throw bad("GENERATION_RATIO_INVALID", "图片比例不受支持");
    String resolution = request.resolution() == null ? "" : request.resolution().trim().toUpperCase(Locale.ROOT);
    if (!RESOLUTIONS.contains(resolution)) throw bad("GENERATION_RESOLUTION_INVALID", "图片分辨率不受支持");
    if (request.imageCount() < 1 || request.imageCount() > 8) throw bad("GENERATION_IMAGE_COUNT_INVALID", "图片数量应为 1-8 张");
    List<String> references = request.referenceImageUrls() == null ? List.of() : request.referenceImageUrls().stream()
        .filter(value -> value != null && !value.isBlank()).map(String::trim).toList();
    if (references.size() > 4) throw bad("GENERATION_REFERENCE_COUNT_INVALID", "参考图最多 4 张");
    if (references.stream().anyMatch(value -> !value.startsWith("/uploads/references/")))
      throw bad("GENERATION_REFERENCE_INVALID", "参考图地址无效");
    int unit = "4K".equals(resolution) ? 2 : 1;
    return new Validated(key, request.sessionId(), prompt, model, ratio, resolution, request.imageCount(), references,
        unit, unit * request.imageCount(), new Draft(prompt, model, ratio, resolution, request.imageCount(), references));
  }

  private Draft normalizeDraft(Draft draft) {
    String prompt = draft == null || draft.prompt() == null ? "" : draft.prompt().trim();
    if (prompt.length() > 4000) throw bad("VALIDATION_ERROR", "提示词长度应为 0-4000 个字符");
    String model = draft == null || draft.model() == null || draft.model().isBlank() ? MODELS.get(0) : draft.model();
    String ratio = draft == null || draft.ratio() == null || draft.ratio().isBlank() ? "1:1" : draft.ratio();
    String resolution = draft == null || draft.resolution() == null || draft.resolution().isBlank() ? "2K" : draft.resolution();
    int imageCount = draft == null || draft.imageCount() < 1 ? 1 : draft.imageCount();
    TaskRequest request = new TaskRequest("draft-key-12345678", null, prompt.isEmpty() ? "draft" : prompt,
        model, ratio, resolution, imageCount,
        draft == null ? List.of() : draft.referenceImageUrls());
    Validated validated = validate(request);
    return new Draft(prompt, validated.model(), validated.ratio(), validated.resolution(), validated.imageCount(), validated.referenceImageUrls());
  }

  private boolean sameInput(GenerationTaskRecord existing, Validated input) {
    return existing.sessionId().equals(input.sessionId() == null ? existing.sessionId() : input.sessionId())
        && existing.prompt().equals(input.prompt()) && existing.model().equals(input.model())
        && existing.ratio().databaseValue().equals(input.ratio()) && existing.resolution().databaseValue().equals(input.resolution())
        && existing.imageCount() == input.imageCount() && existing.unitCost() == input.unitCost()
        && existing.totalCost() == input.totalCost() && json(input.referenceImageUrls()).equals(existing.referenceImageUrls().toString());
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
      if (!results.isEmpty()) thumbnail = "/generation/results/" + results.get(0).id() + "/thumbnail";
    }
    return new SessionSummary(session.id(), session.title(), thumbnail, session.createdAt(), session.updatedAt());
  }

  private SessionDetail detail(String userId, GenerationSessionRecord session) {
    return new SessionDetail(session.id(), session.title(), session.draft(), session.createdAt(), session.updatedAt(),
        mapper.listTasks(session.id()).stream().map(this::taskView).toList());
  }

  private TaskView taskView(GenerationTaskRecord task) {
    List<ResultView> results = mapper.listResults(task.id()).stream().map(result -> new ResultView(result.id(), result.index(),
        "/generation/results/" + result.id() + "/content", "/generation/results/" + result.id() + "/thumbnail",
        result.width(), result.height(), result.mimeType(), result.byteSize(), result.isAiGenerated(),
        apiValue(result.moderationStatus()))).toList();
    List<String> refs = task.referenceImageUrls() == null || !task.referenceImageUrls().isArray() ? List.of()
        : objectMapper.convertValue(task.referenceImageUrls(), objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
    return new TaskView(task.id(), task.sessionId(), apiValue(task.status()), task.prompt(), task.model(), task.ratio().databaseValue(),
        task.resolution().databaseValue(), task.imageCount(), refs, task.unitCost(), task.totalCost(), task.errorCode(), task.errorMessage(),
        task.startedAt(), task.completedAt(), task.createdAt(), task.updatedAt(), results);
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

  private String titleFor(String prompt) {
    String title = prompt == null ? "新的创作" : prompt.trim();
    if (title.isEmpty()) title = "新的创作";
    return title.length() > 24 ? title.substring(0, 24) : title;
  }

  private static boolean terminal(GenerationTaskStatus status) {
    return status == GenerationTaskStatus.SUCCEEDED || status == GenerationTaskStatus.PARTIALLY_SUCCEEDED
        || status == GenerationTaskStatus.FAILED || status == GenerationTaskStatus.CANCELLED;
  }

  private static ApiException bad(String code, String message) { return new ApiException(HttpStatus.BAD_REQUEST, code, message); }
  private static ApiException badNotFound() { return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "资源不存在"); }

  private record Validated(String idempotencyKey, String sessionId, String prompt, String model, String ratio,
      String resolution, int imageCount, List<String> referenceImageUrls, int unitCost, int totalCost, Draft draft) {}
  private record CreatedTask(GenerationTaskRecord task) {}
}
