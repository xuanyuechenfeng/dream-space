package com.dreamspace.worker.reconciliation;

import com.dreamspace.common.persistence.database.DatabaseEnums.GenerationTaskStatus;
import com.dreamspace.common.persistence.generation.GenerationTaskRecord;
import com.dreamspace.common.persistence.generation.GenerationExecutionRecord;
import com.dreamspace.common.persistence.generation.GenerationV2Mapper;
import com.dreamspace.common.persistence.quota.QuotaAccountRecord;
import com.dreamspace.common.persistence.quota.QuotaTransactionService;
import com.dreamspace.worker.persistence.reconciliation.QuotaReconciliationMapper;
import com.dreamspace.common.persistence.reconciliation.QuotaReconciliationRunRecord;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class QuotaReconciliationService {
  private static final Logger log = LoggerFactory.getLogger(QuotaReconciliationService.class);
  private final QuotaReconciliationMapper mapper;
  private final ObjectMapper json;
  private final TransactionTemplate transactions;
  private final GenerationV2Mapper generation;
  private final QuotaTransactionService quota;

  public QuotaReconciliationService(QuotaReconciliationMapper mapper, ObjectMapper json,
      PlatformTransactionManager transactionManager) {
    this(mapper, json, transactionManager, null, null);
  }

  @org.springframework.beans.factory.annotation.Autowired
  public QuotaReconciliationService(QuotaReconciliationMapper mapper, ObjectMapper json,
      PlatformTransactionManager transactionManager, GenerationV2Mapper generation,
      QuotaTransactionService quota) {
    this.mapper = mapper;
    this.json = json;
    this.transactions = new TransactionTemplate(transactionManager);
    this.generation = generation;
    this.quota = quota;
  }

  public Summary run(Instant now, long windowMillis) {
    if (windowMillis < 1) throw new IllegalArgumentException("windowMillis must be positive");
    String windowKey = "quota:" + now.toEpochMilli() / windowMillis;
    String candidateId = UUID.randomUUID().toString();
    if (mapper.insertRun(candidateId, windowKey) != 1) return summary(mapper.findRun(windowKey));
    int scannedTasks = 0;
    int mismatches = 0;
    int repaired = 0;
    List<QuotaAccountRecord> accounts = List.of();
    try {
      int staleRepairs = generation == null || quota == null ? 0 : recoverStaleExecutions(now, windowMillis);
      mismatches += staleRepairs;
      repaired += staleRepairs;
      accounts = mapper.listAccounts();
      for (QuotaAccountRecord account : accounts) {
        List<GenerationTaskRecord> tasks = mapper.listTasks(account.userId());
        List<GenerationExecutionRecord> executions = mapper.listV2Executions(account.userId());
        List<QuotaReconciliationMapper.V2SlotSettlement> slotSettlements =
            mapper.listV2SlotSettlements(account.userId());
        scannedTasks += tasks.size();
        int activeReserved = tasks.stream()
            .filter(task -> task.settlementVersionValue() != 2 && isActive(task.status()))
            .mapToInt(GenerationTaskRecord::totalCost).sum()
            + executions.stream().filter(execution -> isActive(execution.status()))
                .mapToInt(QuotaReconciliationService::remainingReserve).sum();
        for (GenerationTaskRecord task : tasks) {
          if (task.settlementVersionValue() == 2) continue;
          String expectedType = expectedLedgerType(task.status());
          if (expectedType == null) continue;
          Integer actual = mapper.findLedgerAmount(task.id(), expectedType);
          if (actual != null && actual == task.totalCost()) continue;
          mismatches++;
          String kind = actual != null ? "SETTLEMENT_AMOUNT_MISMATCH" : "MISSING_" + expectedType;
          String key = "reconciliation:" + kind.toLowerCase().replace('_', '-') + ":" + task.id();
          recordFinding(candidateId, account.userId(), task.id(), kind, key, task.totalCost(),
              actual == null ? 0 : actual, Map.of("taskStatus", task.status().name()));
          boolean fixed = actual == null && repair(expectedType, task, activeReserved);
          mapper.finishFinding(candidateId, key, fixed ? "REPAIRED" : "BLOCKED");
          if (fixed) repaired++;
        }
        for (GenerationExecutionRecord execution : executions) {
          int reserve = mapper.sumExecutionLedger(execution.id(), "RESERVE");
          int consume = mapper.sumExecutionLedger(execution.id(), "CONSUME");
          int release = mapper.sumExecutionLedger(execution.id(), "RELEASE");
          boolean terminal = execution.status() == com.dreamspace.common.persistence.database.DatabaseEnums.GenerationExecutionStatus.SUCCEEDED
              || execution.status() == com.dreamspace.common.persistence.database.DatabaseEnums.GenerationExecutionStatus.FAILED
              || execution.status() == com.dreamspace.common.persistence.database.DatabaseEnums.GenerationExecutionStatus.CANCELLED;
          if (reserve != execution.reservedAmount() || consume != execution.consumedAmount()
              || release != execution.releasedAmount()
              || execution.reservedAmount() < execution.consumedAmount() + execution.releasedAmount()
              || (terminal && reserve != consume + release)) {
            mismatches++;
            String key = "reconciliation:v2-execution-settlement:" + execution.id();
            recordFinding(candidateId, account.userId(), execution.taskId(), "SETTLEMENT_AMOUNT_MISMATCH", key,
                execution.reservedAmount(), reserve, Map.of("executionId", execution.id(), "consumedLedger", consume,
                    "releasedLedger", release, "executionStatus", execution.status().name()));
            mapper.finishFinding(candidateId, key, "BLOCKED");
          }
        }
        for (QuotaReconciliationMapper.V2SlotSettlement slot : slotSettlements) {
          boolean succeeded = "SUCCEEDED".equals(slot.status());
          int expected = succeeded ? slot.unitCost() : 0;
          int expectedCount = succeeded ? 1 : 0;
          if (slot.consumedAmount() == expected && slot.consumeCount() == expectedCount) continue;
          mismatches++;
          String key = "reconciliation:v2-slot-settlement:" + slot.taskId() + ":" + slot.slotIndex();
          recordFinding(candidateId, account.userId(), slot.taskId(), "SETTLEMENT_AMOUNT_MISMATCH", key,
              expected, slot.consumedAmount(), Map.of("slotIndex", slot.slotIndex(), "slotStatus", slot.status(),
                  "consumeCount", slot.consumeCount()));
          mapper.finishFinding(candidateId, key, "BLOCKED");
        }
        for (GenerationTaskRecord task : tasks) {
          if (task.settlementVersionValue() != 2) continue;
          long succeededSlots = slotSettlements.stream()
              .filter(slot -> task.id().equals(slot.taskId()) && "SUCCEEDED".equals(slot.status())).count();
          int expectedConsumed = Math.toIntExact(succeededSlots * task.unitCost());
          if (task.consumedCost() == expectedConsumed) continue;
          mismatches++;
          String key = "reconciliation:v2-task-consumed:" + task.id();
          recordFinding(candidateId, account.userId(), task.id(), "SETTLEMENT_AMOUNT_MISMATCH", key,
              expectedConsumed, task.consumedCost(), Map.of("succeededSlotCount", succeededSlots,
                  "unitCost", task.unitCost()));
          mapper.finishFinding(candidateId, key, "BLOCKED");
        }
        DriftCounts drift = recordDrift(candidateId, account, activeReserved);
        mismatches += drift.mismatches();
      }
      mapper.completeRun(candidateId, accounts.size(), scannedTasks, mismatches, repaired);
      return summary(mapper.findRun(windowKey));
    } catch (RuntimeException error) {
      mapper.failRun(candidateId, truncate(error.getMessage()));
      return summary(mapper.findRun(windowKey));
    }
  }

  private boolean repair(String type, GenerationTaskRecord task, int activeReserved) {
    if ("CONSUME".equals(type)) {
      return transactions.execute(status -> mapper.insertMissingConsume(UUID.randomUUID().toString(),
          task.userId(), task.id(), task.totalCost(), "consume:" + task.id()) == 1);
    }
    if ("RELEASE".equals(type)) {
      Boolean repaired = transactions.execute(status -> {
        if (mapper.findLedgerAmount(task.id(), "RELEASE") != null) return true;
        if (mapper.releaseExcessReserved(task.userId(), task.totalCost(), activeReserved + task.totalCost()) != 1) return false;
        if (mapper.insertMissingRelease(UUID.randomUUID().toString(), task.userId(), task.id(),
            task.totalCost(), "failure-release:" + task.id()) != 1) {
          status.setRollbackOnly();
          return false;
        }
        return true;
      });
      return Boolean.TRUE.equals(repaired);
    }
    return false;
  }

  private DriftCounts recordDrift(String runId, QuotaAccountRecord account, int expectedReserved) {
    int grants = mapper.sumLedger(account.userId(), "GRANT");
    int consumes = mapper.sumLedger(account.userId(), "CONSUME");
    int expectedAvailable = grants - consumes - expectedReserved;
    int count = 0;
    count += blockDrift(runId, account.userId(), "TOTAL_DRIFT", grants, account.total());
    count += blockDrift(runId, account.userId(), "RESERVED_DRIFT", expectedReserved, account.reserved());
    count += blockDrift(runId, account.userId(), "AVAILABLE_DRIFT", expectedAvailable, account.available());
    return new DriftCounts(count);
  }

  private int blockDrift(String runId, String userId, String kind, int expected, int actual) {
    if (expected == actual) return 0;
    String key = "reconciliation:" + kind.toLowerCase().replace('_', '-') + ":" + userId;
    recordFinding(runId, userId, null, kind, key, expected, actual,
        Map.of("source", "business_state_and_quota_ledger"));
    mapper.finishFinding(runId, key, "BLOCKED");
    return 1;
  }

  private void recordFinding(String runId, String userId, String taskId, String kind, String key,
      int expected, int actual, Map<String, ?> details) {
    try {
      mapper.upsertFinding(UUID.randomUUID().toString(), runId, userId, taskId, kind, key,
          expected, actual, json.writeValueAsString(details));
    } catch (JsonProcessingException error) {
      throw new IllegalStateException("reconciliation details serialization failed", error);
    }
  }

  private static boolean isActive(GenerationTaskStatus status) {
    return status == GenerationTaskStatus.QUEUED || status == GenerationTaskStatus.GENERATING;
  }

  private int recoverStaleExecutions(Instant now, long windowMillis) {
    long timeoutMillis = Math.max(3_600_000L, windowMillis);
    Instant cutoff = now.minusMillis(timeoutMillis);
    int repaired = 0;
    for (GenerationExecutionRecord execution : generation.listStaleExecutions(cutoff, 100)) {
      Boolean fixed = transactions.execute(status -> {
        var taskSnapshot = generation.findTaskForWorker(execution.taskId());
        if (taskSnapshot == null) return false;
        var task = generation.lockTask(taskSnapshot.userId(), taskSnapshot.id());
        if (task == null) return false;
        GenerationExecutionRecord locked = generation.lockExecution(execution.id());
        if (locked == null || !isActive(locked.status()) || locked.updatedAt() == null
            || !locked.updatedAt().isBefore(cutoff)) return false;
        generation.failStaleExecutionSlots(task.id(), locked.id());
        int remaining = remainingReserve(locked);
        if (remaining > 0 && !quota.settle(task.userId(), task.id(), remaining, "RELEASE",
            "release:" + locked.id(), locked.id(), null)) return false;
        if (generation.finishExecution(locked.id(), remaining, "FAILED", "STALE_EXECUTION_RECOVERED") != 1) return false;
        if (generation.findActiveExecution(task.id()) == null) {
          boolean hasSuccess = generation.countSucceededSlots(task.id()) > 0;
          if (generation.setTaskV2Status(task.id(), hasSuccess ? "PARTIALLY_SUCCEEDED" : "FAILED",
              "STALE_EXECUTION_RECOVERED", "生成任务超时，已自动结束", true) != 1) return false;
        }
        return true;
      });
      if (Boolean.TRUE.equals(fixed)) {
        repaired++;
        log.atWarn().addKeyValue("executionId", execution.id()).addKeyValue("taskId", execution.taskId())
            .log("recovered stale generation execution");
      }
    }
    return repaired;
  }

  private static boolean isActive(com.dreamspace.common.persistence.database.DatabaseEnums.GenerationExecutionStatus status) {
    return status == com.dreamspace.common.persistence.database.DatabaseEnums.GenerationExecutionStatus.QUEUED
        || status == com.dreamspace.common.persistence.database.DatabaseEnums.GenerationExecutionStatus.GENERATING;
  }

  private static int remainingReserve(GenerationExecutionRecord execution) {
    return Math.max(0, execution.reservedAmount() - execution.consumedAmount() - execution.releasedAmount());
  }

  private static String expectedLedgerType(GenerationTaskStatus status) {
    if (isActive(status)) return "RESERVE";
    if (status == GenerationTaskStatus.SUCCEEDED || status == GenerationTaskStatus.PARTIALLY_SUCCEEDED) return "CONSUME";
    if (status == GenerationTaskStatus.FAILED || status == GenerationTaskStatus.CANCELLED) return "RELEASE";
    return null;
  }

  private static String truncate(String message) {
    if (message == null || message.isBlank()) return "reconciliation failed";
    return message.length() <= 500 ? message : message.substring(0, 500);
  }

  private static Summary summary(QuotaReconciliationRunRecord run) {
    if (run == null) throw new IllegalStateException("reconciliation run is unavailable");
    return new Summary(run.id(), run.status().name(), run.scannedUsers(), run.scannedTasks(),
        run.mismatchCount(), run.repairedCount());
  }

  private record DriftCounts(int mismatches) {}
  public record Summary(String runId, String status, int scannedUsers, int scannedTasks,
      int mismatchCount, int repairedCount) {}
}
