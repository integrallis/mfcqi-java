package com.integrallis.mfcqi.analysis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Raw tool-output context fed to the LLM prompt template alongside normalized metric scores. Direct
 * port of the {@code tool_outputs} dict the Python {@code LLMAnalysisEngine} builds in {@code
 * mfcqi/analysis/engine.py:_format_tool_outputs} — the same shape lets the Pebble template iterate
 * {@code tool_outputs.bandit.top_issues}, {@code tool_outputs.complexity.complex_functions}, etc.
 *
 * <p>Use {@link #builder()} to assemble; fields default to empty so any subset can be supplied.
 */
public final class ToolOutputs {

  /** Bandit-equivalent security finding summary, keyed for the prompt template. */
  public static final class SecurityIssue {
    private final String testId;
    private final String testName;
    private final String file;
    private final int lineNumber;
    private final String severity;
    private final String message;

    /**
     * Creates a security finding.
     *
     * @param testId the rule/test identifier that produced the finding
     * @param testName the human-readable rule/test name
     * @param file the file the finding refers to
     * @param lineNumber the line number within the file
     * @param severity the severity label (e.g., {@code CRITICAL}, {@code HIGH})
     * @param message the finding description
     */
    public SecurityIssue(
        String testId,
        String testName,
        String file,
        int lineNumber,
        String severity,
        String message) {
      this.testId = Objects.requireNonNull(testId, "testId");
      this.testName = Objects.requireNonNull(testName, "testName");
      this.file = Objects.requireNonNull(file, "file");
      this.lineNumber = lineNumber;
      this.severity = Objects.requireNonNull(severity, "severity");
      this.message = Objects.requireNonNull(message, "message");
    }

    /**
     * The rule/test identifier that produced this finding.
     *
     * @return the test id
     */
    public String getTestId() {
      return testId;
    }

    /**
     * The human-readable rule/test name.
     *
     * @return the test name
     */
    public String getTestName() {
      return testName;
    }

    /**
     * The file this finding refers to.
     *
     * @return the file path
     */
    public String getFile() {
      return file;
    }

    /**
     * The line number within the file.
     *
     * @return the line number
     */
    public int getLineNumber() {
      return lineNumber;
    }

    /**
     * The severity label (e.g., {@code CRITICAL}, {@code HIGH}).
     *
     * @return the severity
     */
    public String getSeverity() {
      return severity;
    }

    /**
     * The finding description.
     *
     * @return the message
     */
    public String getMessage() {
      return message;
    }
  }

  /** Per-function complexity hotspot — name, file, line, raw CC. */
  public static final class ComplexityHotspot {
    private final String functionName;
    private final String file;
    private final int lineNumber;
    private final int complexity;

    /**
     * Creates a complexity hotspot.
     *
     * @param functionName the function/method name
     * @param file the file the function is declared in
     * @param lineNumber the line number of the declaration
     * @param complexity the raw cyclomatic complexity value
     */
    public ComplexityHotspot(String functionName, String file, int lineNumber, int complexity) {
      this.functionName = Objects.requireNonNull(functionName, "functionName");
      this.file = Objects.requireNonNull(file, "file");
      this.lineNumber = lineNumber;
      this.complexity = complexity;
    }

    /**
     * The function/method name.
     *
     * @return the function name
     */
    public String getFunctionName() {
      return functionName;
    }

    /**
     * The file the function is declared in.
     *
     * @return the file path
     */
    public String getFile() {
      return file;
    }

    /**
     * The line number of the declaration.
     *
     * @return the line number
     */
    public int getLineNumber() {
      return lineNumber;
    }

    /**
     * The raw cyclomatic complexity value.
     *
     * @return the complexity
     */
    public int getComplexity() {
      return complexity;
    }
  }

  /** Bandit-equivalent rollup that the Pebble template renders verbatim from Python's shape. */
  public static final class BanditSummary {
    private final List<SecurityIssue> topIssues;
    private final int criticalCount;
    private final int highCount;
    private final int mediumCount;
    private final int lowCount;
    private final String summary;

    /**
     * Builds a rollup from raw security findings: retains the ten highest-severity issues (sorted
     * descending) and tallies counts per severity bucket.
     *
     * @param issues the security findings to summarize
     */
    public BanditSummary(List<SecurityIssue> issues) {
      List<SecurityIssue> all = new ArrayList<>(issues);
      // Sort by severity descending — matches Python's `sorted(... reverse=True)`.
      all.sort(
          (a, b) -> Integer.compare(severityRank(b.getSeverity()), severityRank(a.getSeverity())));
      this.topIssues = Collections.unmodifiableList(all.subList(0, Math.min(10, all.size())));

      int crit = 0;
      int high = 0;
      int med = 0;
      int low = 0;
      for (SecurityIssue i : issues) {
        switch (i.getSeverity().toUpperCase(java.util.Locale.ROOT)) {
          case "CRITICAL":
            crit++;
            break;
          case "HIGH":
            high++;
            break;
          case "MEDIUM":
            med++;
            break;
          case "LOW":
          default:
            low++;
            break;
        }
      }
      this.criticalCount = crit;
      this.highCount = high;
      this.mediumCount = med;
      this.lowCount = low;
      this.summary = "Found " + issues.size() + " security issues";
    }

    /**
     * The highest-severity findings, capped at ten and sorted by severity descending.
     *
     * @return an unmodifiable list of the top issues
     */
    public List<SecurityIssue> getTopIssues() {
      return topIssues;
    }

    /**
     * The number of {@code CRITICAL}-severity findings.
     *
     * @return the critical count
     */
    public int getCriticalCount() {
      return criticalCount;
    }

    /**
     * The number of {@code HIGH}-severity findings.
     *
     * @return the high count
     */
    public int getHighCount() {
      return highCount;
    }

    /**
     * The number of {@code MEDIUM}-severity findings.
     *
     * @return the medium count
     */
    public int getMediumCount() {
      return mediumCount;
    }

    /**
     * The number of {@code LOW}-severity (and unclassified) findings.
     *
     * @return the low count
     */
    public int getLowCount() {
      return lowCount;
    }

    /**
     * A short human-readable summary line of the total finding count.
     *
     * @return the summary text
     */
    public String getSummary() {
      return summary;
    }

    private static int severityRank(String s) {
      switch (s.toUpperCase(java.util.Locale.ROOT)) {
        case "CRITICAL":
          return 4;
        case "HIGH":
          return 3;
        case "MEDIUM":
          return 2;
        case "LOW":
        default:
          return 1;
      }
    }
  }

  /** Complexity rollup mirroring Python's {@code tool_outputs.complexity}. */
  public static final class ComplexitySummary {
    private final List<ComplexityHotspot> complexFunctions;

    /**
     * Builds a rollup, sorting the hotspots by complexity descending.
     *
     * @param hotspots the per-function complexity entries to summarize
     */
    public ComplexitySummary(List<ComplexityHotspot> hotspots) {
      List<ComplexityHotspot> all = new ArrayList<>(hotspots);
      all.sort((a, b) -> Integer.compare(b.getComplexity(), a.getComplexity()));
      this.complexFunctions = Collections.unmodifiableList(all);
    }

    /**
     * The complexity hotspots, sorted by complexity descending.
     *
     * @return an unmodifiable list of hotspots
     */
    public List<ComplexityHotspot> getComplexFunctions() {
      return complexFunctions;
    }
  }

  private final BanditSummary bandit;
  private final ComplexitySummary complexity;
  private final int totalFiles;
  private final int totalLines;
  private final double cyclomaticComplexityRaw;
  private final double halsteadVolumeRaw;

  private ToolOutputs(Builder b) {
    this.bandit = b.bandit;
    this.complexity = b.complexity;
    this.totalFiles = b.totalFiles;
    this.totalLines = b.totalLines;
    this.cyclomaticComplexityRaw = b.cyclomaticComplexityRaw;
    this.halsteadVolumeRaw = b.halsteadVolumeRaw;
  }

  /**
   * The security-finding rollup, or {@code null} if none was supplied.
   *
   * @return the bandit summary, or {@code null}
   */
  public BanditSummary getBandit() {
    return bandit;
  }

  /**
   * The complexity rollup, or {@code null} if none was supplied.
   *
   * @return the complexity summary, or {@code null}
   */
  public ComplexitySummary getComplexity() {
    return complexity;
  }

  /**
   * The total number of source files analyzed.
   *
   * @return the total file count
   */
  public int getTotalFiles() {
    return totalFiles;
  }

  /**
   * The total number of source lines analyzed.
   *
   * @return the total line count
   */
  public int getTotalLines() {
    return totalLines;
  }

  /**
   * The raw (un-normalized) average cyclomatic complexity.
   *
   * @return the raw cyclomatic complexity
   */
  public double getCyclomaticComplexityRaw() {
    return cyclomaticComplexityRaw;
  }

  /**
   * The raw (un-normalized) Halstead volume.
   *
   * @return the raw Halstead volume
   */
  public double getHalsteadVolumeRaw() {
    return halsteadVolumeRaw;
  }

  /**
   * Creates a new builder with all fields defaulting to empty/zero.
   *
   * @return a fresh {@link Builder}
   */
  public static Builder builder() {
    return new Builder();
  }

  /** Empty tool-output container — the Pebble template branches handle missing data. */
  public static ToolOutputs empty() {
    return new Builder().build();
  }

  /** Fluent builder for {@link ToolOutputs}. */
  public static final class Builder {
    private BanditSummary bandit;
    private ComplexitySummary complexity;
    private int totalFiles;
    private int totalLines;
    private double cyclomaticComplexityRaw;
    private double halsteadVolumeRaw;

    private Builder() {}

    /**
     * Sets the security findings, summarized into a {@link BanditSummary}.
     *
     * @param issues the security findings
     * @return this builder
     */
    public Builder bandit(List<SecurityIssue> issues) {
      this.bandit = new BanditSummary(issues);
      return this;
    }

    /**
     * Sets the complexity hotspots, summarized into a {@link ComplexitySummary}.
     *
     * @param hotspots the complexity hotspots
     * @return this builder
     */
    public Builder complexity(List<ComplexityHotspot> hotspots) {
      this.complexity = new ComplexitySummary(hotspots);
      return this;
    }

    /**
     * Sets the total number of source files analyzed.
     *
     * @param n the file count
     * @return this builder
     */
    public Builder totalFiles(int n) {
      this.totalFiles = n;
      return this;
    }

    /**
     * Sets the total number of source lines analyzed.
     *
     * @param n the line count
     * @return this builder
     */
    public Builder totalLines(int n) {
      this.totalLines = n;
      return this;
    }

    /**
     * Sets the raw (un-normalized) average cyclomatic complexity.
     *
     * @param v the raw cyclomatic complexity
     * @return this builder
     */
    public Builder cyclomaticComplexityRaw(double v) {
      this.cyclomaticComplexityRaw = v;
      return this;
    }

    /**
     * Sets the raw (un-normalized) Halstead volume.
     *
     * @param v the raw Halstead volume
     * @return this builder
     */
    public Builder halsteadVolumeRaw(double v) {
      this.halsteadVolumeRaw = v;
      return this;
    }

    /**
     * Builds the immutable tool-output container.
     *
     * @return a new {@link ToolOutputs}
     */
    public ToolOutputs build() {
      return new ToolOutputs(this);
    }
  }
}
