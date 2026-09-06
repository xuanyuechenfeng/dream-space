package com.dreamspace.common.persistence.generation;

import com.dreamspace.common.persistence.database.DatabaseEnums.GenerationSlotStatus;
import java.time.Instant;

public record GenerationResultSlotRecord(String taskId, int slotIndex, String label, String role,
    String intentSummary, String promptHash, GenerationSlotStatus status, String resultId,
    String activeExecutionId, int slotAttempt, String errorCode, String errorMessage,
    Instant startedAt, Instant completedAt, Instant createdAt, Instant updatedAt) {}
