package com.integrallis.mfcqi.analysis;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Output of {@link AnalysisEngine#analyze}. Mirrors Python's {@code AnalysisResult}. */
public final class AnalysisResult {

  private final double mfcqiScore;
  private final Map<String, Double> metricScores;
  private final List<String> recommendations;
  private final String modelUsed;

  /**
   * Creates an analysis result. The metric-score map and recommendation list are wrapped as
   * unmodifiable views.
   *
   * @param mfcqiScore the overall MFCQI score
   * @param metricScores per-metric normalized scores keyed by metric name
   * @param recommendations the LLM-generated recommendation strings
   * @param modelUsed the model identifier that produced this result
   */
  public AnalysisResult(
      double mfcqiScore,
      Map<String, Double> metricScores,
      List<String> recommendations,
      String modelUsed) {
    this.mfcqiScore = mfcqiScore;
    this.metricScores =
        Collections.unmodifiableMap(Objects.requireNonNull(metricScores, "metricScores"));
    this.recommendations =
        Collections.unmodifiableList(Objects.requireNonNull(recommendations, "recommendations"));
    this.modelUsed = Objects.requireNonNull(modelUsed, "modelUsed");
  }

  /**
   * The overall MFCQI score.
   *
   * @return the MFCQI score
   */
  public double mfcqiScore() {
    return mfcqiScore;
  }

  /**
   * The per-metric normalized scores, keyed by metric name.
   *
   * @return an unmodifiable map of metric scores
   */
  public Map<String, Double> metricScores() {
    return metricScores;
  }

  /**
   * The LLM-generated recommendations, each formatted as {@code "[SEVERITY] Title: Description"}.
   *
   * @return an unmodifiable list of recommendation strings
   */
  public List<String> recommendations() {
    return recommendations;
  }

  /**
   * The model identifier that produced this result.
   *
   * @return the model id
   */
  public String modelUsed() {
    return modelUsed;
  }
}
