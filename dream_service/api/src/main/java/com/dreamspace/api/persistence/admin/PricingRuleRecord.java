package com.dreamspace.api.persistence.admin;

import java.time.Instant;

public record PricingRuleRecord(String id, String code, int version, String operation,
    String modelPattern, String resolution, Integer minWidth, Integer maxWidth, Integer minHeight,
    Integer maxHeight, int unitCreditCost, String formula, Instant effectiveFrom, Instant effectiveTo,
    String status, String createdBy, Instant createdAt, Instant updatedAt) {}
