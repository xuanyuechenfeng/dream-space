package com.dreamspace.api.persistence.admin;

import java.time.Instant;

public record RefundRecord(String id, String orderId, String paymentTransactionId, long amountMinor,
    String reason, String status, String providerRefundId, String idempotencyKey, String createdBy,
    Instant createdAt, Instant completedAt) {}
