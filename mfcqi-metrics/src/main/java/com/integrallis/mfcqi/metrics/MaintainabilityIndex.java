package com.integrallis.mfcqi.metrics;

import com.integrallis.mfcqi.core.Metric;
import com.integrallis.mfcqi.metrics.internal.FileMetrics;
import com.integrallis.mfcqi.metrics.internal.HalsteadCounter;
import com.integrallis.mfcqi.metrics.internal.ParsedFile;
import com.integrallis.mfcqi.metrics.internal.ParsedSources;
import java.nio.file.Path;
import java.util.List;

/**
 * Maintainability Index averaged across files.
 *
 * <p>Direct port of {@code mfcqi/metrics/maintainability.py:MaintainabilityIndex}, which delegates
 * to {@code radon.metrics.mi_visit}. The radon formula ({@code mi_compute}) is translated verbatim
 * here:
 *
 * <pre>
 *   if HV &lt;= 0 or SLOC &lt;= 0: return 100.0
 *   nn_mi = 171 - 5.2*ln(HV) - 0.23*CC - 16.2*ln(SLOC) + 50*sin(sqrt(2.46 * radians(comments_pct)))
 *   return clamp(nn_mi * 100/171, 0, 100)
 * </pre>
 *
 * <p>Per-file inputs come from {@link HalsteadCounter} (HV), {@link FileMetrics} (CC sum, SLOC,
 * comment-line count). Normalization, weight, and name come verbatim from the Python source
 * (Python-calibrated thresholds 70/50/30/20, October 2025).
 */
public final class MaintainabilityIndex extends Metric<Double> {

  @Override
  protected boolean validateCodebase(Path codebase) {
    return codebase != null
        && (java.nio.file.Files.isDirectory(codebase)
            || java.nio.file.Files.isRegularFile(codebase));
  }

  @Override
  public Double extract(Path codebase) {
    List<ParsedFile> files = ParsedSources.parseAll(codebase);
    if (files.isEmpty()) {
      // Mirrors Python `return 100.0` when no parseable code is found.
      return 100.0;
    }
    double sum = 0.0;
    int counted = 0;
    for (ParsedFile file : files) {
      double hv = HalsteadCounter.volume(file);
      int cc = FileMetrics.totalCyclomaticComplexity(file);
      int sloc = FileMetrics.sourceLines(file.source());
      int commentLines = FileMetrics.commentLines(file);
      double commentsPct = sloc == 0 ? 0.0 : ((double) commentLines / sloc) * 100.0;
      double mi = miCompute(hv, cc, sloc, commentsPct);
      sum += mi;
      counted++;
    }
    return counted == 0 ? 100.0 : sum / counted;
  }

  /** Verbatim port of radon's {@code mi_compute}. Returns the normalized MI in {@code [0, 100]}. */
  static double miCompute(double halsteadVolume, int complexity, int sloc, double commentsPct) {
    if (halsteadVolume <= 0.0 || sloc <= 0) {
      return 100.0;
    }
    double slocScale = Math.log(sloc);
    double volumeScale = Math.log(halsteadVolume);
    double commentsScale = Math.sqrt(2.46 * Math.toRadians(commentsPct));
    double nnMi =
        171.0
            - 5.2 * volumeScale
            - 0.23 * complexity
            - 16.2 * slocScale
            + 50.0 * Math.sin(commentsScale);
    return Math.min(Math.max(0.0, nnMi * 100.0 / 171.0), 100.0);
  }

  @Override
  public double normalize(Double value) {
    // Verbatim port of Python normalize() (Python-calibrated 70/50/30/20 thresholds, Oct 2025):
    //   if value <= 0       : 0.0
    //   elif value >= 70    : 0.85 + 0.15 * min(1.0, (value-70)/30)
    //   elif value >= 50    : 0.70 + 0.15 * (value-50)/20
    //   elif value >= 30    : 0.50 + 0.20 * (value-30)/20
    //   elif value >= 20    : 0.25 + 0.25 * (value-20)/10
    //   else                : 0.25 * value / 20
    if (value == null) {
      return 0.0;
    }
    double v = value;
    if (v <= 0.0) {
      return 0.0;
    }
    if (v >= 70.0) {
      return 0.85 + 0.15 * Math.min(1.0, (v - 70.0) / 30.0);
    }
    if (v >= 50.0) {
      return 0.70 + 0.15 * (v - 50.0) / 20.0;
    }
    if (v >= 30.0) {
      return 0.50 + 0.20 * (v - 30.0) / 20.0;
    }
    if (v >= 20.0) {
      return 0.25 + 0.25 * (v - 20.0) / 10.0;
    }
    return 0.25 * v / 20.0;
  }

  @Override
  public double getWeight() {
    return 0.5;
  }

  @Override
  public String getName() {
    return "Maintainability Index";
  }
}
