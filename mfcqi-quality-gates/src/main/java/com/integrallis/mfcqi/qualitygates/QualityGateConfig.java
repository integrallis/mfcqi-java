package com.integrallis.mfcqi.qualitygates;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.yaml.snakeyaml.Yaml;

/**
 * Quality-gate configuration: minimum thresholds for the overall MFCQI score and individual metric
 * scores. Direct port of {@code mfcqi/quality_gates.py:QualityGateConfig}.
 *
 * <p>YAML schema (matches Python):
 *
 * <pre>
 *   quality_gates:
 *     overall:
 *       mfcqi_score: 0.7
 *     metrics:
 *       security: 0.8
 *       cyclomatic_complexity: 0.7
 * </pre>
 */
public final class QualityGateConfig {

  private final Map<String, Double> overallGates;
  private final Map<String, Double> metricGates;

  public QualityGateConfig(Map<String, Double> overallGates, Map<String, Double> metricGates) {
    this.overallGates =
        Collections.unmodifiableMap(
            new LinkedHashMap<>(Objects.requireNonNull(overallGates, "overallGates")));
    this.metricGates =
        Collections.unmodifiableMap(
            new LinkedHashMap<>(Objects.requireNonNull(metricGates, "metricGates")));
  }

  public Map<String, Double> overallGates() {
    return overallGates;
  }

  public Map<String, Double> metricGates() {
    return metricGates;
  }

  /** Load from a YAML file. Mirrors Python's {@code from_file}. */
  public static QualityGateConfig fromFile(Path configPath) {
    try (InputStream in = Files.newInputStream(configPath)) {
      return parse(in);
    } catch (IOException e) {
      throw new IllegalArgumentException("Failed to read quality-gate config: " + configPath, e);
    }
  }

  /** Default thresholds. Mirrors Python's {@code from_defaults}. */
  public static QualityGateConfig fromDefaults() {
    Map<String, Double> overall = new LinkedHashMap<>();
    overall.put("mfcqi_score", 0.6);
    Map<String, Double> metrics = new LinkedHashMap<>();
    metrics.put("security", 0.8);
    metrics.put("cyclomatic_complexity", 0.7);
    metrics.put("cognitive_complexity", 0.7);
    metrics.put("maintainability_index", 0.7);
    return new QualityGateConfig(overall, metrics);
  }

  static QualityGateConfig parse(InputStream in) {
    Object raw = new Yaml().load(in);
    if (!(raw instanceof Map)) {
      throw new IllegalArgumentException("Configuration file must contain 'quality_gates' section");
    }
    @SuppressWarnings("unchecked")
    Map<String, Object> root = (Map<String, Object>) raw;
    Object gatesObj = root.get("quality_gates");
    if (!(gatesObj instanceof Map)) {
      throw new IllegalArgumentException("Configuration file must contain 'quality_gates' section");
    }
    @SuppressWarnings("unchecked")
    Map<String, Object> gates = (Map<String, Object>) gatesObj;
    return new QualityGateConfig(
        asDoubleMap(gates.get("overall")), asDoubleMap(gates.get("metrics")));
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Double> asDoubleMap(Object value) {
    if (!(value instanceof Map)) {
      return Collections.emptyMap();
    }
    Map<String, Double> out = new LinkedHashMap<>();
    for (Map.Entry<String, Object> e : ((Map<String, Object>) value).entrySet()) {
      Object v = e.getValue();
      if (v instanceof Number) {
        out.put(e.getKey(), ((Number) v).doubleValue());
      }
    }
    return out;
  }
}
