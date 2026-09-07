package com.dreamspace.worker.generation;

import com.dreamspace.common.persistence.database.DatabaseEnums.CollectionMode;
import com.dreamspace.common.persistence.database.DatabaseEnums.GenerationInputMode;
import com.dreamspace.common.persistence.database.DatabaseEnums.GenerationPreflightStatus;
import com.dreamspace.common.persistence.database.DatabaseEnums.GenerationRatio;
import com.dreamspace.common.persistence.database.DatabaseEnums.GenerationResolution;
import com.dreamspace.common.persistence.generation.GenerationPreflightRecord;
import com.dreamspace.common.persistence.generation.GenerationV2Mapper;
import com.dreamspace.common.persistence.generation.ImageCollectionPlan;
import com.dreamspace.common.persistence.generation.ResultSlotPlan;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Performs the non-billable collection planning work item in Worker. */
@Service
public class CollectionPreflightProcessor {
  private static final Logger log = LoggerFactory.getLogger(CollectionPreflightProcessor.class);
  private static final Pattern NUMBER = Pattern.compile(
      "(?:生成|输出|做|要|需要|提供)\\s*([0-9一二两三四五六七八九十]+)\\s*(?:张|幅|个|图片|图)?(?![A-Za-z0-9])"
          + "|(?<![\\p{L}\\p{N}])([0-9一二两三四五六七八九十]+)\\s*(?:张|幅|个|图片|图)");
  private final GenerationV2Mapper mapper;
  private final ObjectMapper json;
  private final ContentModerator moderator;
  private final PlanningModel planning;
  private final TransactionTemplate transactions;

  public CollectionPreflightProcessor(GenerationV2Mapper mapper, ObjectMapper json) {
    this(mapper, json, null, null, (TransactionTemplate) null);
  }

  public CollectionPreflightProcessor(GenerationV2Mapper mapper, ObjectMapper json,
      ContentModerator moderator, PlanningModel planning) {
    this(mapper, json, moderator, planning, (TransactionTemplate) null);
  }

  @org.springframework.beans.factory.annotation.Autowired
  public CollectionPreflightProcessor(GenerationV2Mapper mapper, ObjectMapper json,
      @org.springframework.beans.factory.annotation.Autowired(required = false) ContentModerator moderator,
      @org.springframework.beans.factory.annotation.Autowired(required = false) PlanningModel planning,
      PlatformTransactionManager transactionManager) {
    this(mapper, json, moderator, planning,
        transactionManager == null ? null : new TransactionTemplate(transactionManager));
  }

  private CollectionPreflightProcessor(GenerationV2Mapper mapper, ObjectMapper json,
      ContentModerator moderator, PlanningModel planning, TransactionTemplate transactions) {
    this.mapper = mapper;
    this.json = json;
    this.moderator = moderator;
    this.planning = planning;
    this.transactions = transactions;
  }

  public boolean process(String preflightId) {
    return process(preflightId, new GenerationAttempt("preflight:" + preflightId + ":1", 1, 1));
  }

  public boolean process(String preflightId, GenerationAttempt attempt) {
    long started = System.nanoTime();
    GenerationPreflightRecord preflight = mapper.findPreflightForWorker(preflightId);
    if (preflight == null) return false;
    boolean claimed = transaction(() -> mapper.claimPreflight(preflightId) == 1);
    if (!claimed
        && !(attempt.number() > 1 && preflight.status() == GenerationPreflightStatus.PLANNING)) {
      return false;
    }
    try {
      String prompt = preflight.inputJson() == null ? "" : preflight.inputJson().path("prompt").asText("");
      String mode = preflight.inputJson() == null ? "AUTO" : preflight.inputJson().path("imageCountMode").asText("AUTO");
      transaction(() -> { mapper.insertPreflightEvent(preflight.id(), "preflight.planning", "PLANNING",
          write(java.util.Map.of("preflightId", preflight.id()))); return true; });
      Integer requested = requestedCount(preflight.inputJson(), mode);
      if (requested != null && (requested < 1 || requested > 4)) {
        return finish(preflight, "NEEDS_CLARIFICATION", null, "GENERATION_COUNT_LIMIT", "图片数量必须在 1-4 张之间");
      }
      if (moderator != null) {
        ContentModerator.Decision decision = moderator.moderateInput(snapshot(preflight, prompt, requested));
        if (!decision.approved())
          return finish(preflight, "FAILED", null, "INPUT_MODERATION_REJECTED", "提示词或参考内容未通过审核");
      }
      if (planning == null) {
        return finish(preflight, "FAILED", null, "PLANNING_MODEL_UNAVAILABLE", "集合规划模型不可用");
      }
      long modelStarted = System.nanoTime();
      CollectionPlanProposal proposal = planning.collection(snapshot(preflight, prompt, requested), requested,
          mode, new StageContext("preflight:" + preflight.id(), preflight.id(),
              "preflight:" + preflight.id(), "collection_planning"));
      log.atInfo().addKeyValue("preflightId", preflight.id()).addKeyValue("sessionId", preflight.sessionId())
          .addKeyValue("attempt", attempt.number()).addKeyValue("durationMs", elapsedMillis(modelStarted))
          .log("collection preflight planning model completed");
      if (proposal == null) {
        return finish(preflight, "FAILED", null, "GENERATION_COLLECTION_PLAN_INVALID", "集合规划模型未返回结果");
      }
      if (proposal.needsClarification()) {
        String details = proposal.clarificationReason().isBlank() ? "集合图片职责或必要事实需要确认" : proposal.clarificationReason();
        return finish(preflight, "NEEDS_CLARIFICATION", null, "GENERATION_COLLECTION_NEEDS_CLARIFICATION", details);
      }
      FrozenCollection frozen;
      try {
        frozen = validateAndFreeze(proposal, preflight, requested);
      } catch (PlanValidationException error) {
        return finish(preflight, error.status, null, error.code, error.getMessage());
      }
      if (!transaction(() -> mapper.updatePreflightOutput(preflight.id(), frozen.ratio(), frozen.width(), frozen.height()) == 1)) {
        return false;
      }
      boolean finished = finish(preflight, "READY", frozen.plan(), null, null);
      log.atInfo().addKeyValue("preflightId", preflight.id()).addKeyValue("sessionId", preflight.sessionId())
          .addKeyValue("status", finished ? "READY" : "UNCHANGED")
          .addKeyValue("durationMs", elapsedMillis(started)).log("collection preflight processing completed");
      return finished;
    } catch (RuntimeException error) {
      log.atWarn().addKeyValue("preflightId", preflight.id())
          .addKeyValue("attempt", attempt.number()).addKeyValue("maxAttempts", attempt.maxAttempts())
          .addKeyValue("errorCode", error instanceof GenerationProviderException provider ? provider.code() : "GENERATION_COLLECTION_PLANNING_FAILED")
          .addKeyValue("retryable", error instanceof GenerationProviderException provider && provider.retryable())
          .log("collection preflight failed", error);
      if (error instanceof GenerationProviderException provider && provider.retryable()) {
        if (attempt.number() < attempt.maxAttempts()) throw provider;
        return finish(preflight, "FAILED", null, provider.code(), "集合规划服务多次失败，请稍后重试");
      }
      if (error instanceof GenerationProviderException provider) {
        return finish(preflight, "FAILED", null, provider.code(), planningFailureDetails(provider.code()));
      }
      return finish(preflight, "FAILED", null, "GENERATION_COLLECTION_PLANNING_FAILED", "集合规划失败");
    }
  }

  private String planningFailureDetails(String code) {
    return switch (code) {
      case "PLANNING_OUTPUT_INVALID" -> "生成要求解析失败，请重试";
      case "PLANNING_EMPTY_RESPONSE" -> "生成准备未完成，请重试";
      case "PLANNING_COLLECTION_UNSUPPORTED" -> "当前描述暂不支持批量生成，请调整描述后重试";
      default -> "生成准备失败，请重试";
    };
  }

  private boolean finish(GenerationPreflightRecord p, String status, ImageCollectionPlan plan, String code, String details) {
    boolean finished = transaction(() -> {
      String planJson = plan == null ? null : write(plan);
      Integer count = plan == null ? null : plan.slots().size();
      Integer estimated = plan == null ? null : count * p.unitCost();
      if (mapper.finishPreflight(p.id(), status, planJson, plan == null ? null : "collection-v2",
          plan == null ? null : plan.mode().name(), count, estimated, code, details) != 1) return false;
      mapper.insertPreflightEvent(p.id(), "preflight." + status.toLowerCase(Locale.ROOT), status,
          write(java.util.Map.of("preflightId", p.id(), "status", status, "targetImageCount", count == null ? 0 : count)));
      return true;
    });
    if (!finished) {
      log.atWarn().addKeyValue("preflightId", p.id()).addKeyValue("sessionId", p.sessionId())
          .addKeyValue("requestedStatus", status).addKeyValue("errorCode", code)
          .log("collection preflight terminal state was not written");
    } else {
      log.atInfo().addKeyValue("preflightId", p.id()).addKeyValue("sessionId", p.sessionId())
          .addKeyValue("status", status).addKeyValue("errorCode", code)
          .log("collection preflight terminal state written");
    }
    return finished;
  }

  private static long elapsedMillis(long started) {
    return java.time.Duration.ofNanos(System.nanoTime() - started).toMillis();
  }

  private <T> T transaction(java.util.function.Supplier<T> action) {
    if (transactions == null) return action.get();
    return transactions.execute(status -> action.get());
  }

  private Integer requestedCount(JsonNode input, String mode) {
    if (input != null && input.has("imageCount") && !input.path("imageCount").isNull()) {
      return input.path("imageCount").asInt();
    }
    if (mode == null) return null;
    String normalized = mode.trim().toUpperCase(Locale.ROOT);
    if (!normalized.matches("[0-9]+")) return null;
    return Integer.valueOf(normalized);
  }
  private String write(Object value) { try { return json.writeValueAsString(value); } catch (Exception e) { throw new IllegalStateException(e); } }

  private WorkerTaskSnapshot snapshot(GenerationPreflightRecord preflight, String prompt, Integer requestedCount) {
    List<String> imageIds = preflight.inputJson() == null || !preflight.inputJson().path("imageIds").isArray()
        ? List.of() : json.convertValue(preflight.inputJson().path("imageIds"),
            json.getTypeFactory().constructCollectionType(List.class, String.class));
    GenerationRatio ratio = parseRatio(preflight.ratio());
    GenerationResolution resolution = parseResolution(preflight.resolution());
    return new WorkerTaskSnapshot(preflight.id(), preflight.userId(), preflight.sessionId(), prompt,
        GenerationInputMode.AUTO, imageIds, "image-4.7", ratio, resolution, preflight.width(), preflight.height(),
        requestedCount == null ? 1 : requestedCount,
        preflight.estimatedCost() == null ? preflight.unitCost() : preflight.estimatedCost(), 0);
  }

  private FrozenCollection validateAndFreeze(CollectionPlanProposal proposal,
      GenerationPreflightRecord preflight, Integer requestedCount) {
    List<ResultSlotPlan> slots = proposal.slots();
    if (slots.size() < 1 || slots.size() > 4) {
      throw new PlanValidationException("NEEDS_CLARIFICATION", "GENERATION_COUNT_LIMIT", "集合结果必须为 1-4 张");
    }
    if (requestedCount != null && slots.size() != requestedCount) {
      throw new PlanValidationException("NEEDS_CLARIFICATION", "GENERATION_COUNT_CONFLICT", "手动数量与规划槽位数量冲突");
    }
    if (!Double.isFinite(proposal.confidence()) || proposal.confidence() < 0.70) {
      throw new PlanValidationException("NEEDS_CLARIFICATION", "GENERATION_COLLECTION_NEEDS_CLARIFICATION", "集合规划置信度不足，请补充图片职责");
    }
    if (proposal.shared() == null || !proposal.shared().isObject()) {
      throw new PlanValidationException("FAILED", "GENERATION_COLLECTION_PLAN_INVALID", "集合共享事实必须是对象");
    }
    ObjectNode shared = validateShared(proposal.shared(), preflight);
    shared.remove("warnings");
    Integer promptCount = explicitPromptCount(preflight.inputJson() == null
        ? "" : preflight.inputJson().path("prompt").asText(""));
    if (requestedCount != null && promptCount != null && !requestedCount.equals(promptCount)
        && proposal.mode() == CollectionMode.VARIATIONS) {
      shared.putArray("warnings").add("已按生成参数输出 " + requestedCount
          + " 张，覆盖描述中的 " + promptCount + " 张");
    }
    String resolvedRatio = preflight.ratio();
    Integer resolvedWidth = preflight.width();
    Integer resolvedHeight = preflight.height();
    if ("smart".equalsIgnoreCase(resolvedRatio)) {
      resolvedRatio = smartRatio(shared.path("outputAspectRatio").asText(null));
      GenerationRatio ratio = parseRatio(resolvedRatio);
      OutputDimensions dimensions = OutputDimensions.resolve(ratio, parseResolution(preflight.resolution()));
      resolvedWidth = dimensions.width();
      resolvedHeight = dimensions.height();
    }
    Set<Integer> indexes = new HashSet<>();
    Set<String> labels = new HashSet<>();
    Set<String> prompts = new HashSet<>();
    List<ResultSlotPlan> frozen = new ArrayList<>();
    for (ResultSlotPlan slot : slots) {
      if (slot == null || slot.index() < 0 || slot.index() >= slots.size()
          || !indexes.add(slot.index()) || !hasText(slot.label()) || !labels.add(slot.label())
          || !hasText(slot.role()) || !hasText(slot.intent())
          || !hasNonBlankValues(slot.contentScope(), false)
          || !hasNonBlankValues(slot.variationConstraints(), false)
          || slot.prompt() == null || !slot.prompt().isObject() || slot.acceptance() == null || !slot.acceptance().isObject()) {
        throw new PlanValidationException("FAILED", "GENERATION_COLLECTION_PLAN_INVALID", "集合槽位字段不完整或索引不连续");
      }
      String positivePrompt = slot.prompt().path("positivePrompt").asText("").trim();
      if (positivePrompt.isBlank() || !prompts.add(positivePrompt)) {
        throw new PlanValidationException("FAILED", "GENERATION_COLLECTION_PLAN_INVALID", "集合槽位必须拥有独立且不漂移的 Prompt");
      }
      ObjectNode packageNode = (ObjectNode) slot.prompt().deepCopy();
      ObjectNode modelInput = packageNode.with("modelInput");
      modelInput.put("slotIndex", slot.index());
      modelInput.put("label", slot.label());
      modelInput.put("collectionMode", proposal.mode().name());
      modelInput.put("aspectRatio", resolvedRatio);
      modelInput.put("resolution", preflight.resolution());
      modelInput.put("width", resolvedWidth);
      modelInput.put("height", resolvedHeight);
      modelInput.set("collectionShared", shared.deepCopy());
      modelInput.set("slotAcceptance", slot.acceptance().deepCopy());
      frozen.add(new ResultSlotPlan(slot.index(), slot.label(), slot.role(), slot.intent(),
          slot.contentScope(), slot.variationConstraints(), packageNode, slot.acceptance()));
    }
    for (int index = 0; index < slots.size(); index++) if (!indexes.contains(index)) {
      throw new PlanValidationException("FAILED", "GENERATION_COLLECTION_PLAN_INVALID", "集合槽位索引必须连续");
    }
    ImageCollectionPlan plan = new ImageCollectionPlan("collection-v2", proposal.mode(), shared, frozen,
        proposal.confidence(), proposal.unknowns());
    return new FrozenCollection(plan, resolvedRatio, resolvedWidth, resolvedHeight);
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private static boolean hasNonBlankValues(List<String> values, boolean requireValue) {
    return values != null && (!requireValue || !values.isEmpty())
        && values.stream().allMatch(CollectionPreflightProcessor::hasText);
  }

  private ObjectNode validateShared(JsonNode value, GenerationPreflightRecord preflight) {
    ObjectNode shared = (ObjectNode) value.deepCopy();
    List<String> imageIds = preflight.inputJson() == null || !preflight.inputJson().path("imageIds").isArray()
        ? List.of() : json.convertValue(preflight.inputJson().path("imageIds"),
            json.getTypeFactory().constructCollectionType(List.class, String.class));
    JsonNode assignments = shared.get("imageAssignments");
    if (imageIds.isEmpty()) {
      if (assignments == null || assignments.isNull()) shared.putArray("imageAssignments");
      else if (!assignments.isArray() || !assignments.isEmpty()) {
        throw new PlanValidationException("FAILED", "GENERATION_COLLECTION_PLAN_INVALID",
            "集合计划包含未提供素材的角色映射");
      }
      return shared;
    }
    if (assignments == null || !assignments.isArray() || assignments.size() != imageIds.size()) {
      throw new PlanValidationException("FAILED", "GENERATION_COLLECTION_PLAN_INVALID",
          "集合计划未完整定义素材角色");
    }
    Set<String> expected = new HashSet<>(imageIds);
    Set<String> actual = new HashSet<>();
    int targets = 0;
    int references = 0;
    for (JsonNode assignment : assignments) {
      String imageId = assignment.path("imageId").asText("");
      String role = assignment.path("role").asText("").toUpperCase(Locale.ROOT);
      if (!assignment.isObject() || !expected.contains(imageId) || !actual.add(imageId)
          || !Set.of("TARGET_A", "REFERENCE_B", "UNUSED").contains(role)) {
        throw new PlanValidationException("FAILED", "GENERATION_COLLECTION_PLAN_INVALID",
            "集合计划素材角色无效");
      }
      if ("TARGET_A".equals(role)) targets++;
      if ("REFERENCE_B".equals(role)) references++;
    }
    if (!actual.equals(expected) || targets > 1 || references > 1) {
      throw new PlanValidationException("FAILED", "GENERATION_COLLECTION_PLAN_INVALID",
          "集合计划素材角色冲突");
    }
    return shared;
  }

  private static String smartRatio(String value) {
    if (value == null) {
      throw new PlanValidationException("FAILED", "GENERATION_COLLECTION_PLAN_INVALID",
          "智能比例未在预规划中解析");
    }
    String normalized = value.trim().toLowerCase(Locale.ROOT);
    if (!Set.of("21:9", "16:9", "3:2", "4:3", "1:1", "3:4", "2:3", "9:16").contains(normalized)) {
      throw new PlanValidationException("FAILED", "GENERATION_COLLECTION_PLAN_INVALID",
          "智能比例解析结果无效");
    }
    return normalized;
  }

  private static Integer explicitPromptCount(String prompt) {
    Matcher matcher = NUMBER.matcher(prompt == null ? "" : prompt);
    if (!matcher.find()) return null;
    String value = matcher.group(1) == null ? matcher.group(2) : matcher.group(1);
    if (value == null || value.isBlank()) return null;
    try { return Integer.valueOf(value); }
    catch (NumberFormatException ignored) {
      return switch (value) {
        case "一" -> 1; case "二", "两" -> 2; case "三" -> 3; case "四" -> 4;
        case "五" -> 5; case "六" -> 6; case "七" -> 7; case "八" -> 8;
        case "九" -> 9; case "十" -> 10; default -> null;
      };
    }
  }

  private static final class PlanValidationException extends RuntimeException {
    private final String status;
    private final String code;
    private PlanValidationException(String status, String code, String message) {
      super(message); this.status = status; this.code = code;
    }
  }

  private record FrozenCollection(ImageCollectionPlan plan, String ratio, Integer width, Integer height) {}

  private static GenerationRatio parseRatio(String value) {
    String normalized = value == null ? "1:1" : value;
    return switch (normalized) {
      case "smart" -> GenerationRatio.SMART; case "21:9" -> GenerationRatio.RATIO_21_9;
      case "16:9" -> GenerationRatio.RATIO_16_9; case "3:2" -> GenerationRatio.RATIO_3_2;
      case "4:3" -> GenerationRatio.RATIO_4_3; case "3:4" -> GenerationRatio.RATIO_3_4;
      case "2:3" -> GenerationRatio.RATIO_2_3; case "9:16" -> GenerationRatio.RATIO_9_16;
      case "custom" -> GenerationRatio.CUSTOM; default -> GenerationRatio.RATIO_1_1;
    };
  }

  private static GenerationResolution parseResolution(String value) {
    return "4K".equalsIgnoreCase(value) ? GenerationResolution.K4 : GenerationResolution.K2;
  }
}
