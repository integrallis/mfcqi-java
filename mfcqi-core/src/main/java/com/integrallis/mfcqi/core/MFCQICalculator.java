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
 * Callers register {@code Metric} instances (always-on "core" metrics) and optionally a {@link
 * ParadigmDetector} that selects additional OO metrics based on the codebase's paradigm. The
 * concrete metric implementations and the default registry live in {@code mfcqi-metrics}.
 *
 * <p>The geometric mean uses the same {@code min_threshold = 0.1} floor as the Python reference:
 * any single zero is treated as 0.1 so that a missing tool output cannot collapse the entire score.
 * This is documented in {@code mfcqi.calculator._calculate_geometric_mean}.
 */
public final class MFCQICalculator {

  private static final Logger LOG = LoggerFactory.getLogger(MFCQICalculator.class);

  /** Floor applied to each value before multiplying — matches Python reference. */
  static final double GEOMETRIC_MEAN_FLOOR = 0.1;

  private final Map<String, Metric<?>> coreMetrics;
  private final Map<Paradigm, Map<String, Metric<?>>> paradigmMetrics;
  private final ParadigmDetector paradigmDetector;

  private MFCQICalculator(Builder b) {
    this.coreMetrics = Collections.unmodifiableMap(new LinkedHashMap<>(b.coreMetrics));
    LinkedHashMap<Paradigm, Map<String, Metric<?>>> pm = new LinkedHashMap<>();
    for (Map.Entry<Paradigm, LinkedHashMap<String, Metric<?>>> e : b.paradigmMetrics.entrySet()) {
      pm.put(e.getKey(), Collections.unmodifiableMap(new LinkedHashMap<>(e.getValue())));
    }
    this.paradigmMetrics = Collections.unmodifiableMap(pm);
    this.paradigmDetector = b.paradigmDetector;
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Calculate the MFCQI score for {@code codebase}. */
  public double calculate(Path codebase) {
    if (codebase == null) {
      return 0.0;
    }
    Map<String, Metric<?>> applicable = applicableMetrics(codebase);
    if (applicable.isEmpty()) {
      return 0.0;
    }
    List<Double> normalized = new ArrayList<>(applicable.size());
    for (Metric<?> m : applicable.values()) {
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
    Map<String, Metric<?>> applicable = applicableMetrics(codebase);
    for (Map.Entry<String, Metric<?>> e : applicable.entrySet()) {
      out.put(e.getKey(), safeNormalize(e.getValue(), codebase));
    }
    out.put("mfcqi_score", calculate(codebase));
    return out;
  }

  Map<String, Metric<?>> applicableMetrics(Path codebase) {
    LinkedHashMap<String, Metric<?>> result = new LinkedHashMap<>(coreMetrics);
    if (paradigmDetector == null || paradigmMetrics.isEmpty()) {
      return result;
    }
    try {
      ParadigmDetection detection = paradigmDetector.detect(codebase);
      Map<String, Metric<?>> extra = paradigmMetrics.get(detection.paradigm());
      if (extra != null) {
        result.putAll(extra);
      }
    } catch (RuntimeException e) {
      LOG.debug("Paradigm detection failed; falling back to core metrics only", e);
    }
    return result;
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
    private final LinkedHashMap<String, Metric<?>> coreMetrics = new LinkedHashMap<>();
    private final LinkedHashMap<Paradigm, LinkedHashMap<String, Metric<?>>> paradigmMetrics =
        new LinkedHashMap<>();
    private ParadigmDetector paradigmDetector;

    private Builder() {}

    public Builder addCoreMetric(Metric<?> metric) {
      Objects.requireNonNull(metric, "metric");
      coreMetrics.put(metric.getName(), metric);
      return this;
    }

    public Builder addParadigmMetric(Paradigm paradigm, Metric<?> metric) {
      Objects.requireNonNull(paradigm, "paradigm");
      Objects.requireNonNull(metric, "metric");
      paradigmMetrics
          .computeIfAbsent(paradigm, p -> new LinkedHashMap<>())
          .put(metric.getName(), metric);
      return this;
    }

    public Builder paradigmDetector(ParadigmDetector detector) {
      this.paradigmDetector = detector;
      return this;
    }

    public MFCQICalculator build() {
      return new MFCQICalculator(this);
    }
  }
}
