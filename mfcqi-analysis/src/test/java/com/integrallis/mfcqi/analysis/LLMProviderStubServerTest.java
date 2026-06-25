package com.integrallis.mfcqi.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Exercises {@link AnthropicProvider}, {@link OpenAIProvider}, and {@link OllamaProvider} against a
 * local {@link HttpServer} bound to an ephemeral loopback port. Each provider accepts its endpoint
 * via constructor, so the stub URL is injected directly — no real network, API keys, or Ollama
 * daemon required.
 */
@Tag("unit")
class LLMProviderStubServerTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

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

  /** Capture the request body/headers and reply with a canned status+body. */
  private AtomicReference<JsonNode> stub(String path, int status, String responseBody) {
    AtomicReference<JsonNode> captured = new AtomicReference<>();
    server.createContext(
        path,
        exchange -> {
          byte[] reqBytes = exchange.getRequestBody().readAllBytes();
          if (reqBytes.length > 0) {
            captured.set(MAPPER.readTree(reqBytes));
          }
          // expose headers via a side channel: store under "__headers"
          byte[] resp = responseBody.getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(status, resp.length);
          try (OutputStream os = exchange.getResponseBody()) {
            os.write(resp);
          }
        });
    return captured;
  }

  // ----- Anthropic -----

  @Test
  void anthropic_parsesContentText_andSendsHeadersAndBody() {
    AtomicReference<String> apiKeyHeader = new AtomicReference<>();
    AtomicReference<String> versionHeader = new AtomicReference<>();
    AtomicReference<JsonNode> body = new AtomicReference<>();
    server.createContext(
        "/v1/messages",
        exchange -> {
          apiKeyHeader.set(exchange.getRequestHeaders().getFirst("x-api-key"));
          versionHeader.set(exchange.getRequestHeaders().getFirst("anthropic-version"));
          body.set(MAPPER.readTree(exchange.getRequestBody().readAllBytes()));
          byte[] resp =
              "{\"content\":[{\"type\":\"text\",\"text\":\"## [HIGH] Fix it\"}]}"
                  .getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, resp.length);
          try (OutputStream os = exchange.getResponseBody()) {
            os.write(resp);
          }
        });

    AnthropicProvider provider = new AnthropicProvider(baseUrl + "/v1/messages");
    AnalysisConfig cfg =
        AnalysisConfig.builder()
            .model("claude-sonnet-4-5")
            .anthropicApiKey("secret-key")
            .maxTokens(1234)
            .temperature(0.2)
            .timeoutSeconds(5)
            .build();

    String text = provider.complete("hello prompt", cfg);

    assertThat(text).isEqualTo("## [HIGH] Fix it");
    assertThat(apiKeyHeader.get()).isEqualTo("secret-key");
    assertThat(versionHeader.get()).isEqualTo("2023-06-01");
    assertThat(body.get().path("model").asText()).isEqualTo("claude-sonnet-4-5");
    assertThat(body.get().path("max_tokens").asInt()).isEqualTo(1234);
    assertThat(body.get().path("temperature").asDouble()).isEqualTo(0.2);
    assertThat(body.get().path("messages").get(0).path("role").asText()).isEqualTo("user");
    assertThat(body.get().path("messages").get(0).path("content").asText())
        .isEqualTo("hello prompt");
  }

  @Test
  void anthropic_returnsEmptyString_whenContentArrayMissing() {
    stub("/v1/messages", 200, "{\"content\":[]}");
    AnthropicProvider provider = new AnthropicProvider(baseUrl + "/v1/messages");
    AnalysisConfig cfg = configWithAnthropicKey();
    assertThat(provider.complete("p", cfg)).isEmpty();
  }

  @Test
  void anthropic_returnsEmptyString_whenContentNodeIsNotArray() {
    stub("/v1/messages", 200, "{\"content\":\"nope\"}");
    AnthropicProvider provider = new AnthropicProvider(baseUrl + "/v1/messages");
    assertThat(provider.complete("p", configWithAnthropicKey())).isEmpty();
  }

  @Test
  void anthropic_throwsWhenApiKeyMissing() {
    AnthropicProvider provider = new AnthropicProvider(baseUrl + "/v1/messages");
    AnalysisConfig cfg = AnalysisConfig.builder().model("claude-sonnet-4-5").build();
    assertThatThrownBy(() -> provider.complete("p", cfg))
        .isInstanceOf(LLMException.class)
        .hasMessageContaining("ANTHROPIC_API_KEY");
  }

  @Test
  void anthropic_propagatesHttpErrorAsLlmException() {
    stub("/v1/messages", 429, "{\"error\":\"rate limited\"}");
    AnthropicProvider provider = new AnthropicProvider(baseUrl + "/v1/messages");
    assertThatThrownBy(() -> provider.complete("p", configWithAnthropicKey()))
        .isInstanceOf(LLMException.class)
        .hasMessageContaining("HTTP 429");
  }

  @Test
  void anthropic_metadataAndDefaultConstructor() {
    AnthropicProvider provider = new AnthropicProvider();
    assertThat(provider.name()).isEqualTo("anthropic");
    assertThat(provider.handles("claude-sonnet-4-5")).isTrue();
    assertThat(provider.handles("gpt-4o")).isFalse();
  }

  // ----- OpenAI -----

  @Test
  void openai_parsesChoiceContent_andSendsAuthHeaderAndBody() {
    AtomicReference<String> authHeader = new AtomicReference<>();
    AtomicReference<JsonNode> body = new AtomicReference<>();
    server.createContext(
        "/v1/chat/completions",
        exchange -> {
          authHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
          body.set(MAPPER.readTree(exchange.getRequestBody().readAllBytes()));
          byte[] resp =
              "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"## [LOW] Tidy\"}}]}"
                  .getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, resp.length);
          try (OutputStream os = exchange.getResponseBody()) {
            os.write(resp);
          }
        });

    OpenAIProvider provider = new OpenAIProvider(baseUrl + "/v1/chat/completions");
    AnalysisConfig cfg =
        AnalysisConfig.builder()
            .model("gpt-4o")
            .openaiApiKey("ok-123")
            .maxTokens(500)
            .temperature(0.3)
            .timeoutSeconds(5)
            .build();

    String text = provider.complete("analyze this", cfg);

    assertThat(text).isEqualTo("## [LOW] Tidy");
    assertThat(authHeader.get()).isEqualTo("Bearer ok-123");
    assertThat(body.get().path("model").asText()).isEqualTo("gpt-4o");
    assertThat(body.get().path("max_tokens").asInt()).isEqualTo(500);
    assertThat(body.get().path("messages").get(0).path("content").asText())
        .isEqualTo("analyze this");
  }

  @Test
  void openai_returnsEmptyString_whenChoicesEmpty() {
    stub("/v1/chat/completions", 200, "{\"choices\":[]}");
    OpenAIProvider provider = new OpenAIProvider(baseUrl + "/v1/chat/completions");
    assertThat(provider.complete("p", configWithOpenAiKey())).isEmpty();
  }

  @Test
  void openai_returnsEmptyString_whenChoicesMissing() {
    stub("/v1/chat/completions", 200, "{}");
    OpenAIProvider provider = new OpenAIProvider(baseUrl + "/v1/chat/completions");
    assertThat(provider.complete("p", configWithOpenAiKey())).isEmpty();
  }

  @Test
  void openai_throwsWhenApiKeyMissing() {
    OpenAIProvider provider = new OpenAIProvider(baseUrl + "/v1/chat/completions");
    AnalysisConfig cfg = AnalysisConfig.builder().model("gpt-4o").build();
    assertThatThrownBy(() -> provider.complete("p", cfg))
        .isInstanceOf(LLMException.class)
        .hasMessageContaining("OPENAI_API_KEY");
  }

  @Test
  void openai_propagatesHttpErrorAsLlmException() {
    stub("/v1/chat/completions", 403, "forbidden");
    OpenAIProvider provider = new OpenAIProvider(baseUrl + "/v1/chat/completions");
    assertThatThrownBy(() -> provider.complete("p", configWithOpenAiKey()))
        .isInstanceOf(LLMException.class)
        .hasMessageContaining("HTTP 403");
  }

  @Test
  void openai_metadataAndDefaultConstructor() {
    OpenAIProvider provider = new OpenAIProvider();
    assertThat(provider.name()).isEqualTo("openai");
    assertThat(provider.handles("gpt-4o")).isTrue();
    assertThat(provider.handles("o1-preview")).isTrue();
    assertThat(provider.handles("claude-3")).isFalse();
  }

  // ----- Ollama -----

  @Test
  void ollama_parsesMessageContent_stripsPrefix_andPostsToApiChat() {
    AtomicReference<JsonNode> body = new AtomicReference<>();
    AtomicReference<String> path = new AtomicReference<>();
    server.createContext(
        "/api/chat",
        exchange -> {
          path.set(exchange.getRequestURI().getPath());
          body.set(MAPPER.readTree(exchange.getRequestBody().readAllBytes()));
          byte[] resp =
              "{\"message\":{\"role\":\"assistant\",\"content\":\"## [MEDIUM] Local\"}}"
                  .getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, resp.length);
          try (OutputStream os = exchange.getResponseBody()) {
            os.write(resp);
          }
        });

    OllamaProvider provider = new OllamaProvider(baseUrl);
    AnalysisConfig cfg =
        AnalysisConfig.builder()
            .model("ollama:llama3")
            .maxTokens(256)
            .temperature(0.4)
            .timeoutSeconds(5)
            .build();

    String text = provider.complete("local prompt", cfg);

    assertThat(text).isEqualTo("## [MEDIUM] Local");
    assertThat(path.get()).isEqualTo("/api/chat");
    assertThat(body.get().path("model").asText()).isEqualTo("llama3");
    assertThat(body.get().path("stream").asBoolean()).isFalse();
    assertThat(body.get().path("options").path("temperature").asDouble()).isEqualTo(0.4);
    assertThat(body.get().path("options").path("num_predict").asInt()).isEqualTo(256);
    assertThat(body.get().path("messages").get(0).path("content").asText())
        .isEqualTo("local prompt");
  }

  @Test
  void ollama_stripsSlashPrefixVariant() {
    AtomicReference<JsonNode> body = new AtomicReference<>();
    server.createContext(
        "/api/chat",
        exchange -> {
          body.set(MAPPER.readTree(exchange.getRequestBody().readAllBytes()));
          byte[] resp = "{\"message\":{\"content\":\"x\"}}".getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, resp.length);
          try (OutputStream os = exchange.getResponseBody()) {
            os.write(resp);
          }
        });

    OllamaProvider provider = new OllamaProvider(baseUrl);
    AnalysisConfig cfg = AnalysisConfig.builder().model("ollama/mistral").build();
    provider.complete("p", cfg);
    assertThat(body.get().path("model").asText()).isEqualTo("mistral");
  }

  @Test
  void ollama_keepsModelWhenNoPrefix() {
    AtomicReference<JsonNode> body = new AtomicReference<>();
    server.createContext(
        "/api/chat",
        exchange -> {
          body.set(MAPPER.readTree(exchange.getRequestBody().readAllBytes()));
          byte[] resp = "{\"message\":{\"content\":\"x\"}}".getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, resp.length);
          try (OutputStream os = exchange.getResponseBody()) {
            os.write(resp);
          }
        });

    OllamaProvider provider = new OllamaProvider(baseUrl);
    AnalysisConfig cfg = AnalysisConfig.builder().model("llama3").build();
    provider.complete("p", cfg);
    assertThat(body.get().path("model").asText()).isEqualTo("llama3");
  }

  @Test
  void ollama_returnsEmptyString_whenMessageMissing() {
    stub("/api/chat", 200, "{}");
    OllamaProvider provider = new OllamaProvider(baseUrl);
    AnalysisConfig cfg = AnalysisConfig.builder().model("ollama:llama3").build();
    assertThat(provider.complete("p", cfg)).isEmpty();
  }

  @Test
  void ollama_propagatesHttpErrorAsLlmException() {
    stub("/api/chat", 500, "server error");
    OllamaProvider provider = new OllamaProvider(baseUrl);
    AnalysisConfig cfg = AnalysisConfig.builder().model("ollama:llama3").build();
    assertThatThrownBy(() -> provider.complete("p", cfg))
        .isInstanceOf(LLMException.class)
        .hasMessageContaining("HTTP 500");
  }

  @Test
  void ollama_metadataAndConstants() {
    OllamaProvider provider = new OllamaProvider();
    assertThat(provider.name()).isEqualTo("ollama");
    assertThat(provider.handles("ollama:llama3")).isTrue();
    assertThat(provider.handles("ollama/mistral")).isTrue();
    assertThat(provider.handles("gpt-4o")).isFalse();
    assertThat(OllamaProvider.DEFAULT_ENDPOINT).isEqualTo("http://localhost:11434");
  }

  private static AnalysisConfig configWithAnthropicKey() {
    return AnalysisConfig.builder()
        .model("claude-sonnet-4-5")
        .anthropicApiKey("k")
        .timeoutSeconds(5)
        .build();
  }

  private static AnalysisConfig configWithOpenAiKey() {
    return AnalysisConfig.builder().model("gpt-4o").openaiApiKey("k").timeoutSeconds(5).build();
  }
}
