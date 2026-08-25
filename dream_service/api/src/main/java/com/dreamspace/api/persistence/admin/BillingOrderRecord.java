package com.dreamspace.api.persistence.admin;
import java.time.Instant;
public record BillingOrderRecord(String id, String orderNo, String userId, String productId, String productCode, String productName, int quantity, int creditAmount, long amountMinor, String currency, String status, String provider, String idempotencyKey, Instant expiresAt, Instant paidAt, Instant createdAt, Instant updatedAt) {}
