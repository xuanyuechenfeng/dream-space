package com.dreamspace.api.service;

import static com.dreamspace.common.persistence.database.DatabaseEnums.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dreamspace.api.common.ApiException;
import com.dreamspace.api.persistence.admin.AdminApplicationMapper;
import com.dreamspace.api.persistence.admin.AdminTaskRecord;
import com.dreamspace.common.persistence.generation.GenerationResultRecord;
import com.dreamspace.common.persistence.storage.ObjectStorage;
import com.dreamspace.common.persistence.storage.ObjectStorageFactory;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class AdminTasksServiceTest {
  @Test
  void detailMasksPhoneAndRedactsSensitiveErrors() {
    AdminApplicationMapper mapper = mock(AdminApplicationMapper.class);
    ObjectStorageFactory storage = new ObjectStorageFactory(mock(ObjectStorage.class));
    var refs = JsonNodeFactory.instance.arrayNode().add("/dream_web/uploads/references/ref/content");
    when(mapper.findTask("task-1")).thenReturn(new AdminTaskRecord("task-1", "session-1", "会话",
        "user-1", "18812340000", GenerationTaskStatus.FAILED, "prompt", "image-4.7",
        GenerationRatio.RATIO_1_1, GenerationResolution.K2, 1, 0, 1, 3, refs,
        "PROVIDER_ERROR", "authorization=secret 13812345678", ModerationStatus.APPROVED,
        ModerationStatus.REJECTED, Instant.now(), null, Instant.now()));
    when(mapper.listTaskResults("task-1")).thenReturn(List.of());
    AdminTasksService service = new AdminTasksService(mapper, storage);

    AdminTasksService.TaskDetail detail = service.get("task-1");

    assertThat(detail.userPhoneMasked()).isEqualTo("188****0000");
    assertThat(detail.errorMessage()).doesNotContain("secret", "13812345678").contains("***");
    assertThat(detail.imageIds()).containsExactly("/dream_web/uploads/references/ref/content");
  }

  @Test
  void resultReadNeverAcceptsAnUntrustedObjectKey() {
    AdminApplicationMapper mapper = mock(AdminApplicationMapper.class);
    ObjectStorage objectStorage = mock(ObjectStorage.class);
    when(mapper.findResult("result-1")).thenReturn(new GenerationResultRecord("result-1", "task-1", 0,
        "../secret", "../secret", "../secret", "sum", 10, 10, "image/webp", 10,
        10, 10, 10, ModerationStatus.APPROVED, true, Instant.now()));
    when(objectStorage.get("../secret")).thenThrow(new IllegalArgumentException("invalid object key"));
    AdminTasksService service = new AdminTasksService(mapper, new ObjectStorageFactory(objectStorage));

    assertThatThrownBy(() -> service.readResult("result-1", false))
        .isInstanceOfSatisfying(ApiException.class,
            error -> assertThat(error.status().value()).isEqualTo(404));
  }
}
