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

  @Test
  void normalizeModel_prefixesOllamaModelsWhenProviderForced() {
    // --provider ollama with a bare model must gain the ollama: prefix so it routes locally
    // (and validation does not demand an API key).
    assertThat(AnalyzeCommand.normalizeModel("qwen3-coder", AnalyzeCommand.ProviderName.ollama))
        .isEqualTo("ollama:qwen3-coder");
    // Already-prefixed forms (ollama: or ollama/) are left untouched.
    assertThat(AnalyzeCommand.normalizeModel("ollama:llama3", AnalyzeCommand.ProviderName.ollama))
        .isEqualTo("ollama:llama3");
    assertThat(AnalyzeCommand.normalizeModel("ollama/mistral", AnalyzeCommand.ProviderName.ollama))
        .isEqualTo("ollama/mistral");
  }

  @Test
  void normalizeModel_leavesNonOllamaModelsUnchanged() {
    assertThat(AnalyzeCommand.normalizeModel("gpt-4o", AnalyzeCommand.ProviderName.openai))
        .isEqualTo("gpt-4o");
    assertThat(AnalyzeCommand.normalizeModel("claude-sonnet-4-5", null))
        .isEqualTo("claude-sonnet-4-5");
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
  void render_htmlHasStyledScoreAndMetrics(@TempDir Path tmp) {
    Map<String, Double> metrics = new LinkedHashMap<>();
    metrics.put("mfcqi_score", 0.85);
    metrics.put("security", 0.42);
    String html = AnalyzeCommand.renderHtml(tmp, 0.85, metrics, null);
    assertThat(html).contains("<!DOCTYPE html>");
    assertThat(html).contains("MFCQI Analysis Report");
    // Score color: 0.85 -> green hex from Python.
    assertThat(html).contains("color: #00aa00");
    // Metric row.
    assertThat(html).contains("<strong>security:</strong> 0.42");
  }

  @Test
  void render_htmlIncludesRecommendationsWhenPresent(@TempDir Path tmp) {
    Map<String, Double> metrics = new LinkedHashMap<>();
    metrics.put("mfcqi_score", 0.5);
    metrics.put("security", 0.3);
    com.integrallis.mfcqi.analysis.AnalysisResult llm =
        new com.integrallis.mfcqi.analysis.AnalysisResult(
            0.5,
            metrics,
            java.util.Arrays.asList("[HIGH] Fix MD5 in Crypto.java"),
            "claude-sonnet-4-5");
    String html = AnalyzeCommand.renderHtml(tmp, 0.5, metrics, llm);
    assertThat(html).contains("AI Recommendations");
    assertThat(html).contains("Fix MD5 in Crypto.java");
  }

  @Test
  void render_htmlEscapesAngleBrackets(@TempDir Path tmp) {
    Map<String, Double> metrics = new LinkedHashMap<>();
    metrics.put("mfcqi_score", 0.5);
    metrics.put("<script>alert('xss')</script>", 0.5);
    String html = AnalyzeCommand.renderHtml(tmp, 0.5, metrics, null);
    assertThat(html).doesNotContain("<script>alert").contains("&lt;script&gt;");
  }

  @Test
  void scoreColorHex_followsPythonThresholds() {
    assertThat(AnalyzeCommand.scoreColorHex(0.85)).isEqualTo("#00aa00");
    assertThat(AnalyzeCommand.scoreColorHex(0.65)).isEqualTo("#ffaa00");
    assertThat(AnalyzeCommand.scoreColorHex(0.20)).isEqualTo("#aa0000");
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
            "claude-sonnet-4-5");
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
  void toolOutputCollector_populatesRawCcAndHvForRealCode(@TempDir Path tmp) throws Exception {
    // Round-2 review Low E: raw CC/HV in tool outputs must be real averages, not placeholder 0.0.
    Path src = Files.createDirectories(tmp.resolve("src/main/java"));
    Files.writeString(
        src.resolve("Sample.java"),
        "public class Sample {\n"
            + "  public int score(int x, int y) {\n"
            + "    if (x > 0 && y > 0) {\n"
            + "      for (int i = 0; i < x; i++) {\n"
            + "        if (i % 2 == 0) { y += i; } else { y -= i; }\n"
            + "      }\n"
            + "    }\n"
            + "    return y;\n"
            + "  }\n"
            + "}");
    com.integrallis.mfcqi.analysis.ToolOutputs out =
        com.integrallis.mfcqi.cli.ToolOutputCollector.collect(tmp, new LinkedHashMap<>());
    assertThat(out.getCyclomaticComplexityRaw()).isGreaterThan(0.0);
    assertThat(out.getHalsteadVolumeRaw()).isGreaterThan(0.0);
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
  void cli_helpListsParallelismOption() {
    int code = new CommandLine(new Main()).execute("analyze", "--help");

    assertThat(code).isEqualTo(0);
    assertThat(stdout.toString(StandardCharsets.UTF_8)).contains("--parallelism");
  }

  @Test
  void cli_parallelismOptionRunsAnalysis(@TempDir Path tmp) throws Exception {
    Path src = Files.createDirectories(tmp.resolve("src/main/java"));
    Files.writeString(src.resolve("X.java"), "public class X { public int v() { return 1; } }");

    int code =
        new CommandLine(new Main())
            .execute(
                "analyze", "--skip-llm", "--parallelism", "2", "--format", "json", tmp.toString());

    assertThat(code).isEqualTo(0);
    assertThat(stdout.toString(StandardCharsets.UTF_8)).contains("\"mfcqi_score\"");
  }

  @Test
  void cli_autoDetectsKotlinAndRunsTheFullMetricContract(@TempDir Path tmp) throws Exception {
    Files.writeString(
        tmp.resolve("Sample.kt"),
        "/** Sample. */ class Sample { /** Value. */ fun value(x: Int) = if (x > 0) x else 0 }");

    int code =
        new CommandLine(new Main())
            .execute("analyze", "--skip-llm", "--format", "json", tmp.toString());

    assertThat(code).isZero();
    assertThat(stdout.toString(StandardCharsets.UTF_8))
        .contains("\"Cyclomatic Complexity\"")
        .contains("\"Cognitive Complexity\"")
        .contains("\"Dependency Security\"")
        .contains("\"Lack of Cohesion of Methods\"");
  }

  @Test
  void cli_autoDetectsMixedSources(@TempDir Path tmp) throws Exception {
    Files.writeString(
        tmp.resolve("Sample.java"), "public class Sample { int value() { return 1; } }");
    Files.writeString(
        tmp.resolve("Complex.kt"),
        "fun complex(x: Int) = if (x > 0 && x < 10) x else if (x < -10) -x else 0");

    int code =
        new CommandLine(new Main())
            .execute("analyze", "--skip-llm", "--format", "terminal", tmp.toString());

    assertThat(code).isZero();
    assertThat(stderr.toString(StandardCharsets.UTF_8)).contains("Analyzing as mixed.");
    assertThat(stdout.toString(StandardCharsets.UTF_8))
        .contains("Cyclomatic Complexity")
        .contains("Cognitive Complexity")
        .contains("Dependency Security");
  }

  @Test
  void cli_parallelismRejectsNonPositiveValue(@TempDir Path tmp) throws Exception {
    Path src = Files.createDirectories(tmp.resolve("src/main/java"));
    Files.writeString(src.resolve("X.java"), "public class X { public int v() { return 1; } }");

    int code = new CommandLine(new Main()).execute("analyze", "--parallelism", "0", tmp.toString());

    assertThat(code).isEqualTo(2);
    assertThat(stderr.toString(StandardCharsets.UTF_8)).contains("parallelism must be positive");
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
    assertThat(AnalyzeCommand.defaultModelForProvider(AnalyzeCommand.ProviderName.anthropic))
        .isEqualTo("claude-sonnet-4-5");
    assertThat(AnalyzeCommand.defaultModelForProvider(AnalyzeCommand.ProviderName.openai))
        .isEqualTo("gpt-4o");
    assertThat(AnalyzeCommand.defaultModelForProvider(AnalyzeCommand.ProviderName.ollama))
        .isEqualTo("ollama:llama3");
  }

  @Test
  void cli_unknownProviderIsRejectedAtParseTime(@TempDir Path tmp) throws Exception {
    Path src = Files.createDirectories(tmp.resolve("src/main/java"));
    Files.writeString(src.resolve("X.java"), "public class X { public int v() { return 1; } }");
    int code =
        new CommandLine(new Main())
            .execute("analyze", "--provider", "bogus-provider", tmp.toString());
    // Picocli returns 2 for usage errors — matches Python click.Choice's UsageError exit.
    assertThat(code).isEqualTo(2);
    String err = stderr.toString(StandardCharsets.UTF_8);
    assertThat(err).containsIgnoringCase("provider");
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
