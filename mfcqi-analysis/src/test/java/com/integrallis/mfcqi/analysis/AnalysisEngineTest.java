package com.integrallis.mfcqi.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class AnalysisEngineTest {

  @Test
  void pickProvider_routesByModelName() {
    AnalysisEngine engine =
        new AnalysisEngine(
            java.util.Arrays.asList(
                new AnthropicProvider(), new OpenAIProvider(), new OllamaProvider()));
    assertThat(engine.pickProvider("claude-3-5-sonnet").name()).isEqualTo("anthropic");
    assertThat(engine.pickProvider("gpt-4o").name()).isEqualTo("openai");
    assertThat(engine.pickProvider("ollama:llama3").name()).isEqualTo("ollama");
    assertThatThrownBy(() -> engine.pickProvider("bogus-model")).isInstanceOf(LLMException.class);
  }

  @Test
  void renderPrompt_includesMetricsAndCriticalSection() {
    AnalysisEngine engine =
        new AnalysisEngine(Collections.singletonList(new StubProvider("anything")));
    Map<String, Double> metrics = new LinkedHashMap<>();
    metrics.put("mfcqi_score", 0.42);
    metrics.put("security", 0.10);
    metrics.put("Cyclomatic Complexity", 0.85);
    String prompt = engine.renderPrompt("/tmp/repo", metrics, 5);
    assertThat(prompt).contains("/tmp/repo");
    assertThat(prompt).contains("0.42");
    assertThat(prompt).contains("security");
    assertThat(prompt).contains("CRITICAL");
    assertThat(prompt).contains("Generate 5 recommendations");
  }

  @Test
  void analyze_callsProviderAndParsesRecommendations() {
    String response =
        "## [HIGH] Fix MD5 usage in Crypto.java\n"
            + "**Description:** MD5 is cryptographically broken.\n"
            + "**Solution:** Switch to SHA-256.\n"
            + "**Impact:** security score should rise.\n"
            + "\n"
            + "## [MEDIUM] Reduce method length in Service.process\n"
            + "**Description:** Method is 80 lines long.\n"
            + "**Solution:** Extract helpers.\n"
            + "**Impact:** complexity score will improve.\n";
    StubProvider stub = new StubProvider(response);
    AnalysisEngine engine = new AnalysisEngine(Collections.singletonList(stub));
    AnalysisConfig cfg =
        AnalysisConfig.builder().model("claude-sonnet-4-5").anthropicApiKey("k").build();

    Map<String, Double> metrics = new LinkedHashMap<>();
    metrics.put("mfcqi_score", 0.5);
    metrics.put("security", 0.1);

    AnalysisResult r = engine.analyze("/repo", metrics, 5, cfg);
    assertThat(r.mfcqiScore()).isEqualTo(0.5);
    assertThat(r.metricScores()).containsEntry("security", 0.1);
    assertThat(r.recommendations()).hasSize(2);
    assertThat(r.recommendations().get(0)).startsWith("[HIGH] Fix MD5 usage");
    assertThat(r.modelUsed()).isEqualTo("claude-sonnet-4-5");
  }

  @Test
  void parseRecommendations_truncatesDescriptionToFirstSentence() {
    String response =
        "## [HIGH] Title here\n"
            + "**Description:** First sentence. Second sentence with more detail.\n"
            + "**Solution:** Fix it.\n";
    List<String> recs = AnalysisEngine.parseRecommendations(response, 10);
    assertThat(recs).hasSize(1);
    assertThat(recs.get(0)).isEqualTo("[HIGH] Title here: First sentence.");
  }

  @Test
  void renderPrompt_includesBanditAndComplexitySections() {
    AnalysisEngine engine =
        new AnalysisEngine(java.util.Collections.singletonList(new StubProvider("ok")));
    java.util.List<ToolOutputs.SecurityIssue> issues =
        java.util.Arrays.asList(
            new ToolOutputs.SecurityIssue(
                "B303", "use_of_weak_hash", "Crypto.java", 42, "MEDIUM", "MD5 used"));
    java.util.List<ToolOutputs.ComplexityHotspot> hotspots =
        java.util.Arrays.asList(
            new ToolOutputs.ComplexityHotspot("process", "Service.java", 18, 21));
    ToolOutputs outputs =
        ToolOutputs.builder()
            .bandit(issues)
            .complexity(hotspots)
            .totalFiles(7)
            .totalLines(800)
            .build();
    Map<String, Double> metrics = new LinkedHashMap<>();
    metrics.put("mfcqi_score", 0.55);
    metrics.put("security", 0.2);
    String prompt = engine.renderPrompt("/repo", metrics, outputs, 5);
    assertThat(prompt)
        .contains("Total Files Analyzed: 7")
        .contains("Lines of Code: 800")
        .contains("Found 1 security issues")
        .contains("[MEDIUM] B303 use_of_weak_hash")
        .contains("Crypto.java:42")
        .contains("Most Complex Methods")
        .contains("process in Service.java:18 — Cyclomatic Complexity = 21");
  }

  @Test
  void parseRecommendations_capsAtMax() {
    StringBuilder buf = new StringBuilder();
    for (int i = 0; i < 5; i++) {
      buf.append("## [LOW] Rec ")
          .append(i)
          .append("\n")
          .append("**Description:** Desc ")
          .append(i)
          .append(".\n")
          .append("**Solution:** Solve.\n\n");
    }
    List<String> recs = AnalysisEngine.parseRecommendations(buf.toString(), 3);
    assertThat(recs).hasSize(3);
  }

  /** Stub provider that returns a canned response and routes any model. */
  static final class StubProvider implements LLMProvider {
    private final String response;

    StubProvider(String response) {
      this.response = response;
    }

    @Override
    public String name() {
      return "stub";
    }

    @Override
    public boolean handles(String modelName) {
      return true;
    }

    @Override
    public String complete(String prompt, AnalysisConfig config) {
      return response;
    }
  }
}
