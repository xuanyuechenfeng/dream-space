package com.dreamspace.worker.generation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.retry.TransientAiException;

public class OpenAiCompatibleGenerationProvider implements GenerationProvider {
  private static final int MAX_IMAGE_BYTES = 20 * 1024 * 1024;
  private static final String SYSTEM_PROMPT = "You are an image generation adapter. Return only JSON in the form "
      + "{\"images\":[{\"index\":0,\"url\":\"https://...\"}]} or use a data/base64 field. "
      + "Return exactly the requested number of images and no prose.";
  private final ChatModel model;
  private final ObjectMapper json;
  private final HttpClient http;

  public OpenAiCompatibleGenerationProvider(ChatModel model, ObjectMapper json, Duration timeout) {
    this.model = model;
    this.json = json;
    this.http = HttpClient.newBuilder().connectTimeout(timeout).followRedirects(HttpClient.Redirect.NORMAL).build();
  }

  @Override public List<ProviderImage> generate(WorkerTaskSnapshot task, GenerationAttempt attempt) {
    String request = "prompt: " + task.prompt() + "\nmodel: " + task.model() + "\nratio: "
        + task.ratio().databaseValue() + "\nresolution: " + task.resolution().databaseValue()
        + "\nimageCount: " + task.imageCount();
    try {
      var response = model.call(new Prompt(List.of(new SystemMessage(SYSTEM_PROMPT), new UserMessage(request))));
      if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
        throw new GenerationProviderException("PROVIDER_EMPTY_RESPONSE", "provider returned no response", true);
      }
      String text = response.getResult().getOutput().getText();
      if (text == null || text.isBlank()) throw new GenerationProviderException("PROVIDER_EMPTY_RESPONSE", "provider returned empty content", true);
      return decodeResponse(text, task.imageCount());
    } catch (GenerationProviderException error) {
      throw error;
    } catch (TransientAiException error) {
      throw new GenerationProviderException("PROVIDER_TEMPORARILY_UNAVAILABLE", "provider request failed temporarily", true, error);
    } catch (RuntimeException error) {
      throw new GenerationProviderException("PROVIDER_REQUEST_REJECTED", "provider request failed", false, error);
    }
  }

  private List<ProviderImage> decodeResponse(String content, int expectedCount) {
    try {
      JsonNode root = json.readTree(stripCodeFence(content));
      JsonNode images = root.path("images");
      if (!images.isArray() || images.isEmpty() || images.size() > expectedCount) {
        throw invalid("provider response has an invalid image count", null);
      }
      List<ProviderImage> result = new ArrayList<>();
      for (int position = 0; position < images.size(); position++) {
        JsonNode image = images.get(position);
        int index = image.has("index") ? image.path("index").asInt(-1) : position;
        DecodedImage decoded = image.hasNonNull("url") ? download(image.path("url").asText())
            : decodeBase64(image.hasNonNull("data") ? image.path("data").asText() : image.path("base64").asText(null));
        result.add(new ProviderImage(index, decoded.bytes(), decoded.mimeType(), "provider-" + index));
      }
      return List.copyOf(result);
    } catch (GenerationProviderException error) {
      throw error;
    } catch (IOException error) {
      throw invalid("provider response is not valid JSON", error);
    }
  }

  private DecodedImage download(String value) {
    try {
      URI uri = URI.create(value);
      if (!"https".equalsIgnoreCase(uri.getScheme()) && !"http".equalsIgnoreCase(uri.getScheme())) throw invalid("provider image URL scheme is not allowed", null);
      HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30)).GET().build();
      HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
      if (response.statusCode() == 429 || response.statusCode() >= 500) {
        throw new GenerationProviderException("PROVIDER_IMAGE_UNAVAILABLE", "provider image download failed temporarily", true);
      }
      if (response.statusCode() < 200 || response.statusCode() >= 300) throw invalid("provider image download was rejected", null);
      if (response.body().length == 0 || response.body().length > MAX_IMAGE_BYTES) throw invalid("provider image size is invalid", null);
      String mime = response.headers().firstValue("content-type").orElse("application/octet-stream").split(";", 2)[0];
      return new DecodedImage(response.body(), mime);
    } catch (GenerationProviderException error) {
      throw error;
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      throw new GenerationProviderException("PROVIDER_IMAGE_UNAVAILABLE", "provider image download interrupted", true, error);
    } catch (IllegalArgumentException | IOException error) {
      throw new GenerationProviderException("PROVIDER_IMAGE_UNAVAILABLE", "provider image download failed", true, error);
    }
  }

  private DecodedImage decodeBase64(String value) {
    if (value == null || value.isBlank()) throw invalid("provider image has no data", null);
    String payload = value;
    String mime = "application/octet-stream";
    if (value.startsWith("data:")) {
      int separator = value.indexOf(',');
      if (separator < 0 || !value.substring(0, separator).contains(";base64")) throw invalid("provider data URL is invalid", null);
      mime = value.substring(5, value.indexOf(';', 5));
      payload = value.substring(separator + 1);
    }
    try {
      byte[] bytes = Base64.getDecoder().decode(payload);
      if (bytes.length == 0 || bytes.length > MAX_IMAGE_BYTES) throw invalid("provider image size is invalid", null);
      return new DecodedImage(bytes, mime);
    } catch (IllegalArgumentException error) {
      throw invalid("provider image base64 is invalid", error);
    }
  }

  private static String stripCodeFence(String value) {
    String trimmed = value.trim();
    if (!trimmed.startsWith("```")) return trimmed;
    int firstLine = trimmed.indexOf('\n');
    int end = trimmed.lastIndexOf("```");
    return firstLine >= 0 && end > firstLine ? trimmed.substring(firstLine + 1, end).trim() : trimmed;
  }

  private static GenerationProviderException invalid(String message, Throwable cause) {
    return new GenerationProviderException("PROVIDER_OUTPUT_INVALID", message, false, cause);
  }

  private record DecodedImage(byte[] bytes, String mimeType) {}
}
