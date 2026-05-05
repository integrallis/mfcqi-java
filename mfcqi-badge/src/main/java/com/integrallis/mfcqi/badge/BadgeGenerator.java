package com.integrallis.mfcqi.badge;

import java.util.Locale;

/**
 * Builds badge artifacts (shields.io URL, endpoint JSON, markdown snippet) from an MFCQI score.
 * Direct port of {@code mfcqi/cli/commands/badge.py} string-formatting branches.
 */
public final class BadgeGenerator {

  private BadgeGenerator() {}

  /** Format the score the same way Python does: {@code "%.2f"}. */
  public static String formatScore(double score) {
    return String.format(Locale.ROOT, "%.2f", score);
  }

  /** shields.io static-badge URL — Python verbatim format string. */
  public static String url(double score, BadgeStyle style) {
    BadgeRating rating = BadgeRating.forScore(score);
    return "https://img.shields.io/badge/MFCQI-"
        + formatScore(score)
        + "-"
        + rating.color()
        + ".svg?style="
        + style.value();
  }

  /** shields.io endpoint JSON — same field names + cacheSeconds 3600 as the Python source. */
  public static String endpointJson(double score) {
    BadgeRating rating = BadgeRating.forScore(score);
    String message = formatScore(score) + " (" + rating.label() + ")";
    return "{\n"
        + "  \"schemaVersion\": 1,\n"
        + "  \"label\": \"MFCQI\",\n"
        + "  \"message\": \""
        + message
        + "\",\n"
        + "  \"color\": \""
        + rating.color()
        + "\",\n"
        + "  \"cacheSeconds\": 3600\n"
        + "}";
  }

  /** Markdown snippet ready to paste into a README. */
  public static String markdown(double score, BadgeStyle style) {
    return "![MFCQI Score](" + url(score, style) + ")";
  }
}
