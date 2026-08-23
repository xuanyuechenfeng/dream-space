package com.dreamspace.worker.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dreamspace.common.persistence.database.DatabaseEnums.GenerationInputMode;
import com.dreamspace.common.persistence.database.DatabaseEnums.GenerationRatio;
import com.dreamspace.common.persistence.database.DatabaseEnums.GenerationResolution;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.errors.OpenAIInvalidDataException;
import com.openai.errors.UnexpectedStatusCodeException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;

class ChatContentModeratorTest {
  private final ChatModel model = mock(ChatModel.class);
  private final ReferenceImageLoader references = mock(ReferenceImageLoader.class);
  private final ChatContentModerator moderator = new ChatContentModerator(model, new ObjectMapper(), references);

  @Test
  void treatsMalformedProviderResponseAsPermanentProtocolFailure() {
    when(model.call(any(Prompt.class))).thenThrow(
        new OpenAIInvalidDataException("Error reading response", new IllegalArgumentException("invalid JSON")));

    assertThatThrownBy(() -> moderator.moderateInput(task()))
        .isInstanceOfSatisfying(GenerationProviderException.class, error -> {
          assertThat(error.code()).isEqualTo("MODERATION_PROVIDER_PROTOCOL_ERROR");
          assertThat(error.retryable()).isFalse();
          assertThat(error.getCause()).isInstanceOf(OpenAIInvalidDataException.class);
        });
  }

  @Test
  void keepsTransientAiFailureRetryable() {
    when(model.call(any(Prompt.class))).thenThrow(new TransientAiException("gateway unavailable"));

    assertThatThrownBy(() -> moderator.moderateInput(task()))
        .isInstanceOfSatisfying(GenerationProviderException.class, error -> {
          assertThat(error.code()).isEqualTo("MODERATION_TEMPORARILY_UNAVAILABLE");
          assertThat(error.retryable()).isTrue();
        });
  }

  @Test
  void treatsRetiredProviderRouteAsRetryable() {
    when(model.call(any(Prompt.class))).thenThrow(providerStatus(410));

    assertThatThrownBy(() -> moderator.moderateInput(task()))
        .isInstanceOfSatisfying(GenerationProviderException.class, error -> {
          assertThat(error.code()).isEqualTo("MODERATION_TEMPORARILY_UNAVAILABLE");
          assertThat(error.retryable()).isTrue();
          assertThat(error.getCause()).isInstanceOf(UnexpectedStatusCodeException.class);
        });
  }

  @Test
  void keepsAuthenticationFailurePermanent() {
    when(model.call(any(Prompt.class))).thenThrow(providerStatus(401));

    assertThatThrownBy(() -> moderator.moderateInput(task()))
        .isInstanceOfSatisfying(GenerationProviderException.class, error -> {
          assertThat(error.code()).isEqualTo("MODERATION_PROVIDER_REQUEST_FAILED");
          assertThat(error.retryable()).isFalse();
        });
  }

  @Test
  void treatsNonTransientProviderFailureAsPermanent() {
    when(model.call(any(Prompt.class))).thenThrow(new NonTransientAiException("invalid credentials"));

    assertThatThrownBy(() -> moderator.moderateInput(task()))
        .isInstanceOfSatisfying(GenerationProviderException.class, error -> {
          assertThat(error.code()).isEqualTo("MODERATION_PROVIDER_REQUEST_FAILED");
          assertThat(error.retryable()).isFalse();
        });
  }

  @Test
  void treatsInvalidModerationContentAsPermanentOutputFailure() {
    when(model.call(any(Prompt.class))).thenReturn(response("not-json"));

    assertThatThrownBy(() -> moderator.moderateInput(task()))
        .isInstanceOfSatisfying(GenerationProviderException.class, error -> {
          assertThat(error.code()).isEqualTo("MODERATION_OUTPUT_INVALID");
          assertThat(error.retryable()).isFalse();
        });
  }

  @Test
  void returnsValidModerationDecision() {
    when(model.call(any(Prompt.class))).thenReturn(response("{\"approved\":true,\"code\":\"SAFE\"}"));

    ContentModerator.Decision decision = moderator.moderateInput(task());

    assertThat(decision.approved()).isTrue();
    assertThat(decision.code()).isEqualTo("SAFE");
  }

  private static ChatResponse response(String text) {
    return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
  }

  private static UnexpectedStatusCodeException providerStatus(int status) {
    return UnexpectedStatusCodeException.builder().statusCode(status)
        .headers(com.openai.core.http.Headers.builder().build()).build();
  }

  private static WorkerTaskSnapshot task() {
    return new WorkerTaskSnapshot("task-1", "user-1", "session-1", "safe prompt",
        GenerationInputMode.AUTO, List.of(), "chat-model", GenerationRatio.RATIO_1_1,
        GenerationResolution.K2, 1024, 1024, 1, 1, 0);
  }
}
