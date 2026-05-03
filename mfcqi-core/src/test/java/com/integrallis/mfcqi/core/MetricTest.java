package com.integrallis.mfcqi.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("unit")
class MetricTest {

  @Test
  void calculate_runsTemplateInOrder(@TempDir Path tmp) {
    RecordingMetric m = new RecordingMetric();
    MetricResult result = m.calculate(tmp);

    assertThat(m.calls)
        .containsExactly(
            "validate", "preProcess", "extract", "postProcessRaw", "normalize", "postCalculate");
    assertThat(result.metricName()).isEqualTo("recording");
    assertThat(result.normalizedValue()).isEqualTo(0.5);
    assertThat(result.weightedValue()).isEqualTo(0.5 * 0.7);
    assertThat(result.weight()).isEqualTo(0.7);
    assertThat(result.isError()).isFalse();
  }

  @Test
  void calculate_returnsErrorResultForMissingCodebase() {
    RecordingMetric m = new RecordingMetric();
    Path missing = Path.of("/nonexistent/path/that/should/not/exist");
    MetricResult result = m.calculate(missing);

    assertThat(result.isError()).isTrue();
    assertThat(result.normalizedValue()).isEqualTo(0.0);
    assertThat(result.weightedValue()).isEqualTo(0.0);
  }

  @Test
  void calculate_validatesAgainstFiles(@TempDir Path tmp) throws Exception {
    Path file = Files.createFile(tmp.resolve("a.txt"));
    RecordingMetric m = new RecordingMetric();
    MetricResult result = m.calculate(file);
    assertThat(result.isError()).isTrue();
  }

  private static final class RecordingMetric extends Metric<Double> {
    final java.util.List<String> calls = new java.util.ArrayList<>();

    @Override
    protected boolean validateCodebase(Path codebase) {
      calls.add("validate");
      return super.validateCodebase(codebase);
    }

    @Override
    protected void preProcess(Path codebase) {
      calls.add("preProcess");
    }

    @Override
    public Double extract(Path codebase) {
      calls.add("extract");
      return 0.5;
    }

    @Override
    protected Double postProcessRaw(Double rawValue) {
      calls.add("postProcessRaw");
      return rawValue;
    }

    @Override
    public double normalize(Double value) {
      calls.add("normalize");
      return value;
    }

    @Override
    protected void postCalculate(MetricResult result) {
      calls.add("postCalculate");
    }

    @Override
    public double getWeight() {
      return 0.7;
    }

    @Override
    public String getName() {
      return "recording";
    }
  }
}
