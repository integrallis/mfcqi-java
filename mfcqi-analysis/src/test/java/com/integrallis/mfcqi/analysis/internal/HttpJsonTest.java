package com.integrallis.mfcqi.analysis.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.integrallis.mfcqi.analysis.LLMException;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Exercises {@link HttpJson} against a local {@link HttpServer} bound to an ephemeral loopback
 * port. No real network or API keys are involved.
 */
@Tag("unit")
class HttpJsonTest {

  private HttpServer server;
  private String baseUrl;

  @BeforeEach
  void startServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.start();
    baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
  }

  @AfterEach
  void stopServer() {
    if (server != null) {
      server.stop(0);
    }
  }

  /** Register a handler that responds with the given status and body. */
  private void handle(String path, int status, String body) {
    server.createContext(
        path,
        exchange -> {
          byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(status, bytes.length);
          try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
          }
        });
  }

  @Test
  void post_sendsBodyAndHeaders_andParsesJsonResponse() {
    AtomicReference<String> capturedBody = new AtomicReference<>();
    AtomicReference<String> capturedContentType = new AtomicReference<>();
    AtomicReference<String> capturedCustom = new AtomicReference<>();
    AtomicReference<String> capturedMethod = new AtomicReference<>();
    server.createContext(
        "/echo",
        exchange -> {
          capturedMethod.set(exchange.getRequestMethod());
          capturedContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
          capturedCustom.set(exchange.getRequestHeaders().getFirst("X-Custom"));
          capturedBody.set(
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          byte[] resp = "{\"ok\":true,\"value\":7}".getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, resp.length);
          try (OutputStream os = exchange.getResponseBody()) {
            os.write(resp);
          }
        });

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("hello", "world");
    Map<String, String> headers = new HashMap<>();
    headers.put("X-Custom", "abc");

    JsonNode response = new HttpJson(5).post(baseUrl + "/echo", headers, body, 5);

    assertThat(response.path("ok").asBoolean()).isTrue();
    assertThat(response.path("value").asInt()).isEqualTo(7);
    assertThat(capturedMethod.get()).isEqualTo("POST");
    assertThat(capturedContentType.get()).isEqualTo("application/json");
    assertThat(capturedCustom.get()).isEqualTo("abc");
    assertThat(capturedBody.get()).isEqualTo("{\"hello\":\"world\"}");
  }

  @Test
  void post_throwsLlmException_onNon2xxStatus() {
    handle("/boom", 500, "{\"error\":\"kaboom\"}");

    assertThatThrownBy(
            () -> new HttpJson(5).post(baseUrl + "/boom", new HashMap<>(), Map.of("a", 1), 5))
        .isInstanceOf(LLMException.class)
        .hasMessageContaining("HTTP 500")
        .hasMessageContaining("kaboom");
  }

  @Test
  void post_throwsLlmException_on4xxStatus() {
    handle("/unauth", 401, "unauthorized");

    assertThatThrownBy(
            () -> new HttpJson(5).post(baseUrl + "/unauth", new HashMap<>(), Map.of("a", 1), 5))
        .isInstanceOf(LLMException.class)
        .hasMessageContaining("HTTP 401");
  }

  @Test
  void post_throwsLlmException_onMalformedJsonBody() {
    handle("/garbage", 200, "this is not json{{{");

    assertThatThrownBy(
            () -> new HttpJson(5).post(baseUrl + "/garbage", new HashMap<>(), Map.of("a", 1), 5))
        .isInstanceOf(LLMException.class)
        .hasMessageContaining("I/O failure");
  }

  @Test
  void post_throwsLlmException_onConnectionFailure() {
    // Nothing listening on this port path after server stop simulation: use an unroutable port.
    server.stop(0);
    server = null;

    assertThatThrownBy(
            () -> new HttpJson(2).post(baseUrl + "/echo", new HashMap<>(), Map.of("a", 1), 2))
        .isInstanceOf(LLMException.class)
        .hasMessageContaining("I/O failure");
  }

  @Test
  void mapperIsShared_andReadsTrees() throws Exception {
    JsonNode node = HttpJson.MAPPER.readTree("{\"k\":\"v\"}");
    assertThat(node.path("k").asText()).isEqualTo("v");
  }
}
