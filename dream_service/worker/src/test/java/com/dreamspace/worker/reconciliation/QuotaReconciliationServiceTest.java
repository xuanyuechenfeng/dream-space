package com.dreamspace.worker.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dreamspace.common.persistence.database.DatabaseEnums.GenerationExecutionKind;
import com.dreamspace.common.persistence.database.DatabaseEnums.GenerationExecutionStatus;
import com.dreamspace.common.persistence.database.DatabaseEnums.GenerationRatio;
import com.dreamspace.common.persistence.database.DatabaseEnums.GenerationResolution;
import com.dreamspace.common.persistence.database.DatabaseEnums.GenerationTaskStatus;
import com.dreamspace.common.persistence.database.DatabaseEnums.GenerationInputMode;
import com.dreamspace.common.persistence.database.DatabaseEnums.QuotaReconciliationRunStatus;
import com.dreamspace.common.persistence.generation.GenerationTaskRecord;
import com.dreamspace.common.persistence.generation.GenerationExecutionRecord;
import com.dreamspace.common.persistence.quota.QuotaAccountRecord;
import com.dreamspace.worker.persistence.reconciliation.QuotaReconciliationMapper;
import com.dreamspace.common.persistence.reconciliation.QuotaReconciliationRunRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

class QuotaReconciliationServiceTest {
  @Test void repairsAProvableMissingConsumeLedger() {
    QuotaReconciliationMapper mapper = mock(QuotaReconciliationMapper.class);
    Instant now = Instant.parse("2026-08-17T00:00:00Z");
    when(mapper.insertRun(anyString(), anyString())).thenReturn(1);
    when(mapper.listAccounts()).thenReturn(List.of(new QuotaAccountRecord("user-1", 100, 90, 0, now, now)));
    when(mapper.listTasks("user-1")).thenReturn(List.of(task(now)));
    when(mapper.findLedgerAmount("task-1", "CONSUME")).thenReturn(null);
    when(mapper.insertMissingConsume(anyString(), eq("user-1"), eq("task-1"), eq(10), eq("consume:task-1"))).thenReturn(1);
    when(mapper.sumLedger("user-1", "GRANT")).thenReturn(100);
    when(mapper.sumLedger("user-1", "RESERVE")).thenReturn(10);
    when(mapper.sumLedger("user-1", "CONSUME")).thenReturn(10);
    when(mapper.sumLedger("user-1", "RELEASE")).thenReturn(0);
    when(mapper.findRun(anyString())).thenReturn(run(now, 1, 1, 1, 1));

    QuotaReconciliationService.Summary summary = service(mapper).run(now, 3_600_000);

    assertThat(summary.status()).isEqualTo("COMPLETED");
    assertThat(summary.repairedCount()).isEqualTo(1);
    verify(mapper).finishFinding(anyString(), eq("reconciliation:missing-consume:task-1"), eq("REPAIRED"));
  }

  @Test void blocksAmbiguousAccountDrift() {
    QuotaReconciliationMapper mapper = mock(QuotaReconciliationMapper.class);
    Instant now = Instant.parse("2026-08-17T00:00:00Z");
    when(mapper.insertRun(anyString(), anyString())).thenReturn(1);
    when(mapper.listAccounts()).thenReturn(List.of(new QuotaAccountRecord("user-1", 100, 80, 0, now, now)));
    when(mapper.listTasks("user-1")).thenReturn(List.of());
    when(mapper.sumLedger("user-1", "GRANT")).thenReturn(100);
    when(mapper.sumLedger("user-1", "RESERVE")).thenReturn(0);
    when(mapper.sumLedger("user-1", "CONSUME")).thenReturn(0);
    when(mapper.sumLedger("user-1", "RELEASE")).thenReturn(0);
    when(mapper.findRun(anyString())).thenReturn(run(now, 1, 0, 1, 0));

    QuotaReconciliationService.Summary summary = service(mapper).run(now, 3_600_000);

    assertThat(summary.mismatchCount()).isEqualTo(1);
    verify(mapper).finishFinding(anyString(), eq("reconciliation:available-drift:user-1"), eq("BLOCKED"));
    verify(mapper).completeRun(anyString(), eq(1), eq(0), eq(1), eq(0));
  }

  @Test void keepsV2TasksOutOfTheLegacyTaskSettlementPath() {
    QuotaReconciliationMapper mapper = mock(QuotaReconciliationMapper.class);
    Instant now = Instant.parse("2026-08-17T00:00:00Z");
    GenerationTaskRecord task = v2Task(now, GenerationTaskStatus.SUCCEEDED, 2);
    GenerationExecutionRecord execution = execution(now, GenerationExecutionStatus.SUCCEEDED, 2, 2, 0);
    when(mapper.insertRun(anyString(), anyString())).thenReturn(1);
    when(mapper.listAccounts()).thenReturn(List.of(new QuotaAccountRecord("user-1", 10, 8, 0, now, now)));
    when(mapper.listTasks("user-1")).thenReturn(List.of(task));
    when(mapper.listV2Executions("user-1")).thenReturn(List.of(execution));
    when(mapper.listV2SlotSettlements("user-1")).thenReturn(List.of(
        new QuotaReconciliationMapper.V2SlotSettlement("task-v2", 0, "SUCCEEDED", 2, 2, 1)));
    when(mapper.sumExecutionLedger("execution-1", "RESERVE")).thenReturn(2);
    when(mapper.sumExecutionLedger("execution-1", "CONSUME")).thenReturn(2);
    when(mapper.sumLedger("user-1", "GRANT")).thenReturn(10);
    when(mapper.sumLedger("user-1", "CONSUME")).thenReturn(2);
    when(mapper.findRun(anyString())).thenReturn(run(now, 1, 1, 0, 0));

    service(mapper).run(now, 3_600_000);

    verify(mapper, never()).findLedgerAmount(eq("task-v2"), anyString());
    verify(mapper).completeRun(anyString(), eq(1), eq(1), eq(0), eq(0));
  }

  @Test void derivesReservedCreditsFromActiveExecutionRemainders() {
    QuotaReconciliationMapper mapper = mock(QuotaReconciliationMapper.class);
    Instant now = Instant.parse("2026-08-17T00:00:00Z");
    GenerationTaskRecord task = v2Task(now, GenerationTaskStatus.GENERATING, 2);
    GenerationExecutionRecord execution = execution(now, GenerationExecutionStatus.GENERATING, 4, 2, 0);
    when(mapper.insertRun(anyString(), anyString())).thenReturn(1);
    when(mapper.listAccounts()).thenReturn(List.of(new QuotaAccountRecord("user-1", 10, 6, 2, now, now)));
    when(mapper.listTasks("user-1")).thenReturn(List.of(task));
    when(mapper.listV2Executions("user-1")).thenReturn(List.of(execution));
    when(mapper.listV2SlotSettlements("user-1")).thenReturn(List.of(
        new QuotaReconciliationMapper.V2SlotSettlement("task-v2", 0, "SUCCEEDED", 2, 2, 1),
        new QuotaReconciliationMapper.V2SlotSettlement("task-v2", 1, "GENERATING", 2, 0, 0)));
    when(mapper.sumExecutionLedger("execution-1", "RESERVE")).thenReturn(4);
    when(mapper.sumExecutionLedger("execution-1", "CONSUME")).thenReturn(2);
    when(mapper.sumLedger("user-1", "GRANT")).thenReturn(10);
    when(mapper.sumLedger("user-1", "CONSUME")).thenReturn(2);
    when(mapper.findRun(anyString())).thenReturn(run(now, 1, 1, 0, 0));

    service(mapper).run(now, 3_600_000);

    verify(mapper).completeRun(anyString(), eq(1), eq(1), eq(0), eq(0));
  }

  @Test void blocksMissingSuccessfulSlotConsumeAndTaskConsumedDrift() {
    QuotaReconciliationMapper mapper = mock(QuotaReconciliationMapper.class);
    Instant now = Instant.parse("2026-08-17T00:00:00Z");
    GenerationTaskRecord task = v2Task(now, GenerationTaskStatus.PARTIALLY_SUCCEEDED, 0);
    when(mapper.insertRun(anyString(), anyString())).thenReturn(1);
    when(mapper.listAccounts()).thenReturn(List.of(new QuotaAccountRecord("user-1", 10, 10, 0, now, now)));
    when(mapper.listTasks("user-1")).thenReturn(List.of(task));
    when(mapper.listV2Executions("user-1")).thenReturn(List.of());
    when(mapper.listV2SlotSettlements("user-1")).thenReturn(List.of(
        new QuotaReconciliationMapper.V2SlotSettlement("task-v2", 0, "SUCCEEDED", 2, 0, 0),
        new QuotaReconciliationMapper.V2SlotSettlement("task-v2", 1, "FAILED", 2, 0, 0)));
    when(mapper.sumLedger("user-1", "GRANT")).thenReturn(10);
    when(mapper.sumLedger("user-1", "CONSUME")).thenReturn(0);
    when(mapper.findRun(anyString())).thenReturn(run(now, 1, 1, 2, 0));

    service(mapper).run(now, 3_600_000);

    verify(mapper).finishFinding(anyString(), eq("reconciliation:v2-slot-settlement:task-v2:0"), eq("BLOCKED"));
    verify(mapper).finishFinding(anyString(), eq("reconciliation:v2-task-consumed:task-v2"), eq("BLOCKED"));
    verify(mapper).completeRun(anyString(), eq(1), eq(1), eq(2), eq(0));
  }

  private static QuotaReconciliationService service(QuotaReconciliationMapper mapper) {
    PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
    return new QuotaReconciliationService(mapper, new ObjectMapper(), transactionManager);
  }

  private static GenerationTaskRecord task(Instant now) {
    return new GenerationTaskRecord("task-1", "session-1", "user-1", GenerationTaskStatus.SUCCEEDED,
        "prompt", GenerationInputMode.AUTO, null, "model", GenerationRatio.RATIO_1_1, GenerationResolution.K2, 2048, 2048, 1,
        10, 10, "idempotency", "1-0", 1, "task-1:1", null, null, null, null,
        now, now, now, now);
  }

  private static GenerationTaskRecord v2Task(Instant now, GenerationTaskStatus status, int consumedCost) {
    return new GenerationTaskRecord("task-v2", "session-1", "user-1", status,
        "prompt", GenerationInputMode.AUTO, null, "model", GenerationRatio.RATIO_1_1,
        GenerationResolution.K2, 2048, 2048, 2, 2, 4, "idempotency", null, 0, null,
        null, null, null, null, null, null, now, now, "rule-1", 1, (short) 2,
        "preflight-1", consumedCost);
  }

  private static GenerationExecutionRecord execution(Instant now, GenerationExecutionStatus status,
      int reserved, int consumed, int released) {
    return new GenerationExecutionRecord("execution-1", "task-v2", GenerationExecutionKind.INITIAL,
        status, "execution-key", new ObjectMapper().createArrayNode().add(0).add(1), reserved,
        consumed, released, "rule-1", 1, null, 1, null, now, now);
  }

  private static QuotaReconciliationRunRecord run(Instant now, int users, int tasks,
      int mismatches, int repaired) {
    return new QuotaReconciliationRunRecord("run-1", "quota:1", QuotaReconciliationRunStatus.COMPLETED,
        now, now, users, tasks, mismatches, repaired, null, now);
  }
}
