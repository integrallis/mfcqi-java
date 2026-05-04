package com.integrallis.mfcqi.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Behaviour-parity tests for {@link CognitiveComplexity}. Where possible, expected counts are
 * derived from the published Campbell 2018 examples or directly traced through the Python {@code
 * cognitive_complexity} algorithm.
 */
@Tag("unit")
class CognitiveComplexityTest {

  private final CognitiveComplexity metric = new CognitiveComplexity();

  @Test
  void weightAndNameMatchPythonReference() {
    assertThat(metric.getWeight()).isEqualTo(0.75);
    assertThat(metric.getName()).isEqualTo("Cognitive Complexity");
  }

  @Test
  void normalize_returnsOneForZeroAverage() {
    CognitiveResult zero =
        new CognitiveResult(
            0.0, java.util.Collections.emptyList(), java.util.Collections.emptyList());
    assertThat(metric.normalize(zero)).isEqualTo(1.0);
  }

  @Test
  void normalize_followsPythonPiecewiseCurve() {
    // Spot-check breakpoints from the Python normalize(). The Python source uses 16.67 (a
    // 4-significant-digit approximation of 50/3) in the avg<=15 branch, so the value at avg=15
    // is 0.7 - 5/16.67 = 0.40006... not exactly 0.4. We assert the Python-produced value
    // verbatim to lock in parity, including this approximation drift.
    assertThat(metric.normalize(result(5.0))).isCloseTo(0.9, within(1e-9));
    assertThat(metric.normalize(result(10.0))).isCloseTo(0.7, within(1e-9));
    assertThat(metric.normalize(result(15.0))).isCloseTo(0.7 - 5.0 / 16.67, within(1e-9));
    assertThat(metric.normalize(result(25.0))).isCloseTo(0.2, within(1e-9));
  }

  @Test
  void normalize_clampsAtZeroForExtremeAverage() {
    assertThat(metric.normalize(result(1000.0))).isEqualTo(0.0);
  }

  @Test
  void extract_emptyCodebaseReturnsZeroAverage(@TempDir Path tmp) {
    CognitiveResult r = metric.extract(tmp);
    assertThat(r.average()).isEqualTo(0.0);
    assertThat(r.functions()).isEmpty();
    assertThat(r.hotspots()).isEmpty();
  }

  @Test
  void extract_simpleMethodHasZeroCognitiveComplexity(@TempDir Path tmp) throws Exception {
    Path src = Files.createDirectories(tmp.resolve("src/main/java"));
    Files.writeString(src.resolve("Simple.java"), "public class Simple { int v() { return 1; } }");
    CognitiveResult r = metric.extract(tmp);
    assertThat(r.average()).isEqualTo(0.0);
    assertThat(r.functions()).hasSize(1);
  }

  @Test
  void extract_singleIfIsOne(@TempDir Path tmp) throws Exception {
    // Python algorithm: if (no else, no nesting) -> increment_by=1, base = max(1, 1) = 1
    String code =
        "public class A {\n"
            + "  int v(int x) {\n"
            + "    if (x > 0) { return 1; }\n"
            + "    return 0;\n"
            + "  }\n"
            + "}";
    write(tmp, "A.java", code);
    assertThat(metric.extract(tmp).average()).isEqualTo(1.0);
  }

  @Test
  void extract_ifElseIsTwo(@TempDir Path tmp) throws Exception {
    // Python: hasElse -> increment=1, increment_by=1, base = max(1,1) + 1 = 2
    String code =
        "public class A {\n"
            + "  int v(int x) {\n"
            + "    if (x > 0) { return 1; } else { return 0; }\n"
            + "  }\n"
            + "}";
    write(tmp, "A.java", code);
    assertThat(metric.extract(tmp).average()).isEqualTo(2.0);
  }

  @Test
  void extract_ifElifIsTwo(@TempDir Path tmp) throws Exception {
    // Python: outer if (elif chain) -> increment=0, increment_by stays -> base=1
    //         inner if -> no else -> base=1
    // total = 2
    String code =
        "public class A {\n"
            + "  int v(int x) {\n"
            + "    if (x > 0) { return 1; }\n"
            + "    else if (x < 0) { return -1; }\n"
            + "    return 0;\n"
            + "  }\n"
            + "}";
    write(tmp, "A.java", code);
    assertThat(metric.extract(tmp).average()).isEqualTo(2.0);
  }

  @Test
  void extract_ifElifElseIsThree(@TempDir Path tmp) throws Exception {
    // Python: outer if (elif chain) -> 1; inner if has else -> base = max(1,1)+1 = 2
    // total = 3
    String code =
        "public class A {\n"
            + "  int v(int x) {\n"
            + "    if (x > 0) { return 1; }\n"
            + "    else if (x < 0) { return -1; }\n"
            + "    else { return 0; }\n"
            + "  }\n"
            + "}";
    write(tmp, "A.java", code);
    assertThat(metric.extract(tmp).average()).isEqualTo(3.0);
  }

  @Test
  void extract_nestedIfDoublesPenalty(@TempDir Path tmp) throws Exception {
    // Outer if: base = max(1, 1) = 1
    // Inner if (nested inside outer): increment_by=2, base = max(1, 2) = 2
    // total = 3
    String code =
        "public class A {\n"
            + "  void v(int x, int y) {\n"
            + "    if (x > 0) {\n"
            + "      if (y > 0) {\n"
            + "        System.out.println(\"both\");\n"
            + "      }\n"
            + "    }\n"
            + "  }\n"
            + "}";
    write(tmp, "A.java", code);
    assertThat(metric.extract(tmp).average()).isEqualTo(3.0);
  }

  @Test
  void extract_singleBooleanChainCountsOnce(@TempDir Path tmp) throws Exception {
    // `a && b && c` = one same-op chain = 1; the if itself = 1; total = 2
    String code =
        "public class A {\n"
            + "  void v(boolean a, boolean b, boolean c) {\n"
            + "    if (a && b && c) { System.out.println(); }\n"
            + "  }\n"
            + "}";
    write(tmp, "A.java", code);
    assertThat(metric.extract(tmp).average()).isEqualTo(2.0);
  }

  @Test
  void extract_mixedBooleanChainCountsTransitions(@TempDir Path tmp) throws Exception {
    // `a && b || c` = AND chunk + OR chunk = 2; if = 1; total = 3
    String code =
        "public class A {\n"
            + "  void v(boolean a, boolean b, boolean c) {\n"
            + "    if (a && b || c) { System.out.println(); }\n"
            + "  }\n"
            + "}";
    write(tmp, "A.java", code);
    assertThat(metric.extract(tmp).average()).isEqualTo(3.0);
  }

  @Test
  void extract_ternaryCountsAsOneAtRootNesting(@TempDir Path tmp) throws Exception {
    // Ternary at top level: increment_by=1, base = max(1,1) = 1
    String code = "public class A {\n" + "  int v(int x) { return x > 0 ? 1 : -1; }\n" + "}";
    write(tmp, "A.java", code);
    assertThat(metric.extract(tmp).average()).isEqualTo(1.0);
  }

  @Test
  void extract_catchAddsOnePerHandler(@TempDir Path tmp) throws Exception {
    String code =
        "public class A {\n"
            + "  void v() {\n"
            + "    try { System.out.println(); }\n"
            + "    catch (RuntimeException e) {}\n"
            + "    catch (Error e) {}\n"
            + "  }\n"
            + "}";
    write(tmp, "A.java", code);
    // Two catch handlers, neither nested inside the other, both at depth 1.
    assertThat(metric.extract(tmp).average()).isEqualTo(2.0);
  }

  @Test
  void extract_recursiveCallAddsOne(@TempDir Path tmp) throws Exception {
    // Python: `if has_recursive_calls(funcdef): complexity += 1`
    String code =
        "public class A {\n"
            + "  int fact(int n) {\n"
            + "    if (n <= 1) { return 1; }\n"
            + "    return n * fact(n - 1);\n"
            + "  }\n"
            + "}";
    write(tmp, "A.java", code);
    // if = 1, recursive call = +1 -> total 2
    assertThat(metric.extract(tmp).average()).isEqualTo(2.0);
  }

  @Test
  void extract_hotspotsAreSortedAndThresholded(@TempDir Path tmp) throws Exception {
    // Build a class with one method whose CC will exceed the default threshold (15).
    StringBuilder body = new StringBuilder("int v(int x) {\n  int n = 0;\n");
    for (int i = 0; i < 20; i++) {
      body.append("  if (x == ").append(i).append(") { n++; }\n");
    }
    body.append("  return n;\n}\n");
    String code = "public class Hot { " + body + " }";
    write(tmp, "Hot.java", code);

    CognitiveResult r = metric.extract(tmp);
    assertThat(r.hotspots()).hasSize(1);
    assertThat(r.hotspots().get(0).complexity()).isGreaterThanOrEqualTo(15);
    assertThat(r.hotspots().get(0).functionName()).isEqualTo("v");
  }

  @Test
  void extract_skipsTestDirectories(@TempDir Path tmp) throws Exception {
    // Python skips files whose parent path contains a part starting with "test".
    Path mainSrc = Files.createDirectories(tmp.resolve("src/main/java"));
    Path testSrc = Files.createDirectories(tmp.resolve("tests"));
    Files.writeString(mainSrc.resolve("Keep.java"), "public class Keep { int v() { return 1; } }");
    Files.writeString(
        testSrc.resolve("KeepTest.java"),
        "public class KeepTest { int v() { if(true){return 1;} return 0; } }");

    CognitiveResult r = metric.extract(tmp);
    assertThat(r.functions()).hasSize(1);
    assertThat(r.functions().get(0).functionName()).isEqualTo("v");
    assertThat(r.functions().get(0).filePath()).contains("Keep.java");
  }

  private static void write(Path tmp, String fileName, String code) throws java.io.IOException {
    Path src = Files.createDirectories(tmp.resolve("src/main/java"));
    Files.writeString(src.resolve(fileName), code);
  }

  private static CognitiveResult result(double average) {
    return new CognitiveResult(
        average, java.util.Collections.emptyList(), java.util.Collections.emptyList());
  }
}
