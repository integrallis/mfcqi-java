package com.integrallis.mfcqi.metrics;

import com.integrallis.mfcqi.core.Metric;
import com.integrallis.mfcqi.metrics.internal.CyclomaticUnits;
import com.integrallis.mfcqi.metrics.internal.ParsedFile;
import com.integrallis.mfcqi.metrics.internal.ParsedSources;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * McCabe cyclomatic complexity, averaged across executable units (methods, constructors, and
 * initializer blocks).
 *
 * <p>Direct port of {@code mfcqi/metrics/complexity.py:CyclomaticComplexity}. The Python
 * implementation uses {@code radon.complexity.cc_visit} on each source file; this Java
 * implementation uses JavaParser visitors via {@link CyclomaticUnits}. Normalization, weight, and
 * name are translated verbatim from the Python source — including the exponential-decay curve
 * {@code score = e^(-0.1*(CC-1))} with hard floors at CC ≤ 1 (1.0) and CC ≥ 25 (0.0).
 */
public final class CyclomaticComplexity extends Metric<Double> {

  @Override
  protected boolean validateCodebase(Path codebase) {
    // The Python reference runs against single files too, so a regular file is acceptable.
    return codebase != null
        && (java.nio.file.Files.isDirectory(codebase)
            || java.nio.file.Files.isRegularFile(codebase));
  }

  @Override
  public Double extract(Path codebase) {
    List<ParsedFile> files = ParsedSources.parseAll(codebase);
    List<Integer> complexities = new ArrayList<>();
    for (ParsedFile file : files) {
      complexities.addAll(CyclomaticUnits.complexitiesIn(file));
    }
    if (complexities.isEmpty()) {
      // Default to 1 if no functions found — matches Python `return 1.0`.
      return 1.0;
    }
    long sum = 0;
    for (int c : complexities) {
      sum += c;
    }
    return ((double) sum) / complexities.size();
  }

  @Override
  public double normalize(Double value) {
    // Verbatim port of Python normalize():
    //   if value <= 1: return 1.0
    //   elif value >= 25: return 0.0
    //   else: return math.exp(-0.1 * (value - 1))
    if (value == null) {
      return 0.0;
    }
    double v = value;
    if (v <= 1.0) {
      return 1.0;
    }
    if (v >= 25.0) {
      return 0.0;
    }
    return Math.exp(-0.1 * (v - 1.0));
  }

  @Override
  public double getWeight() {
    return 0.85;
  }

  @Override
  public String getName() {
    return "Cyclomatic Complexity";
  }
}
