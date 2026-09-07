package com.dreamspace.worker.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ResponsesPlanningClientTest {
  private final ObjectMapper json = new ObjectMapper();
  private HttpServer server;

  @AfterEach
  void stopServer() {
    if (server != null) server.stop(0);
  }

  @Test
  void postsMultimodalJsonRequestToResponsesAndExtractsOutputText() throws Exception {
    AtomicReference<String> requestPath = new AtomicReference<>();
    AtomicReference<String> authorization = new AtomicReference<>();
    AtomicReference<String> requestBody = new AtomicReference<>();
    server = server(exchange -> {
      requestPath.set(exchange.getRequestURI().getPath());
      authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
      requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
      respond(exchange, 200, """
          {"output":[{"type":"reasoning","summary":[]},{"type":"message","content":[
            {"type":"output_text","text":"{\\"intent\\":\\"TEXT_TO_IMAGE\\"}"}
          ]}]}
          """);
    });
    ResponsesPlanningClient client = client();

    String result = client.call("system rules", "draw a poster",
        List.of(new ReferenceImage("reference-1", new byte[] {1, 2, 3}, "image/png")));

    assertThat(result).isEqualTo("{\"intent\":\"TEXT_TO_IMAGE\"}");
    assertThat(requestPath.get()).isEqualTo("/v1/responses");
    assertThat(authorization.get()).isEqualTo("Bearer test-key");
    JsonNode body = json.readTree(requestBody.get());
    assertThat(body.path("model").asText()).isEqualTo("test-model");
    assertThat(body.path("instructions").asText()).isEqualTo("system rules");
    assertThat(body.path("store").asBoolean()).isFalse();
    assertThat(body.path("text").path("format").path("type").asText()).isEqualTo("json_object");
    assertThat(body.path("input").get(0).path("role").asText()).isEqualTo("user");
    JsonNode content = body.path("input").get(0).path("content");
    assertThat(content.get(0).path("type").asText()).isEqualTo("input_text");
    assertThat(content.get(0).path("text").asText()).isEqualTo("draw a poster");
    assertThat(content.get(1).path("type").asText()).isEqualTo("input_image");
    assertThat(content.get(1).path("image_url").asText())
        .isEqualTo("data:image/png;base64,AQID");
  }

  @Test
  void treatsTransientHttpStatusesAsRetryable() throws Exception {
    for (int status : List.of(429, 502)) {
      restart(exchange -> respond(exchange, status, "{\"error\":{\"message\":\"temporary\"}}"));
      ResponsesPlanningClient client = client();

      assertThatThrownBy(() -> client.call("rules", "input", List.of()))
          .isInstanceOfSatisfying(GenerationProviderException.class, error -> {
            assertThat(error.code()).isEqualTo("PLANNING_TEMPORARILY_UNAVAILABLE");
            assertThat(error.retryable()).isTrue();
          });
    }
  }

  @Test
  void treatsCredentialRejectionAsNonRetryable() throws Exception {
    server = server(exchange -> respond(exchange, 401, "{\"error\":{\"message\":\"unauthorized\"}}"));

    assertThatThrownBy(() -> client().call("rules", "input", List.of()))
        .isInstanceOfSatisfying(GenerationProviderException.class, error -> {
          assertThat(error.code()).isEqualTo("PLANNING_PROVIDER_UNAUTHORIZED");
          assertThat(error.retryable()).isFalse();
        });
  }

  @Test
  void rejectsSuccessfulResponseWithoutOutputTextAsRetryable() throws Exception {
    server = server(exchange -> respond(exchange, 200,
        "{\"output\":[{\"type\":\"message\",\"content\":[]}]}"));

    assertThatThrownBy(() -> client().call("rules", "input", List.of()))
        .isInstanceOfSatisfying(GenerationProviderException.class, error -> {
          assertThat(error.code()).isEqualTo("PLANNING_EMPTY_RESPONSE");
          assertThat(error.retryable()).isTrue();
        });
  }

  private ResponsesPlanningClient client() {
    return new ResponsesPlanningClient(baseUrl(), "test-key", "test-model", Duration.ofSeconds(5), json);
  }

  private String baseUrl() {
    return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
  }

  private void restart(ExchangeHandler handler) throws IOException {
    if (server != null) server.stop(0);
    server = server(handler);
  }

  private static HttpServer server(ExchangeHandler handler) throws IOException {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/v1/responses", exchange -> handler.handle(exchange));
    server.start();
    return server;
  }

  private static void respond(HttpExchange exchange, int status, String body) throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, bytes.length);
    exchange.getResponseBody().write(bytes);
    exchange.close();
  }

  @FunctionalInterface
  private interface ExchangeHandler {
    void handle(HttpExchange exchange) throws IOException;
  }
}
