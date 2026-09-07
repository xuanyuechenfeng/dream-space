package com.dreamspace.worker.generation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

/** Minimal Responses API adapter used only by the planning pipeline. */
public final class ResponsesPlanningClient {
  private final String endpoint;
  private final String apiKey;
  private final String model;
  private final ObjectMapper json;
  private final HttpClient http;
  private final Duration timeout;

  public ResponsesPlanningClient(String baseUrl, String apiKey, String model, Duration timeout,
      ObjectMapper json) {
    this.endpoint = baseUrl.replaceAll("/$", "") + "/responses";
    this.apiKey = apiKey;
    this.model = model;
    this.json = json;
    this.timeout = timeout == null || timeout.isNegative() || timeout.isZero()
        ? Duration.ofSeconds(600) : timeout;
    this.http = HttpClient.newBuilder().connectTimeout(this.timeout).build();
  }

  public String call(String instructions, String userText, List<ReferenceImage> images) {
    ObjectNode body = json.createObjectNode().put("model", model)
        .put("instructions", instructions).put("store", false);
    ObjectNode format = body.putObject("text").putObject("format");
    format.put("type", "json_object");
    ArrayNode input = body.putArray("input");
    ObjectNode message = input.addObject().put("role", "user");
    ArrayNode content = message.putArray("content");
    content.addObject().put("type", "input_text").put("text", userText == null ? "" : userText);
    if (images != null) {
      for (ReferenceImage image : images) {
        if (image == null || image.bytes() == null) continue;
        String mime = image.mimeType() == null || image.mimeType().isBlank()
            ? "application/octet-stream" : image.mimeType();
        content.addObject().put("type", "input_image")
            .put("image_url", "data:" + mime + ";base64,"
                + Base64.getEncoder().encodeToString(image.bytes()));
      }
    }
    HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint)).timeout(timeout)
        .header("Authorization", "Bearer " + apiKey)
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
    try {
      HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
      int status = response.statusCode();
      if (status < 200 || status >= 300) throw providerError(status);
      JsonNode root;
      try {
        root = json.readTree(response.body());
      } catch (IOException parseError) {
        throw new GenerationProviderException("PLANNING_OUTPUT_INVALID",
            "planning Responses payload was not valid JSON", false, parseError);
      }
      String text = extractText(root);
      if (text == null || text.isBlank()) {
        throw new GenerationProviderException("PLANNING_EMPTY_RESPONSE",
            "planning Responses payload contained no output text", true);
      }
      return text;
    } catch (GenerationProviderException error) {
      throw error;
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      throw new GenerationProviderException("PLANNING_TEMPORARILY_UNAVAILABLE",
          "planning Responses request was interrupted", true, error);
    } catch (IOException | RuntimeException error) {
      throw new GenerationProviderException("PLANNING_TEMPORARILY_UNAVAILABLE",
          "planning Responses request failed", true, error);
    }
  }

  static String extractText(JsonNode root) {
    if (root == null || root.isNull()) return null;
    JsonNode direct = root.get("output_text");
    if (direct != null && direct.isTextual()) return direct.asText();
    JsonNode output = root.get("output");
    if (output == null || !output.isArray()) return null;
    for (JsonNode item : output) {
      if (!"message".equals(item.path("type").asText())) continue;
      JsonNode content = item.path("content");
      if (!content.isArray()) continue;
      for (JsonNode part : content) {
        if ("output_text".equals(part.path("type").asText()) && part.path("text").isTextual()) {
          return part.path("text").asText();
        }
      }
    }
    return null;
  }

  private static GenerationProviderException providerError(int status) {
    if (status == 401 || status == 403) {
      return new GenerationProviderException("PLANNING_PROVIDER_UNAUTHORIZED",
          "planning provider rejected credentials", false);
    }
    boolean retryable = status == 408 || status == 425 || status == 429 || status >= 500;
    return new GenerationProviderException(
        retryable ? "PLANNING_TEMPORARILY_UNAVAILABLE" : "PLANNING_PROVIDER_REJECTED",
        "planning provider returned HTTP " + status, retryable);
  }
}
