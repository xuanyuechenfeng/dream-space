package com.dreamspace.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dreamspace.api.persistence.upload.ReferenceUploadMapper;
import com.dreamspace.common.persistence.config.DreamSpaceProperties;
import com.dreamspace.common.persistence.database.DatabaseEnums.GenerationExecutionKind;
import com.dreamspace.common.persistence.database.DatabaseEnums.GenerationExecutionStatus;
import com.dreamspace.common.persistence.database.DatabaseEnums.GenerationInputMode;
import com.dreamspace.common.persistence.database.DatabaseEnums.GenerationRatio;
import com.dreamspace.common.persistence.database.DatabaseEnums.GenerationResolution;
import com.dreamspace.common.persistence.database.DatabaseEnums.GenerationSlotStatus;
import com.dreamspace.common.persistence.database.DatabaseEnums.GenerationTaskStatus;
import com.dreamspace.common.persistence.generation.GenerationExecutionRecord;
import com.dreamspace.common.persistence.generation.GenerationMapper;
import com.dreamspace.common.persistence.generation.GenerationResultSlotRecord;
import com.dreamspace.common.persistence.generation.GenerationSessionRecord;
import com.dreamspace.common.persistence.generation.GenerationTaskRecord;
import com.dreamspace.common.persistence.generation.GenerationV2Mapper;
import com.dreamspace.common.persistence.quota.QuotaAccountRecord;
import com.dreamspace.common.persistence.quota.QuotaTransactionService;
import com.dreamspace.common.persistence.storage.ObjectStorage;
import com.dreamspace.common.persistence.storage.ObjectStorageFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

class GenerationV2LifecycleServiceTest {
  private static final Instant NOW = Instant.parse("2026-09-01T00:00:00Z");

  @Test
  void continuationResetsAndReservesOnlyMissingSlotsInLockOrder() {
    GenerationMapper generation = mock(GenerationMapper.class);
    GenerationV2Mapper v2 = mock(GenerationV2Mapper.class);
    QuotaTransactionService quota = mock(QuotaTransactionService.class);
    GenerationQueuePublisher queue = mock(GenerationQueuePublisher.class);
    GenerationTaskRecord task = task(GenerationTaskStatus.PARTIALLY_SUCCEEDED, 2);
    List<GenerationResultSlotRecord> slots = List.of(
        slot(0, GenerationSlotStatus.SUCCEEDED, "result-0"),
        slot(1, GenerationSlotStatus.FAILED, null),
        slot(2, GenerationSlotStatus.WAITING, null),
        slot(3, GenerationSlotStatus.CANCELLED, null));
    givenProjection(generation, v2, quota, task, slots);
    when(v2.lockTask("user-1", "task-1")).thenReturn(task);
    when(v2.insertExecution(anyString(), eq("task-1"), eq("CONTINUATION"), eq("continue-key-1"),
        eq("[1,2,3]"), eq(6), eq("rule-1"), eq(1))).thenReturn(1);
    when(v2.resetMissingSlots(eq("task-1"), anyString())).thenReturn(3);
    when(quota.reserve(eq("user-1"), eq("task-1"), eq(6), anyString(), eq(100),
        eq("rule-1"), eq(1), anyString())).thenReturn(true);
    when(v2.queueTaskForContinuation("task-1")).thenReturn(1);
    when(queue.publishExecution(eq("task-1"), anyString(), anyString())).thenReturn(true);

    GenerationService.SubmitResponse response = service(generation, v2, quota, queue)
        .continueMissing("user-1", "task-1", "continue-key-1");

    assertThat(response.replayed()).isFalse();
    verify(v2).insertExecution(anyString(), eq("task-1"), eq("CONTINUATION"),
        eq("continue-key-1"), eq("[1,2,3]"), eq(6), eq("rule-1"), eq(1));
    InOrder order = inOrder(v2, quota);
    order.verify(v2).lockTask("user-1", "task-1");
    order.verify(v2).insertExecution(anyString(), eq("task-1"), eq("CONTINUATION"),
        eq("continue-key-1"), eq("[1,2,3]"), eq(6), eq("rule-1"), eq(1));
    order.verify(v2).resetMissingSlots(eq("task-1"), anyString());
    order.verify(quota).reserve(eq("user-1"), eq("task-1"), eq(6), anyString(), eq(100),
        eq("rule-1"), eq(1), anyString());
  }

  @Test
  void repeatedContinuationReplaysWithoutAnotherExecutionOrReserve() {
    GenerationMapper generation = mock(GenerationMapper.class);
    GenerationV2Mapper v2 = mock(GenerationV2Mapper.class);
    QuotaTransactionService quota = mock(QuotaTransactionService.class);
    GenerationQueuePublisher queue = mock(GenerationQueuePublisher.class);
    GenerationTaskRecord task = task(GenerationTaskStatus.PARTIALLY_SUCCEEDED, 2);
    givenProjection(generation, v2, quota, task, List.of(
        slot(0, GenerationSlotStatus.SUCCEEDED, "result-0"),
        slot(1, GenerationSlotStatus.FAILED, null)));
    when(v2.findExecutionByIdempotency("task-1", "continue-key-1"))
        .thenReturn(execution(GenerationExecutionStatus.QUEUED, 2, 0, 0));

    GenerationService.SubmitResponse response = service(generation, v2, quota, queue)
        .continueMissing("user-1", "task-1", "continue-key-1");

    assertThat(response.replayed()).isTrue();
    verify(v2, never()).insertExecution(anyString(), anyString(), anyString(), anyString(),
        anyString(), eq(2), anyString(), eq(1));
    verify(quota, never()).reserve(anyString(), anyString(), eq(2), anyString(), eq(100),
        anyString(), eq(1), anyString());
    verify(queue, never()).publishExecution(anyString(), anyString(), anyString());
  }

  @Test
  void concurrentContinuationRechecksIdempotencyAfterTaskLock() {
    GenerationMapper generation = mock(GenerationMapper.class);
    GenerationV2Mapper v2 = mock(GenerationV2Mapper.class);
    QuotaTransactionService quota = mock(QuotaTransactionService.class);
    GenerationQueuePublisher queue = mock(GenerationQueuePublisher.class);
    GenerationTaskRecord task = task(GenerationTaskStatus.PARTIALLY_SUCCEEDED, 2);
    givenProjection(generation, v2, quota, task, List.of(
        slot(0, GenerationSlotStatus.SUCCEEDED, "result-0"),
        slot(1, GenerationSlotStatus.FAILED, null)));
    GenerationExecutionRecord concurrent = execution(GenerationExecutionStatus.QUEUED, 2, 0, 0);
    when(v2.findExecutionByIdempotency("task-1", "continue-key-1"))
        .thenReturn(null, concurrent);
    when(v2.lockTask("user-1", "task-1")).thenReturn(task);

    GenerationService.SubmitResponse response = service(generation, v2, quota, queue)
        .continueMissing("user-1", "task-1", "continue-key-1");

    assertThat(response.replayed()).isTrue();
    verify(v2).lockTask("user-1", "task-1");
    verify(v2, never()).insertExecution(anyString(), anyString(), anyString(), anyString(),
        anyString(), eq(2), anyString(), eq(1));
    verify(quota, never()).reserve(anyString(), anyString(), eq(2), anyString(), eq(100),
        anyString(), eq(1), anyString());
    verify(queue, never()).publishExecution(anyString(), anyString(), anyString());
  }

  @Test
  void cancellationKeepsSuccessfulSlotsAndReleasesOnlyExecutionRemainder() {
    GenerationMapper generation = mock(GenerationMapper.class);
    GenerationV2Mapper v2 = mock(GenerationV2Mapper.class);
    QuotaTransactionService quota = mock(QuotaTransactionService.class);
    GenerationQueuePublisher queue = mock(GenerationQueuePublisher.class);
    GenerationTaskRecord running = task(GenerationTaskStatus.GENERATING, 2);
    GenerationTaskRecord cancelled = task(GenerationTaskStatus.CANCELLED, 2);
    List<GenerationResultSlotRecord> before = List.of(
        slot(0, GenerationSlotStatus.SUCCEEDED, "result-0"),
        slot(1, GenerationSlotStatus.GENERATING, null),
        slot(2, GenerationSlotStatus.WAITING, null),
        slot(3, GenerationSlotStatus.WAITING, null));
    List<GenerationResultSlotRecord> after = List.of(
        slot(0, GenerationSlotStatus.SUCCEEDED, "result-0"),
        slot(1, GenerationSlotStatus.CANCELLED, null),
        slot(2, GenerationSlotStatus.CANCELLED, null),
        slot(3, GenerationSlotStatus.CANCELLED, null));
    when(generation.findTask("task-1")).thenReturn(running, cancelled);
    when(generation.listResults("task-1")).thenReturn(List.of());
    when(generation.listIterations("task-1")).thenReturn(List.of());
    when(generation.listResultSlots("task-1")).thenReturn(after);
    when(v2.lockTask("user-1", "task-1")).thenReturn(running);
    when(v2.findActiveExecution("task-1")).thenReturn(execution(GenerationExecutionStatus.GENERATING, 8, 2, 0));
    when(v2.lockExecution("execution-1")).thenReturn(execution(GenerationExecutionStatus.GENERATING, 8, 2, 0));
    when(v2.cancelMissingSlots("task-1")).thenReturn(3);
    when(quota.settle("user-1", "task-1", 6, "RELEASE", "release:execution-1", "execution-1", null))
        .thenReturn(true);
    when(v2.finishExecution("execution-1", 6, "CANCELLED", "TASK_CANCELLED")).thenReturn(1);
    when(v2.cancelTaskV2("user-1", "task-1")).thenReturn(1);

    GenerationService.TaskView result = service(generation, v2, quota, queue)
        .cancel("user-1", "task-1");

    assertThat(result.slots()).extracting(GenerationService.SlotView::status)
        .containsExactly("succeeded", "cancelled", "cancelled", "cancelled");
    assertThat(before.getFirst().status()).isEqualTo(GenerationSlotStatus.SUCCEEDED);
    InOrder order = inOrder(v2, quota);
    order.verify(v2).lockTask("user-1", "task-1");
    order.verify(v2).lockExecution("execution-1");
    order.verify(v2).cancelMissingSlots("task-1");
    order.verify(quota).settle("user-1", "task-1", 6, "RELEASE",
        "release:execution-1", "execution-1", null);
  }

  private static GenerationService service(GenerationMapper generation, GenerationV2Mapper v2,
      QuotaTransactionService quota, GenerationQueuePublisher queue) {
    DreamSpaceProperties properties = new DreamSpaceProperties(null, null, null, null, null);
    return new GenerationService(generation, quota, queue,
        new ObjectStorageFactory(mock(ObjectStorage.class)), properties, new ObjectMapper(),
        new TestTransactionManager(), mock(ReferenceUploadMapper.class), null, v2);
  }

  private static void givenProjection(GenerationMapper generation, GenerationV2Mapper v2,
      QuotaTransactionService quota, GenerationTaskRecord task, List<GenerationResultSlotRecord> slots) {
    when(generation.findTask("task-1")).thenReturn(task);
    when(generation.findSession("user-1", "session-1")).thenReturn(
        new GenerationSessionRecord("session-1", "user-1", "Session", new ObjectMapper().createObjectNode(), NOW, NOW));
    when(generation.listTasks("session-1")).thenReturn(List.of(task));
    when(generation.listResults("task-1")).thenReturn(List.of());
    when(generation.listIterations("task-1")).thenReturn(List.of());
    when(generation.listResultSlots("task-1")).thenReturn(slots);
    when(v2.listResultSlots("task-1")).thenReturn(slots);
    when(quota.ensureAndRead("user-1", 100)).thenReturn(new QuotaAccountRecord("user-1", 100, 92, 0, NOW, NOW));
  }

  private static GenerationTaskRecord task(GenerationTaskStatus status, int consumedCost) {
    return new GenerationTaskRecord("task-1", "session-1", "user-1", status, "prompt",
        GenerationInputMode.AUTO, new ObjectMapper().createArrayNode(), "image-4.7",
        GenerationRatio.RATIO_1_1, GenerationResolution.K2, 2048, 2048, 4, 2, 8,
        "create-key", null, 0, null, null, null, null, null, null,
        status == GenerationTaskStatus.GENERATING ? null : NOW, NOW, NOW, "rule-1", 1,
        (short) 2, "preflight-1", consumedCost);
  }

  private static GenerationResultSlotRecord slot(int index, GenerationSlotStatus status, String resultId) {
    return new GenerationResultSlotRecord("task-1", index, "slot " + index, "ROLE", "intent",
        "hash", status, resultId, status == GenerationSlotStatus.GENERATING ? "execution-1" : null,
        1, null, null, null, status == GenerationSlotStatus.WAITING ? null : NOW, NOW, NOW);
  }

  private static GenerationExecutionRecord execution(GenerationExecutionStatus status,
      int reserved, int consumed, int released) {
    return new GenerationExecutionRecord("execution-1", "task-1", GenerationExecutionKind.CONTINUATION,
        status, "continue-key-1", new ObjectMapper().createArrayNode().add(1).add(2).add(3),
        reserved, consumed, released, "rule-1", 1, null, 1, null, NOW, NOW);
  }

  private static final class TestTransactionManager extends AbstractPlatformTransactionManager {
    @Override protected Object doGetTransaction() { return new Object(); }
    @Override protected void doBegin(Object transaction, TransactionDefinition definition) {}
    @Override protected void doCommit(DefaultTransactionStatus status) {}
    @Override protected void doRollback(DefaultTransactionStatus status) {}
  }
}
