package com.integrallis.mfcqi.depssecurity;

import com.integrallis.mfcqi.core.Metric;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Dependency Security metric — counts CVE-impacted dependencies, weighted, then exponentially
 * normalized.
 *
 * <p>Direct port of {@code mfcqi/metrics/dependency_security.py:DependencySecurityMetric}. The
 * Python implementation walks all dependency files (requirements.txt, pyproject.toml, etc.), scans
 * each via {@code pip-audit}, and assigns a uniform weight of 2.0 per vulnerability before applying
 * {@code exp(-count/5.0)}. Java equivalence:
 *
 * <ul>
 *   <li>Dependency discovery: Maven {@code pom.xml} + Gradle {@code build.gradle(.kts)} via {@link
 *       DependencyExtractor} (analog of Python's glob-based file collection)
 *   <li>Vulnerability lookup: pluggable {@link VulnerabilityScanner}; defaults to {@link
 *       OsvVulnerabilityScanner} so the metric actually scans dependencies via the public OSV.dev
 *       API (no API key required), matching the behavior of the Python source's {@code pip-audit}
 *       integration. Use {@link OfflineNoOpScanner} explicitly for reproducible offline tests.
 *   <li>Per-vulnerability weight: 2.0 (verbatim)
 *   <li>Normalization: {@code 0 -> 1.0; else exp(-count/5.0)} (verbatim)
 *   <li>Weight: 0.75 (verbatim)
 *   <li>Name: {@code "Dependency Security"} (verbatim)
 * </ul>
 */
public final class DependencySecurityMetric extends Metric<Double> {

  /** Per-vulnerability weight — verbatim from Python's {@code MEDIUM severity weight}. */
  static final double VULN_WEIGHT = 2.0;

  /** Normalization decay constant — verbatim from Python's {@code threshold = 5.0}. */
  static final double DECAY_THRESHOLD = 5.0;

  private final VulnerabilityScanner scanner;

  /** Creates a metric backed by the default {@link OsvVulnerabilityScanner}. */
  public DependencySecurityMetric() {
    this(new OsvVulnerabilityScanner());
  }

  /**
   * Creates a metric backed by the supplied scanner.
   *
   * @param scanner the vulnerability scanner each dependency is dispatched to
   */
  public DependencySecurityMetric(VulnerabilityScanner scanner) {
    this.scanner = Objects.requireNonNull(scanner, "scanner");
  }

  /**
   * The {@link VulnerabilityScanner} this metric will dispatch each dependency to.
   *
   * @return the scanner
   */
  public VulnerabilityScanner scanner() {
    return scanner;
  }

  @Override
  protected boolean validateCodebase(Path codebase) {
    return codebase != null
        && (java.nio.file.Files.isDirectory(codebase)
            || java.nio.file.Files.isRegularFile(codebase));
  }

  @Override
  public Double extract(Path codebase) {
    List<Dependency> deps = DependencyExtractor.extractAll(codebase);
    if (deps.isEmpty()) {
      // Mirrors Python: `if not dependency_files: return 0.0`. No deps to scan = no risk surface.
      return 0.0;
    }
    double weighted = 0.0;
    for (Dependency d : deps) {
      List<Vulnerability> vulns = scanner.scan(d);
      weighted += vulns.size() * VULN_WEIGHT;
    }
    return weighted;
  }

  @Override
  public double normalize(Double value) {
    // Verbatim port: `if value == 0.0: return 1.0` then `return exp(-value/5.0)`.
    if (value == null || value == 0.0) {
      return 1.0;
    }
    return Math.max(0.0, Math.min(1.0, Math.exp(-value / DECAY_THRESHOLD)));
  }

  @Override
  public double getWeight() {
    return 0.75;
  }

  @Override
  public String getName() {
    return "Dependency Security";
  }
}
