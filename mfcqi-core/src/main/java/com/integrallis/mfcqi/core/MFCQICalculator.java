package com.integrallis.mfcqi.core;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Computes a Multi-Factor Code Quality Index (MFCQI) score in {@code [0.0, 1.0]} by aggregating
 * individual {@link Metric} results with a weighted geometric mean.
 *
 * <p>This class is the framework-level orchestrator; it knows nothing about specific metrics.
 * Callers register {@code Metric} instances and the calculator applies all of them on every
 * codebase. Concrete metric implementations and the default registry live in {@code mfcqi-metrics}.
 *
 * <p>The geometric mean uses the same {@code min_threshold = 0.1} floor as the Python reference:
 * any single zero is treated as 0.1 so that a missing tool output cannot collapse the entire score.
 * This is documented in {@code mfcqi.calculator._calculate_geometric_mean}.
 */
public final class MFCQICalculator {

  private static final Logger LOG = LoggerFactory.getLogger(MFCQICalculator.class);

  /** Floor applied to each value before multiplying — matches Python reference. */
  static final double GEOMETRIC_MEAN_FLOOR = 0.1;

  private final Map<String, Metric<?>> metrics;

  private MFCQICalculator(Builder b) {
    this.metrics = Collections.unmodifiableMap(new LinkedHashMap<>(b.metrics));
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Calculate the MFCQI score for {@code codebase}. */
  public double calculate(Path codebase) {
    if (codebase == null || hasNoAnalyzableSource(codebase)) {
      // Mirrors mfcqi/calculator.py:115 — empty codebases get 0.0, not a partial score from
      // metrics that happen to default to 1.0 on no input.
      return 0.0;
    }
    if (metrics.isEmpty()) {
      return 0.0;
    }
    List<Double> normalized = new ArrayList<>(metrics.size());
    for (Metric<?> m : metrics.values()) {
      normalized.add(safeNormalize(m, codebase));
    }
    return geometricMean(normalized);
  }

  /**
   * Per-metric breakdown plus the overall {@code mfcqi_score} entry, mirroring {@code
   * MFCQICalculator.get_detailed_metrics} in Python.
   */
  public Map<String, Double> detailedMetrics(Path codebase) {
    Map<String, Double> out = new LinkedHashMap<>();
    if (codebase == null || hasNoAnalyzableSource(codebase)) {
      // Empty codebase: emit the standard set of metric keys with 0.0 each so the JSON/SARIF
      // schema is stable. Mirrors Python's get_detailed_metrics empty-path branch.
      for (Metric<?> m : metrics.values()) {
        out.put(m.getName(), 0.0);
      }
      out.put("mfcqi_score", 0.0);
      return out;
    }
    for (Map.Entry<String, Metric<?>> e : metrics.entrySet()) {
      out.put(e.getKey(), safeNormalize(e.getValue(), codebase));
    }
    out.put("mfcqi_score", calculate(codebase));
    return out;
  }

  /** True when the codebase is a directory with no {@code .java} files anywhere underneath. */
  private static boolean hasNoAnalyzableSource(Path codebase) {
    if (java.nio.file.Files.isRegularFile(codebase)) {
      // Single-file mode: caller handed us a specific file; trust they wanted it scanned.
      return false;
    }
    return JavaSourceFiles.findAll(codebase).isEmpty();
  }

  private static <T> double safeNormalize(Metric<T> metric, Path codebase) {
    try {
      T raw = metric.extract(codebase);
      double n = metric.normalize(raw);
      return clamp01(n);
    } catch (RuntimeException e) {
      LOG.debug("Metric '{}' failed; using 0.0", metric.getName(), e);
      return 0.0;
    }
  }

  private static double clamp01(double v) {
    if (Double.isNaN(v)) {
      return 0.0;
    }
    return Math.max(0.0, Math.min(1.0, v));
  }

  /**
   * Geometric mean with a 0.1 floor applied per value. Returns 0.0 for an empty input. Matches the
   * algorithm in {@code mfcqi.calculator._calculate_geometric_mean}.
   */
  static double geometricMean(List<Double> values) {
    if (values == null || values.isEmpty()) {
      return 0.0;
    }
    double product = 1.0;
    for (double v : values) {
      product *= Math.max(v, GEOMETRIC_MEAN_FLOOR);
    }
    if (Double.isNaN(product) || Double.isInfinite(product) || product <= 0.0) {
      return 0.0;
    }
    double mean = Math.pow(product, 1.0 / values.size());
    return clamp01(mean);
  }

  /** Fluent builder for {@link MFCQICalculator}. */
  public static final class Builder {
    private final LinkedHashMap<String, Metric<?>> metrics = new LinkedHashMap<>();

    private Builder() {}

    public Builder addMetric(Metric<?> metric) {
      Objects.requireNonNull(metric, "metric");
      metrics.put(metric.getName(), metric);
      return this;
    }

    public MFCQICalculator build() {
      return new MFCQICalculator(this);
    }
  }
}
