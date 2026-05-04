package com.integrallis.mfcqi.security;

import com.integrallis.mfcqi.core.JavaSourceFiles;
import com.integrallis.mfcqi.core.Metric;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Source-level Java security metric — direct port of {@code
 * mfcqi/metrics/security.py:SecurityMetric}.
 *
 * <p>The Python implementation uses Bandit's library API to harvest source-level findings, scores
 * each by a CVSS-like rubric (severity × confidence multiplier with bounded ranges), boosts
 * critical CWEs by 20%, and divides the total CVSS by lines of code to get a vulnerability density
 * before applying {@code exp(-density / threshold)}. The Java port mirrors every step:
 *
 * <ul>
 *   <li>findings: {@link JavaSecurityScanner} (Java AST analog of the Bandit checks)
 *   <li>per-finding CVSS: {@link #calculateCvss(SecurityFinding.Severity,
 *       SecurityFinding.Confidence)} — verbatim base scores and confidence multipliers, with
 *       Bandit's range clamps preserved
 *   <li>CWE boost: {@link #adjustForCwe(double, String)} — verbatim 20% boost for {@link
 *       #CRITICAL_CWES}
 *   <li>density: total CVSS / SLOC (where SLOC counts lines with content from {@link
 *       JavaSourceFiles})
 *   <li>normalization: {@code exp(-density / 0.03)} (default threshold matches Python)
 *   <li>weight: 0.7 (verbatim); name: {@code "security"} (verbatim)
 * </ul>
 */
public final class SecurityMetric extends Metric<Double> {

  /** Critical CWEs that get a 20% CVSS boost — verbatim port of Python's {@code CRITICAL_CWES}. */
  static final Set<String> CRITICAL_CWES;

  static {
    Set<String> s = new HashSet<>();
    Collections.addAll(s, "CWE-78", "CWE-89", "CWE-94", "CWE-502", "CWE-259", "CWE-22", "CWE-79");
    CRITICAL_CWES = Collections.unmodifiableSet(s);
  }

  /** Default vulnerability-density threshold — verbatim from Python {@code threshold=0.03}. */
  public static final double DEFAULT_THRESHOLD = 0.03;

  private final JavaSecurityScanner scanner;
  private final double threshold;

  /** Last scan's findings, exposed for downstream LLM/recommendation context. */
  private List<SecurityFinding> lastFindings = Collections.emptyList();

  public SecurityMetric() {
    this(new JavaSecurityScanner(), DEFAULT_THRESHOLD);
  }

  public SecurityMetric(JavaSecurityScanner scanner, double threshold) {
    this.scanner = scanner;
    this.threshold = threshold;
  }

  @Override
  protected boolean validateCodebase(Path codebase) {
    return codebase != null
        && (java.nio.file.Files.isDirectory(codebase)
            || java.nio.file.Files.isRegularFile(codebase));
  }

  @Override
  public Double extract(Path codebase) {
    List<SecurityFinding> findings = scanner.scan(codebase);
    lastFindings = findings;
    if (findings.isEmpty()) {
      return 0.0;
    }
    double totalScore = 0.0;
    for (SecurityFinding f : findings) {
      double cvss = calculateCvss(f.severity(), f.confidence());
      cvss = adjustForCwe(cvss, f.cweId());
      totalScore += cvss;
    }
    int loc = countLines(codebase);
    if (loc == 0) {
      return 0.0;
    }
    return totalScore / loc;
  }

  /** Python: middle-of-CVSS-range base × confidence multiplier, then clamped per severity band. */
  static double calculateCvss(
      SecurityFinding.Severity severity, SecurityFinding.Confidence confidence) {
    double base;
    switch (severity) {
      case HIGH:
        base = 8.0;
        break;
      case MEDIUM:
        base = 5.5;
        break;
      case LOW:
      default:
        base = 2.0;
        break;
    }
    double factor;
    switch (confidence) {
      case HIGH:
        factor = 1.0;
        break;
      case MEDIUM:
        factor = 0.85;
        break;
      case LOW:
      default:
        factor = 0.6;
        break;
    }
    double score = base * factor;
    switch (severity) {
      case LOW:
        return Math.max(0.1, Math.min(3.9, score));
      case MEDIUM:
        return Math.max(2.0, Math.min(6.9, score));
      case HIGH:
        return Math.max(4.0, Math.min(8.9, score));
      default:
        return score;
    }
  }

  /** Python: critical CWEs get a 20% boost up to a 10.0 cap. */
  static double adjustForCwe(double baseScore, String cweId) {
    if (CRITICAL_CWES.contains(cweId)) {
      return Math.min(10.0, baseScore * 1.2);
    }
    return baseScore;
  }

  @Override
  public double normalize(Double value) {
    if (value == null || value == 0.0) {
      return 1.0;
    }
    double score = Math.exp(-value / threshold);
    return Math.max(0.0, Math.min(1.0, score));
  }

  @Override
  public double getWeight() {
    return 0.7;
  }

  @Override
  public String getName() {
    return "security";
  }

  /** Findings from the last {@link #extract(Path)} invocation. */
  public List<SecurityFinding> lastFindings() {
    return Collections.unmodifiableList(lastFindings);
  }

  /** Count source lines of code across analyzable files (rough port of Python's _count_lines). */
  static int countLines(Path codebase) {
    int total = 0;
    for (Path file : JavaSourceFiles.findAll(codebase)) {
      try {
        for (String line :
            new String(Files.readAllBytes(file), StandardCharsets.UTF_8).split("\\r?\\n", -1)) {
          String trimmed = line.trim();
          if (!trimmed.isEmpty() && !trimmed.startsWith("//") && !trimmed.startsWith("*")) {
            total++;
          }
        }
      } catch (IOException e) {
        // Match Python: silent skip for unreadable files.
      }
    }
    return total;
  }
}
