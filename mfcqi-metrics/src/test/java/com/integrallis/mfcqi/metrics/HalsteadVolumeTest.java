package com.integrallis.mfcqi.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("unit")
class HalsteadVolumeTest {

  private final HalsteadVolume metric = new HalsteadVolume();

  @Test
  void weightAndNameMatchPythonReference() {
    assertThat(metric.getWeight()).isEqualTo(0.65);
    assertThat(metric.getName()).isEqualTo("Halstead Volume");
  }

  @Test
  void normalize_atZeroReturnsOne() {
    assertThat(metric.normalize(0.0)).isEqualTo(1.0);
    assertThat(metric.normalize(-5.0)).isEqualTo(1.0);
  }

  @Test
  void normalize_atOrAboveUpperCutoffReturnsZero() {
    // Java cutoff is 25000 (= Python's 5000 scaled by the ~5x token-model factor).
    assertThat(metric.normalize(25000.0)).isEqualTo(0.0);
    assertThat(metric.normalize(40000.0)).isEqualTo(0.0);
  }

  @Test
  void normalize_appliesTanhInBetween() {
    // Java-recalibrated: 1 - tanh(V/12500). Spot-check V=12500 → 1 - tanh(1.0) ≈ 0.2384.
    assertThat(metric.normalize(12500.0)).isCloseTo(1.0 - Math.tanh(1.0), within(1e-9));
    assertThat(metric.normalize(2500.0)).isCloseTo(1.0 - Math.tanh(0.2), within(1e-9));
  }

  @Test
  void extract_returnsZeroForEmptyCodebase(@TempDir Path tmp) {
    // Mirrors Python's `return 0.0` when no parseable code is found.
    assertThat(metric.extract(tmp)).isEqualTo(0.0);
  }

  @Test
  void extract_producesPositiveVolumeForRealCode(@TempDir Path tmp) throws Exception {
    Path src = Files.createDirectories(tmp.resolve("src/main/java"));
    Files.writeString(
        src.resolve("Sample.java"),
        "public class Sample {\n"
            + "  public int compute(int x, int y) {\n"
            + "    int sum = x + y;\n"
            + "    int product = x * y;\n"
            + "    return sum - product;\n"
            + "  }\n"
            + "}");

    double v = metric.extract(tmp);
    // We don't assert an exact volume because operator/operand classification can shift between
    // implementations, but for any non-trivial file Halstead volume must be strictly positive.
    assertThat(v).isPositive();
  }

  @Test
  void extract_largerCodebaseHasLargerAverageVolume(@TempDir Path tmp) throws Exception {
    Path src = Files.createDirectories(tmp.resolve("src/main/java"));
    Files.writeString(
        src.resolve("Tiny.java"), "public class Tiny { public int v() { return 1; } }");
    double tiny = metric.extract(tmp);

    // Add a much larger file with diverse identifiers and literals.
    StringBuilder big = new StringBuilder("public class Big {\n");
    for (int i = 0; i < 20; i++) {
      big.append("  public int m").append(i).append("(int a").append(i).append(") {\n");
      big.append("    int x")
          .append(i)
          .append(" = a")
          .append(i)
          .append(" + ")
          .append(i + 1)
          .append(";\n");
      big.append("    return x").append(i).append(" * ").append(i + 2).append(";\n");
      big.append("  }\n");
    }
    big.append("}");
    Files.writeString(src.resolve("Big.java"), big.toString());

    double avg = metric.extract(tmp);
    assertThat(avg).isGreaterThan(tiny);
  }

  @Test
  void extract_skipsUnparseableFiles(@TempDir Path tmp) throws Exception {
    Path src = Files.createDirectories(tmp.resolve("src/main/java"));
    Files.writeString(src.resolve("Good.java"), "public class Good { int v() { return 1; } }");
    Files.writeString(src.resolve("Bad.java"), "}}}}}");

    assertThat(metric.extract(tmp)).isPositive();
  }
}
