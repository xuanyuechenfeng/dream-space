package com.dreamspace.api.persistence.admin;

import java.time.Instant;

public record CreditProductRecord(String id, String code, String name, int creditAmount, long amountMinor,
    String currency, Integer validityDays, String status, int sortOrder, Instant createdAt, Instant updatedAt) {}
