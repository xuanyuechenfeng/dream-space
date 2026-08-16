package com.dreamspace.api;

import com.dreamspace.persistence.admin.AdminApplicationMapper;
import com.dreamspace.persistence.admin.AdminDeadLetterRecord;
import com.dreamspace.persistence.admin.AdminReconciliationFindingRecord;
import com.dreamspace.persistence.admin.AdminTaskRecord;
import com.dreamspace.persistence.generation.GenerationResultRecord;
import com.dreamspace.persistence.reconciliation.QuotaReconciliationRunRecord;
import com.dreamspace.persistence.storage.ObjectStorage;
import com.dreamspace.persistence.storage.ObjectStorageFactory;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class AdminTasksService {
  private static final Set<String> STATUSES = Set.of(
      "queued", "generating", "succeeded", "partially_succeeded", "failed", "cancelled");
  private final AdminApplicationMapper mapper;
  private final ObjectStorageFactory storage;

  public AdminTasksService(AdminApplicationMapper mapper, ObjectStorageFactory storage) {
    this.mapper = mapper;
    this.storage = storage;
  }

  public record Page(List<TaskSummary> items, long total, int page, int pageSize, int pageCount) {}
  public record TaskSummary(String id, String sessionId, String sessionTitle,
      String userPhoneMasked, String status, String prompt, String model, String ratio,
      String resolution, int imageCount, int resultCount, int totalCost, int attempts,
      String inputModerationStatus, String outputModerationStatus, Instant createdAt,
      Instant startedAt, Instant completedAt) {}
  public record TaskDetail(String id, String sessionId, String sessionTitle,
      String userPhoneMasked, String status, String prompt, String model, String ratio,
      String resolution, int imageCount, int resultCount, int totalCost, int attempts,
      String inputModerationStatus, String outputModerationStatus, Instant createdAt,
      Instant startedAt, Instant completedAt, List<String> referenceImageUrls,
      String errorCode, String errorMessage, DeadLetter deadLetter, List<Result> results) {}
  public record DeadLetter(String errorCode, String errorMessage, int attempts, Instant createdAt,
      Instant resolvedAt) {}
  public record Result(String id, int index, String imageUrl, String thumbnailUrl, int width,
      int height, String mimeType, int byteSize, boolean isAiGenerated, String moderationStatus) {}
  public record ReconciliationResponse(List<ReconciliationRun> items) {}
  public record ReconciliationRun(String id, String status, Instant startedAt, Instant completedAt,
      int scannedUsers, int scannedTasks, int mismatchCount, int repairedCount,
      String errorMessage, List<ReconciliationFinding> findings) {}
  public record ReconciliationFinding(String id, String userId, String taskId, String kind,
      String status, Integer expectedAmount, Integer actualAmount, Instant repairedAt,
      Instant createdAt) {}

  public Page list(String status, String model, String query, String createdFrom, String createdTo,
      int page, int pageSize) {
    String normalizedStatus = optional(status);
    if (normalizedStatus != null) {
      normalizedStatus = normalizedStatus.toLowerCase(Locale.ROOT);
      if (!STATUSES.contains(normalizedStatus)) throw bad("TASK_STATUS_INVALID", "任务状态无效");
    }
    String normalizedModel = bounded(model, 64, "模型名称过长");
    String normalizedQuery = bounded(query, 100, "搜索关键词过长");
    int normalizedPage = range(page, 1, 1_000_000, "页码无效");
    int normalizedSize = range(pageSize, 1, 100, "每页数量无效");
    Instant from = startOfDay(createdFrom);
    Instant to = endOfDay(createdTo);
    if (from != null && to != null && from.isAfter(to)) throw bad("DATE_RANGE_INVALID", "日期范围无效");
    String databaseStatus = normalizedStatus == null ? null : normalizedStatus.toUpperCase(Locale.ROOT);
    long total = mapper.countTasks(databaseStatus, normalizedModel, normalizedQuery, from, to);
    List<TaskSummary> items = mapper.listTasks(databaseStatus, normalizedModel, normalizedQuery,
        from, to, normalizedSize, (normalizedPage - 1) * normalizedSize).stream()
        .map(this::summary).toList();
    return new Page(items, total, normalizedPage, normalizedSize,
        (int) Math.ceil(total / (double) normalizedSize));
  }

  public TaskDetail get(String taskId) {
    AdminTaskRecord task = mapper.findTask(requiredId(taskId));
    if (task == null) throw notFound("任务不存在");
    AdminDeadLetterRecord dead = mapper.findDeadLetter(task.id());
    return new TaskDetail(task.id(), task.sessionId(), task.sessionTitle(), maskPhone(task.userPhone()),
        value(task.status()), task.prompt(), task.model(), task.ratio().databaseValue(),
        task.resolution().databaseValue(), task.imageCount(), task.resultCount(), task.totalCost(),
        task.attempts(), value(task.inputModerationStatus()), value(task.outputModerationStatus()),
        task.createdAt(), task.startedAt(), task.completedAt(), strings(task.referenceImageUrls()),
        task.errorCode(), redact(task.errorMessage()), dead == null ? null : new DeadLetter(
            dead.errorCode(), redact(dead.errorMessage()), dead.attempts(), dead.createdAt(), dead.resolvedAt()),
        mapper.listTaskResults(task.id()).stream().map(this::result).toList());
  }

  public ReconciliationResponse reconciliation() {
    List<ReconciliationRun> runs = mapper.listReconciliationRuns(20).stream().map(this::run).toList();
    return new ReconciliationResponse(runs);
  }

  public ObjectStorage.ObjectData readResult(String resultId, boolean thumbnail) {
    GenerationResultRecord result = mapper.findResult(requiredId(resultId));
    if (result == null) throw notFound("生成结果不存在");
    String key = thumbnail ? result.thumbnailObjectKey() : result.objectKey();
    if (key == null || key.isBlank()) key = result.imagePath();
    try {
      return storage.selected().get(key).orElseThrow(() -> notFound("生成结果不存在"));
    } catch (IllegalArgumentException invalidKey) {
      throw notFound("生成结果不存在");
    }
  }

  private TaskSummary summary(AdminTaskRecord task) {
    return new TaskSummary(task.id(), task.sessionId(), task.sessionTitle(), maskPhone(task.userPhone()),
        value(task.status()), task.prompt(), task.model(), task.ratio().databaseValue(),
        task.resolution().databaseValue(), task.imageCount(), task.resultCount(), task.totalCost(),
        task.attempts(), value(task.inputModerationStatus()), value(task.outputModerationStatus()),
        task.createdAt(), task.startedAt(), task.completedAt());
  }

  private Result result(GenerationResultRecord result) {
    String base = "/admin/tasks/results/" + result.id();
    return new Result(result.id(), result.index(), base + "/content", base + "/thumbnail",
        result.width(), result.height(), result.mimeType(), result.byteSize(), result.isAiGenerated(),
        value(result.moderationStatus()));
  }

  private ReconciliationRun run(QuotaReconciliationRunRecord run) {
    List<ReconciliationFinding> findings = mapper.listReconciliationFindings(run.id()).stream()
        .map(this::finding).toList();
    return new ReconciliationRun(run.id(), value(run.status()), run.startedAt(), run.completedAt(),
        run.scannedUsers(), run.scannedTasks(), run.mismatchCount(), run.repairedCount(),
        redact(run.errorMessage()), findings);
  }

  private ReconciliationFinding finding(AdminReconciliationFindingRecord finding) {
    return new ReconciliationFinding(finding.id(), finding.userId(), finding.taskId(),
        value(finding.kind()), value(finding.status()), finding.expectedAmount(), finding.actualAmount(),
        finding.repairedAt(), finding.createdAt());
  }

  private static String value(Enum<?> value) {
    return value == null ? null : value.name().toLowerCase(Locale.ROOT);
  }
  private static String maskPhone(String phone) {
    if (phone == null || phone.length() < 7) return "***";
    return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
  }
  private static String redact(String value) {
    if (value == null) return null;
    return value.replaceAll("(?<!\\d)1\\d{10}(?!\\d)", "***")
        .replaceAll("(?i)(api[_-]?key|authorization|token)\\s*[:=]\\s*[^\\s,;]+", "$1=***");
  }
  private static List<String> strings(JsonNode node) {
    if (node == null || !node.isArray()) return List.of();
    return java.util.stream.StreamSupport.stream(node.spliterator(), false)
        .filter(JsonNode::isTextual).map(JsonNode::asText).toList();
  }
  private static String requiredId(String value) {
    String normalized = optional(value);
    if (normalized == null || normalized.length() > 100) throw bad("ID_INVALID", "ID 无效");
    return normalized;
  }
  private static String bounded(String value, int max, String message) {
    String normalized = optional(value);
    if (normalized != null && normalized.length() > max) throw bad("VALIDATION_ERROR", message);
    return normalized;
  }
  private static String optional(String value) {
    return value == null || value.trim().isEmpty() ? null : value.trim();
  }
  private static int range(int value, int min, int max, String message) {
    if (value < min || value > max) throw bad("VALIDATION_ERROR", message);
    return value;
  }
  private static Instant startOfDay(String value) { return date(value, false); }
  private static Instant endOfDay(String value) { return date(value, true); }
  private static Instant date(String value, boolean end) {
    String normalized = optional(value);
    if (normalized == null) return null;
    try {
      LocalDate date = LocalDate.parse(normalized);
      return end ? date.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).minusNanos(1)
          : date.atStartOfDay().toInstant(ZoneOffset.UTC);
    } catch (DateTimeParseException invalid) {
      throw bad("DATE_INVALID", "日期格式无效");
    }
  }
  private static ApiException bad(String code, String message) {
    return new ApiException(HttpStatus.BAD_REQUEST, code, message);
  }
  private static ApiException notFound(String message) {
    return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
  }
}
