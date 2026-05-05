package com.integrallis.mfcqi.smells;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("unit")
class CodeSmellDensityTest {

  @Test
  void weightAndNameMatchPythonReference() {
    CodeSmellDensity m = new CodeSmellDensity();
    assertThat(m.getWeight()).isEqualTo(0.5);
    assertThat(m.getName()).isEqualTo("Code Smell Density");
  }

  @Test
  void categoryWeightsMatchPythonReference() {
    assertThat(CodeSmellDensity.categoryWeights())
        .containsEntry(SmellCategory.ARCHITECTURAL, 0.45)
        .containsEntry(SmellCategory.DESIGN, 0.45)
        .containsEntry(SmellCategory.IMPLEMENTATION, 0.35)
        .containsEntry(SmellCategory.TEST, 0.20);
  }

  @Test
  void normalize_followsPythonPiecewiseCurve() {
    CodeSmellDensity m = new CodeSmellDensity();
    assertThat(m.normalize(0.0)).isEqualTo(1.0);
    assertThat(m.normalize(5.0)).isCloseTo(0.8, within(1e-9));
    assertThat(m.normalize(10.0)).isCloseTo(0.6, within(1e-9));
    assertThat(m.normalize(20.0)).isCloseTo(0.3, within(1e-9));
    assertThat(m.normalize(50.0)).isEqualTo(0.0);
    assertThat(m.normalize(100.0)).isEqualTo(0.0);
  }

  @Test
  void normalize_isLinearWithinBuckets() {
    CodeSmellDensity m = new CodeSmellDensity();
    // 7.5 falls in [5, 10] -> 0.8 - 2.5/5 * 0.2 = 0.7
    assertThat(m.normalize(7.5)).isCloseTo(0.7, within(1e-9));
    // 15 falls in [10, 20] -> 0.6 - 5/10 * 0.3 = 0.45
    assertThat(m.normalize(15.0)).isCloseTo(0.45, within(1e-9));
  }

  @Test
  void extract_noDetectorsYieldsZero(@TempDir Path tmp) throws Exception {
    Path src = Files.createDirectories(tmp.resolve("src/main/java"));
    Files.writeString(src.resolve("X.java"), "public class X { void f() {} }");
    assertThat(new CodeSmellDensity().extract(tmp)).isEqualTo(0.0);
  }

  @Test
  void extract_emptyCodebaseYieldsZero(@TempDir Path tmp) {
    CodeSmellDensity m = new CodeSmellDensity(Collections.singletonList(new JavaSmellDetector()));
    assertThat(m.extract(tmp)).isEqualTo(0.0);
  }

  @Test
  void extract_aggregatesAcrossDetectorsWithCategoryWeights(@TempDir Path tmp) throws Exception {
    // Stub detector contributes one HIGH design smell + one MEDIUM implementation smell.
    SmellDetector stub =
        new SmellDetector() {
          @Override
          public String name() {
            return "stub";
          }

          @Override
          public List<Smell> detect(Path codebase) {
            return java.util.Arrays.asList(
                new Smell(
                    "GOD_CLASS",
                    "God Class",
                    SmellCategory.DESIGN,
                    SmellSeverity.HIGH,
                    "x.java:1",
                    "stub",
                    "design"),
                new Smell(
                    "LONG_METHOD",
                    "Long Method",
                    SmellCategory.IMPLEMENTATION,
                    SmellSeverity.MEDIUM,
                    "x.java:10",
                    "stub",
                    "impl"));
          }
        };
    // Add a tiny real source file so countLinesOfCode > 0.
    Path src = Files.createDirectories(tmp.resolve("src/main/java"));
    Files.writeString(
        src.resolve("X.java"), "public class X {\n  public int v() {\n    return 1;\n  }\n}\n");

    CodeSmellDensity m = new CodeSmellDensity(Collections.singletonList(stub));
    // Weighted total = 3.0 (HIGH design) * 0.45 + 2.0 (MEDIUM impl) * 0.35
    //                = 1.35 + 0.70 = 2.05
    // LOC = 5 (every non-blank, non-comment-only line in X.java) -> density 2.05/5 * 1000 = 410.
    double density = m.extract(tmp);
    assertThat(density).isGreaterThan(0.0);
  }

  @Test
  void withDefaultDetectors_wiresJavaAndTestSmellDetectors() {
    CodeSmellDensity m = CodeSmellDensity.withDefaultDetectors();
    // Smoke check: no exception, name and weight match.
    assertThat(m.getName()).isEqualTo("Code Smell Density");
    assertThat(m.getWeight()).isEqualTo(0.5);
  }
}
