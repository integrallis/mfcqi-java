package com.integrallis.mfcqi.security;

import java.nio.file.Path;
import java.util.Objects;

/**
 * A single security issue: which check fired, where, the issue text, and severity/confidence
 * derived in the Bandit style. Mirrors the dict {@code mfcqi/metrics/security.py} stores in {@code
 * last_issues} for downstream LLM context.
 */
public final class SecurityFinding {

  private final Path file;
  private final int lineNumber;
  private final String testId;
  private final String testName;
  private final Severity severity;
  private final Confidence confidence;
  private final String issueText;
  private final String cweId;

  public SecurityFinding(
      Path file,
      int lineNumber,
      String testId,
      String testName,
      Severity severity,
      Confidence confidence,
      String issueText,
      String cweId) {
    this.file = Objects.requireNonNull(file, "file");
    this.lineNumber = lineNumber;
    this.testId = Objects.requireNonNull(testId, "testId");
    this.testName = Objects.requireNonNull(testName, "testName");
    this.severity = Objects.requireNonNull(severity, "severity");
    this.confidence = Objects.requireNonNull(confidence, "confidence");
    this.issueText = Objects.requireNonNull(issueText, "issueText");
    this.cweId = Objects.requireNonNull(cweId, "cweId");
  }

  /**
   * Returns the file in which the issue was found.
   *
   * @return the source file
   */
  public Path file() {
    return file;
  }

  /**
   * Returns the 1-based line number where the issue was found.
   *
   * @return the line number
   */
  public int lineNumber() {
    return lineNumber;
  }

  /**
   * Returns the Bandit-style check identifier that fired (e.g. {@code "B303"}).
   *
   * @return the test id
   */
  public String testId() {
    return testId;
  }

  /**
   * Returns the Bandit-style check name (e.g. {@code "use_of_weak_hash"}).
   *
   * @return the test name
   */
  public String testName() {
    return testName;
  }

  /**
   * Returns the severity bucket assigned to this issue.
   *
   * @return the severity
   */
  public Severity severity() {
    return severity;
  }

  /**
   * Returns the confidence bucket assigned to this issue.
   *
   * @return the confidence
   */
  public Confidence confidence() {
    return confidence;
  }

  /**
   * Returns the human-readable text describing the issue.
   *
   * @return the issue text
   */
  public String issueText() {
    return issueText;
  }

  /**
   * Returns the associated CWE identifier (e.g. {@code "CWE-327"}).
   *
   * @return the CWE id
   */
  public String cweId() {
    return cweId;
  }

  @Override
  public String toString() {
    return testId
        + "("
        + severity
        + "/"
        + confidence
        + ") "
        + file.getFileName()
        + ":"
        + lineNumber;
  }

  /** Bandit-equivalent severity buckets. */
  public enum Severity {
    LOW,
    MEDIUM,
    HIGH
  }

  /** Bandit-equivalent confidence buckets. */
  public enum Confidence {
    LOW,
    MEDIUM,
    HIGH
  }
}
