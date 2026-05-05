package com.integrallis.mfcqi.analysis;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Configuration for LLM analysis. Direct port of {@code mfcqi/analysis/config.py:AnalysisConfig}.
 *
 * <p>Defaults match the Python source verbatim: {@code claude-3-5-sonnet-20241022}, temperature
 * 0.1, max_tokens 8000, timeout 60. API keys are read from {@code ANTHROPIC_API_KEY} and {@code
 * OPENAI_API_KEY} environment variables when not supplied explicitly. The model can also be
 * overridden via {@code CQI_LLM_MODEL}.
 */
public final class AnalysisConfig {

  public static final String DEFAULT_MODEL = "claude-3-5-sonnet-20241022";
  public static final double DEFAULT_TEMPERATURE = 0.1;
  public static final int DEFAULT_MAX_TOKENS = 8000;
  public static final int DEFAULT_TIMEOUT_SECONDS = 60;

  private final String model;
  private final double temperature;
  private final int maxTokens;
  private final int timeoutSeconds;
  private final String anthropicApiKey;
  private final String openaiApiKey;

  private AnalysisConfig(Builder b) {
    this.model = Objects.requireNonNull(b.model, "model");
    this.temperature = b.temperature;
    this.maxTokens = b.maxTokens;
    this.timeoutSeconds = b.timeoutSeconds;
    this.anthropicApiKey = b.anthropicApiKey;
    this.openaiApiKey = b.openaiApiKey;
  }

  public String model() {
    return model;
  }

  public double temperature() {
    return temperature;
  }

  public int maxTokens() {
    return maxTokens;
  }

  public int timeoutSeconds() {
    return timeoutSeconds;
  }

  public Optional<String> anthropicApiKey() {
    return Optional.ofNullable(anthropicApiKey);
  }

  public Optional<String> openaiApiKey() {
    return Optional.ofNullable(openaiApiKey);
  }

  /** Mirrors Python's {@code get_api_key_for_model}: claude→Anthropic, gpt→OpenAI, else none. */
  public Optional<String> apiKeyForModel(String modelName) {
    if (modelName.startsWith("claude")) {
      return anthropicApiKey();
    }
    if (modelName.startsWith("gpt")) {
      return openaiApiKey();
    }
    return Optional.empty();
  }

  /** Mirrors Python's {@code validate_config} — non-Ollama models must have an API key. */
  public void validate() {
    if (model.startsWith("ollama:")) {
      return;
    }
    if (!apiKeyForModel(model).isPresent()) {
      throw new IllegalStateException("No API key found for model " + model);
    }
  }

  /** Verbatim port of Python's {@code get_supported_models}. */
  public List<String> supportedModels() {
    return Collections.unmodifiableList(
        Arrays.asList(
            "claude-3-5-sonnet-20241022", "gpt-4o", "gpt-4o-mini", "gpt-5", "gpt-5-mini"));
  }

  /** Build using environment variables (CQI_LLM_MODEL, ANTHROPIC_API_KEY, OPENAI_API_KEY). */
  public static AnalysisConfig fromEnvironment() {
    return fromEnvironment(System::getenv);
  }

  /**
   * Build using environment variables, falling back to a {@code .env} file discovered by walking up
   * from {@code start}. Real shell {@code export}s win over the file. Use this in CLI/test code
   * paths where developer-local API keys live in {@code .env}.
   */
  public static AnalysisConfig fromEnvironmentAndDotenv(java.nio.file.Path start) {
    java.util.Map<String, String> dotenv =
        DotEnv.findUp(start).map(DotEnv::load).orElse(java.util.Collections.emptyMap());
    return fromEnvironment(
        name -> {
          String shell = System.getenv(name);
          return (shell != null && !shell.isEmpty()) ? shell : dotenv.get(name);
        });
  }

  /** Test seam — accepts a function that returns the value of a named env var. */
  static AnalysisConfig fromEnvironment(java.util.function.Function<String, String> env) {
    Builder b = builder();
    String model = env.apply("CQI_LLM_MODEL");
    if (model != null && !model.isEmpty()) {
      b.model(model);
    } else {
      // Python from_environment: prefer Claude, then OpenAI, else default.
      if (env.apply("ANTHROPIC_API_KEY") != null) {
        b.model("claude-3-5-sonnet-20241022");
      } else if (env.apply("OPENAI_API_KEY") != null) {
        b.model("gpt-4o");
      }
    }
    String anthropic = env.apply("ANTHROPIC_API_KEY");
    if (anthropic != null && !anthropic.isEmpty()) {
      b.anthropicApiKey(anthropic);
    }
    String openai = env.apply("OPENAI_API_KEY");
    if (openai != null && !openai.isEmpty()) {
      b.openaiApiKey(openai);
    }
    return b.build();
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private String model = DEFAULT_MODEL;
    private double temperature = DEFAULT_TEMPERATURE;
    private int maxTokens = DEFAULT_MAX_TOKENS;
    private int timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;
    private String anthropicApiKey;
    private String openaiApiKey;

    private Builder() {}

    public Builder model(String model) {
      this.model = model;
      return this;
    }

    public Builder temperature(double t) {
      this.temperature = t;
      return this;
    }

    public Builder maxTokens(int n) {
      this.maxTokens = n;
      return this;
    }

    public Builder timeoutSeconds(int s) {
      this.timeoutSeconds = s;
      return this;
    }

    public Builder anthropicApiKey(String key) {
      this.anthropicApiKey = key;
      return this;
    }

    public Builder openaiApiKey(String key) {
      this.openaiApiKey = key;
      return this;
    }

    public AnalysisConfig build() {
      return new AnalysisConfig(this);
    }
  }
}
