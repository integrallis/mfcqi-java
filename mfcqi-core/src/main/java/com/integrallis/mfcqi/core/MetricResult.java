package com.integrallis.mfcqi.core;

import java.util.Objects;

/**
 * Immutable result of evaluating a single {@link Metric} against a codebase. Carries both the raw
 * extracted value and its normalized/weighted form so downstream consumers (LLM analysis, badge
 * rendering, JSON export) can reason about either.
 *
 * <p>Mirrors the dict structure returned by {@code Metric.calculate} in the Python reference
 * implementation.
 */
public final class MetricResult {

  private final String metricName;
  private final Object rawValue;
  private final Object processedValue;
  private final double normalizedValue;
  private final double weightedValue;
  private final double weight;
  private final String error;

  public MetricResult(
      String metricName,
      Object rawValue,
      Object processedValue,
      double normalizedValue,
      double weightedValue,
      double weight,
      String error) {
    this.metricName = Objects.requireNonNull(metricName, "metricName");
    this.rawValue = rawValue;
    this.processedValue = processedValue;
    this.normalizedValue = normalizedValue;
    this.weightedValue = weightedValue;
    this.weight = weight;
    this.error = error;
  }

  public String metricName() {
    return metricName;
  }

  public Object rawValue() {
    return rawValue;
  }

  public Object processedValue() {
    return processedValue;
  }

  public double normalizedValue() {
    return normalizedValue;
  }

  public double weightedValue() {
    return weightedValue;
  }

  public double weight() {
    return weight;
  }

  public String error() {
    return error;
  }

  public boolean isError() {
    return error != null;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof MetricResult)) {
      return false;
    }
    MetricResult that = (MetricResult) o;
    return Double.compare(that.normalizedValue, normalizedValue) == 0
        && Double.compare(that.weightedValue, weightedValue) == 0
        && Double.compare(that.weight, weight) == 0
        && metricName.equals(that.metricName)
        && Objects.equals(rawValue, that.rawValue)
        && Objects.equals(processedValue, that.processedValue)
        && Objects.equals(error, that.error);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        metricName, rawValue, processedValue, normalizedValue, weightedValue, weight, error);
  }

  @Override
  public String toString() {
    return "MetricResult{"
        + "name='"
        + metricName
        + "', normalized="
        + normalizedValue
        + ", weighted="
        + weightedValue
        + ", weight="
        + weight
        + (error != null ? ", error='" + error + "'" : "")
        + '}';
  }
}
