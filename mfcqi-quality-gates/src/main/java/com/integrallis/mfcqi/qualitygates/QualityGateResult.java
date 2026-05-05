package com.integrallis.mfcqi.qualitygates;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Outcome of evaluating a {@link QualityGateConfig} against an analysis result. */
public final class QualityGateResult {

  private final boolean passed;
  private final boolean overallResult;
  private final List<MetricResult> metricResults;

  public QualityGateResult(
      boolean passed, boolean overallResult, List<MetricResult> metricResults) {
    this.passed = passed;
    this.overallResult = overallResult;
    this.metricResults =
        Collections.unmodifiableList(
            new ArrayList<>(Objects.requireNonNull(metricResults, "metricResults")));
  }

  public boolean passed() {
    return passed;
  }

  public boolean overallResult() {
    return overallResult;
  }

  public List<MetricResult> metricResults() {
    return metricResults;
  }

  /** Number of failed gates including the overall gate. */
  public int failedCount() {
    int failed = 0;
    for (MetricResult r : metricResults) {
      if (!r.passed) {
        failed++;
      }
    }
    if (!overallResult) {
      failed++;
    }
    return failed;
  }

  /** Number of passed gates including the overall gate. */
  public int passedCount() {
    int passed = 0;
    for (MetricResult r : metricResults) {
      if (r.passed) {
        passed++;
      }
    }
    if (overallResult) {
      passed++;
    }
    return passed;
  }

  /** Per-metric gate evaluation: which metric, threshold, actual value, pass/fail. */
  public static final class MetricResult {
    private final String metric;
    private final double threshold;
    private final double actual;
    private final boolean passed;

    public MetricResult(String metric, double threshold, double actual, boolean passed) {
      this.metric = Objects.requireNonNull(metric, "metric");
      this.threshold = threshold;
      this.actual = actual;
      this.passed = passed;
    }

    public String metric() {
      return metric;
    }

    public double threshold() {
      return threshold;
    }

    public double actual() {
      return actual;
    }

    public boolean passed() {
      return passed;
    }
  }
}
