package com.dreamspace.api;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dreamspace.common.persistence.database.DatabaseEnums.InspirationCategory;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class HttpContractMockMvcTest {
  @Test
  void publicInspirationResponseKeepsTheFrozenEnvelope() throws Exception {
    InspirationService service = mock(InspirationService.class);
    when(service.list(any(), any(), any(Integer.class), any(Integer.class))).thenReturn(
        new InspirationService.Page(List.of(new InspirationService.Item("i1", "portrait-01", "肖像",
            "提示词摘要", "portrait", "/inspiration/portrait-01.webp", "/inspiration/portrait-01.webp",
            1350, 1800, "Dream Space", 12, "Image 4.7", "3:4", "2K", true, "完整提示词",
            "Dream Space Gallery", null, Instant.parse("2026-07-31T00:00:00Z"))), 1, 1, 24, 1));
    MockMvc mvc = MockMvcBuilders.standaloneSetup(new InspirationController(service)).build();

    mvc.perform(get("/inspirations").param("page", "1").param("pageSize", "24"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].slug", is("portrait-01")))
        .andExpect(jsonPath("$.items[0].category", is("portrait")))
        .andExpect(jsonPath("$.pageCount", is(1)));
  }

  @Test
  void domainErrorsUseTheDocumentedCodeAndRequestIdEnvelope() throws Exception {
    InspirationService service = mock(InspirationService.class);
    when(service.detail("missing")).thenThrow(new ApiException(org.springframework.http.HttpStatus.NOT_FOUND,
        "NOT_FOUND", "灵感不存在"));
    MockMvc mvc = MockMvcBuilders.standaloneSetup(new InspirationController(service))
        .setControllerAdvice(new ApiExceptionHandler()).build();

    mvc.perform(get("/inspirations/missing").header("X-Request-Id", "contract-1"))
        .andExpect(status().isNotFound()).andExpect(jsonPath("$.code", is("NOT_FOUND")))
        .andExpect(jsonPath("$.requestId", is("contract-1")));
  }

  @Test
  void malformedAdminWriteIsRejectedBeforePersistence() throws Exception {
    AdminInspirationsService service = mock(AdminInspirationsService.class);
    AdminInspirationsController controller = new AdminInspirationsController(service);
    MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new ApiExceptionHandler()).build();

    mvc.perform(post("/admin/inspirations").contentType("application/json").content("{\"slug\":[]}"))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code", is("VALIDATION_ERROR")));
  }
}
