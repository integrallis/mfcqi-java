package com.integrallis.mfcqi.cli.commands;

import static org.assertj.core.api.Assertions.assertThat;

import com.integrallis.mfcqi.cli.Main;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

@Tag("unit")
class QualityGateCommandTest {

  private final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
  private final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
  private PrintStream originalOut;
  private PrintStream originalErr;

  @BeforeEach
  void redirect() {
    originalOut = System.out;
    originalErr = System.err;
    System.setOut(new PrintStream(stdout, true, StandardCharsets.UTF_8));
    System.setErr(new PrintStream(stderr, true, StandardCharsets.UTF_8));
  }

  @AfterEach
  void restore() {
    System.setOut(originalOut);
    System.setErr(originalErr);
  }

  private static void writeSample(Path tmp) throws Exception {
    Path src = Files.createDirectories(tmp.resolve("src/main/java"));
    Files.writeString(
        src.resolve("X.java"),
        "/** module */ public class X { /** doc */ public int v() { return 1; } }");
  }

  @Test
  void cli_passesWhenThresholdsAreLow(@TempDir Path tmp) throws Exception {
    writeSample(tmp);
    Files.writeString(
        tmp.resolve(".mfcqi.yaml"),
        "quality_gates:\n  overall:\n    mfcqi_score: 0.0\n  metrics: {}\n");
    int code = new CommandLine(new Main()).execute("quality-gate", tmp.toString());
    assertThat(code).isZero();
    String out = stdout.toString(StandardCharsets.UTF_8);
    assertThat(out).contains("MFCQI score:");
    assertThat(out).contains("Overall gate: PASS");
    assertThat(out).contains("ALL GATES PASS");
  }

  @Test
  void cli_failsWhenOverallThresholdTooHigh(@TempDir Path tmp) throws Exception {
    writeSample(tmp);
    Files.writeString(
        tmp.resolve(".mfcqi.yaml"),
        "quality_gates:\n  overall:\n    mfcqi_score: 0.99\n  metrics: {}\n");
    int code = new CommandLine(new Main()).execute("quality-gate", tmp.toString());
    assertThat(code).isEqualTo(1);
    String out = stdout.toString(StandardCharsets.UTF_8);
    assertThat(out).contains("Overall gate: FAIL");
    assertThat(out).contains("gate(s) failed");
  }

  @Test
  void cli_printsPerMetricLinesWhenMetricGatesConfigured(@TempDir Path tmp) throws Exception {
    writeSample(tmp);
    // A passing metric gate (threshold 0.0) so the per-metric PASS branch is exercised. The
    // metric key must match the calculator's display name ("Cyclomatic Complexity").
    Files.writeString(
        tmp.resolve(".mfcqi.yaml"),
        "quality_gates:\n"
            + "  overall:\n"
            + "    mfcqi_score: 0.0\n"
            + "  metrics:\n"
            + "    Cyclomatic Complexity: 0.0\n");
    int code = new CommandLine(new Main()).execute("quality-gate", tmp.toString());
    assertThat(code).isZero();
    String out = stdout.toString(StandardCharsets.UTF_8);
    assertThat(out).contains("Cyclomatic Complexity");
    assertThat(out).contains("(threshold");
  }

  @Test
  void cli_metricGateFailureProducesExitOne(@TempDir Path tmp) throws Exception {
    writeSample(tmp);
    // Threshold above the max possible score (1.0) guarantees the metric gate fails -> exit 1.
    Files.writeString(
        tmp.resolve(".mfcqi.yaml"),
        "quality_gates:\n"
            + "  overall:\n"
            + "    mfcqi_score: 0.0\n"
            + "  metrics:\n"
            + "    Cyclomatic Complexity: 1.01\n");
    int code = new CommandLine(new Main()).execute("quality-gate", tmp.toString());
    assertThat(code).isEqualTo(1);
    assertThat(stdout.toString(StandardCharsets.UTF_8)).contains("FAIL Cyclomatic Complexity");
  }

  @Test
  void cli_explicitConfigOptionIsUsed(@TempDir Path tmp) throws Exception {
    writeSample(tmp);
    Path cfg = tmp.resolve("gates.yaml");
    Files.writeString(cfg, "quality_gates:\n  overall:\n    mfcqi_score: 0.0\n  metrics: {}\n");
    int code =
        new CommandLine(new Main())
            .execute("quality-gate", "--config", cfg.toString(), tmp.toString());
    assertThat(code).isZero();
    assertThat(stdout.toString(StandardCharsets.UTF_8)).contains("Overall gate: PASS");
  }

  @Test
  void cli_fallsBackToDefaultsWhenNoConfigPresent(@TempDir Path tmp) throws Exception {
    // No .mfcqi.yaml present -> QualityGateConfig.fromDefaults() path is exercised. The exact
    // pass/fail depends on the default thresholds, so assert only on the structural output and
    // that a valid gate exit code (0 = pass, 1 = fail) is returned.
    writeSample(tmp);
    int code = new CommandLine(new Main()).execute("quality-gate", tmp.toString());
    assertThat(code).isIn(0, 1);
    String out = stdout.toString(StandardCharsets.UTF_8);
    assertThat(out).contains("MFCQI score:");
    assertThat(out).contains("Overall gate:");
  }
}
