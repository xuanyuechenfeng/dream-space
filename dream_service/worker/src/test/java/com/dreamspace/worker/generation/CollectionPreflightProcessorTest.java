package com.dreamspace.worker.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dreamspace.common.persistence.database.DatabaseEnums.CollectionMode;
import com.dreamspace.common.persistence.database.DatabaseEnums.GenerationPreflightStatus;
import com.dreamspace.common.persistence.generation.GenerationPreflightRecord;
import com.dreamspace.common.persistence.generation.GenerationV2Mapper;
import com.dreamspace.common.persistence.generation.ImageCollectionPlan;
import com.dreamspace.common.persistence.generation.ResultSlotPlan;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CollectionPreflightProcessorTest {
  private final GenerationV2Mapper mapper = mock(GenerationV2Mapper.class);
  private final PlanningModel planning = mock(PlanningModel.class);
  private final ObjectMapper json = new ObjectMapper();
  private final CollectionPreflightProcessor processor =
      new CollectionPreflightProcessor(mapper, json, null, planning);

  @Test
  void freezesModelDecompositionForFourSeasonPromptAsDimensionalSlots() throws Exception {
    givenPreflight("为同一个景色生成春夏秋冬各一张", null);
    givenProposal(proposal(CollectionMode.DIMENSIONAL,
        slot(0, "春", "SEASON", "同一景色的春季状态", "同一湖畔景色，春季新绿和花朵"),
        slot(1, "夏", "SEASON", "同一景色的夏季状态", "同一湖畔景色，夏季浓绿和明亮阳光"),
        slot(2, "秋", "SEASON", "同一景色的秋季状态", "同一湖畔景色，秋季金黄树叶"),
        slot(3, "冬", "SEASON", "同一景色的冬季状态", "同一湖畔景色，冬季积雪和结冰湖面")));

    assertThat(processor.process("preflight-1")).isTrue();

    ImageCollectionPlan plan = finishedPlan();
    assertThat(plan.mode()).isEqualTo(CollectionMode.DIMENSIONAL);
    assertThat(plan.slots()).extracting(ResultSlotPlan::label)
        .containsExactly("春", "夏", "秋", "冬");
    assertThat(plan.slots()).extracting(slot -> slot.prompt().path("positivePrompt").asText())
        .containsExactly(
            "同一湖畔景色，春季新绿和花朵",
            "同一湖畔景色，夏季浓绿和明亮阳光",
            "同一湖畔景色，秋季金黄树叶",
            "同一湖畔景色，冬季积雪和结冰湖面");
    assertThat(plan.slots().get(0).prompt().path("modelInput").path("slotIndex").asInt())
        .isZero();
    assertThat(plan.slots().get(0).prompt().path("modelInput").path("aspectRatio").asText())
        .isEqualTo("1:1");
  }

  @Test
  void freezesSummaryAndProcessDescriptionAsSequenceSlots() throws Exception {
    givenPreflight("输出一张总结图和流程1、流程2、流程3", null);
    givenProposal(proposal(CollectionMode.SEQUENCE,
        slot(0, "总结", "SUMMARY", "完整流程总览", "展示完整业务流程的全局总结图"),
        slot(1, "流程 1", "PROCESS_STEP", "第一阶段", "只展示流程第一阶段的输入和操作"),
        slot(2, "流程 2", "PROCESS_STEP", "第二阶段", "只展示流程第二阶段的处理和判断"),
        slot(3, "流程 3", "PROCESS_STEP", "第三阶段", "只展示流程第三阶段的输出结果")));

    assertThat(processor.process("preflight-1")).isTrue();

    ImageCollectionPlan plan = finishedPlan();
    assertThat(plan.mode()).isEqualTo(CollectionMode.SEQUENCE);
    assertThat(plan.slots()).extracting(ResultSlotPlan::label)
        .containsExactly("总结", "流程 1", "流程 2", "流程 3");
    assertThat(plan.slots()).extracting(ResultSlotPlan::role)
        .containsExactly("SUMMARY", "PROCESS_STEP", "PROCESS_STEP", "PROCESS_STEP");
    assertThat(plan.slots().get(0).prompt().path("positivePrompt").asText())
        .contains("全局总结");
    assertThat(plan.slots().get(1).prompt().path("positivePrompt").asText())
        .contains("第一阶段");
  }

  @Test
  void freezesArbitraryFourIntentDescriptionWithoutKeywordTemplates() throws Exception {
    givenPreflight("根据这段产品描述，输出总览、用户旅程、关键指标、行动建议各一张", null);
    givenProposal(proposal(CollectionMode.DIMENSIONAL,
        slot(0, "总览", "OVERVIEW", "产品核心概览", "产品定位、受众和核心价值的概览图"),
        slot(1, "用户旅程", "JOURNEY", "用户旅程", "用户从发现到完成目标的旅程图"),
        slot(2, "关键指标", "METRICS", "指标体系", "只呈现用户明确提供的关键指标结构"),
        slot(3, "行动建议", "ACTIONS", "行动计划", "基于描述呈现分优先级的行动建议")));

    processor.process("preflight-1");

    ImageCollectionPlan plan = finishedPlan();
    assertThat(plan.mode()).isEqualTo(CollectionMode.DIMENSIONAL);
    assertThat(plan.slots()).extracting(ResultSlotPlan::label)
        .containsExactly("总览", "用户旅程", "关键指标", "行动建议");
    assertThat(plan.slots()).extracting(slot -> slot.prompt().path("positivePrompt").asText())
        .doesNotHaveDuplicates();
  }

  @Test
  void defaultsToOneModelPlannedVariationWhenAutomaticCountIsNotExplicit() throws Exception {
    givenPreflight("一张安静的湖面", null);
    givenProposal(proposal(CollectionMode.VARIATIONS,
        slot(0, "图片 1", "VARIATION", "安静湖面", "安静湖面的完整构图")));

    processor.process("preflight-1");

    assertThat(finishedPlan().slots()).hasSize(1);
    verify(planning).collection(any(WorkerTaskSnapshot.class), eq(null), eq("AUTO"), any(StageContext.class));
  }

  @Test
  void doesNotTreatResolutionDigitsAsAutomaticImageCount() throws Exception {
    givenPreflight("生成一张 4K 海报", null);
    givenProposal(proposal(CollectionMode.VARIATIONS,
        slot(0, "图片 1", "VARIATION", "4K 海报", "一张完整的 4K 海报设计")));

    processor.process("preflight-1");

    assertThat(finishedPlan().slots()).hasSize(1);
    verify(planning).collection(any(WorkerTaskSnapshot.class), eq(null), eq("AUTO"), any(StageContext.class));
  }

  @Test
  void resolvesSmartOutputBeforeFreezingTheCollectionPlan() throws Exception {
    givenPreflight("生成一张宽幅山景", null, "AUTO", "smart");
    CollectionPlanProposal proposal = proposal(CollectionMode.VARIATIONS,
        slot(0, "山景", "VARIATION", "宽幅山景", "电影感宽幅山景"));
    ((ObjectNode) proposal.shared()).put("outputAspectRatio", "16:9");
    givenProposal(proposal);

    assertThat(processor.process("preflight-1")).isTrue();

    verify(mapper).updatePreflightOutput("preflight-1", "16:9", 2048, 1152);
    ImageCollectionPlan plan = finishedPlan();
    assertThat(plan.slots().getFirst().prompt().path("modelInput").path("aspectRatio").asText())
        .isEqualTo("16:9");
    assertThat(plan.slots().getFirst().prompt().path("modelInput").path("width").asInt())
        .isEqualTo(2048);
    assertThat(plan.slots().getFirst().prompt().path("modelInput").path("height").asInt())
        .isEqualTo(1152);
  }

  @Test
  void passesExplicitCountModeToModelWhenImageCountFieldIsAbsent() throws Exception {
    givenPreflight("为同一个产品生成不同方案", null, "3");
    givenProposal(proposal(CollectionMode.VARIATIONS,
        slot(0, "方案 1", "VARIATION", "方案一", "产品方案一"),
        slot(1, "方案 2", "VARIATION", "方案二", "产品方案二"),
        slot(2, "方案 3", "VARIATION", "方案三", "产品方案三")));

    processor.process("preflight-1");

    assertThat(finishedPlan().slots()).hasSize(3);
    verify(planning).collection(any(WorkerTaskSnapshot.class), eq(3), eq("3"), any(StageContext.class));
  }

  @Test
  void freezesWarningWhenManualVariationCountOverridesPromptCount() throws Exception {
    givenPreflight("生成两张产品海报", 3);
    givenProposal(proposal(CollectionMode.VARIATIONS,
        slot(0, "方案 1", "VARIATION", "方案一", "产品海报方案一"),
        slot(1, "方案 2", "VARIATION", "方案二", "产品海报方案二"),
        slot(2, "方案 3", "VARIATION", "方案三", "产品海报方案三")));

    assertThat(processor.process("preflight-1")).isTrue();

    ImageCollectionPlan plan = finishedPlan();
    assertThat(plan.slots()).hasSize(3);
    assertThat(plan.shared().path("warnings").size()).isEqualTo(1);
    assertThat(plan.shared().path("warnings").get(0).asText())
        .isEqualTo("已按生成参数输出 3 张，覆盖描述中的 2 张");
  }

  @Test
  void blocksManualCountAboveFourBeforeCallingModel() {
    givenPreflight("生成五张产品海报", 5);

    assertThat(processor.process("preflight-1")).isTrue();

    verify(mapper).finishPreflight(eq("preflight-1"), eq("NEEDS_CLARIFICATION"), eq(null),
        eq(null), eq(null), eq(null), eq(null), eq("GENERATION_COUNT_LIMIT"), anyString());
    verify(planning, never()).collection(any(), any(), any(), any());
  }

  @Test
  void blocksManualCountWhenModelSlotsHaveDifferentCount() {
    givenPreflight("为同一个景色生成春夏秋冬各一张", 2);
    givenProposal(proposal(CollectionMode.DIMENSIONAL,
        slot(0, "春", "SEASON", "春季", "同一景色的春季"),
        slot(1, "夏", "SEASON", "夏季", "同一景色的夏季"),
        slot(2, "秋", "SEASON", "秋季", "同一景色的秋季"),
        slot(3, "冬", "SEASON", "冬季", "同一景色的冬季")));

    assertThat(processor.process("preflight-1")).isTrue();

    verify(mapper).finishPreflight(eq("preflight-1"), eq("NEEDS_CLARIFICATION"), eq(null),
        eq(null), eq(null), eq(null), eq(null), eq("GENERATION_COUNT_CONFLICT"), anyString());
  }

  @Test
  void requestsClarificationForLowConfidenceModelPlan() {
    givenPreflight("生成几张不同职责的图片", null);
    givenProposal(new CollectionPlanProposal("collection-v2", CollectionMode.DIMENSIONAL,
        json.createObjectNode(), List.of(slot(0, "总览", "OVERVIEW", "总览", "产品总览")),
        0.60, List.of(), false, ""));

    processor.process("preflight-1");

    verify(mapper).finishPreflight(eq("preflight-1"), eq("NEEDS_CLARIFICATION"), eq(null),
        eq(null), eq(null), eq(null), eq(null), eq("GENERATION_COLLECTION_NEEDS_CLARIFICATION"), anyString());
  }

  @Test
  void ignoresDriftedRelationMetadataWhenRequiredPromptIsComplete() {
    givenPreflight("生成一张产品总览", null);
    ResultSlotPlan slot = slot(0, "总览", "OVERVIEW", "产品总览", "无关主题");
    ObjectNode prompt = (ObjectNode) slot.prompt().deepCopy();
    prompt.put("promptRelation", "DRIFTED");
    givenProposal(proposal(CollectionMode.VARIATIONS,
        new ResultSlotPlan(0, slot.label(), slot.role(), slot.intent(), slot.contentScope(),
            slot.variationConstraints(), prompt, slot.acceptance())));

    processor.process("preflight-1");

    verify(mapper).finishPreflight(eq("preflight-1"), eq("READY"), anyString(),
        eq("collection-v2"), eq("VARIATIONS"), eq(1), eq(1), eq(null), eq(null));
  }

  @Test
  void acceptsCaseInsensitiveRelationAndIgnoresUnknownPromptExtensions() throws Exception {
    givenPreflight("生成一张产品总览", null);
    ResultSlotPlan original = slot(0, "总览", "OVERVIEW", "产品总览", "产品总览图");
    ObjectNode prompt = (ObjectNode) original.prompt().deepCopy();
    prompt.put("promptRelation", "expanded");
    prompt.put("layoutHint", "grid");
    givenProposal(proposal(CollectionMode.VARIATIONS,
        new ResultSlotPlan(original.index(), original.label(), original.role(), original.intent(),
            original.contentScope(), original.variationConstraints(), prompt, original.acceptance())));

    assertThat(processor.process("preflight-1")).isTrue();

    assertThat(finishedPlan().slots().getFirst().prompt().path("modelInput").path("slotIndex").asInt())
        .isZero();
  }

  @Test
  void rejectsNullOrBlankRequiredSlotTextInsteadOfApplyingDefaults() {
    givenPreflight("生成两张产品图片", 2);
    ResultSlotPlan first = slot(0, "总览", "VARIATION", "产品总览", "产品总览图");
    ResultSlotPlan second = slot(1, "细节", "VARIATION", "产品细节", "产品细节图");
    givenProposal(proposal(CollectionMode.VARIATIONS,
        new ResultSlotPlan(first.index(), null, first.role(), first.intent(), first.contentScope(),
            first.variationConstraints(), first.prompt(), first.acceptance()),
        new ResultSlotPlan(second.index(), second.label(), " ", second.intent(), second.contentScope(),
            second.variationConstraints(), second.prompt(), second.acceptance())));

    assertThat(processor.process("preflight-1")).isTrue();

    verify(mapper).finishPreflight(eq("preflight-1"), eq("FAILED"), eq(null),
        eq(null), eq(null), eq(null), eq(null), eq("GENERATION_COLLECTION_PLAN_INVALID"), anyString());
  }

  @Test
  void rejectsNullOrBlankSlotCollections() {
    givenPreflight("生成一张产品图片", 1);
    ResultSlotPlan valid = slot(0, "总览", "VARIATION", "产品总览", "产品总览图");
    givenProposal(proposal(CollectionMode.VARIATIONS,
        new ResultSlotPlan(valid.index(), valid.label(), valid.role(), valid.intent(), null,
            List.of(" "), valid.prompt(), valid.acceptance())));

    assertThat(processor.process("preflight-1")).isTrue();

    verify(mapper).finishPreflight(eq("preflight-1"), eq("FAILED"), eq(null),
        eq(null), eq(null), eq(null), eq(null), eq("GENERATION_COLLECTION_PLAN_INVALID"), anyString());
  }

  @Test
  void acceptsEmptyOptionalContentScope() throws Exception {
    givenPreflight("生成一张产品图片", 1);
    ResultSlotPlan valid = slot(0, "总览", "VARIATION", "产品总览", "产品总览图");
    givenProposal(proposal(CollectionMode.VARIATIONS,
        new ResultSlotPlan(valid.index(), valid.label(), valid.role(), valid.intent(), List.of(),
            valid.variationConstraints(), valid.prompt(), valid.acceptance())));

    assertThat(processor.process("preflight-1")).isTrue();

    ImageCollectionPlan plan = finishedPlan();
    assertThat(plan.slots()).hasSize(1);
    assertThat(plan.slots().getFirst().contentScope()).isEmpty();
  }

  @Test
  void rethrowsRetryablePlanningFailureBeforeLastQueueAttempt() {
    givenPreflight("生成一张产品总览", null);
    when(planning.collection(any(), any(), any(), any()))
        .thenThrow(new GenerationProviderException("PLANNING_TEMPORARILY_UNAVAILABLE", "temporary", true));

    assertThatThrownBy(() -> processor.process("preflight-1",
        new GenerationAttempt("preflight-1:1", 1, 2)))
        .isInstanceOf(GenerationProviderException.class)
        .hasMessage("temporary");

    verify(mapper, never()).finishPreflight(any(), any(), any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void marksPreflightFailedWhenRetryablePlanningFailureReachesLastQueueAttempt() {
    givenPreflight("生成一张产品总览", null);
    when(planning.collection(any(), any(), any(), any()))
        .thenThrow(new GenerationProviderException("PLANNING_TEMPORARILY_UNAVAILABLE", "temporary", true));

    assertThat(processor.process("preflight-1",
        new GenerationAttempt("preflight-1:2", 2, 2))).isTrue();

    verify(mapper).finishPreflight(eq("preflight-1"), eq("FAILED"), eq(null),
        eq(null), eq(null), eq(null), eq(null), eq("PLANNING_TEMPORARILY_UNAVAILABLE"), anyString());
  }

  @Test
  void preservesNonRetryablePlanningErrorCodeWithUserFacingDetails() {
    givenPreflight("生成一张产品总览", null);
    when(planning.collection(any(), any(), any(), any()))
        .thenThrow(new GenerationProviderException("PLANNING_OUTPUT_INVALID", "invalid modelInput", false));

    assertThat(processor.process("preflight-1")).isTrue();

    verify(mapper).finishPreflight(eq("preflight-1"), eq("FAILED"), eq(null),
        eq(null), eq(null), eq(null), eq(null), eq("PLANNING_OUTPUT_INVALID"),
        eq("生成要求解析失败，请重试"));
  }

  @Test
  void doesNotPublishReadyWhenPreflightWasSupersededDuringPlanning() {
    givenPreflight("生成一张产品总览", null);
    givenProposal(proposal(CollectionMode.VARIATIONS,
        slot(0, "总览", "OVERVIEW", "产品总览", "产品总览")));
    when(mapper.finishPreflight(anyString(), eq("READY"), anyString(), anyString(), anyString(),
        any(), any(), eq(null), eq(null))).thenReturn(0);

    assertThat(processor.process("preflight-1")).isFalse();

    verify(mapper, never()).insertPreflightEvent(eq("preflight-1"), eq("preflight.ready"), eq("READY"), anyString());
  }

  private CollectionPlanProposal proposal(CollectionMode mode, ResultSlotPlan... slots) {
    ObjectNode shared = json.createObjectNode().put("subject", "用户描述中的共享主体");
    return new CollectionPlanProposal("collection-v2", mode, shared, List.of(slots),
        0.93, List.of(), false, "");
  }

  private ResultSlotPlan slot(int index, String label, String role, String intent,
      String positivePrompt) {
    PromptPackage prompt = new PromptPackage(positivePrompt, "", Map.of("semanticFocus", label),
        "preserve supplied text", "collection-v2", PromptPackage.PromptRelation.EXPANDED,
        0.95, "为当前槽位补充视觉表达");
    return new ResultSlotPlan(index, label, role, intent, List.of(intent),
        List.of("与其他槽位保持职责区分"), json.valueToTree(prompt),
        json.createObjectNode().put("intentCovered", true));
  }

  private void givenProposal(CollectionPlanProposal proposal) {
    when(planning.collection(any(WorkerTaskSnapshot.class), any(), anyString(), any(StageContext.class)))
        .thenReturn(proposal);
  }

  private void givenPreflight(String prompt, Integer imageCount) {
    givenPreflight(prompt, imageCount, imageCount == null ? "AUTO" : Integer.toString(imageCount));
  }

  private void givenPreflight(String prompt, Integer imageCount, String imageCountMode) {
    givenPreflight(prompt, imageCount, imageCountMode, "1:1");
  }

  private void givenPreflight(String prompt, Integer imageCount, String imageCountMode, String ratio) {
    ObjectNode input = json.createObjectNode().put("prompt", prompt)
        .put("imageCountMode", imageCountMode);
    if (imageCount != null) input.put("imageCount", imageCount);
    GenerationPreflightRecord preflight = new GenerationPreflightRecord(
        "preflight-1", "user-1", null, GenerationPreflightStatus.QUEUED, "key-1", "draft-1", "hash",
        input, null, null, null, imageCount, ratio, "2K",
        "smart".equals(ratio) ? null : 2048, "smart".equals(ratio) ? null : 2048,
        null, null, 1, null, null, null, null, null, null, null, Instant.now(), Instant.now());
    when(mapper.findPreflightForWorker("preflight-1")).thenReturn(preflight);
    when(mapper.claimPreflight("preflight-1")).thenReturn(1);
    when(mapper.updatePreflightOutput(anyString(), anyString(), any(), any())).thenReturn(1);
    when(mapper.finishPreflight(anyString(), anyString(), any(), nullable(String.class), nullable(String.class),
        any(), any(), nullable(String.class), nullable(String.class))).thenReturn(1);
    when(mapper.insertPreflightEvent(anyString(), anyString(), anyString(), anyString())).thenReturn(1);
  }

  private ImageCollectionPlan finishedPlan() throws Exception {
    ArgumentCaptor<String> planJson = ArgumentCaptor.forClass(String.class);
    verify(mapper).finishPreflight(eq("preflight-1"), eq("READY"), planJson.capture(),
        eq("collection-v2"), anyString(), any(), any(), eq(null), eq(null));
    return json.readValue(planJson.getValue(), ImageCollectionPlan.class);
  }
}
