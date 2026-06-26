package com.integrallis.mfcqi.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class AnalysisConfigTest {

  @Test
  void defaults_matchPythonReference() {
    AnalysisConfig cfg = AnalysisConfig.builder().build();
    // Default model id diverges from the Python source: claude-3-5-sonnet-20241022 has been
    // retired by Anthropic and now returns HTTP 404, so the Java port pins the current Sonnet
    // release. Other defaults (temperature, max tokens, timeout) match Python verbatim.
    assertThat(cfg.model()).isEqualTo("claude-sonnet-4-5");
    assertThat(cfg.temperature()).isEqualTo(0.1);
    assertThat(cfg.maxTokens()).isEqualTo(8000);
    assertThat(cfg.timeoutSeconds()).isEqualTo(60);
    assertThat(cfg.supportedModels())
        .contains("claude-sonnet-4-5", "gpt-4o", "gpt-4o-mini")
        .doesNotContain("claude-3-5-sonnet-20241022");
  }

  @Test
  void apiKeyForModel_dispatchesByPrefix() {
    AnalysisConfig cfg =
        AnalysisConfig.builder().anthropicApiKey("a-key").openaiApiKey("o-key").build();
    assertThat(cfg.apiKeyForModel("claude-3-5-sonnet")).contains("a-key");
    assertThat(cfg.apiKeyForModel("gpt-4o")).contains("o-key");
    assertThat(cfg.apiKeyForModel("ollama:llama3")).isEmpty();
  }

  @Test
  void validate_throwsForUnconfiguredHostedModel() {
    AnalysisConfig cfg = AnalysisConfig.builder().model("claude-sonnet-4-5").build();
    assertThatThrownBy(cfg::validate)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("No API key");
  }

  @Test
  void validate_passesForOllamaWithoutApiKey() {
    AnalysisConfig cfg = AnalysisConfig.builder().model("ollama:llama3").build();
    cfg.validate(); // should not throw
  }

  @Test
  void fromEnvironment_picksClaudeWhenAnthropicKeyPresent() {
    Map<String, String> env = new HashMap<>();
    env.put("ANTHROPIC_API_KEY", "ak");
    AnalysisConfig cfg = AnalysisConfig.fromEnvironment(env::get);
    assertThat(cfg.model()).isEqualTo("claude-sonnet-4-5");
    assertThat(cfg.anthropicApiKey()).contains("ak");
  }

  @Test
  void fromEnvironment_picksGptWhenOnlyOpenAiKeyPresent() {
    Map<String, String> env = new HashMap<>();
    env.put("OPENAI_API_KEY", "ok");
    AnalysisConfig cfg = AnalysisConfig.fromEnvironment(env::get);
    assertThat(cfg.model()).isEqualTo("gpt-4o");
    assertThat(cfg.openaiApiKey()).contains("ok");
  }

  @Test
  void fromEnvironment_honoursCqiLlmModelOverride() {
    Map<String, String> env = new HashMap<>();
    env.put("CQI_LLM_MODEL", "ollama:llama3.1");
    AnalysisConfig cfg = AnalysisConfig.fromEnvironment(env::get);
    assertThat(cfg.model()).isEqualTo("ollama:llama3.1");
  }

  @Test
  void fromEnvironment_honoursMfcqiTimeoutOverride() {
    Map<String, String> env = new HashMap<>();
    env.put("MFCQI_TIMEOUT", "300");
    assertThat(AnalysisConfig.fromEnvironment(env::get).timeoutSeconds()).isEqualTo(300);
  }

  @Test
  void fromEnvironment_honoursCqiLlmTimeoutOverride() {
    Map<String, String> env = new HashMap<>();
    env.put("CQI_LLM_TIMEOUT", "120");
    assertThat(AnalysisConfig.fromEnvironment(env::get).timeoutSeconds()).isEqualTo(120);
  }

  @Test
  void fromEnvironment_keepsDefaultTimeoutWhenOverrideMissingOrInvalid() {
    Map<String, String> env = new HashMap<>();
    assertThat(AnalysisConfig.fromEnvironment(env::get).timeoutSeconds())
        .isEqualTo(AnalysisConfig.DEFAULT_TIMEOUT_SECONDS);
    env.put("MFCQI_TIMEOUT", "not-a-number");
    assertThat(AnalysisConfig.fromEnvironment(env::get).timeoutSeconds())
        .isEqualTo(AnalysisConfig.DEFAULT_TIMEOUT_SECONDS);
    env.put("MFCQI_TIMEOUT", "-5");
    assertThat(AnalysisConfig.fromEnvironment(env::get).timeoutSeconds())
        .isEqualTo(AnalysisConfig.DEFAULT_TIMEOUT_SECONDS);
  }

  @Test
  void fromEnvironmentAndDotenv_picksUpKeysFromDotenvFile(
      @org.junit.jupiter.api.io.TempDir java.nio.file.Path tmp) throws Exception {
    java.nio.file.Files.writeString(
        tmp.resolve(".env"), "ANTHROPIC_API_KEY=ak-from-dotenv\nOPENAI_API_KEY=ok-from-dotenv\n");
    AnalysisConfig cfg = AnalysisConfig.fromEnvironmentAndDotenv(tmp);
    // Real shell may also have these set; we assert at minimum that the dotenv values are used
    // when the shell does not override them.
    if (System.getenv("ANTHROPIC_API_KEY") == null) {
      assertThat(cfg.anthropicApiKey()).contains("ak-from-dotenv");
    }
    if (System.getenv("OPENAI_API_KEY") == null) {
      assertThat(cfg.openaiApiKey()).contains("ok-from-dotenv");
    }
  }
}
