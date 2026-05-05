package com.integrallis.mfcqi.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.integrallis.mfcqi.analysis.internal.HttpJson;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI Chat Completions provider — POSTs to {@code https://api.openai.com/v1/chat/completions}.
 */
public final class OpenAIProvider implements LLMProvider {

  private static final String DEFAULT_ENDPOINT = "https://api.openai.com/v1/chat/completions";

  private final String endpoint;

  public OpenAIProvider() {
    this(DEFAULT_ENDPOINT);
  }

  public OpenAIProvider(String endpoint) {
    this.endpoint = endpoint;
  }

  @Override
  public String name() {
    return "openai";
  }

  @Override
  public boolean handles(String modelName) {
    return modelName.startsWith("gpt") || modelName.startsWith("o1");
  }

  @Override
  public String complete(String prompt, AnalysisConfig config) {
    String apiKey =
        config.openaiApiKey().orElseThrow(() -> new LLMException("OPENAI_API_KEY not configured"));

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("model", config.model());
    body.put("max_tokens", config.maxTokens());
    body.put("temperature", config.temperature());
    Map<String, Object> message = new LinkedHashMap<>();
    message.put("role", "user");
    message.put("content", prompt);
    body.put("messages", List.of(message));

    Map<String, String> headers = new HashMap<>();
    headers.put("Authorization", "Bearer " + apiKey);

    JsonNode response =
        new HttpJson(config.timeoutSeconds())
            .post(endpoint, headers, body, config.timeoutSeconds());
    JsonNode choices = response.path("choices");
    if (choices.isArray() && choices.size() > 0) {
      return choices.get(0).path("message").path("content").asText("");
    }
    return "";
  }
}
