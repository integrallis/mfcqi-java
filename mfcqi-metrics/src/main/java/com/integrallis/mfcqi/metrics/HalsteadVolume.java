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
 * <p>Port of {@code mfcqi/metrics/complexity.py:HalsteadComplexity}. The S-curve shape {@code 1 -
 * tanh(V/D)} and weight/name come from the Python source, but the divisor {@code D} and the upper
 * cutoff are <b>recalibrated for Java's token model</b>. Halstead volume is not language-neutral:
 * radon (Python) only counts operators/operands that appear inside arithmetic/boolean/comparison
 * expressions, whereas this JavaParser-token counter counts all operator symbols, identifiers, and
 * literals. Measured on the two functionally-equivalent sibling codebases, the mean per-file volume
 * is ~322 for Python (radon) and ~1608 for Java — a ~5x factor. Reusing Python's {@code D=2500}
 * would score equivalent Java code at ~0.43 instead of ~0.87. The constants are therefore scaled by
 * 5: {@code D=12500}, cutoff {@code 25000}. (Calibration method: see {@code docs/} / commit notes —
 * equivalent small samples plus full sibling-codebase means under radon vs this counter.)
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
    // Java-recalibrated tanh curve (see class javadoc — ~5x radon's volume on equivalent code):
    //   if value <= 0:     return 1.0
    //   elif value >= 25000: return 0.0
    //   else: normalized = 1.0 - tanh(value / 12500.0); clamp [0, 1]
    if (value == null) {
      return 1.0;
    }
    double v = value;
    if (v <= 0.0) {
      return 1.0;
    }
    if (v >= 25000.0) {
      return 0.0;
    }
    double normalized = 1.0 - Math.tanh(v / 12500.0);
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
