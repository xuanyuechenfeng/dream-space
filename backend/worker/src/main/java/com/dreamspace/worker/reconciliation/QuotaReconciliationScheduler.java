package com.dreamspace.worker.reconciliation;

import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "dream-space.worker", name = "enabled", havingValue = "true", matchIfMissing = true)
public class QuotaReconciliationScheduler {
  private static final Logger log = LoggerFactory.getLogger(QuotaReconciliationScheduler.class);
  private final QuotaReconciliationService service;
  private final long windowMillis;

  public QuotaReconciliationScheduler(QuotaReconciliationService service,
      @Value("${dream-space.worker.reconciliation-window-ms:3600000}") long windowMillis) {
    this.service = service;
    this.windowMillis = windowMillis;
  }

  @Scheduled(initialDelayString = "${dream-space.worker.reconciliation-initial-delay-ms:60000}",
      fixedDelayString = "${dream-space.worker.reconciliation-delay-ms:3600000}")
  public void reconcile() {
    try {
      QuotaReconciliationService.Summary summary = service.run(Instant.now(), windowMillis);
      log.atInfo().addKeyValue("runId", summary.runId()).addKeyValue("status", summary.status())
          .addKeyValue("mismatches", summary.mismatchCount()).addKeyValue("repaired", summary.repairedCount())
          .log("quota reconciliation completed");
    } catch (RuntimeException error) {
      log.error("quota reconciliation could not start", error);
    }
  }
}
