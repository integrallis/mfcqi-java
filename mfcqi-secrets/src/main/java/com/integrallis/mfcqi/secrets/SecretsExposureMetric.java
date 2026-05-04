package com.integrallis.mfcqi.secrets;

import com.integrallis.mfcqi.core.Metric;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Secrets Exposure metric — counts hard-coded secrets in a codebase, mapping the count to a
 * severe-penalty normalize curve.
 *
 * <p>Direct port of {@code mfcqi/metrics/secrets_exposure.py:SecretsExposureMetric}. The Python
 * implementation delegates to the {@code detect-secrets} library; this Java implementation uses
 * {@link SecretScanner} (a port of the same regex catalog + Shannon-entropy approach) so no
 * external service or subprocess is required. The file-skipping heuristic for tests/examples is
 * translated verbatim.
 */
public final class SecretsExposureMetric extends Metric<Double> {

  /** Substrings whose presence in a file path causes the file to be skipped (Python verbatim). */
  static final String[] SKIP_SUBSTRINGS = {"test_", "example.", ".example", "fixture", "/tests/"};

  private final SecretScanner scanner;

  public SecretsExposureMetric() {
    this(new SecretScanner());
  }

  public SecretsExposureMetric(SecretScanner scanner) {
    this.scanner = Objects.requireNonNull(scanner, "scanner");
  }

  @Override
  protected boolean validateCodebase(Path codebase) {
    return codebase != null
        && (java.nio.file.Files.isDirectory(codebase)
            || java.nio.file.Files.isRegularFile(codebase));
  }

  @Override
  public Double extract(Path codebase) {
    List<SecretFinding> findings = scanner.scanDirectory(codebase);
    int real = 0;
    for (SecretFinding f : findings) {
      if (!shouldSkip(f.file().toString())) {
        real++;
      }
    }
    return (double) real;
  }

  /** Port of Python: any of the SKIP_SUBSTRINGS appearing in the path causes a skip. */
  static boolean shouldSkip(String filePath) {
    for (String s : SKIP_SUBSTRINGS) {
      if (filePath.contains(s)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public double normalize(Double value) {
    // Verbatim port of Python normalize():
    //   0       : 1.0
    //   1       : 0.3
    //   2 or 3  : 0.1
    //   else    : 0.0
    if (value == null) {
      return 1.0;
    }
    double v = value;
    if (v == 0.0) {
      return 1.0;
    }
    if (v == 1.0) {
      return 0.3;
    }
    if (v <= 3.0) {
      return 0.1;
    }
    return 0.0;
  }

  @Override
  public double getWeight() {
    return 0.85;
  }

  @Override
  public String getName() {
    return "Secrets Exposure";
  }
}
