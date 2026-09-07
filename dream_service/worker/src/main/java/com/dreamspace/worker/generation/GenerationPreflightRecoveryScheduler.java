package com.dreamspace.worker.generation;

import com.dreamspace.common.persistence.generation.GenerationPreflightRecord;
import com.dreamspace.common.persistence.generation.GenerationV2Mapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Terminates abandoned planning rows so clients always observe a final state. */
@Component
public class GenerationPreflightRecoveryScheduler {
  private static final Logger log = LoggerFactory.getLogger(GenerationPreflightRecoveryScheduler.class);
  private final GenerationV2Mapper mapper;
  private final ObjectMapper json;
  private final TransactionTemplate transactions;
  private final Duration timeout;

  public GenerationPreflightRecoveryScheduler(GenerationV2Mapper mapper, ObjectMapper json,
      PlatformTransactionManager transactionManager,
      @Value("${dream-space.worker.preflight-timeout:PT120S}") Duration timeout) {
    this.mapper = mapper;
    this.json = json;
    this.transactions = new TransactionTemplate(transactionManager);
    this.timeout = timeout == null || timeout.isNegative() || timeout.isZero()
        ? Duration.ofSeconds(120) : timeout;
  }

  @Scheduled(initialDelayString = "${dream-space.worker.preflight-recovery-initial-delay-ms:30000}",
      fixedDelayString = "${dream-space.worker.preflight-recovery-delay-ms:30000}")
  public void recover() {
    Instant cutoff = Instant.now().minus(timeout);
    for (GenerationPreflightRecord preflight : mapper.listStalePlanningPreflights(cutoff, 100)) {
      Boolean recovered = transactions.execute(status -> {
        if (mapper.timeoutPreflight(preflight.id(), cutoff) != 1) return false;
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("preflightId", preflight.id());
        payload.put("sessionId", preflight.sessionId());
        payload.put("status", "FAILED");
        payload.put("errorCode", "GENERATION_PREFLIGHT_TIMEOUT");
        if (mapper.insertPreflightEvent(preflight.id(), "preflight.failed", "FAILED", write(payload)) != 1)
          throw new IllegalStateException("preflight timeout event was not inserted");
        return true;
      });
      if (Boolean.TRUE.equals(recovered)) {
        log.atWarn().addKeyValue("preflightId", preflight.id())
            .addKeyValue("sessionId", preflight.sessionId())
            .addKeyValue("errorCode", "GENERATION_PREFLIGHT_TIMEOUT")
            .addKeyValue("timeoutMs", timeout.toMillis())
            .log("stale collection preflight moved to terminal failure");
      }
    }
  }

  private String write(Object value) {
    try { return json.writeValueAsString(value); }
    catch (Exception error) { throw new IllegalStateException("failed to serialize preflight event", error); }
  }
}
