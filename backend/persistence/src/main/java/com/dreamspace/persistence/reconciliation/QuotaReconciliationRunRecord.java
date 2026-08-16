package com.dreamspace.persistence.reconciliation;

import com.dreamspace.persistence.database.DatabaseEnums.QuotaReconciliationRunStatus;
import java.time.Instant;

public record QuotaReconciliationRunRecord(String id, String windowKey, QuotaReconciliationRunStatus status,
    Instant startedAt, Instant completedAt, int scannedUsers, int scannedTasks, int mismatchCount,
    int repairedCount, String errorMessage, Instant createdAt) {}
