package com.integrallis.mfcqi.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("unit")
class RFCMetricTest {

  private final RFCMetric metric = new RFCMetric();

  @Test
  void weightAndNameMatchPythonReference() {
    assertThat(metric.getWeight()).isEqualTo(0.65);
    assertThat(metric.getName()).isEqualTo("rfc");
  }

  @Test
  void normalize_followsPythonPiecewiseCurve() {
    assertThat(metric.normalize(15.0)).isEqualTo(1.0);
    assertThat(metric.normalize(50.0)).isCloseTo(0.75, within(1e-9));
    assertThat(metric.normalize(100.0)).isCloseTo(0.35, within(1e-9));
    assertThat(metric.normalize(120.0)).isEqualTo(0.0);
    assertThat(metric.normalize(200.0)).isEqualTo(0.0);
  }

  @Test
  void extract_emptyCodebaseHasZero(@TempDir Path tmp) {
    assertThat(metric.extract(tmp)).isEqualTo(0.0);
  }

  @Test
  void extract_simpleClassYieldsLocalMethodsPlusCallNames(@TempDir Path tmp) throws Exception {
    Path src = Files.createDirectories(tmp.resolve("src/main/java"));
    String code =
        "public class A {\n"
            + "  public int one() { return helper(); }\n"
            + "  public int two() { System.out.println(); return 0; }\n"
            + "  private int helper() { return 42; }\n"
            + "}";
    Files.writeString(src.resolve("A.java"), code);
    // Local methods: {one, two, helper} = 3
    // Remote calls (distinct names): {helper, println} = 2
    // RFC = 5
    assertThat(metric.extract(tmp)).isCloseTo(5.0, within(1e-9));
  }

  @Test
  void extract_returnsMaxAcrossClasses(@TempDir Path tmp) throws Exception {
    Path src = Files.createDirectories(tmp.resolve("src/main/java"));
    Files.writeString(
        src.resolve("Tiny.java"), "public class Tiny { public int v() { return 1; } }");
    // Class with many methods + many distinct calls.
    StringBuilder big = new StringBuilder("public class Big {\n");
    for (int i = 0; i < 8; i++) {
      big.append("  public void m")
          .append(i)
          .append("() { System.out.println(); h")
          .append(i)
          .append("(); }\n");
    }
    big.append("}");
    Files.writeString(src.resolve("Big.java"), big.toString());

    // Big: local = 8, remote distinct = {println, h0..h7} = 9 -> RFC = 17
    // Tiny: local = 1, remote = 0 -> RFC = 1
    assertThat(metric.extract(tmp)).isCloseTo(17.0, within(1e-9));
  }
}
