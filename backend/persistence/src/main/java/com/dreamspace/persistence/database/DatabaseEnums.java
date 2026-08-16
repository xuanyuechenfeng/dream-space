package com.dreamspace.persistence.database;

public final class DatabaseEnums {
  private DatabaseEnums() {}

  public enum InspirationCategory implements DatabaseValue { PORTRAIT, PHOTOGRAPHY, ANIME, ILLUSTRATION, DESIGN; public String databaseValue() { return name(); } }
  public enum InspirationStatus implements DatabaseValue { DRAFT, PUBLISHED, ARCHIVED; public String databaseValue() { return name(); } }
  public enum InspirationSourceType implements DatabaseValue { AI_PUBLIC_GALLERY, LICENSED, INTERNAL; public String databaseValue() { return name(); } }
  public enum GenerationTaskStatus implements DatabaseValue { QUEUED, GENERATING, SUCCEEDED, PARTIALLY_SUCCEEDED, FAILED, CANCELLED; public String databaseValue() { return name(); } }
  public enum QuotaLedgerType implements DatabaseValue { GRANT, RESERVE, CONSUME, RELEASE; public String databaseValue() { return name(); } }
  public enum AdminRole implements DatabaseValue { VIEWER, OPERATOR, ADMIN; public String databaseValue() { return name(); } }
  public enum ModerationStatus implements DatabaseValue { PENDING, APPROVED, REJECTED; public String databaseValue() { return name(); } }
  public enum GenerationRatio implements DatabaseValue {
    SMART("smart"), RATIO_21_9("21:9"), RATIO_16_9("16:9"), RATIO_3_2("3:2"), RATIO_4_3("4:3"),
    RATIO_1_1("1:1"), RATIO_3_4("3:4"), RATIO_2_3("2:3"), RATIO_9_16("9:16");
    private final String value; GenerationRatio(String value) { this.value = value; } public String databaseValue() { return value; }
  }
  public enum GenerationResolution implements DatabaseValue {
    K2("2K"), K4("4K"); private final String value; GenerationResolution(String value) { this.value = value; } public String databaseValue() { return value; }
  }
  public enum QuotaReconciliationRunStatus implements DatabaseValue { RUNNING, COMPLETED, FAILED; public String databaseValue() { return name(); } }
  public enum QuotaReconciliationFindingKind implements DatabaseValue {
    MISSING_RESERVE, MISSING_RELEASE, MISSING_CONSUME, SETTLEMENT_AMOUNT_MISMATCH, TOTAL_DRIFT, RESERVED_DRIFT, AVAILABLE_DRIFT;
    public String databaseValue() { return name(); }
  }
  public enum QuotaReconciliationFindingStatus implements DatabaseValue { OPEN, REPAIRED, BLOCKED; public String databaseValue() { return name(); } }
}
