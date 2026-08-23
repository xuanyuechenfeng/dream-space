package com.dreamspace.api.service;

import static com.dreamspace.common.persistence.database.DatabaseEnums.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dreamspace.api.common.ApiException;
import com.dreamspace.api.persistence.admin.AdminApplicationMapper;
import com.dreamspace.api.persistence.inspiration.InspirationRecord;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AdminInspirationsServiceTest {
  @Test
  void createRequiresSourceAndLicenseMetadata() {
    AdminInspirationsService service = new AdminInspirationsService(mock(AdminApplicationMapper.class));
    AdminInspirationsService.Input input = valid(null, null, null);

    assertThatThrownBy(() -> service.create(input))
        .isInstanceOfSatisfying(ApiException.class,
            error -> assertThat(error.status().value()).isEqualTo(400));
  }

  @Test
  void staleUpdateReturnsConflict() {
    AdminApplicationMapper mapper = mock(AdminApplicationMapper.class);
    Instant version = Instant.parse("2026-08-17T00:00:00Z");
    when(mapper.findInspiration("item-1")).thenReturn(record(version));
    when(mapper.updateInspiration(anyString(), any(), anyString(), anyString(), anyString(), anyString(),
        anyString(), anyString(), anyInt(), anyInt(), anyString(), anyString(), anyString(), anyString(),
        anyString(), anyString(), any(), anyString(), anyBoolean(), anyInt(), anyInt())).thenReturn(0);
    AdminInspirationsService service = new AdminInspirationsService(mapper);

    assertThatThrownBy(() -> service.update("item-1", valid("造梦空间", "内部生成素材", version)))
        .isInstanceOfSatisfying(ApiException.class, error -> {
          assertThat(error.status().value()).isEqualTo(409);
          assertThat(error.code()).isEqualTo("OPTIMISTIC_CONFLICT");
        });
  }

  private static AdminInspirationsService.Input valid(String source, String license, Instant version) {
    return new AdminInspirationsService.Input("sample-item", "示例灵感", "详细提示词", "portrait",
        "/inspiration/sample.webp", "/inspiration/sample.webp", 100, 100, "image-4.7", "1:1",
        "100 × 100", "运营精选", "internal", source, null, license, true, 0, 0, version);
  }

  private static InspirationRecord record(Instant version) {
    return new InspirationRecord("item-1", "sample-item", "示例灵感", "详细提示词",
        InspirationCategory.PORTRAIT, "/image.webp", "/thumb.webp", 100, 100, "image-4.7", "1:1",
        "100 × 100", "运营精选", InspirationSourceType.INTERNAL, "造梦空间", null, "内部生成素材",
        true, 0, 0, InspirationStatus.DRAFT, null, version, version);
  }
}
