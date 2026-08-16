package com.dreamspace.persistence.admin;

import com.dreamspace.persistence.database.DatabaseEnums.QuotaReconciliationFindingKind;
import com.dreamspace.persistence.database.DatabaseEnums.QuotaReconciliationFindingStatus;
import java.time.Instant;

public record AdminReconciliationFindingRecord(String id, String runId, String userId, String taskId,
    QuotaReconciliationFindingKind kind, QuotaReconciliationFindingStatus status,
    Integer expectedAmount, Integer actualAmount, Instant repairedAt, Instant createdAt) {}
