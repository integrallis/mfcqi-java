package com.integrallis.mfcqi.badge;

/**
 * Badge rating bucket — color + label keyed off the MFCQI score. Direct port of the if/else ladder
 * in {@code mfcqi/cli/commands/badge.py:badge}.
 */
public enum BadgeRating {
  EXCELLENT("brightgreen", "excellent"),
  GOOD("green", "good"),
  FAIR("yellow", "fair"),
  POOR("red", "poor");

  private final String color;
  private final String label;

  BadgeRating(String color, String label) {
    this.color = color;
    this.label = label;
  }

  public String color() {
    return color;
  }

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
