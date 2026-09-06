package com.dreamspace.worker.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dreamspace.common.persistence.database.DatabaseEnums.GenerationExecutionKind;
import com.dreamspace.common.persistence.database.DatabaseEnums.GenerationExecutionStatus;
import com.dreamspace.common.persistence.database.DatabaseEnums.GenerationInputMode;
import com.dreamspace.common.persistence.database.DatabaseEnums.GenerationRatio;
import com.dreamspace.common.persistence.database.DatabaseEnums.GenerationResolution;
import com.dreamspace.common.persistence.database.DatabaseEnums.GenerationSlotStatus;
import com.dreamspace.common.persistence.database.DatabaseEnums.GenerationTaskStatus;
import com.dreamspace.common.persistence.database.DatabaseEnums.ModerationStatus;
import com.dreamspace.common.persistence.generation.GenerationExecutionRecord;
import com.dreamspace.common.persistence.generation.GenerationMapper;
import com.dreamspace.common.persistence.generation.GenerationResultSlotRecord;
import com.dreamspace.common.persistence.generation.GenerationTaskRecord;
import com.dreamspace.common.persistence.generation.GenerationV2Mapper;
import com.dreamspace.common.persistence.quota.QuotaTransactionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

class GenerationV2SettlementServiceTest {
  private static final String TASK_ID = "task-1";
  private static final String EXECUTION_ID = "execution-1";

  private final GenerationMapper generation = mock(GenerationMapper.class);
  private final GenerationV2Mapper v2 = mock(GenerationV2Mapper.class);
  private final QuotaTransactionService quota = mock(QuotaTransactionService.class);
  private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
  private final GenerationV2SettlementService settlement = new GenerationV2SettlementService(
      generation, v2, quota, transactionManager, new ObjectMapper());

  @BeforeEach
  void configureTransactions() {
    when(transactionManager.getTransaction(any())).thenAnswer(ignored -> new SimpleTransactionStatus());
  }

  @Test
  void publishesOneSlotAndConsumesExactlyItsFrozenUnitCost() {
    GenerationTaskRecord task = task(0);
    givenOwnership(task, execution(4, 0, 0, GenerationExecutionStatus.GENERATING),
        slot(0, GenerationSlotStatus.GENERATING, EXECUTION_ID));
    StoredGenerationResult result = stored(0);
    when(generation.insertResult(anyString(), anyString(), anyInt(), anyString(), anyString(), anyString(),
        anyString(), anyInt(), anyInt(), anyString(), anyInt(), anyInt(), anyInt(), anyInt(), eq(true)))
        .thenReturn(1);
    when(quota.settle("user-1", TASK_ID, 2, "CONSUME", "consume:" + TASK_ID + ":slot:0",
        EXECUTION_ID, 0)).thenReturn(true);
    when(v2.addExecutionConsumed(EXECUTION_ID, 2)).thenReturn(1);
    when(v2.addTaskConsumed(TASK_ID, 2)).thenReturn(1);
    when(v2.finishResultSlot(TASK_ID, 0, "SUCCEEDED", result.id(), null, null, EXECUTION_ID))
        .thenReturn(1);

    assertThat(settlement.publishSlot(EXECUTION_ID, TASK_ID, 0, result, 2)).isTrue();

    verify(quota).settle("user-1", TASK_ID, 2, "CONSUME",
        "consume:" + TASK_ID + ":slot:0", EXECUTION_ID, 0);
    verify(v2).addExecutionConsumed(EXECUTION_ID, 2);
    verify(v2).addTaskConsumed(TASK_ID, 2);
  }

  @Test
  void rejectsLatePublishAfterExecutionWasCancelledWithoutConsuming() {
    GenerationTaskRecord task = task(0);
    givenOwnership(task, execution(2, 0, 2, GenerationExecutionStatus.CANCELLED),
        slot(0, GenerationSlotStatus.CANCELLED, EXECUTION_ID));

    assertThat(settlement.publishSlot(EXECUTION_ID, TASK_ID, 0, stored(0), 2)).isFalse();

    verify(generation, never()).insertResult(anyString(), anyString(), anyInt(), anyString(), anyString(),
        anyString(), anyString(), anyInt(), anyInt(), anyString(), anyInt(), anyInt(), anyInt(), anyInt(), anyBoolean());
    verify(quota, never()).settle(anyString(), anyString(), anyInt(), anyString(), anyString(), anyString(), any());
  }

  @Test
  void duplicatePublishOfSucceededSlotDoesNotConsumeAgain() {
    GenerationTaskRecord task = task(2, 1);
    givenOwnership(task, execution(2, 2, 0, GenerationExecutionStatus.GENERATING),
        slot(0, GenerationSlotStatus.SUCCEEDED, EXECUTION_ID));

    assertThat(settlement.publishSlot(EXECUTION_ID, TASK_ID, 0, stored(0), 2)).isFalse();

    verify(generation, never()).insertResult(anyString(), anyString(), anyInt(), anyString(), anyString(),
        anyString(), anyString(), anyInt(), anyInt(), anyString(), anyInt(), anyInt(), anyInt(), anyInt(), anyBoolean());
    verify(quota, never()).settle(anyString(), anyString(), anyInt(), anyString(), anyString(), anyString(), any());
    verify(v2, never()).addExecutionConsumed(anyString(), anyInt());
  }

  @Test
  void refusesToCompleteExecutionWhileAnyReserveRemainsUnsettled() {
    GenerationTaskRecord task = task(2, 2);
    when(generation.findTask(TASK_ID)).thenReturn(task);
    when(v2.lockTask("user-1", TASK_ID)).thenReturn(task);
    when(v2.lockExecution(EXECUTION_ID)).thenReturn(
        execution(4, 2, 0, GenerationExecutionStatus.GENERATING));
    when(v2.countSucceededSlots(TASK_ID)).thenReturn(1);
    when(v2.countIncompleteSlots(TASK_ID)).thenReturn(1);

    assertThat(settlement.completeExecution(EXECUTION_ID, TASK_ID, 2)).isFalse();

    verify(v2, never()).finishExecution(anyString(), anyInt(), anyString(), any());
    verify(v2, never()).setTaskV2Status(anyString(), anyString(), any(), any(), eq(true));
  }

  @Test
  void completesExecutionOnlyAfterEveryReservedSlotWasConsumed() {
    GenerationTaskRecord task = task(4, 2);
    when(generation.findTask(TASK_ID)).thenReturn(task);
    when(v2.lockTask("user-1", TASK_ID)).thenReturn(task);
    when(v2.lockExecution(EXECUTION_ID)).thenReturn(
        execution(4, 4, 0, GenerationExecutionStatus.GENERATING));
    when(v2.countSucceededSlots(TASK_ID)).thenReturn(2);
    when(v2.countIncompleteSlots(TASK_ID)).thenReturn(0);
    when(v2.finishExecution(EXECUTION_ID, 0, "SUCCEEDED", null)).thenReturn(1);
    when(v2.setTaskV2Status(TASK_ID, "SUCCEEDED", null, null, true)).thenReturn(1);

    assertThat(settlement.completeExecution(EXECUTION_ID, TASK_ID, 2)).isTrue();

    verify(v2).finishExecution(EXECUTION_ID, 0, "SUCCEEDED", null);
    verify(v2).setTaskV2Status(TASK_ID, "SUCCEEDED", null, null, true);
  }

  @Test
  void aggregatesMixedSlotsAndReleasesOnlyUnconsumedReserve() {
    GenerationTaskRecord task = task(4, 3);
    when(generation.findTask(TASK_ID)).thenReturn(task);
    when(v2.lockTask("user-1", TASK_ID)).thenReturn(task);
    when(v2.lockExecution(EXECUTION_ID)).thenReturn(
        execution(6, 4, 0, GenerationExecutionStatus.GENERATING));
    when(v2.countSucceededSlots(TASK_ID)).thenReturn(2);
    when(v2.countFailedSlots(TASK_ID)).thenReturn(1);
    when(v2.countIncompleteSlots(TASK_ID)).thenReturn(1);
    when(quota.settle("user-1", TASK_ID, 2, "RELEASE", "release:" + EXECUTION_ID,
        EXECUTION_ID, null)).thenReturn(true);
    when(v2.finishExecution(EXECUTION_ID, 2, "FAILED", "SLOT_GENERATION_FAILED")).thenReturn(1);
    when(v2.setTaskV2Status(TASK_ID, "PARTIALLY_SUCCEEDED", "SLOT_GENERATION_FAILED",
        "部分图片生成失败", true)).thenReturn(1);

    assertThat(settlement.completeExecution(EXECUTION_ID, TASK_ID, 3)).isTrue();

    verify(quota).settle("user-1", TASK_ID, 2, "RELEASE", "release:" + EXECUTION_ID,
        EXECUTION_ID, null);
    verify(v2).finishExecution(EXECUTION_ID, 2, "FAILED", "SLOT_GENERATION_FAILED");
    verify(v2).setTaskV2Status(TASK_ID, "PARTIALLY_SUCCEEDED", "SLOT_GENERATION_FAILED",
        "部分图片生成失败", true);
  }

  @Test
  void aggregatesAllFailedSlotsAndReleasesTheFullReserve() {
    GenerationTaskRecord task = task(0, 2);
    when(generation.findTask(TASK_ID)).thenReturn(task);
    when(v2.lockTask("user-1", TASK_ID)).thenReturn(task);
    when(v2.lockExecution(EXECUTION_ID)).thenReturn(
        execution(4, 0, 0, GenerationExecutionStatus.GENERATING));
    when(v2.countFailedSlots(TASK_ID)).thenReturn(2);
    when(v2.countIncompleteSlots(TASK_ID)).thenReturn(2);
    when(quota.settle("user-1", TASK_ID, 4, "RELEASE", "release:" + EXECUTION_ID,
        EXECUTION_ID, null)).thenReturn(true);
    when(v2.finishExecution(EXECUTION_ID, 4, "FAILED", "SLOT_GENERATION_FAILED")).thenReturn(1);
    when(v2.setTaskV2Status(TASK_ID, "FAILED", "SLOT_GENERATION_FAILED",
        "部分图片生成失败", true)).thenReturn(1);

    assertThat(settlement.completeExecution(EXECUTION_ID, TASK_ID, 2)).isTrue();

    verify(quota).settle("user-1", TASK_ID, 4, "RELEASE", "release:" + EXECUTION_ID,
        EXECUTION_ID, null);
    verify(v2).setTaskV2Status(TASK_ID, "FAILED", "SLOT_GENERATION_FAILED",
        "部分图片生成失败", true);
  }

  @Test
  void recordsOneSlotFailureWithoutEndingSharedExecutionOrReleasingQuota() {
    GenerationTaskRecord task = task(0, 2);
    givenOwnership(task, execution(4, 0, 0, GenerationExecutionStatus.GENERATING),
        slot(0, GenerationSlotStatus.GENERATING, EXECUTION_ID));
    when(v2.finishResultSlot(TASK_ID, 0, "FAILED", null, "PROVIDER_REJECTED", "failed", EXECUTION_ID))
        .thenReturn(1);

    assertThat(settlement.recordSlotFailure(
        EXECUTION_ID, TASK_ID, 0, "PROVIDER_REJECTED", "failed")).isTrue();

    verify(v2).finishResultSlot(TASK_ID, 0, "FAILED", null, "PROVIDER_REJECTED", "failed", EXECUTION_ID);
    verify(v2, never()).finishExecution(anyString(), anyInt(), anyString(), any());
    verify(quota, never()).settle(anyString(), anyString(), anyInt(), anyString(), anyString(), anyString(), any());
  }

  @Test
  void failedSlotReleasesOnlyTheExecutionRemainderAndPreservesConsumedCost() {
    GenerationTaskRecord task = task(2);
    givenOwnership(task, execution(8, 2, 1, GenerationExecutionStatus.GENERATING),
        slot(1, GenerationSlotStatus.GENERATING, EXECUTION_ID));
    when(v2.finishResultSlot(TASK_ID, 1, "FAILED", null, "PROVIDER_REJECTED", "failed", EXECUTION_ID))
        .thenReturn(1);
    when(quota.settle("user-1", TASK_ID, 5, "RELEASE", "release:" + EXECUTION_ID,
        EXECUTION_ID, null)).thenReturn(true);
    when(v2.finishExecution(EXECUTION_ID, 5, "FAILED", "PROVIDER_REJECTED")).thenReturn(1);
    when(v2.setTaskV2Status(TASK_ID, "PARTIALLY_SUCCEEDED", "PROVIDER_REJECTED", "failed", true))
        .thenReturn(1);

    assertThat(settlement.failExecution(
        EXECUTION_ID, TASK_ID, 1, "PROVIDER_REJECTED", "failed")).isTrue();

    verify(quota).settle("user-1", TASK_ID, 5, "RELEASE", "release:" + EXECUTION_ID,
        EXECUTION_ID, null);
    verify(v2).finishExecution(EXECUTION_ID, 5, "FAILED", "PROVIDER_REJECTED");
    verify(v2).setTaskV2Status(
        TASK_ID, "PARTIALLY_SUCCEEDED", "PROVIDER_REJECTED", "failed", true);
    verify(quota, never()).settle(anyString(), anyString(), anyInt(), eq("CONSUME"),
        anyString(), anyString(), any());
  }

  @Test
  void firstSlotFailureReleasesTheFullExecutionReserve() {
    GenerationTaskRecord task = task(0);
    givenOwnership(task, execution(4, 0, 0, GenerationExecutionStatus.GENERATING),
        slot(0, GenerationSlotStatus.GENERATING, EXECUTION_ID));
    when(v2.finishResultSlot(TASK_ID, 0, "FAILED", null, "PROVIDER_REJECTED", "failed", EXECUTION_ID))
        .thenReturn(1);
    when(quota.settle("user-1", TASK_ID, 4, "RELEASE", "release:" + EXECUTION_ID,
        EXECUTION_ID, null)).thenReturn(true);
    when(v2.finishExecution(EXECUTION_ID, 4, "FAILED", "PROVIDER_REJECTED")).thenReturn(1);
    when(v2.setTaskV2Status(TASK_ID, "FAILED", "PROVIDER_REJECTED", "failed", true)).thenReturn(1);

    assertThat(settlement.failExecution(
        EXECUTION_ID, TASK_ID, 0, "PROVIDER_REJECTED", "failed")).isTrue();

    verify(quota).settle("user-1", TASK_ID, 4, "RELEASE", "release:" + EXECUTION_ID,
        EXECUTION_ID, null);
    verify(v2).setTaskV2Status(TASK_ID, "FAILED", "PROVIDER_REJECTED", "failed", true);
  }

  @Test
  void exhaustedQueuedExecutionReleasesItsReserveWithoutUsingLegacySettlement() {
    GenerationTaskRecord task = new GenerationTaskRecord(TASK_ID, "session-1", "user-1",
        GenerationTaskStatus.QUEUED, "prompt", GenerationInputMode.TEXT_TO_IMAGE,
        new ObjectMapper().createArrayNode(), "image-model", GenerationRatio.RATIO_1_1,
        GenerationResolution.K2, 1024, 1024, 2, 2, 4, "task-key", null, 0, null,
        null, null, ModerationStatus.PENDING, ModerationStatus.PENDING, null, null,
        null, null, "rule-1", 1, (short) 2, "preflight-1", 0);
    givenOwnership(task, execution(4, 0, 0, GenerationExecutionStatus.QUEUED),
        slot(0, GenerationSlotStatus.WAITING, EXECUTION_ID));
    when(quota.settle("user-1", TASK_ID, 4, "RELEASE", "release:" + EXECUTION_ID,
        EXECUTION_ID, null)).thenReturn(true);
    when(v2.finishExecution(EXECUTION_ID, 4, "FAILED", "QUEUE_ATTEMPTS_EXHAUSTED")).thenReturn(1);
    when(v2.setTaskV2Status(TASK_ID, "FAILED", "QUEUE_ATTEMPTS_EXHAUSTED", "failed", true))
        .thenReturn(1);

    assertThat(settlement.failExecution(EXECUTION_ID, TASK_ID, -1,
        "QUEUE_ATTEMPTS_EXHAUSTED", "failed")).isTrue();

    verify(quota).settle("user-1", TASK_ID, 4, "RELEASE", "release:" + EXECUTION_ID,
        EXECUTION_ID, null);
    verify(quota, never()).settle(anyString(), anyString(), anyInt(), eq("CONSUME"),
        anyString(), anyString(), any());
  }

  @Test
  void rejectsCorruptExecutionAccountingInsteadOfHidingNegativeRemainder() {
    GenerationTaskRecord task = task(2);
    givenOwnership(task, execution(2, 3, 0, GenerationExecutionStatus.GENERATING),
        slot(1, GenerationSlotStatus.GENERATING, EXECUTION_ID));
    when(v2.finishResultSlot(TASK_ID, 1, "FAILED", null, "FAILED", "failed", EXECUTION_ID))
        .thenReturn(1);

    assertThatThrownBy(() -> settlement.failExecution(
        EXECUTION_ID, TASK_ID, 1, "FAILED", "failed"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("settlement invariant");

    verify(quota, never()).settle(anyString(), anyString(), anyInt(), anyString(), anyString(), anyString(), any());
    verify(v2, never()).finishExecution(anyString(), anyInt(), anyString(), any());
  }

  private void givenOwnership(GenerationTaskRecord task, GenerationExecutionRecord execution,
      GenerationResultSlotRecord slot) {
    when(generation.findTask(TASK_ID)).thenReturn(task);
    when(v2.lockTask("user-1", TASK_ID)).thenReturn(task);
    when(v2.lockExecution(EXECUTION_ID)).thenReturn(execution);
    when(v2.lockResultSlot(TASK_ID, slot.slotIndex())).thenReturn(slot);
  }

  private GenerationExecutionRecord execution(int reserved, int consumed, int released,
      GenerationExecutionStatus status) {
    return new GenerationExecutionRecord(EXECUTION_ID, TASK_ID, GenerationExecutionKind.INITIAL,
        status, "execution-key", new ObjectMapper().valueToTree(List.of(0, 1)),
        reserved, consumed, released, "rule-1", 1, null, 1, null, null, null);
  }

  private GenerationResultSlotRecord slot(int index, GenerationSlotStatus status,
      String activeExecutionId) {
    return new GenerationResultSlotRecord(TASK_ID, index, "slot " + index, "VARIATION",
        "intent " + index, "hash-" + index, status, null, activeExecutionId,
        1, null, null, null, null, null, null);
  }

  private GenerationTaskRecord task(int consumedCost) {
    return task(consumedCost, 4);
  }

  private GenerationTaskRecord task(int consumedCost, int imageCount) {
    return new GenerationTaskRecord(TASK_ID, "session-1", "user-1", GenerationTaskStatus.GENERATING,
        "prompt", GenerationInputMode.TEXT_TO_IMAGE, new ObjectMapper().createArrayNode(), "image-model",
        GenerationRatio.RATIO_1_1, GenerationResolution.K2, 1024, 1024, imageCount, 2, imageCount * 2,
        "task-key", null, 1, EXECUTION_ID + ":1", null, null,
        ModerationStatus.APPROVED, ModerationStatus.PENDING, null, null, null, null,
        "rule-1", 1, (short) 2, "preflight-1", consumedCost);
  }

  private static StoredGenerationResult stored(int index) {
    return new StoredGenerationResult("result-" + index, index, "/result/" + index,
        "object-" + index, "thumbnail-" + index, "checksum-" + index,
        1024, 1024, "image/png", 10, 480, 480, 5);
  }
}
