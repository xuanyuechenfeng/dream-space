package com.dreamspace.worker.generation;

import com.openai.errors.OpenAIInvalidDataException;
import com.openai.errors.UnexpectedStatusCodeException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.MimeTypeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ChatContentModerator implements ContentModerator {
  private static final Logger log = LoggerFactory.getLogger(ChatContentModerator.class);
  private static final String SYSTEM = "You are a production content safety classifier. Return strict JSON only: "
      + "{\"approved\":true|false,\"code\":\"SAFE\"}. Reject illegal, unsafe, disallowed or clearly infringing content."
      + " Do not explain your decision.";
  private final ChatModel model;
  private final ObjectMapper json;
  private final ReferenceImageLoader references;

  public ChatContentModerator(ChatModel model, ObjectMapper json, ReferenceImageLoader references) {
    this.model = model; this.json = json; this.references = references;
  }

  @Override public Decision moderateInput(WorkerTaskSnapshot task) {
    List<Media> media = new ArrayList<>();
    int index = 1;
    for (String imageId : task.imageIds()) addReference(media, task, imageId, "input-image-" + index++);
    return classify(task.id(), "input", "mode=" + task.mode() + "\nprompt=" + task.prompt(), media);
  }

  @Override public Decision moderateOutput(WorkerTaskSnapshot task, ProviderImage image) {
    return classify(task.id(), "output", "mode=" + task.mode() + "\nprompt=" + task.prompt(),
        List.of(media(image.sourceName(), image.data(), image.mimeType())));
  }

  private Decision classify(String taskId, String stage, String text, List<Media> media) {
    try {
      var prompt = new Prompt(List.of(new SystemMessage(SYSTEM),
          UserMessage.builder().text(text).media(media).build()),
          OpenAiChatOptions.builder().timeout(ModelTimeouts.DETECTION).build());
      ChatResponse response = callProvider(prompt);
      if (response == null || response.getResult() == null || response.getResult().getOutput() == null) throw invalid();
      String responseText = response.getResult().getOutput().getText();
      String responsePreview = preview(responseText);
      log.atInfo().addKeyValue("taskId", taskId).addKeyValue("stage", stage)
          .addKeyValue("responseLength", responseText == null ? 0 : responseText.length())
          .addKeyValue("responsePreview", responsePreview)
          .log("content moderation model raw response received (response=" + responsePreview + ")");
      var root = parseResponse(response);
      if (!root.has("approved") || !root.get("approved").isBoolean()) throw invalid();
      Decision decision = new Decision(root.get("approved").asBoolean(), root.path("code").asText("MODEL_DECISION"));
      log.atInfo().addKeyValue("taskId", taskId).addKeyValue("stage", stage)
          .addKeyValue("approved", decision.approved()).addKeyValue("code", decision.code())
          .addKeyValue("mediaCount", media.size()).log("content moderation response parsed");
      return decision;
    } catch (GenerationProviderException error) {
      log.atWarn().addKeyValue("taskId", taskId).addKeyValue("stage", stage)
          .addKeyValue("errorCode", error.code()).addKeyValue("retryable", error.retryable())
          .addKeyValue("exceptionType", exceptionType(error.getCause()))
          .addKeyValue("rootCauseType", rootCauseType(error))
          .addKeyValue("mediaCount", media.size()).log("content moderation failed");
      throw error;
    }
  }

  private ChatResponse callProvider(Prompt prompt) {
    try {
      return model.call(prompt);
    } catch (TransientAiException error) {
      throw new GenerationProviderException("MODERATION_TEMPORARILY_UNAVAILABLE",
          "content moderation model is temporarily unavailable", true, error);
    } catch (UnexpectedStatusCodeException error) {
      int status = error.statusCode();
      boolean retryable = status == 410 || status == 408 || status == 425 || status == 429 || status >= 500;
      throw new GenerationProviderException(
          retryable ? "MODERATION_TEMPORARILY_UNAVAILABLE" : "MODERATION_PROVIDER_REQUEST_FAILED",
          "content moderation provider returned HTTP " + status, retryable, error);
    } catch (Exception error) {
      if (hasCause(error, OpenAIInvalidDataException.class)) {
        throw new GenerationProviderException("MODERATION_PROVIDER_PROTOCOL_ERROR",
            "content moderation provider returned an invalid protocol response", false, error);
      }
      throw new GenerationProviderException("MODERATION_PROVIDER_REQUEST_FAILED",
          "content moderation provider request failed", false, error);
    }
  }

  private com.fasterxml.jackson.databind.JsonNode parseResponse(ChatResponse response) {
    try {
      return json.readTree(response.getResult().getOutput().getText());
    } catch (Exception error) {
      throw new GenerationProviderException("MODERATION_OUTPUT_INVALID",
          "moderation model returned invalid JSON", false, error);
    }
  }

  private void addReference(List<Media> media, WorkerTaskSnapshot task, String id, String name) {
    ReferenceImage image = references.load(task.userId(), id);
    media.add(media(name, image.bytes(), image.mimeType()));
  }

  private static Media media(String name, byte[] bytes, String mime) {
    return Media.builder().id(name).name(name).mimeType(MimeTypeUtils.parseMimeType(mime))
        .data(new ByteArrayResource(bytes)).build();
  }

  private static GenerationProviderException invalid() {
    return new GenerationProviderException("MODERATION_OUTPUT_INVALID", "moderation model returned invalid JSON", false);
  }

  private static String preview(String body) {
    if (body == null) return "<null>";
    String compact = body.replaceAll("\\s+", " ");
    return compact.length() <= 4096 ? compact : compact.substring(0, 4096) + "...";
  }

  private static boolean hasCause(Throwable error, Class<? extends Throwable> type) {
    for (Throwable current = error; current != null; current = current.getCause()) {
      if (type.isInstance(current)) return true;
    }
    return false;
  }

  private static String exceptionType(Throwable error) {
    return error == null ? "none" : error.getClass().getSimpleName();
  }

  private static String rootCauseType(Throwable error) {
    Throwable root = error;
    while (root.getCause() != null && root.getCause() != root) root = root.getCause();
    return root.getClass().getSimpleName();
  }
}
