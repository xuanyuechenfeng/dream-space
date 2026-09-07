package com.dreamspace.worker.generation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dreamspace.common.persistence.database.DatabaseEnums.GenerationPreflightStatus;
import com.dreamspace.common.persistence.generation.GenerationPreflightRecord;
import com.dreamspace.common.persistence.generation.GenerationV2Mapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

class GenerationPreflightRecoverySchedulerTest {
  @Test
  void movesStalePlanningPreflightToFailedAndEmitsTerminalEvent() {
    GenerationV2Mapper mapper = mock(GenerationV2Mapper.class);
    Instant updatedAt = Instant.now().minusSeconds(180);
    GenerationPreflightRecord preflight = new GenerationPreflightRecord(
        "preflight-1", "user-1", "session-1", GenerationPreflightStatus.PLANNING,
        "preflight-key", "draft-key", "input-hash", new ObjectMapper().createObjectNode(), null,
        null, null, null, "1:1", "2K", 2048, 2048, null, null, 1,
        null, null, null, null, null, null, null, updatedAt, updatedAt);
    when(mapper.listStalePlanningPreflights(any(Instant.class), eq(100))).thenReturn(List.of(preflight));
    when(mapper.timeoutPreflight(eq("preflight-1"), any(Instant.class))).thenReturn(1);
    when(mapper.insertPreflightEvent(eq("preflight-1"), eq("preflight.failed"), eq("FAILED"), any(String.class)))
        .thenReturn(1);
    GenerationPreflightRecoveryScheduler scheduler = new GenerationPreflightRecoveryScheduler(
        mapper, new ObjectMapper(), new TestTransactionManager(), Duration.ofSeconds(120));

    scheduler.recover();

    verify(mapper).timeoutPreflight(eq("preflight-1"), any(Instant.class));
    verify(mapper).insertPreflightEvent(eq("preflight-1"), eq("preflight.failed"), eq("FAILED"),
        org.mockito.ArgumentMatchers.contains("GENERATION_PREFLIGHT_TIMEOUT"));
  }

  private static final class TestTransactionManager extends AbstractPlatformTransactionManager {
    @Override protected Object doGetTransaction() { return new Object(); }
    @Override protected void doBegin(Object transaction, TransactionDefinition definition) {}
    @Override protected void doCommit(DefaultTransactionStatus status) {}
    @Override protected void doRollback(DefaultTransactionStatus status) {}
  }
}
