package com.integrallis.mfcqi.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("unit")
class DITMetricTest {

  private final DITMetric metric = new DITMetric();

  @Test
  void weightAndNameMatchPythonReference() {
    assertThat(metric.getWeight()).isEqualTo(0.6);
    assertThat(metric.getName()).isEqualTo("dit");
  }

  @Test
  void normalize_followsPythonCalibratedCurve() {
    assertThat(metric.normalize(0.0)).isEqualTo(1.0);
    assertThat(metric.normalize(3.0)).isEqualTo(1.0);
    assertThat(metric.normalize(6.0)).isCloseTo(0.7, within(1e-9));
    assertThat(metric.normalize(10.0)).isCloseTo(0.4, within(1e-9));
    assertThat(metric.normalize(15.0)).isEqualTo(0.0);
  }

  @Test
  void extract_noClassesYieldsZero(@TempDir Path tmp) {
    assertThat(metric.extract(tmp)).isEqualTo(0.0);
  }

  @Test
  void extract_classWithNoExtendsHasZeroDit(@TempDir Path tmp) throws Exception {
    Path src = Files.createDirectories(tmp.resolve("src/main/java"));
    Files.writeString(src.resolve("A.java"), "public class A {}");
    assertThat(metric.extract(tmp)).isEqualTo(0.0);
  }

  @Test
  void extract_externalParentYieldsOne(@TempDir Path tmp) throws Exception {
    Path src = Files.createDirectories(tmp.resolve("src/main/java"));
    Files.writeString(src.resolve("A.java"), "public class A extends RuntimeException {}");
    // External parent (not declared in this file) -> DIT = 1.
    assertThat(metric.extract(tmp)).isEqualTo(1.0);
  }

  @Test
  void extract_chainedInternalParentsGiveDeepDit(@TempDir Path tmp) throws Exception {
    Path src = Files.createDirectories(tmp.resolve("src/main/java"));
    Files.writeString(
        src.resolve("Tree.java"),
        "public class Tree {\n"
            + "  public static class Root {}\n"
            + "  public static class L1 extends Root {}\n"
            + "  public static class L2 extends L1 {}\n"
            + "  public static class L3 extends L2 {}\n"
            + "}");
    // Root=0, L1=1, L2=2, L3=3 -> max=3
    assertThat(metric.extract(tmp)).isEqualTo(3.0);
  }
}
