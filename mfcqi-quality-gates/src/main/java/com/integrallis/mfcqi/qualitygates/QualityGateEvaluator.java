package com.integrallis.mfcqi.qualitygates;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Evaluates an analysis result against a {@link QualityGateConfig}. Direct port of {@code
 * mfcqi/quality_gates.py:QualityGateEvaluator}.
 *
 * <p>Analysis-result contract (matches Python): a {@link Map} keyed by:
 *
 * <ul>
 *   <li>{@code "mfcqi_score"} — the overall score (double)
 *   <li>{@code "metric_scores"} — a nested {@code Map<String, Double>} of per-metric scores
 * </ul>
 */
public final class QualityGateEvaluator {

  private final QualityGateConfig config;

  public QualityGateEvaluator(QualityGateConfig config) {
    this.config = Objects.requireNonNull(config, "config");
  }

  public QualityGateConfig config() {
    return config;
  }

  /** Evaluate. Verbatim port of Python's {@code evaluate}. */
  @SuppressWarnings("unchecked")
  public QualityGateResult evaluate(Map<String, Object> analysisResult) {
    double mfcqiScore = asDouble(analysisResult.get("mfcqi_score"), 0.0);
    Map<String, Double> metricScores;
    Object scoresObj = analysisResult.get("metric_scores");
    if (scoresObj instanceof Map) {
      metricScores = coerceDoubleMap((Map<String, Object>) scoresObj);
    } else {
      metricScores = java.util.Collections.emptyMap();
    }

    // Overall MFCQI score gate.
    boolean overallResult = true;
    if (config.overallGates().containsKey("mfcqi_score")) {
      double threshold = config.overallGates().get("mfcqi_score");
      overallResult = mfcqiScore >= threshold;
    }

    // Other overall gates (e.g. security_score) — Python checks them against metric_scores.
    for (Map.Entry<String, Double> e : config.overallGates().entrySet()) {
      if ("mfcqi_score".equals(e.getKey())) {
        continue;
      }
      Double actual = metricScores.get(e.getKey());
      if (actual != null && actual < e.getValue()) {
        overallResult = false;
      }
    }

    // Individual metric gates.
    List<QualityGateResult.MetricResult> metricResults = new ArrayList<>();
    for (Map.Entry<String, Double> e : config.metricGates().entrySet()) {
      Double actual = metricScores.get(e.getKey());
      if (actual == null) {
        continue;
      }
      boolean passed = actual >= e.getValue();
      metricResults.add(
          new QualityGateResult.MetricResult(e.getKey(), e.getValue(), actual, passed));
    }

    boolean allMetricsPassed = true;
    for (QualityGateResult.MetricResult r : metricResults) {
      if (!r.passed()) {
        allMetricsPassed = false;
        break;
      }
    }
    return new QualityGateResult(overallResult && allMetricsPassed, overallResult, metricResults);
  }

  /**
   * Look in {@code projectPath} for {@code .mfcqi.yaml} or {@code .mfcqi-gates.yaml}. Mirrors
   * Python's {@code find_quality_gate_config}.
   */
  public static java.util.Optional<Path> findQualityGateConfig(Path projectPath) {
    for (String name : new String[] {".mfcqi.yaml", ".mfcqi-gates.yaml"}) {
      Path candidate = projectPath.resolve(name);
      if (Files.exists(candidate)) {
        return java.util.Optional.of(candidate);
      }
    }
    return java.util.Optional.empty();
  }

  private static double asDouble(Object value, double fallback) {
    return value instanceof Number ? ((Number) value).doubleValue() : fallback;
  }

  private static Map<String, Double> coerceDoubleMap(Map<String, Object> raw) {
    Map<String, Double> out = new java.util.LinkedHashMap<>();
    for (Map.Entry<String, Object> e : raw.entrySet()) {
      if (e.getValue() instanceof Number) {
        out.put(e.getKey(), ((Number) e.getValue()).doubleValue());
      }
    }
    return out;
  }
}
