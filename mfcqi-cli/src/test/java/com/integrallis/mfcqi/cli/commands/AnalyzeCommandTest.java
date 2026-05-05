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
  void render_sarifFlagsLowScoringMetrics(@TempDir Path tmp) {
    Map<String, Double> metrics = new LinkedHashMap<>();
    metrics.put("mfcqi_score", 0.4);
    metrics.put("security", 0.2);
    metrics.put("Cyclomatic Complexity", 0.95);
    String sarif = AnalyzeCommand.renderSarif(tmp, 0.4, metrics, null);
    // Below-warning metric (security 0.2) appears as an error-level result.
    assertThat(sarif).contains("\"version\" : \"2.1.0\"");
    assertThat(sarif).contains("\"ruleId\" : \"mfcqi/security\"");
    assertThat(sarif).contains("\"level\" : \"error\"");
    // A high-scoring metric must NOT produce a SARIF result.
    assertThat(sarif).doesNotContain("\"ruleId\" : \"mfcqi/cyclomatic complexity\"");
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
}
