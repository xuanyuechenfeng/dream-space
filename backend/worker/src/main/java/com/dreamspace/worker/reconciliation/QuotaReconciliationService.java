package com.dreamspace.worker.reconciliation;

import com.dreamspace.persistence.database.DatabaseEnums.GenerationTaskStatus;
import com.dreamspace.persistence.generation.GenerationTaskRecord;
import com.dreamspace.persistence.quota.QuotaAccountRecord;
import com.dreamspace.persistence.reconciliation.QuotaReconciliationMapper;
import com.dreamspace.persistence.reconciliation.QuotaReconciliationRunRecord;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class QuotaReconciliationService {
  private final QuotaReconciliationMapper mapper;
  private final ObjectMapper json;
  private final TransactionTemplate transactions;

  public QuotaReconciliationService(QuotaReconciliationMapper mapper, ObjectMapper json,
      PlatformTransactionManager transactionManager) {
    this.mapper = mapper;
    this.json = json;
    this.transactions = new TransactionTemplate(transactionManager);
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
      accounts = mapper.listAccounts();
      for (QuotaAccountRecord account : accounts) {
        List<GenerationTaskRecord> tasks = mapper.listTasks(account.userId());
        scannedTasks += tasks.size();
        int activeReserved = tasks.stream().filter(task -> isActive(task.status()))
            .mapToInt(GenerationTaskRecord::totalCost).sum();
        for (GenerationTaskRecord task : tasks) {
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
        DriftCounts drift = recordDrift(candidateId, account);
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

  private DriftCounts recordDrift(String runId, QuotaAccountRecord account) {
    int grants = mapper.sumLedger(account.userId(), "GRANT");
    int reserves = mapper.sumLedger(account.userId(), "RESERVE");
    int consumes = mapper.sumLedger(account.userId(), "CONSUME");
    int releases = mapper.sumLedger(account.userId(), "RELEASE");
    int expectedReserved = reserves - consumes - releases;
    int expectedAvailable = grants - reserves + releases;
    int count = 0;
    count += blockDrift(runId, account.userId(), "TOTAL_DRIFT", grants, account.total());
    count += blockDrift(runId, account.userId(), "RESERVED_DRIFT", expectedReserved, account.reserved());
    count += blockDrift(runId, account.userId(), "AVAILABLE_DRIFT", expectedAvailable, account.available());
    return new DriftCounts(count);
  }

  private int blockDrift(String runId, String userId, String kind, int expected, int actual) {
    if (expected == actual) return 0;
    String key = "reconciliation:" + kind.toLowerCase().replace('_', '-') + ":" + userId;
    recordFinding(runId, userId, null, kind, key, expected, actual, Map.of("source", "quota_ledger"));
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
