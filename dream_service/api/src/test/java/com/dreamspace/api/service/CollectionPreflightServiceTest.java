package com.dreamspace.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dreamspace.common.persistence.config.DreamSpaceProperties;
import com.dreamspace.api.common.ApiException;
import com.dreamspace.common.persistence.database.DatabaseEnums.CollectionMode;
import com.dreamspace.common.persistence.database.DatabaseEnums.GenerationPreflightStatus;
import com.dreamspace.common.persistence.generation.GenerationPreflightRecord;
import com.dreamspace.common.persistence.generation.GenerationV2Mapper;
import com.dreamspace.common.persistence.generation.ImageCollectionPlan;
import com.dreamspace.common.persistence.generation.ResultSlotPlan;
import com.dreamspace.common.persistence.quota.QuotaTransactionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

class CollectionPreflightServiceTest {
  private final ObjectMapper json = new ObjectMapper();

  @Test
  void returnsFrozenCountConflictWarningsForReadyPreflight() {
    GenerationV2Mapper mapper = mock(GenerationV2Mapper.class);
    var shared = json.createObjectNode();
    shared.putArray("warnings").add("已按生成参数输出 3 张，覆盖描述中的 2 张");
    ImageCollectionPlan plan = new ImageCollectionPlan("collection-v2", CollectionMode.VARIATIONS,
        shared, List.of(slot(0), slot(1), slot(2)), 0.95, List.of());
    Instant readyAt = Instant.now();
    Instant expiresAt = readyAt.plusSeconds(600);
    GenerationPreflightRecord preflight = new GenerationPreflightRecord(
        "preflight-1", "user-1", null, GenerationPreflightStatus.READY,
        "preflight-key", "draft-key", "input-hash", json.createObjectNode(), json.valueToTree(plan),
        "collection-v2", CollectionMode.VARIATIONS, 3, "1:1", "2K", 2048, 2048,
        "rule-1", 1, 1, 3, null, null, readyAt, expiresAt, null, null, readyAt, readyAt);
    when(mapper.findPreflight("user-1", "preflight-1")).thenReturn(preflight);
    DreamSpaceProperties properties = new DreamSpaceProperties(null, null, null, null, null, null,
        new DreamSpaceProperties.Security(false, "test-preflight-secret"));
    CollectionPreflightService service = new CollectionPreflightService(mapper,
        mock(GenerationService.class), mock(GenerationQueuePublisher.class),
        mock(QuotaTransactionService.class), properties, json, new TestTransactionManager(), null);

    Object response = service.get("user-1", "preflight-1");

    assertThat(response).isInstanceOf(CollectionPreflightService.Ready.class);
    CollectionPreflightService.Ready ready = (CollectionPreflightService.Ready) response;
    assertThat(ready.targetImageCount()).isEqualTo(3);
    assertThat(ready.estimatedCost()).isEqualTo(3);
    assertThat(ready.warnings())
        .containsExactly("已按生成参数输出 3 张，覆盖描述中的 2 张");
  }

  @Test
  void reportsActionableErrorWhenPreflightSigningSecretIsMissing() {
    GenerationV2Mapper mapper = mock(GenerationV2Mapper.class);
    var plan = new ImageCollectionPlan("collection-v2", CollectionMode.VARIATIONS,
        json.createObjectNode(), List.of(slot(0)), 0.95, List.of());
    Instant expiresAt = Instant.now().plusSeconds(600);
    GenerationPreflightRecord preflight = new GenerationPreflightRecord(
        "preflight-1", "user-1", null, GenerationPreflightStatus.READY,
        "preflight-key", "draft-key", "input-hash", json.createObjectNode(), json.valueToTree(plan),
        "collection-v2", CollectionMode.VARIATIONS, 1, "1:1", "2K", 2048, 2048,
        "rule-1", 1, 1, 1, null, null, Instant.now(), expiresAt, null, null, Instant.now(), Instant.now());
    when(mapper.findPreflight("user-1", "preflight-1")).thenReturn(preflight);
    DreamSpaceProperties properties = new DreamSpaceProperties(null, null, null, null, null, null,
        new DreamSpaceProperties.Security(false, "  "));
    CollectionPreflightService service = new CollectionPreflightService(mapper,
        mock(GenerationService.class), mock(GenerationQueuePublisher.class),
        mock(QuotaTransactionService.class), properties, json, new TestTransactionManager(), null);

    assertThatThrownBy(() -> service.get("user-1", "preflight-1"))
        .isInstanceOf(ApiException.class)
        .satisfies(error -> {
          ApiException apiError = (ApiException) error;
          assertThat(apiError.code()).isEqualTo("PREFLIGHT_TOKEN_UNAVAILABLE");
          assertThat(apiError.getMessage()).contains("PREFLIGHT_TOKEN_SECRET");
        });
  }

  private ResultSlotPlan slot(int index) {
    return new ResultSlotPlan(index, "方案 " + (index + 1), "VARIATION", "方案 " + (index + 1),
        List.of("方案 " + (index + 1)), List.of(), json.createObjectNode(), json.createObjectNode());
  }

  private static final class TestTransactionManager extends AbstractPlatformTransactionManager {
    @Override protected Object doGetTransaction() { return new Object(); }
    @Override protected void doBegin(Object transaction, TransactionDefinition definition) {}
    @Override protected void doCommit(DefaultTransactionStatus status) {}
    @Override protected void doRollback(DefaultTransactionStatus status) {}
  }
}
