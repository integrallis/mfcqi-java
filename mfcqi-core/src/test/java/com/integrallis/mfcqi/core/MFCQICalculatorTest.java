package com.integrallis.mfcqi.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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
  void detailedMetrics_extractsEachMetricOnce(@TempDir Path tmp) throws java.io.IOException {
    seedJavaFile(tmp);
    CountingMetric a = new CountingMetric("a", 0.9);
    CountingMetric b = new CountingMetric("b", 0.7);
    MFCQICalculator calc = MFCQICalculator.builder().addMetric(a).addMetric(b).build();

    java.util.Map<String, Double> detail = calc.detailedMetrics(tmp);

    assertThat(detail.get("mfcqi_score")).isCloseTo(Math.pow(0.9 * 0.7, 0.5), within(1e-9));
    assertThat(a.extractCount()).isEqualTo(1);
    assertThat(b.extractCount()).isEqualTo(1);
  }

  @Test
  void detailedMetrics_parallelismRunsMetricsConcurrentlyAndKeepsOrder(@TempDir Path tmp)
      throws Exception {
    seedJavaFile(tmp);
    CountDownLatch bothStarted = new CountDownLatch(2);
    CountDownLatch release = new CountDownLatch(1);
    BlockingMetric a = new BlockingMetric("a", 0.9, bothStarted, release);
    BlockingMetric b = new BlockingMetric("b", 0.7, bothStarted, release);
    MFCQICalculator calc =
        MFCQICalculator.builder().parallelism(2).addMetric(a).addMetric(b).build();

    AtomicInteger completed = new AtomicInteger();
    Thread worker =
        new Thread(
            () -> {
              calc.detailedMetrics(tmp);
              completed.incrementAndGet();
            });
    worker.start();

    assertThat(bothStarted.await(2, TimeUnit.SECONDS)).isTrue();
    assertThat(completed.get()).isEqualTo(0);
    release.countDown();
    worker.join(2_000);

    assertThat(completed.get()).isEqualTo(1);
    assertThat(calc.detailedMetrics(tmp).keySet()).containsExactly("a", "b", "mfcqi_score");
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

  private static class ConstantMetric extends Metric<Double> {
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

  private static final class CountingMetric extends ConstantMetric {
    private final AtomicInteger extractCount = new AtomicInteger();

    CountingMetric(String name, double value) {
      super(name, value);
    }

    @Override
    public Double extract(Path codebase) {
      extractCount.incrementAndGet();
      return super.extract(codebase);
    }

    int extractCount() {
      return extractCount.get();
    }
  }

  private static final class BlockingMetric extends ConstantMetric {
    private final CountDownLatch bothStarted;
    private final CountDownLatch release;

    BlockingMetric(String name, double value, CountDownLatch bothStarted, CountDownLatch release) {
      super(name, value);
      this.bothStarted = bothStarted;
      this.release = release;
    }

    @Override
    public Double extract(Path codebase) {
      bothStarted.countDown();
      try {
        if (!release.await(5, TimeUnit.SECONDS)) {
          throw new AssertionError("timed out waiting for release");
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new AssertionError(e);
      }
      return super.extract(codebase);
    }
  }
}
