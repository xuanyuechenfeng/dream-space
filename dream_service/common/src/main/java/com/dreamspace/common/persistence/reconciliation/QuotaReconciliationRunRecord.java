package com.dreamspace.common.persistence.reconciliation;

import com.dreamspace.common.persistence.database.DatabaseEnums.QuotaReconciliationRunStatus;
import java.time.Instant;

public record QuotaReconciliationRunRecord(String id, String windowKey, QuotaReconciliationRunStatus status,
    Instant startedAt, Instant completedAt, int scannedUsers, int scannedTasks, int mismatchCount,
    int repairedCount, String errorMessage, Instant createdAt) {}
