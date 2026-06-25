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

  /**
   * Creates a result. The metric results are defensively copied into an unmodifiable list.
   *
   * @param passed whether every gate (overall and per-metric) passed
   * @param overallResult whether the overall-gate portion passed independently of metric gates
   * @param metricResults the per-metric evaluation outcomes
   * @throws NullPointerException if {@code metricResults} is {@code null}
   */
  public QualityGateResult(
      boolean passed, boolean overallResult, List<MetricResult> metricResults) {
    this.passed = passed;
    this.overallResult = overallResult;
    this.metricResults =
        Collections.unmodifiableList(
            new ArrayList<>(Objects.requireNonNull(metricResults, "metricResults")));
  }

  /**
   * Returns whether all gates passed (the combined overall and per-metric outcome).
   *
   * @return {@code true} if every gate passed
   */
  public boolean passed() {
    return passed;
  }

  /**
   * Returns whether the overall-gate portion passed, independently of individual metric gates.
   *
   * @return {@code true} if the overall gate passed
   */
  public boolean overallResult() {
    return overallResult;
  }

  /**
   * Returns the per-metric evaluation outcomes.
   *
   * @return an unmodifiable list of metric results
   */
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

    /**
     * Creates a per-metric result.
     *
     * @param metric the metric name
     * @param threshold the minimum acceptable score for this metric
     * @param actual the metric's actual score
     * @param passed whether {@code actual} met or exceeded {@code threshold}
     * @throws NullPointerException if {@code metric} is {@code null}
     */
    public MetricResult(String metric, double threshold, double actual, boolean passed) {
      this.metric = Objects.requireNonNull(metric, "metric");
      this.threshold = threshold;
      this.actual = actual;
      this.passed = passed;
    }

    /**
     * Returns the metric name.
     *
     * @return the metric name
     */
    public String metric() {
      return metric;
    }

    /**
     * Returns the minimum acceptable score for this metric.
     *
     * @return the configured threshold
     */
    public double threshold() {
      return threshold;
    }

    /**
     * Returns the metric's actual score.
     *
     * @return the actual score
     */
    public double actual() {
      return actual;
    }

    /**
     * Returns whether this metric met or exceeded its threshold.
     *
     * @return {@code true} if the metric gate passed
     */
    public boolean passed() {
      return passed;
    }
  }
}
