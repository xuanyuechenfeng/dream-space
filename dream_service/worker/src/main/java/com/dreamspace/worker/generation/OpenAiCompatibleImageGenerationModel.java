package com.dreamspace.worker.generation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import com.dreamspace.worker.observability.WorkerMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class OpenAiCompatibleImageGenerationModel implements ImageGenerationModel {
  private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleImageGenerationModel.class);
  private static final int MAX_IMAGE_BYTES = 20 * 1024 * 1024;
  private static final ObjectMapper PREVIEW_JSON = new ObjectMapper();
  private final String baseUrl;
  private final String apiKey;
  private final String model;
  private final String endpoint;
  private final ObjectMapper json;
  private final ReferenceImageLoader references;
  private final HttpClient http;
  private final WorkerMetrics metrics;
  private final Duration requestTimeout;
  private final int maxAttempts;

  public OpenAiCompatibleImageGenerationModel(String baseUrl, String apiKey, String model, String endpoint,
      ObjectMapper json, Duration timeout, int maxAttempts, ReferenceImageLoader references, WorkerMetrics metrics) {
    this.baseUrl = baseUrl.replaceAll("/$", ""); this.apiKey = apiKey; this.model = model; this.endpoint = endpoint;
    this.json = json; this.references = references;
    this.metrics = metrics;
    this.requestTimeout = timeout == null || timeout.isNegative() || timeout.isZero() ? ModelTimeouts.DETECTION : timeout;
    this.maxAttempts = Math.max(1, maxAttempts);
    this.http = HttpClient.newBuilder().connectTimeout(this.requestTimeout).followRedirects(HttpClient.Redirect.NEVER).build();
  }

  @Override public ImageGenerationResponse generate(ImageGenerationRequest request, GenerationAttempt attempt) {
    long started = metrics.startModel();
    log.atInfo().addKeyValue("taskId", request.task().id()).addKeyValue("attempt", attempt.number())
        .addKeyValue("stage", "image_generation").addKeyValue("provider", "openai-compatible")
        .addKeyValue("requestUrl", baseUrl + endpoint)
        .addKeyValue("model", model).addKeyValue("ratio", request.task().ratio())
        .addKeyValue("resolution", request.task().resolution()).addKeyValue("width", request.task().width())
        .addKeyValue("height", request.task().height()).addKeyValue("inputImageCount", request.task().imageIds().size())
        .log("image provider request started");
    try {
      var body = json.createObjectNode().put("model", model).put("prompt", request.promptPackage().positivePrompt())
          .put("negative_prompt", request.promptPackage().negativePrompt()).put("n", 1)
          .put("size", request.task().width() == null ? request.task().resolution().databaseValue()
              : request.task().width() + "x" + request.task().height())
          .put("aspect_ratio", request.task().ratio().databaseValue());
      if (request.targetImageId() != null) addInputImage(body, request.task(), request.targetImageId());
      if (request.referenceImageId() != null) addInputImage(body, request.task(), request.referenceImageId());
      if (request.refinement() != null) body.set("refinement", json.valueToTree(request.refinement()));
      String clientRequestId = UUID.randomUUID().toString();
      HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(baseUrl + endpoint)).timeout(requestTimeout)
          .header("Authorization", "Bearer " + apiKey).header("Content-Type", "application/json")
          .header("X-Client-Request-Id", clientRequestId)
          .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
      HttpResponse<String> response = send(httpRequest, HttpResponse.BodyHandlers.ofString(), "image generation");
      String responsePreview = preview(response.body());
      log.atInfo().addKeyValue("taskId", request.task().id()).addKeyValue("attempt", attempt.number())
          .addKeyValue("statusCode", response.statusCode()).addKeyValue("bodyBytes", response.body().length())
          .addKeyValue("clientRequestId", clientRequestId)
          .addKeyValue("providerRequestId", response.headers().firstValue("x-request-id").orElse(null))
          .addKeyValue("responsePreview", responsePreview)
          .log("image provider response received (statusCode=" + response.statusCode()
              + ", response=" + responsePreview + ")");
      if (response.statusCode() == 429 || response.statusCode() >= 500)
        throw new GenerationProviderException("IMAGE_PROVIDER_TEMPORARILY_UNAVAILABLE", "image provider is temporarily unavailable", true);
      if (response.statusCode() == 401 || response.statusCode() == 403)
        throw new GenerationProviderException("IMAGE_PROVIDER_UNAUTHORIZED", "image provider rejected credentials", false);
      if (response.statusCode() < 200 || response.statusCode() >= 300)
        throw new GenerationProviderException("IMAGE_PROVIDER_REJECTED", "image provider rejected request", false);
      JsonNode root = json.readTree(response.body());
      GenerationProviderException providerFailure = providerFailure(root);
      if (providerFailure != null) throw providerFailure;
      JsonNode data = imageData(root);
      if (!data.isArray() || data.isEmpty())
        throw invalid("image provider returned no images; response=" + preview(response.body()), null);
      List<ProviderImage> images = new ArrayList<>();
      for (int i = 0; i < data.size() && i < request.task().imageCount(); i++) {
        JsonNode item = data.get(i); String value = item.path("url").asText(null);
        byte[] bytes; String mime;
        if (value != null) { Decoded decoded = download(value); bytes = decoded.bytes(); mime = decoded.mime(); }
        else { value = item.path("b64_json").asText(item.path("data").asText(null)); Decoded decoded = decode(value); bytes = decoded.bytes(); mime = decoded.mime(); }
        images.add(new ProviderImage(i, bytes, mime, item.path("id").asText("provider-" + i)));
      }
      return new ImageGenerationResponse(List.copyOf(images), "openai-compatible", model,
          redactRequestId(response.headers().firstValue("x-request-id").orElse(null)));
    } catch (GenerationProviderException error) {
      log.atWarn().addKeyValue("taskId", request.task().id()).addKeyValue("attempt", attempt.number())
          .addKeyValue("errorCode", error.code()).addKeyValue("retryable", error.retryable())
          .log("image provider request failed");
      throw error;
    } catch (IOException | RuntimeException error) {
      log.atWarn().addKeyValue("taskId", request.task().id()).addKeyValue("attempt", attempt.number())
          .addKeyValue("errorCode", "IMAGE_PROVIDER_REQUEST_FAILED")
          .log("image provider request failed unexpectedly");
      throw new GenerationProviderException("IMAGE_PROVIDER_REQUEST_FAILED", "image provider request failed", true, error);
    } finally { metrics.stopModel(started, "openai-compatible", model, "image_generation"); }
  }

  private JsonNode imageData(JsonNode root) {
    if (root == null || root.isNull()) return json.createArrayNode();
    JsonNode data = root.path("data");
    if (data.isArray()) return data;
    JsonNode images = root.path("images");
    return images.isArray() ? images : data;
  }

  static GenerationProviderException providerFailure(JsonNode root) {
    if (root == null || !root.path("error").isObject()) return null;
    JsonNode error = root.path("error");
    String code = error.path("code").asText("");
    String type = error.path("type").asText("");
    boolean retryable = "IMAGE_GENERATION_TIMEOUT".equalsIgnoreCase(code)
        || "timeout_error".equalsIgnoreCase(type);
    return new GenerationProviderException(
        retryable ? "IMAGE_PROVIDER_TEMPORARILY_UNAVAILABLE" : "IMAGE_PROVIDER_REJECTED",
        retryable ? "image provider generation timed out" : "image provider rejected request",
        retryable);
  }

  static String preview(String body) {
    if (body == null) return "<null>";
    String compact;
    try {
      JsonNode root = PREVIEW_JSON.readTree(body);
      redactImagePayloads(root);
      compact = root == null ? body : root.toString();
    } catch (Exception ignored) {
      compact = body;
    }
    compact = compact.replaceAll("\\s+", " ");
    return compact.length() <= 4096 ? compact : compact.substring(0, 4096) + "...";
  }

  private static void redactImagePayloads(JsonNode node) {
    if (node == null) return;
    if (node.isArray()) {
      node.forEach(OpenAiCompatibleImageGenerationModel::redactImagePayloads);
      return;
    }
    if (!node.isObject()) return;
    ObjectNode object = (ObjectNode) node;
    object.fields().forEachRemaining(entry -> {
      JsonNode value = entry.getValue();
      if (value != null && value.isTextual()
          && ("b64_json".equals(entry.getKey()) || "base64".equals(entry.getKey()) || "data".equals(entry.getKey()))) {
        object.put(entry.getKey(), "<redacted-image-payload>");
      } else {
        redactImagePayloads(value);
      }
    });
  }

  private void addInputImage(com.fasterxml.jackson.databind.node.ObjectNode body,
      WorkerTaskSnapshot task, String imageId) {
    ReferenceImage image = references.load(task.userId(), imageId);
    if (image.bytes().length > MAX_IMAGE_BYTES) throw invalid("reference image is too large", null);
    body.withArray("input_images").add("data:" + image.mimeType() + ";base64,"
        + Base64.getEncoder().encodeToString(image.bytes()));
  }

  private Decoded download(String value) {
    try { URI uri = URI.create(value); if (!"https".equalsIgnoreCase(uri.getScheme()) || privateHost(uri.getHost())) throw invalid("provider image URL is not allowed", null);
      HttpResponse<byte[]> response = send(HttpRequest.newBuilder(uri).timeout(requestTimeout).GET().build(),
          HttpResponse.BodyHandlers.ofByteArray(), "provider image download");
      if (response.statusCode() == 429 || response.statusCode() >= 500) throw new GenerationProviderException("IMAGE_PROVIDER_TEMPORARILY_UNAVAILABLE", "provider image download failed temporarily", true);
      if (response.statusCode() < 200 || response.statusCode() >= 300 || response.body().length == 0 || response.body().length > MAX_IMAGE_BYTES) throw invalid("provider image download is invalid", null);
      return verified(response.body(), response.headers().firstValue("content-type").orElse(null));
    } catch (GenerationProviderException error) { throw error; } catch (Exception error) { throw new GenerationProviderException("IMAGE_PROVIDER_DOWNLOAD_FAILED", "provider image download failed", true, error); }
  }
  private Decoded decode(String value) { if (value == null || value.isBlank()) throw invalid("provider image has no data", null); String payload=value; String mime=null; if(value.startsWith("data:")){int comma=value.indexOf(','); int separator=value.indexOf(';',5); if(comma<0||separator<0||separator>comma)throw invalid("provider data URL is invalid",null); mime=value.substring(5,separator); payload=value.substring(comma+1);} try{byte[] bytes=Base64.getDecoder().decode(payload); if(bytes.length==0||bytes.length>MAX_IMAGE_BYTES)throw invalid("provider image size is invalid",null); return verified(bytes,mime);}catch(IllegalArgumentException e){throw invalid("provider image base64 is invalid",e);} }
  private <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> bodyHandler, String operation) {
    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
      try {
        HttpResponse<T> response = http.send(request, bodyHandler);
        if ((response.statusCode() == 429 || response.statusCode() >= 500) && attempt < maxAttempts) {
          pause(response, attempt);
          continue;
        }
        return response;
      } catch (InterruptedException error) {
        Thread.currentThread().interrupt();
        throw new GenerationProviderException("IMAGE_PROVIDER_INTERRUPTED", operation + " interrupted", true, error);
      } catch (IOException error) {
        if (attempt == maxAttempts) throw new GenerationProviderException("IMAGE_PROVIDER_REQUEST_FAILED", operation + " failed", true, error);
        pause(null, attempt);
      }
    }
    throw new GenerationProviderException("IMAGE_PROVIDER_REQUEST_FAILED", operation + " failed", true);
  }
  private static void pause(HttpResponse<?> response, int attempt) {
    long delay = Math.min(5000L, 500L << Math.min(attempt - 1, 3));
    if (response != null) {
      try { delay = Math.min(30_000L, Long.parseLong(response.headers().firstValue("retry-after").orElse("0")) * 1000L); }
      catch (NumberFormatException ignored) { }
      if (delay <= 0) delay = Math.min(5000L, 500L << Math.min(attempt - 1, 3));
    }
    try { Thread.sleep(delay); }
    catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new GenerationProviderException("IMAGE_PROVIDER_INTERRUPTED", "image provider retry interrupted", true, error); }
  }
  private static Decoded verified(byte[] bytes, String declaredMime) {
    String detected = detectMime(bytes);
    if (detected == null) throw invalid("provider image content is not a supported image", null);
    if (declaredMime != null && !declaredMime.isBlank()) {
      String normalized = declaredMime.split(";", 2)[0].trim().toLowerCase();
      if (!"application/octet-stream".equals(normalized) && !detected.equals(normalized)) {
        throw invalid("provider image MIME does not match its content", null);
      }
    }
    return new Decoded(bytes, detected);
  }
  private static String detectMime(byte[] bytes) {
    if (bytes.length >= 8 && (bytes[0] & 0xff) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G') return "image/png";
    if (bytes.length >= 3 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff) return "image/jpeg";
    if (bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
        && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') return "image/webp";
    return null;
  }
  private static String redactRequestId(String value) {
    return value == null || value.isBlank() ? null : "sha256:" + GenerationHarness.hash(value).substring(0, 16);
  }
  private static boolean privateHost(String host) { try { InetAddress address=InetAddress.getByName(host); return address.isAnyLocalAddress()||address.isLoopbackAddress()||address.isLinkLocalAddress()||address.isSiteLocalAddress(); } catch(Exception e){ return true; } }
  private static GenerationProviderException invalid(String message, Throwable cause) { return new GenerationProviderException("PROVIDER_OUTPUT_INVALID", message, false, cause); }
  private record Decoded(byte[] bytes, String mime) {}
}
