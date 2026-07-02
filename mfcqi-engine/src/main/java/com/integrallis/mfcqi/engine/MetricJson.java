package com.integrallis.mfcqi.engine;

import java.util.Locale;
import java.util.Map;

/** Minimal JSON serialization for the metric map, shared by the Gradle and Maven plugins. */
public final class MetricJson {

  private MetricJson() {}

  /** Serialize a metric-name → score map as a stable JSON object. */
  public static String of(Map<String, Double> metrics) {
    StringBuilder sb = new StringBuilder("{\n");
    int i = 0;
    for (Map.Entry<String, Double> entry : metrics.entrySet()) {
      sb.append("  \"")
          .append(escape(entry.getKey()))
          .append("\": ")
          .append(String.format(Locale.ROOT, "%.6f", entry.getValue()));
      if (++i < metrics.size()) {
        sb.append(',');
      }
      sb.append('\n');
    }
    return sb.append("}\n").toString();
  }

  private static String escape(String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
