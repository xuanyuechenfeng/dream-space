package com.dreamspace.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dreamspace.persistence.config.DreamSpaceProperties;
import com.dreamspace.persistence.database.DatabaseEnums.GenerationRatio;
import com.dreamspace.persistence.database.DatabaseEnums.GenerationResolution;
import com.dreamspace.persistence.database.DatabaseEnums.GenerationTaskStatus;
import com.dreamspace.persistence.database.DatabaseEnums.ModerationStatus;
import com.dreamspace.persistence.generation.GenerationMapper;
import com.dreamspace.persistence.generation.GenerationSessionRecord;
import com.dreamspace.persistence.generation.GenerationTaskRecord;
import com.dreamspace.persistence.quota.QuotaAccountRecord;
import com.dreamspace.persistence.quota.QuotaTransactionService;
import com.dreamspace.persistence.storage.ObjectStorage;
import com.dreamspace.persistence.storage.ObjectStorageFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

class GenerationServiceTest {
  @Test
  void reservesExactCostAndPublishesOnlyAfterCommit() {
    GenerationMapper mapper = mock(GenerationMapper.class);
    QuotaTransactionService quota = mock(QuotaTransactionService.class);
    GenerationQueuePublisher publisher = mock(GenerationQueuePublisher.class);
    TestTransactionManager transactions = new TestTransactionManager();
    ObjectMapper json = new ObjectMapper();
    Instant now = Instant.parse("2026-08-17T00:00:00Z");
    GenerationSessionRecord session = new GenerationSessionRecord("session-1", "user-1", "Test", json.createObjectNode(), now, now);
    GenerationTaskRecord task = task("prompt", "request-key-123", 3, GenerationResolution.K4, json);
    when(mapper.findByIdempotencyKey("user-1", "request-key-123")).thenReturn(null);
    when(mapper.findSession("user-1", "session-1")).thenReturn(session);
    when(mapper.insertTask(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyInt(), anyString(), anyInt(), anyInt(), anyString())).thenReturn(1);
    when(quota.reserve(anyString(), anyString(), anyInt(), anyString(), anyInt())).thenReturn(true);
    when(mapper.insertEvent(anyString(), anyString(), anyString(), anyString())).thenReturn(1);
    when(mapper.findTask(anyString())).thenReturn(task);
    when(mapper.listTasks("session-1")).thenReturn(List.of(task));
    when(mapper.listResults(anyString())).thenReturn(List.of());
    when(quota.ensureAndRead("user-1", 100)).thenReturn(new QuotaAccountRecord("user-1", 100, 94, 6, now, now));
    when(publisher.publish(any())).thenAnswer(invocation -> {
      assertThat(transactions.commits).isEqualTo(1);
      return true;
    });
    GenerationService service = service(mapper, quota, publisher, transactions, json);

    var response = service.submit("user-1", new GenerationService.TaskRequest("request-key-123", "session-1",
        "prompt", "image-4.7", "1:1", "4K", 3, List.of()));

    assertThat(response.task().unitCost()).isEqualTo(2);
    assertThat(response.task().totalCost()).isEqualTo(6);
    assertThat(response.replayed()).isFalse();
    verify(quota).reserve(eq("user-1"), anyString(), eq(6), anyString(), eq(100));
    verify(publisher).publish(task);
  }

  @Test
  void rejectsDifferentPayloadForExistingIdempotencyKey() {
    GenerationMapper mapper = mock(GenerationMapper.class);
    QuotaTransactionService quota = mock(QuotaTransactionService.class);
    GenerationQueuePublisher publisher = mock(GenerationQueuePublisher.class);
    ObjectMapper json = new ObjectMapper();
    when(mapper.findByIdempotencyKey("user-1", "request-key-123"))
        .thenReturn(task("original", "request-key-123", 1, GenerationResolution.K2, json));
    GenerationService service = service(mapper, quota, publisher, new TestTransactionManager(), json);

    assertThatThrownBy(() -> service.submit("user-1", new GenerationService.TaskRequest("request-key-123", "session-1",
        "different", "image-4.7", "1:1", "2K", 1, List.of())))
        .isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.code()).isEqualTo("GENERATION_IDEMPOTENCY_CONFLICT"));
    verify(quota, never()).reserve(anyString(), anyString(), anyInt(), anyString(), anyInt());
    verify(publisher, never()).publish(any());
  }

  @Test
  void mapsDatabaseEnumsToLowercaseContractValues() {
    GenerationMapper mapper = mock(GenerationMapper.class);
    when(mapper.findTask("task-1")).thenReturn(task("prompt", "request-key-123", 1, GenerationResolution.K2, new ObjectMapper()));
    when(mapper.listResults("task-1")).thenReturn(List.of(new com.dreamspace.persistence.generation.GenerationResultRecord(
        "result-1", "task-1", 0, "/results/task-1/result-1.webp", "results/task-1/result-1.webp",
        "thumbnails/task-1/result-1.webp", "checksum", 100, 100, "image/webp", 100, 50, 50, 20,
        ModerationStatus.APPROVED, true, Instant.parse("2026-08-17T00:00:00Z"))));
    GenerationService service = service(mapper, mock(QuotaTransactionService.class),
        mock(GenerationQueuePublisher.class), new TestTransactionManager(), new ObjectMapper());

    var result = service.getTask("user-1", "task-1");

    assertThat(result.status()).isEqualTo("queued");
    assertThat(result.results()).singleElement().satisfies(item -> assertThat(item.moderationStatus()).isEqualTo("approved"));
  }

  private static GenerationService service(GenerationMapper mapper, QuotaTransactionService quota,
      GenerationQueuePublisher publisher, TestTransactionManager transactions, ObjectMapper json) {
    DreamSpaceProperties properties = new DreamSpaceProperties(null, null, null, null, null, null, null);
    return new GenerationService(mapper, quota, publisher, new ObjectStorageFactory(mock(ObjectStorage.class)),
        properties, json, transactions);
  }

  private static GenerationTaskRecord task(String prompt, String key, int count, GenerationResolution resolution, ObjectMapper json) {
    Instant now = Instant.parse("2026-08-17T00:00:00Z");
    int unit = resolution == GenerationResolution.K4 ? 2 : 1;
    return new GenerationTaskRecord("task-1", "session-1", "user-1", GenerationTaskStatus.QUEUED,
        prompt, "image-4.7", GenerationRatio.RATIO_1_1, resolution, count, json.createArrayNode(), unit, unit * count,
        key, null, 0, null, null, null, ModerationStatus.PENDING, ModerationStatus.PENDING,
        null, null, now, now);
  }

  private static final class TestTransactionManager extends AbstractPlatformTransactionManager {
    int commits;
    @Override protected Object doGetTransaction() { return new Object(); }
    @Override protected void doBegin(Object transaction, TransactionDefinition definition) {}
    @Override protected void doCommit(DefaultTransactionStatus status) { commits++; }
    @Override protected void doRollback(DefaultTransactionStatus status) {}
  }
}
