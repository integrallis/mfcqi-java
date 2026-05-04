package com.integrallis.mfcqi.smells;

/**
 * Severity buckets for code smells. Direct port of {@code
 * mfcqi/smell_detection/models.py:SmellSeverity}. Default severity weights are 3.0/2.0/1.0
 * (HIGH/MEDIUM/LOW), matching the Python {@code Smell.__post_init__} fallback.
 */
public enum SmellSeverity {
  HIGH(3.0),
  MEDIUM(2.0),
  LOW(1.0);

  private final double defaultWeight;

  SmellSeverity(double defaultWeight) {
    this.defaultWeight = defaultWeight;
  }

  public double defaultWeight() {
    return defaultWeight;
  }
}
