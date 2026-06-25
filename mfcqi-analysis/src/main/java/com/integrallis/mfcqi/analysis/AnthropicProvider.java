package com.integrallis.mfcqi.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.integrallis.mfcqi.analysis.internal.HttpJson;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Anthropic Messages API provider — POSTs to {@code https://api.anthropic.com/v1/messages}. Mirrors
 * the LiteLLM "anthropic/" routing in the Python source.
 */
public final class AnthropicProvider implements LLMProvider {

  private static final String DEFAULT_ENDPOINT = "https://api.anthropic.com/v1/messages";
  private static final String API_VERSION = "2023-06-01";

  private final String endpoint;

  /** Creates a provider targeting the public Anthropic Messages API endpoint. */
  public AnthropicProvider() {
    this(DEFAULT_ENDPOINT);
  }

  /**
   * Creates a provider targeting a custom endpoint (useful for proxies or tests).
   *
   * @param endpoint the Messages API URL to POST to
   */
  public AnthropicProvider(String endpoint) {
    this.endpoint = endpoint;
  }

  @Override
  public String name() {
    return "anthropic";
  }

  @Override
  public boolean handles(String modelName) {
    return modelName.startsWith("claude");
  }

  @Override
  public String complete(String prompt, AnalysisConfig config) {
    String apiKey =
        config
            .anthropicApiKey()
            .orElseThrow(() -> new LLMException("ANTHROPIC_API_KEY not configured"));

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("model", config.model());
    body.put("max_tokens", config.maxTokens());
    body.put("temperature", config.temperature());
    Map<String, Object> message = new LinkedHashMap<>();
    message.put("role", "user");
    message.put("content", prompt);
    body.put("messages", List.of(message));

    Map<String, String> headers = new HashMap<>();
    headers.put("x-api-key", apiKey);
    headers.put("anthropic-version", API_VERSION);

    JsonNode response =
        new HttpJson(config.timeoutSeconds())
            .post(endpoint, headers, body, config.timeoutSeconds());
    JsonNode contents = response.path("content");
    if (contents.isArray() && contents.size() > 0) {
      return contents.get(0).path("text").asText("");
    }
    return "";
  }
}
