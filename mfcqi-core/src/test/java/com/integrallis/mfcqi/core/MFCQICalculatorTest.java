package com.integrallis.mfcqi.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("unit")
class MFCQICalculatorTest {

  @Test
  void geometricMean_emptyReturnsZero() {
    assertThat(MFCQICalculator.geometricMean(Collections.emptyList())).isEqualTo(0.0);
  }

  @Test
  void geometricMean_allOnesReturnsOne() {
    assertThat(MFCQICalculator.geometricMean(Arrays.asList(1.0, 1.0, 1.0))).isEqualTo(1.0);
  }

  @Test
  void geometricMean_appliesFloorOfTenPercent() {
    // 0.0 should be lifted to 0.1 before multiplying so a single zero does not collapse the score.
    double mean = MFCQICalculator.geometricMean(Arrays.asList(0.0, 1.0));
    assertThat(mean).isCloseTo(Math.sqrt(0.1), within(1e-9));
  }

  @Test
  void geometricMean_matchesPythonReference() {
    // Three metrics: 0.8, 0.6, 0.4 -> floor doesn't kick in; expect (0.8*0.6*0.4)^(1/3).
    double expected = Math.pow(0.8 * 0.6 * 0.4, 1.0 / 3.0);
    double actual = MFCQICalculator.geometricMean(Arrays.asList(0.8, 0.6, 0.4));
    assertThat(actual).isCloseTo(expected, within(1e-9));
  }

  @Test
  void calculate_withZeroMetricsReturnsZero(@TempDir Path tmp) throws java.io.IOException {
    seedJavaFile(tmp);
    MFCQICalculator calc = MFCQICalculator.builder().build();
    assertThat(calc.calculate(tmp)).isEqualTo(0.0);
  }

  @Test
  void calculate_combinesStubMetrics(@TempDir Path tmp) throws java.io.IOException {
    seedJavaFile(tmp);
    MFCQICalculator calc =
        MFCQICalculator.builder()
            .addMetric(new ConstantMetric("a", 1.0))
            .addMetric(new ConstantMetric("b", 0.5))
            .build();

    double expected = Math.pow(1.0 * 0.5, 0.5);
    assertThat(calc.calculate(tmp)).isCloseTo(expected, within(1e-9));
  }

  @Test
  void detailedMetrics_includesEachMetricAndOverall(@TempDir Path tmp) throws java.io.IOException {
    seedJavaFile(tmp);
    MFCQICalculator calc =
        MFCQICalculator.builder()
            .addMetric(new ConstantMetric("a", 0.9))
            .addMetric(new ConstantMetric("b", 0.7))
            .build();

    java.util.Map<String, Double> detail = calc.detailedMetrics(tmp);
    assertThat(detail).containsKeys("a", "b", "mfcqi_score");
    assertThat(detail.get("a")).isEqualTo(0.9);
    assertThat(detail.get("b")).isEqualTo(0.7);
    assertThat(detail.get("mfcqi_score")).isCloseTo(Math.pow(0.9 * 0.7, 0.5), within(1e-9));
  }

  @Test
  void calculate_emptyCodebaseShortCircuitsToZero(@TempDir Path tmp) {
    // No .java files anywhere — Python returns 0.0 to avoid misleading partial scores from
    // metrics that default to 1.0 on empty input.
    MFCQICalculator calc =
        MFCQICalculator.builder()
            .addMetric(new ConstantMetric("a", 1.0))
            .addMetric(new ConstantMetric("b", 1.0))
            .build();
    assertThat(calc.calculate(tmp)).isEqualTo(0.0);
  }

  @Test
  void detailedMetrics_emptyCodebaseEmitsAllZeros(@TempDir Path tmp) {
    MFCQICalculator calc =
        MFCQICalculator.builder()
            .addMetric(new ConstantMetric("a", 1.0))
            .addMetric(new ConstantMetric("b", 1.0))
            .build();
    java.util.Map<String, Double> detail = calc.detailedMetrics(tmp);
    assertThat(detail.get("a")).isEqualTo(0.0);
    assertThat(detail.get("b")).isEqualTo(0.0);
    assertThat(detail.get("mfcqi_score")).isEqualTo(0.0);
  }

  private static void seedJavaFile(Path tmp) throws java.io.IOException {
    Path src = java.nio.file.Files.createDirectories(tmp.resolve("src/main/java"));
    java.nio.file.Files.writeString(src.resolve("X.java"), "public class X {}");
  }

  @Test
  void calculate_failingMetricFallsToZeroAndIsFloored(@TempDir Path tmp)
      throws java.io.IOException {
    seedJavaFile(tmp);
    MFCQICalculator calc =
        MFCQICalculator.builder()
            .addMetric(new ConstantMetric("good", 1.0))
            .addMetric(new ThrowingMetric("bad"))
            .build();

    // bad -> 0.0 -> floored to 0.1, good -> 1.0 -> sqrt(0.1) ~= 0.3162
    assertThat(calc.calculate(tmp)).isCloseTo(Math.sqrt(0.1), within(1e-9));
  }

  private static final class ConstantMetric extends Metric<Double> {
    private final String name;
    private final double value;

    ConstantMetric(String name, double value) {
      this.name = name;
      this.value = value;
    }

    @Override
    protected boolean validateCodebase(Path codebase) {
      return true;
    }

    @Override
    public Double extract(Path codebase) {
      return value;
    }

    @Override
    public double normalize(Double v) {
      return v;
    }

    @Override
    public double getWeight() {
      return 1.0;
    }

    @Override
    public String getName() {
      return name;
    }
  }

  private static final class ThrowingMetric extends Metric<Double> {
    private final String name;

    ThrowingMetric(String name) {
      this.name = name;
    }

    @Override
    protected boolean validateCodebase(Path codebase) {
      return true;
    }

    @Override
    public Double extract(Path codebase) {
      throw new IllegalStateException("boom");
    }

    @Override
    public double normalize(Double v) {
      return 0.0;
    }

    @Override
    public double getWeight() {
      return 1.0;
    }

    @Override
    public String getName() {
      return name;
    }
  }
}
