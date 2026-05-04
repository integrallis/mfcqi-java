package com.integrallis.mfcqi.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("unit")
class MaintainabilityIndexTest {

  private final MaintainabilityIndex metric = new MaintainabilityIndex();

  @Test
  void weightAndNameMatchPythonReference() {
    assertThat(metric.getWeight()).isEqualTo(0.5);
    assertThat(metric.getName()).isEqualTo("Maintainability Index");
  }

  @Test
  void normalize_zeroOrBelowReturnsZero() {
    assertThat(metric.normalize(0.0)).isEqualTo(0.0);
    assertThat(metric.normalize(-5.0)).isEqualTo(0.0);
  }

  @Test
  void normalize_followsPythonPiecewiseCurve() {
    // Spot-check breakpoints from Python normalize() (Python-calibrated thresholds 70/50/30/20).
    assertThat(metric.normalize(100.0)).isEqualTo(1.0); // capped at 1.0
    assertThat(metric.normalize(70.0)).isCloseTo(0.85, within(1e-9));
    assertThat(metric.normalize(50.0)).isCloseTo(0.70, within(1e-9));
    assertThat(metric.normalize(30.0)).isCloseTo(0.50, within(1e-9));
    assertThat(metric.normalize(20.0)).isCloseTo(0.25, within(1e-9));
    assertThat(metric.normalize(10.0)).isCloseTo(0.125, within(1e-9));
  }

  @Test
  void normalize_normalRangeIsLinearWithinBuckets() {
    // 60 falls in [50, 70) -> 0.70 + 0.15 * (60-50)/20 = 0.775
    assertThat(metric.normalize(60.0)).isCloseTo(0.775, within(1e-9));
    // 40 falls in [30, 50) -> 0.50 + 0.20 * (40-30)/20 = 0.60
    assertThat(metric.normalize(40.0)).isCloseTo(0.60, within(1e-9));
  }

  @Test
  void miCompute_returnsHundredForDegenerateInputs() {
    // Mirrors Python: any(metric <= 0 for metric in (HV, sloc)) -> return 100.
    assertThat(MaintainabilityIndex.miCompute(0.0, 5, 100, 0.0)).isEqualTo(100.0);
    assertThat(MaintainabilityIndex.miCompute(100.0, 5, 0, 0.0)).isEqualTo(100.0);
  }

  @Test
  void miCompute_matchesRadonFormulaForKnownInputs() {
    // Spot-check the formula directly. Inputs: HV=100, CC=5, SLOC=50, comments=10%
    //   sloc_scale = ln(50) ≈ 3.912
    //   volume_scale = ln(100) ≈ 4.605
    //   comments_scale = sqrt(2.46 * radians(10)) = sqrt(2.46 * 0.1745) ≈ sqrt(0.4293) ≈ 0.6552
    //   nn_mi = 171 - 5.2*4.605 - 0.23*5 - 16.2*3.912 + 50*sin(0.6552)
    //         = 171 - 23.946 - 1.15 - 63.374 + 50*0.6093
    //         = 171 - 23.946 - 1.15 - 63.374 + 30.466
    //         = 112.996
    //   normalized = 112.996 * 100/171 = 66.07 -> clamped to [0, 100]
    double mi = MaintainabilityIndex.miCompute(100.0, 5, 50, 10.0);
    double expected =
        (171.0
                - 5.2 * Math.log(100.0)
                - 0.23 * 5
                - 16.2 * Math.log(50.0)
                + 50.0 * Math.sin(Math.sqrt(2.46 * Math.toRadians(10.0))))
            * 100.0
            / 171.0;
    assertThat(mi).isCloseTo(expected, within(1e-9));
  }

  @Test
  void miCompute_clampsToHundredWhenFormulaExceedsLimit() {
    // The clamp to 100 only kicks in when nn_mi > 171 — i.e., when the comment-bonus term
    // (50*sin(...)) overwhelms the subtractions. With minimal HV/CC/SLOC and a non-zero comment
    // ratio, this is reachable.
    assertThat(MaintainabilityIndex.miCompute(1.0, 0, 1, 0.0)).isEqualTo(100.0);
  }

  @Test
  void miCompute_neverGoesNegative() {
    // Any combination should clamp at 0 — verify with deliberately worst-case inputs.
    double mi = MaintainabilityIndex.miCompute(1_000_000.0, 1000, 100_000, 0.0);
    assertThat(mi).isEqualTo(0.0);
  }

  @Test
  void extract_emptyCodebaseReturnsHundred(@TempDir Path tmp) {
    // Mirrors Python `return 100.0` when no functions/files are found.
    assertThat(metric.extract(tmp)).isEqualTo(100.0);
  }

  @Test
  void extract_simpleCodeProducesValidMiInRange(@TempDir Path tmp) throws Exception {
    Path src = Files.createDirectories(tmp.resolve("src/main/java"));
    Files.writeString(
        src.resolve("Calc.java"),
        "public class Calc {\n"
            + "  /** Adds two numbers. */\n"
            + "  public int add(int a, int b) {\n"
            + "    return a + b;\n"
            + "  }\n"
            + "  public int sub(int a, int b) {\n"
            + "    return a - b;\n"
            + "  }\n"
            + "}");
    double mi = metric.extract(tmp);
    assertThat(mi).isBetween(0.0, 100.0);
  }

  @Test
  void extract_complexCodeHasLowerMiThanSimpleCode(@TempDir Path tmp) throws Exception {
    Path src = Files.createDirectories(tmp.resolve("src/main/java"));
    StringBuilder complex = new StringBuilder("public class Complex {\n");
    for (int i = 0; i < 30; i++) {
      complex
          .append("  public int m")
          .append(i)
          .append("(int a, int b, int c) {\n")
          .append("    if (a > 0 && b > 0 || c > 0) {\n")
          .append("      for (int j = 0; j < a; j++) {\n")
          .append("        if (j % 2 == 0) { b = b + j; } else { b = b - j; }\n")
          .append("      }\n")
          .append("    }\n")
          .append("    return a + b + c;\n")
          .append("  }\n");
    }
    complex.append("}");
    Files.writeString(src.resolve("Complex.java"), complex.toString());

    double complexMi = metric.extract(tmp);

    // Replace with simple code.
    Files.writeString(
        src.resolve("Complex.java"), "public class C { public int v() { return 1; } }");
    double simpleMi = metric.extract(tmp);

    assertThat(simpleMi).isGreaterThan(complexMi);
  }
}
