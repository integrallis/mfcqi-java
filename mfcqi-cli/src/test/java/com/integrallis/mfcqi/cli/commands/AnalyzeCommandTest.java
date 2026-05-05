package com.integrallis.mfcqi.cli.commands;

import static org.assertj.core.api.Assertions.assertThat;

import com.integrallis.mfcqi.cli.Main;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

@Tag("unit")
class AnalyzeCommandTest {

  private final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
  private final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
  private PrintStream originalOut;
  private PrintStream originalErr;

  @BeforeEach
  void redirectStreams() {
    originalOut = System.out;
    originalErr = System.err;
    System.setOut(new PrintStream(stdout, true, StandardCharsets.UTF_8));
    System.setErr(new PrintStream(stderr, true, StandardCharsets.UTF_8));
  }

  @AfterEach
  void restoreStreams() {
    System.setOut(originalOut);
    System.setErr(originalErr);
  }

  @Test
  void render_terminalIncludesScoreAndMetrics(@TempDir Path tmp) {
    double score = 0.85;
    Map<String, Double> metrics = new LinkedHashMap<>();
    metrics.put("mfcqi_score", score);
    metrics.put("Cyclomatic Complexity", 0.9);
    String out = AnalyzeCommand.renderTerminal(tmp, score, metrics, null);
    assertThat(out).contains("Score: 0.850");
    assertThat(out).contains("Cyclomatic Complexity");
    assertThat(out).contains("0.900");
  }

  @Test
  void render_jsonHasScoreAndMetricsKeys(@TempDir Path tmp) {
    Map<String, Double> metrics = new LinkedHashMap<>();
    metrics.put("mfcqi_score", 0.7);
    metrics.put("security", 0.9);
    String json = AnalyzeCommand.renderJson(tmp, 0.7, metrics, null);
    assertThat(json).contains("\"mfcqi_score\" : 0.7");
    assertThat(json).contains("\"security\" : 0.9");
  }

  @Test
  void render_markdownHasTable(@TempDir Path tmp) {
    Map<String, Double> metrics = new LinkedHashMap<>();
    metrics.put("mfcqi_score", 0.7);
    metrics.put("security", 0.4);
    String md = AnalyzeCommand.renderMarkdown(tmp, 0.7, metrics, null);
    assertThat(md).contains("| Metric | Score |");
    assertThat(md).contains("| security | 0.400 |");
  }

  @Test
  void render_sarifIncludesRulesForEveryMetricAndOverall(@TempDir Path tmp) {
    Map<String, Double> metrics = new LinkedHashMap<>();
    metrics.put("mfcqi_score", 0.5);
    metrics.put("security", 0.2);
    metrics.put("Cyclomatic Complexity", 0.95);
    String sarif = AnalyzeCommand.renderSarif(tmp, 0.5, metrics, null);
    assertThat(sarif).contains("\"version\" : \"2.1.0\"");
    // Driver metadata.
    assertThat(sarif).contains("\"name\" : \"MFCQI\"");
    assertThat(sarif).contains("\"informationUri\"");
    // Rule definitions for every metric AND for the overall score.
    assertThat(sarif).contains("\"id\" : \"security\"");
    assertThat(sarif).contains("\"id\" : \"Cyclomatic Complexity\"");
    assertThat(sarif).contains("\"id\" : \"mfcqi_score\"");
    assertThat(sarif)
        .contains("Cyclomatic complexity measures the number of linearly independent paths");
    // Results: every metric appears (not just below-threshold), keyed by its rule id.
    assertThat(sarif).contains("\"ruleId\" : \"security\"");
    assertThat(sarif).contains("\"ruleId\" : \"Cyclomatic Complexity\"");
    assertThat(sarif).contains("\"ruleId\" : \"mfcqi_score\"");
  }

  @Test
  void render_sarifEmitsRecommendationsAsNoteResults(@TempDir Path tmp) {
    Map<String, Double> metrics = new LinkedHashMap<>();
    metrics.put("mfcqi_score", 0.7);
    metrics.put("security", 0.3);
    com.integrallis.mfcqi.analysis.AnalysisResult llm =
        new com.integrallis.mfcqi.analysis.AnalysisResult(
            0.7,
            metrics,
            java.util.Arrays.asList(
                "[HIGH] Fix MD5 in Crypto.java", "[MEDIUM] Reduce Service complexity"),
            "claude-3-5-sonnet-20241022");
    String sarif = AnalyzeCommand.renderSarif(tmp, 0.7, metrics, llm);
    assertThat(sarif).contains("Recommendation 1: [HIGH] Fix MD5 in Crypto.java");
    assertThat(sarif).contains("Recommendation 2: [MEDIUM] Reduce Service complexity");
    assertThat(sarif).contains("\"level\" : \"note\"");
    assertThat(sarif).contains("\"type\" : \"recommendation\"");
  }

  @Test
  void scoreToSarifLevel_followsPythonThresholds() {
    assertThat(AnalyzeCommand.scoreToSarifLevel(0.85)).isEqualTo("none");
    assertThat(AnalyzeCommand.scoreToSarifLevel(0.65)).isEqualTo("note");
    assertThat(AnalyzeCommand.scoreToSarifLevel(0.45)).isEqualTo("warning");
    assertThat(AnalyzeCommand.scoreToSarifLevel(0.20)).isEqualTo("error");
  }

  @Test
  void scoreToRating_followsPythonThresholds() {
    assertThat(AnalyzeCommand.scoreToRating(0.85)).isEqualTo("Excellent");
    assertThat(AnalyzeCommand.scoreToRating(0.65)).isEqualTo("Good");
    assertThat(AnalyzeCommand.scoreToRating(0.45)).isEqualTo("Needs Work");
    assertThat(AnalyzeCommand.scoreToRating(0.20)).isEqualTo("Poor");
  }

  @Test
  void cli_invocationPrintsAnalysis(@TempDir Path tmp) throws Exception {
    Path src = Files.createDirectories(tmp.resolve("src/main/java"));
    Files.writeString(
        src.resolve("X.java"),
        "/** doc */ public class X { /** doc */ public int v() { return 1; } }");

    int code =
        new CommandLine(new Main())
            .execute("analyze", "--skip-llm", "--format", "terminal", tmp.toString());
    assertThat(code).isEqualTo(0);
    String out = stdout.toString(StandardCharsets.UTF_8);
    assertThat(out).contains("MFCQI analysis:");
    assertThat(out).contains("Score:");
  }

  @Test
  void cli_minScoreReturnsOneWhenBelowThreshold(@TempDir Path tmp) throws Exception {
    Path src = Files.createDirectories(tmp.resolve("src/main/java"));
    Files.writeString(src.resolve("X.java"), "public class X {}");
    int code =
        new CommandLine(new Main())
            .execute("analyze", "--skip-llm", "--min-score", "0.99", tmp.toString());
    assertThat(code).isEqualTo(1);
  }

  @Test
  void cli_llmIsOptInByDefault(@TempDir Path tmp) throws Exception {
    // Python: skip LLM unless --model or --provider is explicitly supplied.
    Path src = Files.createDirectories(tmp.resolve("src/main/java"));
    Files.writeString(src.resolve("X.java"), "public class X { public int v() { return 1; } }");
    int code = new CommandLine(new Main()).execute("analyze", tmp.toString());
    assertThat(code).isEqualTo(0);
    // No "LLM analysis skipped" or "LLM analysis failed" — instead "metrics-only mode" notice.
    String err = stderr.toString(StandardCharsets.UTF_8);
    assertThat(err).contains("metrics-only");
    assertThat(err).doesNotContain("LLM analysis failed");
  }

  @Test
  void cli_jsonOutputIsAutoSilent(@TempDir Path tmp) throws Exception {
    Path src = Files.createDirectories(tmp.resolve("src/main/java"));
    Files.writeString(src.resolve("X.java"), "public class X { public int v() { return 1; } }");
    int code = new CommandLine(new Main()).execute("analyze", "--format", "json", tmp.toString());
    assertThat(code).isEqualTo(0);
    String err = stderr.toString(StandardCharsets.UTF_8);
    // metrics-only chatter must not pollute stderr when json is selected.
    assertThat(err).doesNotContain("metrics-only");
  }

  @Test
  void defaultModelForProvider_mapsAllKnownProviders() {
    assertThat(AnalyzeCommand.defaultModelForProvider("anthropic"))
        .isEqualTo("claude-3-5-sonnet-20241022");
    assertThat(AnalyzeCommand.defaultModelForProvider("openai")).isEqualTo("gpt-4o");
    assertThat(AnalyzeCommand.defaultModelForProvider("ollama")).isEqualTo("ollama:llama3");
    assertThat(AnalyzeCommand.defaultModelForProvider("unknown"))
        .isEqualTo("claude-3-5-sonnet-20241022");
  }

  @Test
  void cli_qualityGateInlineFlagPassesWhenAboveThresholds(@TempDir Path tmp) throws Exception {
    Path src = Files.createDirectories(tmp.resolve("src/main/java"));
    Files.writeString(
        src.resolve("X.java"),
        "/** module */ public class X { /** doc */ public int v() { return 1; } }");
    // Provide a permissive .mfcqi.yaml so the gate passes for the trivial sample.
    Files.writeString(
        tmp.resolve(".mfcqi.yaml"),
        "quality_gates:\n  overall:\n    mfcqi_score: 0.0\n  metrics: {}\n");
    int code = new CommandLine(new Main()).execute("analyze", "--quality-gate", tmp.toString());
    assertThat(code).isEqualTo(0);
  }

  @Test
  void cli_qualityGateInlineFailsWhenBelowThresholds(@TempDir Path tmp) throws Exception {
    Path src = Files.createDirectories(tmp.resolve("src/main/java"));
    Files.writeString(src.resolve("X.java"), "public class X {}");
    Files.writeString(
        tmp.resolve(".mfcqi.yaml"),
        "quality_gates:\n  overall:\n    mfcqi_score: 0.99\n  metrics: {}\n");
    int code = new CommandLine(new Main()).execute("analyze", "--quality-gate", tmp.toString());
    assertThat(code).isEqualTo(1);
  }
}
