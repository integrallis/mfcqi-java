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
    assertThat(cfg.model()).isEqualTo("claude-3-5-sonnet-20241022");
    assertThat(cfg.temperature()).isEqualTo(0.1);
    assertThat(cfg.maxTokens()).isEqualTo(8000);
    assertThat(cfg.timeoutSeconds()).isEqualTo(60);
    assertThat(cfg.supportedModels())
        .containsExactly(
            "claude-3-5-sonnet-20241022", "gpt-4o", "gpt-4o-mini", "gpt-5", "gpt-5-mini");
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
    AnalysisConfig cfg = AnalysisConfig.builder().model("claude-3-5-sonnet-20241022").build();
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
    assertThat(cfg.model()).isEqualTo("claude-3-5-sonnet-20241022");
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
}
