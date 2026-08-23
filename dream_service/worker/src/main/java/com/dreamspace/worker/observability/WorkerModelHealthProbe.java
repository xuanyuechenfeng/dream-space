package com.dreamspace.worker.observability;

import com.dreamspace.common.persistence.config.DreamSpaceProperties;
import com.dreamspace.worker.generation.ModelTimeouts;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatProperties;
import org.springframework.ai.model.openai.autoconfigure.OpenAiConnectionProperties;
import org.springframework.boot.health.contributor.Health;
import org.springframework.stereotype.Component;

@Component
public final class WorkerModelHealthProbe {
  private static final Logger log = LoggerFactory.getLogger(WorkerModelHealthProbe.class);
  private final DreamSpaceProperties properties;
  private final OpenAiConnectionProperties planningConnection;
  private final OpenAiChatProperties planningChat;
  private final HttpClient client;

  public WorkerModelHealthProbe(DreamSpaceProperties properties,
      OpenAiConnectionProperties planningConnection, OpenAiChatProperties planningChat) {
    this.properties = properties;
    this.planningConnection = planningConnection;
    this.planningChat = planningChat;
    this.client = HttpClient.newBuilder().connectTimeout(ModelTimeouts.DETECTION).build();
  }

  public Health check() {
    try {
      DreamSpaceProperties.Planning planning = properties.ai().planning();
      DreamSpaceProperties.Image image = properties.ai().image();
      checkConfigured(planning.enabled(), planningConnection.getBaseUrl(), planningConnection.getApiKey(),
          planningChat.getOptions().getModel(), "planning");
      checkConfigured(image.enabled(), image.baseUrl(), image.apiKey(), image.model(), "image");
      checkProvider(planningConnection.getBaseUrl(), planningConnection.getApiKey(), "planning");
      checkProvider(image.baseUrl(), image.apiKey(), "image");
      return Health.up().withDetail("planning", "reachable").withDetail("image", "reachable").build();
    } catch (Exception error) {
      log.atError().addKeyValue("errorType", error.getClass().getSimpleName())
          .addKeyValue("rootCauseType", rootCauseType(error))
          .log("worker model readiness check failed", error);
      return Health.down().withDetail("reason", error.getMessage() == null ? "model unavailable" : error.getMessage()).build();
    }
  }

  private void checkConfigured(boolean enabled, String baseUrl, String apiKey, String model, String name) {
    if (!enabled || blank(baseUrl) || blank(apiKey) || blank(model)) {
      throw new IllegalStateException(name + " model configuration is incomplete");
    }
    if (baseUrl.startsWith("local-mock") || apiKey.startsWith("local-mock") || model.startsWith("local-mock")) {
      throw new IllegalStateException(name + " model cannot use a mock configuration");
    }
  }

  private void checkProvider(String baseUrl, String apiKey, String name) throws Exception {
    URI base = URI.create(baseUrl);
    String path = base.getPath() == null ? "" : base.getPath();
    String modelsPath = appendPath(path, "/models");
    URI models = new URI(base.getScheme(), base.getUserInfo(), base.getHost(), base.getPort(), modelsPath, null, null);
    HttpRequest request = HttpRequest.newBuilder(models).timeout(ModelTimeouts.DETECTION)
        .header("Authorization", "Bearer " + apiKey).GET().build();
    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
    String responsePreview = preview(response.body());
    log.atInfo().addKeyValue("modelType", name).addKeyValue("statusCode", response.statusCode())
        .addKeyValue("responseLength", response.body() == null ? 0 : response.body().length())
        .addKeyValue("responsePreview", responsePreview)
        .log("model readiness response received (response=" + responsePreview + ")");
    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      throw new IllegalStateException(name + " model health returned HTTP " + response.statusCode());
    }
    String contentType = response.headers().firstValue("content-type").orElse("").toLowerCase(Locale.ROOT);
    String body = response.body() == null ? "" : response.body().trim();
    if (!contentType.contains("json") || body.isEmpty() || (body.charAt(0) != '{' && body.charAt(0) != '[')) {
      throw new IllegalStateException(name + " model health returned a non-JSON response");
    }
  }

  static String appendPath(String basePath, String suffix) {
    String normalized = basePath == null ? "" : basePath.trim();
    if (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
    return normalized + suffix;
  }

  static String preview(String body) {
    if (body == null) return "<null>";
    String compact = body.replaceAll("\\s+", " ");
    return compact.length() <= 2048 ? compact : compact.substring(0, 2048) + "...";
  }

  private static String rootCauseType(Throwable error) {
    Throwable root = error;
    while (root.getCause() != null && root.getCause() != root) root = root.getCause();
    return root.getClass().getSimpleName();
  }

  private static boolean blank(String value) { return value == null || value.isBlank(); }
}
