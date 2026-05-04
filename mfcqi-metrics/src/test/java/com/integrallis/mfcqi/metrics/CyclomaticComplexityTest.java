package com.integrallis.mfcqi.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("unit")
class CyclomaticComplexityTest {

  private final CyclomaticComplexity metric = new CyclomaticComplexity();

  @Test
  void weightAndNameMatchPythonReference() {
    // mfcqi/metrics/complexity.py:CyclomaticComplexity.get_weight() returns 0.85, get_name()
    // returns "Cyclomatic Complexity".
    assertThat(metric.getWeight()).isEqualTo(0.85);
    assertThat(metric.getName()).isEqualTo("Cyclomatic Complexity");
  }

  @Test
  void normalize_returnsOneForCcAtOrBelowOne() {
    assertThat(metric.normalize(0.5)).isEqualTo(1.0);
    assertThat(metric.normalize(1.0)).isEqualTo(1.0);
  }

  @Test
  void normalize_returnsZeroForCcAtOrAboveTwentyFive() {
    assertThat(metric.normalize(25.0)).isEqualTo(0.0);
    assertThat(metric.normalize(40.0)).isEqualTo(0.0);
  }

  @Test
  void normalize_appliesExponentialDecayInBetween() {
    // Python: math.exp(-0.1 * (CC - 1)). Spot-check CC=10 → ~0.4066.
    assertThat(metric.normalize(10.0)).isCloseTo(Math.exp(-0.9), within(1e-9));
  }

  @Test
  void extract_returnsOneForEmptyCodebase(@TempDir Path tmp) {
    // Mirrors Python's `return 1.0` when no functions are found.
    assertThat(metric.extract(tmp)).isEqualTo(1.0);
  }

  @Test
  void extract_countsBaselineOneForSimpleMethod(@TempDir Path tmp) throws Exception {
    Path src = Files.createDirectories(tmp.resolve("src/main/java"));
    Files.writeString(
        src.resolve("Simple.java"), "public class Simple { public int answer() { return 42; } }");

    assertThat(metric.extract(tmp)).isCloseTo(1.0, within(1e-9));
  }

  @Test
  void extract_countsDecisionPoints(@TempDir Path tmp) throws Exception {
    // Method with: 1 (base) + 1 (if) + 1 (for) + 1 (&& ) + 1 (?:) = 5
    String code =
        "public class Branchy {\n"
            + "  public int score(int x, int y) {\n"
            + "    int total = 0;\n"
            + "    if (x > 0 && y > 0) {\n"
            + "      for (int i = 0; i < x; i++) {\n"
            + "        total += i;\n"
            + "      }\n"
            + "    }\n"
            + "    return total > 0 ? total : -1;\n"
            + "  }\n"
            + "}";
    Path src = Files.createDirectories(tmp.resolve("src/main/java"));
    Files.writeString(src.resolve("Branchy.java"), code);

    assertThat(metric.extract(tmp)).isCloseTo(5.0, within(1e-9));
  }

  @Test
  void extract_caseLabelsCountIndividuallyButDefaultDoesNot(@TempDir Path tmp) throws Exception {
    // Method with: 1 (base) + 3 (case A,B,C) + 0 (default) = 4
    String code =
        "public class Switchy {\n"
            + "  public int letter(int n) {\n"
            + "    switch (n) {\n"
            + "      case 1: return 1;\n"
            + "      case 2: return 2;\n"
            + "      case 3: return 3;\n"
            + "      default: return 0;\n"
            + "    }\n"
            + "  }\n"
            + "}";
    Path src = Files.createDirectories(tmp.resolve("src/main/java"));
    Files.writeString(src.resolve("Switchy.java"), code);

    assertThat(metric.extract(tmp)).isCloseTo(4.0, within(1e-9));
  }

  @Test
  void extract_catchClausesEachAddOne(@TempDir Path tmp) throws Exception {
    // Method with: 1 (base) + 2 (catch clauses) = 3
    String code =
        "public class Tryful {\n"
            + "  public void doit() {\n"
            + "    try { System.out.println(); }\n"
            + "    catch (RuntimeException e) {}\n"
            + "    catch (Error e) {}\n"
            + "  }\n"
            + "}";
    Path src = Files.createDirectories(tmp.resolve("src/main/java"));
    Files.writeString(src.resolve("Tryful.java"), code);

    assertThat(metric.extract(tmp)).isCloseTo(3.0, within(1e-9));
  }

  @Test
  void extract_averagesAcrossMultipleMethods(@TempDir Path tmp) throws Exception {
    String code =
        "public class Two {\n"
            + "  public int simple() { return 1; }\n" // CC = 1
            + "  public int branchy(int x) { return x > 0 ? 1 : 0; }\n" // CC = 2
            + "}";
    Path src = Files.createDirectories(tmp.resolve("src/main/java"));
    Files.writeString(src.resolve("Two.java"), code);

    // Average of (1, 2) = 1.5
    assertThat(metric.extract(tmp)).isCloseTo(1.5, within(1e-9));
  }

  @Test
  void extract_skipsUnparseableFiles(@TempDir Path tmp) throws Exception {
    Path src = Files.createDirectories(tmp.resolve("src/main/java"));
    Files.writeString(
        src.resolve("Good.java"), "public class Good { public int v() { return 1; } }");
    Files.writeString(src.resolve("Bad.java"), "this is not valid java");

    // Bad.java is skipped (matches Python's `except (SyntaxError, UnicodeDecodeError): continue`).
    assertThat(metric.extract(tmp)).isCloseTo(1.0, within(1e-9));
  }
}
