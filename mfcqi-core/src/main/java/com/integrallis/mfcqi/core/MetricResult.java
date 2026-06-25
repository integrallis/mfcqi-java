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

  /**
   * Creates an immutable metric result.
   *
   * @param metricName the metric's display/JSON name (must not be {@code null})
   * @param rawValue the raw extracted value, or {@code null} if extraction failed
   * @param processedValue the post-processed raw value, or {@code null}
   * @param normalizedValue the value mapped to {@code [0.0, 1.0]}
   * @param weightedValue the normalized value multiplied by the metric's weight
   * @param weight the metric's evidence-based weight
   * @param error an error message if the result is invalid, or {@code null} on success
   * @throws NullPointerException if {@code metricName} is {@code null}
   */
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

  /**
   * Returns the metric's display/JSON name.
   *
   * @return the metric name, never {@code null}
   */
  public String metricName() {
    return metricName;
  }

  /**
   * Returns the raw value extracted by the metric before normalization.
   *
   * @return the raw value, or {@code null} if extraction failed or yielded no value
   */
  public Object rawValue() {
    return rawValue;
  }

  /**
   * Returns the raw value after the metric's post-processing hook ran.
   *
   * @return the processed value, or {@code null}
   */
  public Object processedValue() {
    return processedValue;
  }

  /**
   * Returns the value mapped to the {@code [0.0, 1.0]} range.
   *
   * @return the normalized value
   */
  public double normalizedValue() {
    return normalizedValue;
  }

  /**
   * Returns the normalized value scaled by the metric's weight.
   *
   * @return the weighted value
   */
  public double weightedValue() {
    return weightedValue;
  }

  /**
   * Returns the evidence-based weight applied to this metric.
   *
   * @return the weight
   */
  public double weight() {
    return weight;
  }

  /**
   * Returns the error message describing why this result is invalid, if any.
   *
   * @return the error message, or {@code null} if the calculation succeeded
   */
  public String error() {
    return error;
  }

  /**
   * Indicates whether this result represents a failed calculation.
   *
   * @return {@code true} if an error message is present, {@code false} otherwise
   */
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
