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

    public String getTestId() {
      return testId;
    }

    public String getTestName() {
      return testName;
    }

    public String getFile() {
      return file;
    }

    public int getLineNumber() {
      return lineNumber;
    }

    public String getSeverity() {
      return severity;
    }

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

    public ComplexityHotspot(String functionName, String file, int lineNumber, int complexity) {
      this.functionName = Objects.requireNonNull(functionName, "functionName");
      this.file = Objects.requireNonNull(file, "file");
      this.lineNumber = lineNumber;
      this.complexity = complexity;
    }

    public String getFunctionName() {
      return functionName;
    }

    public String getFile() {
      return file;
    }

    public int getLineNumber() {
      return lineNumber;
    }

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

    public List<SecurityIssue> getTopIssues() {
      return topIssues;
    }

    public int getCriticalCount() {
      return criticalCount;
    }

    public int getHighCount() {
      return highCount;
    }

    public int getMediumCount() {
      return mediumCount;
    }

    public int getLowCount() {
      return lowCount;
    }

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

    public ComplexitySummary(List<ComplexityHotspot> hotspots) {
      List<ComplexityHotspot> all = new ArrayList<>(hotspots);
      all.sort((a, b) -> Integer.compare(b.getComplexity(), a.getComplexity()));
      this.complexFunctions = Collections.unmodifiableList(all);
    }

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

  public BanditSummary getBandit() {
    return bandit;
  }

  public ComplexitySummary getComplexity() {
    return complexity;
  }

  public int getTotalFiles() {
    return totalFiles;
  }

  public int getTotalLines() {
    return totalLines;
  }

  public double getCyclomaticComplexityRaw() {
    return cyclomaticComplexityRaw;
  }

  public double getHalsteadVolumeRaw() {
    return halsteadVolumeRaw;
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Empty tool-output container — the Pebble template branches handle missing data. */
  public static ToolOutputs empty() {
    return new Builder().build();
  }

  public static final class Builder {
    private BanditSummary bandit;
    private ComplexitySummary complexity;
    private int totalFiles;
    private int totalLines;
    private double cyclomaticComplexityRaw;
    private double halsteadVolumeRaw;

    private Builder() {}

    public Builder bandit(List<SecurityIssue> issues) {
      this.bandit = new BanditSummary(issues);
      return this;
    }

    public Builder complexity(List<ComplexityHotspot> hotspots) {
      this.complexity = new ComplexitySummary(hotspots);
      return this;
    }

    public Builder totalFiles(int n) {
      this.totalFiles = n;
      return this;
    }

    public Builder totalLines(int n) {
      this.totalLines = n;
      return this;
    }

    public Builder cyclomaticComplexityRaw(double v) {
      this.cyclomaticComplexityRaw = v;
      return this;
    }

    public Builder halsteadVolumeRaw(double v) {
      this.halsteadVolumeRaw = v;
      return this;
    }

    public ToolOutputs build() {
      return new ToolOutputs(this);
    }
  }
}
