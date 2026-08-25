package com.dreamspace.api.persistence.admin;
import java.time.Instant;
public record PaymentTransactionRecord(String id, String orderId, String provider, String providerTransactionId, String providerEventId, String status, long amountMinor, String currency, boolean signatureVerified, Instant paidAt, Instant createdAt) {}
