package com.dreamspace.api.service;

import com.dreamspace.api.common.ApiException;
import com.dreamspace.api.persistence.admin.BillingMapper;
import com.dreamspace.api.persistence.admin.PricingRuleRecord;
import com.dreamspace.common.persistence.config.DreamSpaceProperties;
import com.dreamspace.common.persistence.database.DatabaseEnums.CollectionMode;
import com.dreamspace.common.persistence.generation.GenerationPreflightRecord;
import com.dreamspace.common.persistence.generation.GenerationPromptHash;
import com.dreamspace.common.persistence.generation.GenerationResultSlotRecord;
import com.dreamspace.common.persistence.generation.GenerationTaskRecord;
import com.dreamspace.common.persistence.generation.GenerationV2Mapper;
import com.dreamspace.common.persistence.generation.ImageCollectionPlan;
import com.dreamspace.common.persistence.generation.ResultSlotPlan;
import com.dreamspace.common.persistence.quota.QuotaTransactionService;
import com.dreamspace.common.persistence.storage.ObjectStorageFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** API side of the asynchronous, non-billable preflight and v2 task creation flow. */
@Service
public class CollectionPreflightService {
  private final GenerationV2Mapper mapper;
  private final GenerationService generation;
  private final GenerationQueuePublisher queue;
  private final QuotaTransactionService quota;
  private final DreamSpaceProperties properties;
  private final ObjectMapper json;
  private final BillingMapper billing;
  private final TransactionTemplate transactions;
  private final ExecutorService sseExecutor = Executors.newCachedThreadPool(r -> {
    Thread thread = new Thread(r, "dream-space-preflight-sse");
    thread.setDaemon(true);
    return thread;
  });

  public CollectionPreflightService(GenerationV2Mapper mapper, GenerationService generation,
      GenerationQueuePublisher queue, QuotaTransactionService quota, DreamSpaceProperties properties,
      ObjectMapper json, PlatformTransactionManager transactionManager, @org.springframework.beans.factory.annotation.Autowired(required = false) BillingMapper billing) {
    this.mapper = mapper; this.generation = generation; this.queue = queue; this.quota = quota;
    this.properties = properties; this.json = json; this.billing = billing;
    this.transactions = new TransactionTemplate(transactionManager);
  }

  public record Request(String idempotencyKey, String draftKey, String sessionId, String prompt,
      List<String> imageIds, String ratio, String resolution, Integer width, Integer height,
      String imageCountMode, Integer imageCount) {}
  public record Queued(String id, String status, String eventsUrl, String errorCode, String errorDetails) {
    public Queued(String id, String status, String eventsUrl) { this(id, status, eventsUrl, null, null); }
  }
  public record SlotSummary(int index, String label, String role) {}
  public record Ready(String status, String planToken, Instant expiresAt, String collectionMode,
      int targetImageCount, List<SlotSummary> slots, Output output, int unitCost, int estimatedCost, List<String> warnings) {}
  public record Output(String ratio, String resolution, Integer width, Integer height) {}
  public record CreateRequest(String idempotencyKey, String planToken) {}

  public Object preflight(String userId, Request request) {
    Validated value = validate(userId, request);
    GenerationPreflightRecord replay = mapper.findPreflightByIdempotency(userId, value.idempotencyKey());
    if (replay != null) {
      if (!value.inputHash().equals(replay.inputHash())) throw bad("GENERATION_IDEMPOTENCY_CONFLICT", "幂等键已用于其他预规划参数");
      return responseFor(replay);
    }
    String id = UUID.randomUUID().toString();
    try {
      transactions.executeWithoutResult(status -> {
        mapper.supersedePreflights(userId, value.draftKey(), value.inputHash());
        mapper.insertPreflight(id, userId, value.sessionId(), value.idempotencyKey(), value.draftKey(), value.inputHash(),
            value.inputJson(), value.ratio(), value.resolution(), value.width(), value.height(), value.ruleId(), value.ruleVersion(), value.unitCost());
        event(id, "preflight.queued", "QUEUED", java.util.Map.of("preflightId", id));
      });
    } catch (DuplicateKeyException duplicate) {
      GenerationPreflightRecord raceReplay = mapper.findPreflightByIdempotency(userId, value.idempotencyKey());
      if (raceReplay != null) {
        if (!value.inputHash().equals(raceReplay.inputHash())) throw bad("GENERATION_IDEMPOTENCY_CONFLICT", "幂等键已用于其他预规划参数");
        return responseFor(raceReplay);
      }
      throw duplicate;
    }
    queue.publishPreflight(id, value.idempotencyKey());
    return new Queued(id, "queued", "/api/dream_web/generation/preflights/" + id + "/events");
  }

  public Object get(String userId, String id) {
    GenerationPreflightRecord record = mapper.findPreflight(userId, id);
    if (record == null) throw notFound();
    return responseFor(record);
  }

  public SseEmitter events(String userId, String id, long afterId) {
    GenerationPreflightRecord record = mapper.findPreflight(userId, id);
    if (record == null) throw notFound();
    SseEmitter emitter = new SseEmitter(0L);
    sseExecutor.submit(() -> stream(userId, id, Math.max(0L, afterId), emitter));
    return emitter;
  }

  public GenerationService.SubmitResponse create(String userId, CreateRequest request) {
    if (request == null || request.planToken() == null) throw bad("GENERATION_PREFLIGHT_EXPIRED", "预规划已失效");
    if (request.idempotencyKey() == null || !request.idempotencyKey().matches("[A-Za-z0-9:_-]{8,128}"))
      throw bad("VALIDATION_ERROR", "幂等键格式无效");
    Token token = verify(request.planToken());
    // A lost HTTP response is safely replayed by create idempotency key. Check
    // this before requiring READY because the original transaction consumes
    // the preflight token as part of task creation.
    GenerationTaskRecord existing = request.idempotencyKey() == null ? null : generationTaskByKey(userId, request.idempotencyKey());
    if (existing != null) {
      if (!token.preflightId().equals(existing.preflightId())) throw bad("GENERATION_IDEMPOTENCY_CONFLICT", "幂等键已用于其他生成参数");
      return new GenerationService.SubmitResponse(generation.getSession(userId, existing.sessionId()), generation.viewTask(userId, existing.id()), generation.quota(userId), true);
    }
    if (token.expiresAt().isBefore(Instant.now())) throw bad("GENERATION_PREFLIGHT_EXPIRED", "预规划已过期，请重新提交");
    GenerationPreflightRecord preflight = mapper.findPreflight(userId, token.preflightId());
    if (preflight == null || preflight.status() == null || !"READY".equals(preflight.status().name())
        || !userId.equals(token.userId()) || !token.inputHash().equals(preflight.inputHash())
        || preflight.expiresAt() == null || !preflight.expiresAt().equals(token.expiresAt())
        || preflight.expiresAt().isBefore(Instant.now())) throw bad("GENERATION_PREFLIGHT_EXPIRED", "预规划已过期，请重新提交");
    ImageCollectionPlan plan = readPlan(preflight.planJson());
    String taskId = UUID.randomUUID().toString();
    String sessionId = preflight.sessionId();
    if (sessionId == null || sessionId.isBlank()) sessionId = UUID.randomUUID().toString();
    final String sid = sessionId;
    String executionId = UUID.randomUUID().toString();
    String createKey = request.idempotencyKey() == null ? "create-" + UUID.randomUUID() : request.idempotencyKey();
    final String finalSession = sid;
    try {
      transactions.executeWithoutResult(status -> {
        GenerationPreflightRecord locked = mapper.lockPreflight(userId, token.preflightId());
        if (!readyForCreate(locked, token)) {
          GenerationTaskRecord concurrentReplay = generationTaskByKey(userId, createKey);
          if (concurrentReplay != null && token.preflightId().equals(concurrentReplay.preflightId())) {
            throw new ConcurrentCreateReplay();
          }
          throw bad("GENERATION_PREFLIGHT_EXPIRED", "预规划已过期，请重新提交");
        }
        ImageCollectionPlan lockedPlan = readPlan(locked.planJson());
        int estimatedCost = locked.estimatedCost() == null
            ? locked.unitCost() * lockedPlan.slots().size() : locked.estimatedCost();
        java.util.Set<Integer> planIndexes = lockedPlan.slots().stream().map(ResultSlotPlan::index).collect(java.util.stream.Collectors.toSet());
        boolean contiguousPlan = java.util.stream.IntStream.range(0, lockedPlan.slots().size()).allMatch(planIndexes::contains);
        if (!contiguousPlan || planIndexes.size() != lockedPlan.slots().size()
            || lockedPlan.slots().isEmpty() || lockedPlan.slots().size() > 4
            || locked.imageCount() == null || locked.imageCount() != lockedPlan.slots().size()
            || estimatedCost != locked.unitCost() * lockedPlan.slots().size())
          throw bad("GENERATION_COLLECTION_PLAN_INVALID", "集合计划与计费快照不一致");
        if (locked.sessionId() == null || locked.sessionId().isBlank()) {
          if (mapper.insertSession(finalSession, userId, inputPrompt(locked), json(new GenerationService.Draft("AUTO", inputPrompt(locked),
              json.convertValue(locked.inputJson().path("imageIds"), json.getTypeFactory().constructCollectionType(List.class, String.class)),
              locked.ratio(), locked.resolution(), locked.width(), locked.height(),
              locked.inputJson().path("imageCountMode").asText("AUTO"), lockedPlan.slots().size()))) != 1)
            throw new IllegalStateException("generation session was not inserted");
        } else {
          generation.getSession(userId, finalSession);
        }
        // Use the existing task mapper through the v1 service's shared mapper is intentionally avoided here;
        // v2 creation is delegated to a small JDBC statement in GenerationV2Mapper.
        if (mapper.insertTaskV2(taskId, finalSession, userId, inputPrompt(locked), "image-4.7", locked.ratio(), locked.resolution(), locked.width(), locked.height(), lockedPlan.slots().size(), inputImages(locked), locked.unitCost(), estimatedCost, createKey, locked.id(), locked.pricingRuleId(), locked.pricingRuleVersion()) != 1) throw new IllegalStateException("generation task was not inserted");
        mapper.insertCollectionPlan(UUID.randomUUID().toString(), taskId, "collection-v2", locked.inputHash(), json(lockedPlan), lockedPlan.mode().name());
        for (ResultSlotPlan slot : lockedPlan.slots()) mapper.insertResultSlot(taskId, slot.index(), slot.label(),
            slot.role(), slot.intent(), GenerationPromptHash.sha256(slot.prompt()));
        if (mapper.insertExecution(executionId, taskId, "INITIAL", "reserve:" + executionId, json(lockedPlan.slots().stream().map(ResultSlotPlan::index).toList()), estimatedCost, locked.pricingRuleId(), locked.pricingRuleVersion()) != 1) throw new IllegalStateException("generation execution was not inserted");
        if (!quota.reserve(userId, taskId, estimatedCost, "reserve:" + executionId, properties.quota().initialTotal(), locked.pricingRuleId(), locked.pricingRuleVersion(), executionId)) throw bad("QUOTA_INSUFFICIENT", "额度不足");
        mapper.insertTaskEvent(taskId, "task.execution.queued", "QUEUED",
            json(java.util.Map.of("executionId", executionId, "targetImageCount", lockedPlan.slots().size())));
        generation.clearSubmittedDraft(userId, finalSession);
        if (mapper.consumePreflight(locked.id(), userId, taskId) != 1) throw bad("GENERATION_PREFLIGHT_CONSUMED", "预规划已被使用");
      });
    } catch (ConcurrentCreateReplay replay) {
      GenerationTaskRecord existingTask = generationTaskByKey(userId, createKey);
      if (existingTask == null || !token.preflightId().equals(existingTask.preflightId())) {
        throw bad("GENERATION_PREFLIGHT_CONSUMED", "预规划已被使用");
      }
      return new GenerationService.SubmitResponse(generation.getSession(userId, existingTask.sessionId()),
          generation.viewTask(userId, existingTask.id()), generation.quota(userId), true);
    } catch (DuplicateKeyException duplicate) {
      GenerationTaskRecord replay = generationTaskByKey(userId, createKey);
      if (replay == null) throw duplicate;
      if (!token.preflightId().equals(replay.preflightId())) throw bad("GENERATION_IDEMPOTENCY_CONFLICT", "幂等键已用于其他预规划");
      return new GenerationService.SubmitResponse(generation.getSession(userId, replay.sessionId()), generation.viewTask(userId, replay.id()), generation.quota(userId), true);
    }
    GenerationTaskRecord created = generationTaskByKey(userId, createKey);
    queue.publishExecution(taskId, executionId, "execution:" + executionId);
    return new GenerationService.SubmitResponse(generation.getSession(userId, finalSession), generation.viewTask(userId, created.id()), generation.quota(userId), false);
  }

  private GenerationTaskRecord generationTaskByKey(String userId, String key) {
    return mapper.findTaskByIdempotency(userId, key);
  }

  private boolean readyForCreate(GenerationPreflightRecord preflight, Token token) {
    return preflight != null && preflight.status() != null && "READY".equals(preflight.status().name())
        && token.userId().equals(preflight.userId()) && token.preflightId().equals(preflight.id())
        && token.inputHash().equals(preflight.inputHash()) && preflight.expiresAt() != null
        && token.expiresAt().equals(preflight.expiresAt()) && preflight.expiresAt().isAfter(Instant.now());
  }

  private Object responseFor(GenerationPreflightRecord p) {
    if (p.status() == null) return new Queued(p.id(), "queued", "/api/dream_web/generation/preflights/" + p.id() + "/events");
    if (p.status().name().equals("READY")) {
      if (p.expiresAt() == null || p.expiresAt().isBefore(Instant.now()))
        return new Queued(p.id(), "expired", "/api/dream_web/generation/preflights/" + p.id() + "/events");
      ImageCollectionPlan plan = readPlan(p.planJson());
      List<String> warnings = plan.shared() == null || !plan.shared().path("warnings").isArray()
          ? List.of() : json.convertValue(plan.shared().path("warnings"),
              json.getTypeFactory().constructCollectionType(List.class, String.class));
      return new Ready("ready", sign(p.id(), p.userId(), p.inputHash(), p.expiresAt()), p.expiresAt(), plan.mode().name(), plan.slots().size(), plan.slots().stream().map(s -> new SlotSummary(s.index(), s.label(), s.role())).toList(), new Output(p.ratio(), p.resolution(), p.width(), p.height()), p.unitCost(), p.estimatedCost() == null ? p.unitCost() * plan.slots().size() : p.estimatedCost(), warnings);
    }
    return new Queued(p.id(), p.status().name().toLowerCase(Locale.ROOT), "/api/dream_web/generation/preflights/" + p.id() + "/events", p.errorCode(), p.errorDetails());
  }

  private Validated validate(String userId, Request r) {
    if (r == null || r.prompt() == null || r.prompt().isBlank()) throw bad("VALIDATION_ERROR", "提示词不能为空");
    if (r.prompt().trim().length() > 4000) throw bad("VALIDATION_ERROR", "提示词长度应为 1-4000 个字符");
    String key = r.idempotencyKey() == null ? "" : r.idempotencyKey().trim(); if (!key.matches("[A-Za-z0-9:_-]{8,128}")) throw bad("VALIDATION_ERROR", "幂等键格式无效");
    String draftKey = r.draftKey() == null || r.draftKey().isBlank() ? key : r.draftKey().trim();
    String ratio = r.ratio() == null || r.ratio().isBlank() ? "1:1" : r.ratio().toLowerCase(Locale.ROOT);
    String resolution = r.resolution() == null || r.resolution().isBlank() ? "2K" : r.resolution().toUpperCase(Locale.ROOT);
    if (!List.of("smart","21:9","16:9","3:2","4:3","1:1","3:4","2:3","9:16","custom").contains(ratio)) throw bad("GENERATION_RATIO_INVALID", "图片比例无效");
    if (!List.of("2K","4K").contains(resolution)) throw bad("GENERATION_RESOLUTION_INVALID", "图片分辨率无效");
    String countMode = r.imageCountMode() == null || r.imageCountMode().isBlank() ? "AUTO" : r.imageCountMode().trim().toUpperCase(Locale.ROOT);
    if (!List.of("AUTO", "1", "2", "3", "4").contains(countMode)) throw bad("GENERATION_COUNT_INVALID", "图片数量模式无效");
    if (r.imageCount() != null && (r.imageCount() < 1 || r.imageCount() > 4)) throw bad("GENERATION_COUNT_INVALID", "图片数量必须在 1-4 张之间");
    if (!"AUTO".equals(countMode) && r.imageCount() != null && r.imageCount() != Integer.parseInt(countMode)) throw bad("GENERATION_COUNT_INVALID", "图片数量参数冲突");
    if ("smart".equals(ratio) && (r.width() != null || r.height() != null)) throw bad("GENERATION_DIMENSION_INVALID", "智能比例不能指定宽高");
    if (!"smart".equals(ratio) && (r.width() == null || r.height() == null)) throw bad("GENERATION_DIMENSION_INVALID", "图片比例必须指定宽高");
    if (!"smart".equals(ratio)) {
      if (r.width() < 512 || r.height() < 512 || r.width() % 64 != 0 || r.height() % 64 != 0)
        throw bad("GENERATION_DIMENSION_INVALID", "宽高必须不小于 512 且为 64 的整数倍");
      int maxEdge = "4K".equals(resolution) ? 4096 : 2048;
      if (r.width() > maxEdge || r.height() > maxEdge || (long) r.width() * r.height() > (long) maxEdge * maxEdge)
        throw bad("GENERATION_DIMENSION_INVALID", "宽高超过当前分辨率限制");
      if (!"custom".equals(ratio)) {
        String[] parts = ratio.split(":");
        long difference = Math.abs((long) r.width() * Integer.parseInt(parts[1]) - (long) r.height() * Integer.parseInt(parts[0]));
        if (difference > 64L * Math.max(Integer.parseInt(parts[0]), Integer.parseInt(parts[1])))
          throw bad("GENERATION_RATIO_INVALID", "宽高与所选比例不一致");
      }
    }
    if (r.imageIds() != null && r.imageIds().size() > 2) throw bad("GENERATION_IMAGES_INVALID", "最多添加两张图片");
    if (r.imageIds() != null) for (String imageId : r.imageIds()) {
      if (imageId == null || imageId.isBlank() || !imageId.matches("[A-Za-z0-9-]{16,64}")) throw bad("GENERATION_REFERENCE_INVALID", "图片地址无效");
    }
    int unit = "4K".equals(resolution) ? 2 : 1; PricingRuleRecord rule = billing == null ? null : billing.findActivePricingRule("IMAGE_GENERATION", resolution);
    if (billing != null && rule == null) throw bad("PRICING_RULE_NOT_FOUND", "当前生成参数没有可用计费规则");
    if (rule != null) unit = rule.unitCreditCost();
    List<String> imageIds = generation.validateReferenceIds(userId, r.imageIds());
    Integer effectiveCount = r.imageCount() == null && !"AUTO".equals(countMode) ? Integer.valueOf(countMode) : r.imageCount();
    ObjectNode input = json.createObjectNode().put("prompt", r.prompt().trim()).put("model", "image-4.7")
        .put("ratio", ratio).put("resolution", resolution).put("imageCountMode", countMode);
    input.put("draftKey", draftKey); input.put("sessionId", r.sessionId()); if (effectiveCount != null) input.put("imageCount", effectiveCount);
    if (r.width() != null) input.put("width", r.width());
    if (r.height() != null) input.put("height", r.height());
    if (r.sessionId() != null && !r.sessionId().isBlank()) generation.getSession(userId, r.sessionId());
    input.set("imageIds", json.valueToTree(imageIds));
    return new Validated(key, draftKey, r.sessionId(), r.prompt().trim(), ratio, resolution, r.width(), r.height(), unit, rule == null ? null : rule.id(), rule == null ? null : rule.version(), sha256(input.toString()), input.toString());
  }
  private record Validated(String idempotencyKey, String draftKey, String sessionId, String prompt, String ratio, String resolution, Integer width, Integer height, int unitCost, String ruleId, Integer ruleVersion, String inputHash, String inputJson) {}
  private String inputPrompt(GenerationPreflightRecord p) { return p.inputJson() == null ? "" : p.inputJson().path("prompt").asText(""); }
  private String inputImages(GenerationPreflightRecord p) { return p.inputJson() == null ? "[]" : p.inputJson().path("imageIds").toString(); }
  private ImageCollectionPlan readPlan(JsonNode value) { try { return json.treeToValue(value, ImageCollectionPlan.class); } catch (Exception e) { throw bad("GENERATION_COLLECTION_NEEDS_CLARIFICATION", "集合计划不可用"); } }
  private void event(String id, String type, String status, Object payload) { mapper.insertPreflightEvent(id, type, status, json(payload)); }
  private String json(Object value) { try { return json.writeValueAsString(value); } catch (Exception e) { throw new IllegalStateException(e); } }
  private static String sha256(String value) { try { byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)); return java.util.HexFormat.of().formatHex(hash); } catch (Exception e) { throw new IllegalStateException(e); } }
  private String sign(String id, String userId, String hash, Instant expires) { String body = id + "." + userId + "." + hash + "." + expires.toEpochMilli(); return Base64.getUrlEncoder().withoutPadding().encodeToString(body.getBytes(StandardCharsets.UTF_8)) + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(hmac(body)); }
  private Token verify(String value) { try { String[] p = value.split("\\."); if (p.length != 2) throw new Exception(); String body = new String(Base64.getUrlDecoder().decode(p[0]), StandardCharsets.UTF_8); if (!MessageDigest.isEqual(Base64.getUrlDecoder().decode(p[1]), hmac(body))) throw new Exception(); String[] parts = body.split("\\."); if (parts.length != 4) throw new Exception(); Instant expires = Instant.ofEpochMilli(Long.parseLong(parts[3])); return new Token(parts[0], parts[1], parts[2], expires); } catch (ApiException e) { throw e; } catch (Exception e) { throw bad("GENERATION_PREFLIGHT_EXPIRED", "预规划已过期，请重新提交"); } }
  private byte[] hmac(String value) { try { Mac mac = Mac.getInstance("HmacSHA256"); String configured = properties.security() == null ? null : properties.security().preflightTokenSecret(); if (configured == null || configured.isBlank()) throw bad("PREFLIGHT_TOKEN_UNAVAILABLE", "生成准备服务未配置签名密钥，请设置 PREFLIGHT_TOKEN_SECRET 后重启 API"); mac.init(new SecretKeySpec(configured.getBytes(StandardCharsets.UTF_8), "HmacSHA256")); return mac.doFinal(value.getBytes(StandardCharsets.UTF_8)); } catch (ApiException e) { throw e; } catch (Exception e) { throw new IllegalStateException(e); } }
  private void stream(String userId, String id, long cursor, SseEmitter emitter) {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(120);
    try {
      while (System.nanoTime() < deadline) {
        GenerationPreflightRecord record = mapper.findPreflight(userId, id);
        if (record == null) { emitter.complete(); return; }
        for (java.util.Map<String,Object> event : mapper.listPreflightEvents(id, cursor, 100)) {
          long eventId = ((Number) event.get("id")).longValue();
          emitter.send(SseEmitter.event().id(Long.toString(eventId)).name(String.valueOf(event.get("type"))).data(event));
          cursor = eventId;
        }
        if (record.status() != null && switch (record.status().name()) { case "READY", "NEEDS_CLARIFICATION", "FAILED", "CONSUMED", "EXPIRED", "SUPERSEDED" -> true; default -> false; }) { emitter.complete(); return; }
        emitter.send(SseEmitter.event().comment("keep-alive"));
        Thread.sleep(250L);
      }
      emitter.complete();
    } catch (Exception ignored) { emitter.complete(); }
  }
  private record Token(String preflightId, String userId, String inputHash, Instant expiresAt) {}
  private static final class ConcurrentCreateReplay extends RuntimeException {}
  private static ApiException bad(String code, String message) { return new ApiException(HttpStatus.BAD_REQUEST, code, message); }
  private static ApiException notFound() { return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "预规划不存在"); }
}
