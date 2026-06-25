package com.integrallis.mfcqi.badge;

/**
 * Badge rating bucket — color + label keyed off the MFCQI score. Direct port of the if/else ladder
 * in {@code mfcqi/cli/commands/badge.py:badge}.
 */
public enum BadgeRating {
  /** Score &gt;= 0.80 — brightgreen badge. */
  EXCELLENT("brightgreen", "excellent"),
  /** Score in [0.60, 0.80) — green badge. */
  GOOD("green", "good"),
  /** Score in [0.40, 0.60) — yellow badge. */
  FAIR("yellow", "fair"),
  /** Score &lt; 0.40 — red badge. */
  POOR("red", "poor");

  private final String color;
  private final String label;

  BadgeRating(String color, String label) {
    this.color = color;
    this.label = label;
  }

  /**
   * Returns the shields.io color name for this rating.
   *
   * @return the badge color (e.g. {@code "brightgreen"})
   */
  public String color() {
    return color;
  }

  /**
   * Returns the human-readable label for this rating.
   *
   * @return the rating label (e.g. {@code "excellent"})
   */
  public String label() {
    return label;
  }

  /** Verbatim port of Python's score thresholds: 0.80, 0.60, 0.40. */
  public static BadgeRating forScore(double score) {
    if (score >= 0.80) {
      return EXCELLENT;
    }
    if (score >= 0.60) {
      return GOOD;
    }
    if (score >= 0.40) {
      return FAIR;
    }
    return POOR;
  }
}
