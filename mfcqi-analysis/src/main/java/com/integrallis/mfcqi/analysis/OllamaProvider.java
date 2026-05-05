package com.integrallis.mfcqi.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.integrallis.mfcqi.analysis.internal.HttpJson;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ollama local-HTTP provider — POSTs to {@code <endpoint>/api/chat}. Default endpoint is the
 * Python-reference default {@code http://localhost:11434}. Model names of the form {@code
 * "ollama:llama3"} are accepted; the {@code ollama:} prefix is stripped before the request.
 */
public final class OllamaProvider implements LLMProvider {

  public static final String DEFAULT_ENDPOINT = "http://localhost:11434";

  private final String endpoint;

  public OllamaProvider() {
    this(DEFAULT_ENDPOINT);
  }

  public OllamaProvider(String endpoint) {
    this.endpoint = endpoint;
  }

  @Override
  public String name() {
    return "ollama";
  }

  @Override
  public boolean handles(String modelName) {
    return modelName.startsWith("ollama:") || modelName.startsWith("ollama/");
  }

  @Override
  public String complete(String prompt, AnalysisConfig config) {
    String model = config.model();
    if (model.startsWith("ollama:") || model.startsWith("ollama/")) {
      model = model.substring(7);
    }
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("model", model);
    body.put("stream", false);
    Map<String, Object> message = new LinkedHashMap<>();
    message.put("role", "user");
    message.put("content", prompt);
    body.put("messages", List.of(message));
    Map<String, Object> options = new LinkedHashMap<>();
    options.put("temperature", config.temperature());
    options.put("num_predict", config.maxTokens());
    body.put("options", options);

    JsonNode response =
        new HttpJson(config.timeoutSeconds())
            .post(endpoint + "/api/chat", new HashMap<>(), body, config.timeoutSeconds());
    return response.path("message").path("content").asText("");
  }
}
