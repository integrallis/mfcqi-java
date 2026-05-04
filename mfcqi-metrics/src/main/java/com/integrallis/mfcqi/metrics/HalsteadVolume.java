package com.integrallis.mfcqi.metrics;

import com.integrallis.mfcqi.core.Metric;
import com.integrallis.mfcqi.metrics.internal.HalsteadCounter;
import com.integrallis.mfcqi.metrics.internal.ParsedFile;
import com.integrallis.mfcqi.metrics.internal.ParsedSources;
import java.nio.file.Path;
import java.util.List;

/**
 * Halstead Volume averaged across files. {@code V = (N1 + N2) * log2(n1 + n2)} where {@code Nk} is
 * the total count of operators/operands and {@code nk} is the unique count.
 *
 * <p>Direct port of {@code mfcqi/metrics/complexity.py:HalsteadComplexity}. Normalization, weight,
 * and name come verbatim from the Python source — including the tanh S-curve {@code 1 -
 * tanh(V/2500)} (Python-calibrated October 2025 against requests/click/synthetic baselines) with
 * floors at V ≤ 0 (1.0) and V ≥ 5000 (0.0).
 */
public final class HalsteadVolume extends Metric<Double> {

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
      // Default to minimal volume if no functions found — matches Python `return 0.0`.
      return 0.0;
    }
    double sum = 0.0;
    int counted = 0;
    for (ParsedFile file : files) {
      double v = HalsteadCounter.volume(file);
      if (v > 0.0) {
        sum += v;
        counted++;
      }
    }
    return counted == 0 ? 0.0 : sum / counted;
  }

  @Override
  public double normalize(Double value) {
    // Verbatim port of Python normalize() (Python-calibrated tanh curve, Oct 2025):
    //   if value <= 0: return 1.0
    //   elif value >= 5000: return 0.0
    //   else: normalized = 1.0 - math.tanh(value / 2500.0); clamp [0, 1]
    if (value == null) {
      return 1.0;
    }
    double v = value;
    if (v <= 0.0) {
      return 1.0;
    }
    if (v >= 5000.0) {
      return 0.0;
    }
    double normalized = 1.0 - Math.tanh(v / 2500.0);
    return Math.max(0.0, Math.min(1.0, normalized));
  }

  @Override
  public double getWeight() {
    return 0.65;
  }

  @Override
  public String getName() {
    return "Halstead Volume";
  }
}
