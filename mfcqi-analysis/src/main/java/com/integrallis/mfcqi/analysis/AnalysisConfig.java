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

  /**
   * Default model identifier. The Python source pins {@code claude-3-5-sonnet-20241022}; that model
   * has been retired by Anthropic and now returns HTTP 404. We point the default at the current
   * Anthropic Sonnet release. Override via {@code CQI_LLM_MODEL} or {@code --model}.
   */
  public static final String DEFAULT_MODEL = "claude-sonnet-4-5";

  /** Default sampling temperature, matching the Python source ({@code 0.1}). */
  public static final double DEFAULT_TEMPERATURE = 0.1;

  /** Default maximum tokens to generate, matching the Python source ({@code 8000}). */
  public static final int DEFAULT_MAX_TOKENS = 8000;

  /** Default request timeout in seconds, matching the Python source ({@code 60}). */
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

  /**
   * The model identifier to use for analysis (e.g., {@code claude-sonnet-4-5}, {@code gpt-4o}).
   *
   * @return the configured model id
   */
  public String model() {
    return model;
  }

  /**
   * The sampling temperature passed to the LLM.
   *
   * @return the configured temperature
   */
  public double temperature() {
    return temperature;
  }

  /**
   * The maximum number of tokens to generate in the completion.
   *
   * @return the configured max-tokens limit
   */
  public int maxTokens() {
    return maxTokens;
  }

  /**
   * The request timeout in seconds applied to provider HTTP calls.
   *
   * @return the configured timeout in seconds
   */
  public int timeoutSeconds() {
    return timeoutSeconds;
  }

  /**
   * The Anthropic API key, if configured.
   *
   * @return the API key, or empty if none was supplied
   */
  public Optional<String> anthropicApiKey() {
    return Optional.ofNullable(anthropicApiKey);
  }

  /**
   * The OpenAI API key, if configured.
   *
   * @return the API key, or empty if none was supplied
   */
  public Optional<String> openaiApiKey() {
    return Optional.ofNullable(openaiApiKey);
  }

  /**
   * Selects the API key appropriate for the given model name. Mirrors Python's {@code
   * get_api_key_for_model}: {@code claude*} models use the Anthropic key, {@code gpt*} models use
   * the OpenAI key, and any other model resolves to none.
   *
   * @param modelName the model identifier to resolve a key for
   * @return the matching API key, or empty if the model has no associated key
   */
  public Optional<String> apiKeyForModel(String modelName) {
    if (modelName.startsWith("claude")) {
      return anthropicApiKey();
    }
    if (modelName.startsWith("gpt")) {
      return openaiApiKey();
    }
    return Optional.empty();
  }

  /**
   * Validates that this configuration is usable. Mirrors Python's {@code validate_config}: Ollama
   * models always pass; every other model must have a matching API key.
   *
   * @throws IllegalStateException if a non-Ollama model has no associated API key
   */
  public void validate() {
    if (model.startsWith("ollama:")) {
      return;
    }
    if (!apiKeyForModel(model).isPresent()) {
      throw new IllegalStateException("No API key found for model " + model);
    }
  }

  /**
   * Supported model list. The Python source's list contained Anthropic and OpenAI identifiers that
   * have shifted since publication; we list current production-ready model IDs here. The full set
   * is informational — any provider-routable model name is accepted at runtime.
   *
   * @return an unmodifiable list of known model identifiers
   */
  public List<String> supportedModels() {
    return Collections.unmodifiableList(
        Arrays.asList(
            "claude-sonnet-4-5",
            "claude-haiku-4-5",
            "claude-opus-4-7",
            "gpt-4o",
            "gpt-4o-mini",
            "gpt-5",
            "gpt-5-mini"));
  }

  /**
   * Build using environment variables (CQI_LLM_MODEL, ANTHROPIC_API_KEY, OPENAI_API_KEY).
   *
   * @return a configuration populated from the process environment
   */
  public static AnalysisConfig fromEnvironment() {
    return fromEnvironment(System::getenv);
  }

  /**
   * Build using environment variables, falling back to a {@code .env} file discovered by walking up
   * from {@code start}. Real shell {@code export}s win over the file. Use this in CLI/test code
   * paths where developer-local API keys live in {@code .env}.
   *
   * @param start the directory to begin the upward {@code .env} search from
   * @return a configuration populated from the environment and discovered {@code .env} file
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
      // Python from_environment: prefer Claude, then OpenAI, else default. We use a current
      // Anthropic model id rather than the Python source's retired one.
      if (env.apply("ANTHROPIC_API_KEY") != null) {
        b.model(DEFAULT_MODEL);
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
    // Optional request-timeout override (seconds). Useful for slow local models. CQI_LLM_TIMEOUT
    // is the CQI_-prefixed analog of CQI_LLM_MODEL; MFCQI_TIMEOUT is accepted as a convenience.
    String timeout = env.apply("MFCQI_TIMEOUT");
    if (timeout == null || timeout.isEmpty()) {
      timeout = env.apply("CQI_LLM_TIMEOUT");
    }
    if (timeout != null && !timeout.isEmpty()) {
      try {
        int seconds = Integer.parseInt(timeout.trim());
        if (seconds > 0) {
          b.timeoutSeconds(seconds);
        }
      } catch (NumberFormatException ignored) {
        // Ignore a malformed value and keep the default timeout.
      }
    }
    return b.build();
  }

  /**
   * Creates a new builder seeded with the default model and tuning parameters.
   *
   * @return a fresh {@link Builder}
   */
  public static Builder builder() {
    return new Builder();
  }

  /** Fluent builder for {@link AnalysisConfig}. */
  public static final class Builder {
    private String model = DEFAULT_MODEL;
    private double temperature = DEFAULT_TEMPERATURE;
    private int maxTokens = DEFAULT_MAX_TOKENS;
    private int timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;
    private String anthropicApiKey;
    private String openaiApiKey;

    private Builder() {}

    /**
     * Sets the model identifier.
     *
     * @param model the model id
     * @return this builder
     */
    public Builder model(String model) {
      this.model = model;
      return this;
    }

    /**
     * Sets the sampling temperature.
     *
     * @param t the temperature
     * @return this builder
     */
    public Builder temperature(double t) {
      this.temperature = t;
      return this;
    }

    /**
     * Sets the maximum number of tokens to generate.
     *
     * @param n the max-tokens limit
     * @return this builder
     */
    public Builder maxTokens(int n) {
      this.maxTokens = n;
      return this;
    }

    /**
     * Sets the request timeout in seconds.
     *
     * @param s the timeout in seconds
     * @return this builder
     */
    public Builder timeoutSeconds(int s) {
      this.timeoutSeconds = s;
      return this;
    }

    /**
     * Sets the Anthropic API key.
     *
     * @param key the API key
     * @return this builder
     */
    public Builder anthropicApiKey(String key) {
      this.anthropicApiKey = key;
      return this;
    }

    /**
     * Sets the OpenAI API key.
     *
     * @param key the API key
     * @return this builder
     */
    public Builder openaiApiKey(String key) {
      this.openaiApiKey = key;
      return this;
    }

    /**
     * Builds the immutable configuration.
     *
     * @return a new {@link AnalysisConfig}
     */
    public AnalysisConfig build() {
      return new AnalysisConfig(this);
    }
  }
}
