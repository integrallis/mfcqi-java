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

  public double mfcqiScore() {
    return mfcqiScore;
  }

  public Map<String, Double> metricScores() {
    return metricScores;
  }

  public List<String> recommendations() {
    return recommendations;
  }

  public String modelUsed() {
    return modelUsed;
  }
}
