package com.integrallis.mfcqi.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * End-to-end tests that exercise the real Anthropic and OpenAI APIs. Tagged {@code llm-integration}
 * so they are excluded from the default {@code ./gradlew test} run; invoke them with {@code
 * ./gradlew llmIntegrationTest}.
 *
 * <p>API keys are read from the environment first and fall back to a {@code .env} file at the repo
 * root via {@link AnalysisConfig#fromEnvironmentAndDotenv(Path)}. Tests gracefully skip (rather
 * than fail) when no key is configured.
 */
@Tag("llm-integration")
class LLMIntegrationTest {

  private static final Path REPO_ROOT = Path.of(System.getProperty("user.dir"));

  @Test
  void anthropic_realApiReturnsRecommendations() {
    AnalysisConfig fromEnv = AnalysisConfig.fromEnvironmentAndDotenv(REPO_ROOT);
    assumeTrue(
        fromEnv.anthropicApiKey().isPresent(), "ANTHROPIC_API_KEY not configured — skipping");

    AnalysisConfig cfg =
        AnalysisConfig.builder()
            .model("claude-sonnet-4-5")
            .anthropicApiKey(fromEnv.anthropicApiKey().get())
            .maxTokens(1500)
            .build();

    AnalysisEngine engine = AnalysisEngine.withDefaults();
    AnalysisResult result = engine.analyze(REPO_ROOT.toString(), sampleMetrics(), 3, cfg);
    assertThat(result.modelUsed()).isEqualTo("claude-sonnet-4-5");
    assertThat(result.recommendations()).isNotEmpty();
    // Every recommendation must follow the [SEVERITY] Title: Description shape parsed from the
    // template's required output format.
    assertThat(result.recommendations())
        .allMatch(r -> r.startsWith("[") && r.contains("]") && r.contains(":"));
  }

  @Test
  void openai_realApiReturnsRecommendations() {
    AnalysisConfig fromEnv = AnalysisConfig.fromEnvironmentAndDotenv(REPO_ROOT);
    assumeTrue(fromEnv.openaiApiKey().isPresent(), "OPENAI_API_KEY not configured — skipping");

    AnalysisConfig cfg =
        AnalysisConfig.builder()
            .model("gpt-4o-mini")
            .openaiApiKey(fromEnv.openaiApiKey().get())
            .maxTokens(1500)
            .build();

    AnalysisEngine engine = AnalysisEngine.withDefaults();
    AnalysisResult result = engine.analyze(REPO_ROOT.toString(), sampleMetrics(), 3, cfg);
    assertThat(result.modelUsed()).isEqualTo("gpt-4o-mini");
    assertThat(result.recommendations()).isNotEmpty();
    assertThat(result.recommendations())
        .allMatch(r -> r.startsWith("[") && r.contains("]") && r.contains(":"));
  }

  @Test
  void anthropic_richPromptIncludesEvidenceAndProducesGroundedRecommendations() {
    AnalysisConfig fromEnv = AnalysisConfig.fromEnvironmentAndDotenv(REPO_ROOT);
    assumeTrue(
        fromEnv.anthropicApiKey().isPresent(), "ANTHROPIC_API_KEY not configured — skipping");

    AnalysisConfig cfg =
        AnalysisConfig.builder()
            .model("claude-sonnet-4-5")
            .anthropicApiKey(fromEnv.anthropicApiKey().get())
            .maxTokens(2000)
            .build();

    ToolOutputs tools =
        ToolOutputs.builder()
            .totalFiles(50)
            .totalLines(4200)
            .bandit(
                java.util.Arrays.asList(
                    new ToolOutputs.SecurityIssue(
                        "B303",
                        "use_of_weak_hash",
                        "src/main/java/Crypto.java",
                        42,
                        "MEDIUM",
                        "Use of insecure hash function: MD5"),
                    new ToolOutputs.SecurityIssue(
                        "B605",
                        "start_process_with_a_shell",
                        "src/main/java/Shell.java",
                        17,
                        "HIGH",
                        "Possible command injection: Runtime.exec called with concatenated string")))
            .complexity(
                java.util.Arrays.asList(
                    new ToolOutputs.ComplexityHotspot(
                        "process", "src/main/java/Service.java", 88, 23)))
            .build();

    AnalysisEngine engine = AnalysisEngine.withDefaults();
    AnalysisResult result = engine.analyze(REPO_ROOT.toString(), sampleMetrics(), tools, 3, cfg);
    assertThat(result.recommendations()).isNotEmpty();
    // With a rich prompt the model is told to ground recommendations in the cited file:line. We
    // accept any of the three concrete locations appearing in at least one recommendation.
    assertThat(result.recommendations().stream().anyMatch(this::mentionsAnyEvidenceLocation))
        .as("at least one recommendation should reference Crypto.java, Shell.java, or Service.java")
        .isTrue();
  }

  private boolean mentionsAnyEvidenceLocation(String recommendation) {
    return recommendation.contains("Crypto.java")
        || recommendation.contains("Shell.java")
        || recommendation.contains("Service.java")
        || recommendation.contains("MD5")
        || recommendation.contains("Runtime.exec");
  }

  private static Map<String, Double> sampleMetrics() {
    Map<String, Double> m = new LinkedHashMap<>();
    m.put("mfcqi_score", 0.55);
    m.put("security", 0.20);
    m.put("Cyclomatic Complexity", 0.45);
    m.put("Documentation Coverage", 0.35);
    return m;
  }
}
