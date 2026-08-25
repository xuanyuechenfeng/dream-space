package com.dreamspace.api.persistence.admin;

import java.time.Instant;

public record AdminBillingOrderRecord(String orderNo, String userId, String phone, String productCode,
    String productName, int quantity, int creditAmount, long amountMinor, String currency, String status,
    String provider, Instant expiresAt, Instant paidAt, Instant createdAt) {}
