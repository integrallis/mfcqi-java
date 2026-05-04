package com.integrallis.mfcqi.metrics;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Structured result of {@link CognitiveComplexity#extract(java.nio.file.Path)} — the average
 * cognitive complexity across analyzed functions, plus per-function entries and a hotspot list
 * (functions whose CC ≥ the configured threshold). Mirrors the dict {@code {average, functions,
 * hotspots}} returned by {@code mfcqi/metrics/cognitive.py:CognitiveComplexity.extract}.
 */
public final class CognitiveResult {

  private final double average;
  private final List<FunctionEntry> functions;
  private final List<FunctionEntry> hotspots;

  public CognitiveResult(
      double average, List<FunctionEntry> functions, List<FunctionEntry> hotspots) {
    this.average = average;
    this.functions = Collections.unmodifiableList(Objects.requireNonNull(functions, "functions"));
    this.hotspots = Collections.unmodifiableList(Objects.requireNonNull(hotspots, "hotspots"));
  }

  public double average() {
    return average;
  }

  public List<FunctionEntry> functions() {
    return functions;
  }

  public List<FunctionEntry> hotspots() {
    return hotspots;
  }

  /** A single function/method entry — name, complexity, source location. */
  public static final class FunctionEntry {
    private final String functionName;
    private final int complexity;
    private final int lineNumber;
    private final String filePath;

    public FunctionEntry(String functionName, int complexity, int lineNumber, String filePath) {
      this.functionName = Objects.requireNonNull(functionName, "functionName");
      this.complexity = complexity;
      this.lineNumber = lineNumber;
      this.filePath = Objects.requireNonNull(filePath, "filePath");
    }

    public String functionName() {
      return functionName;
    }

    public int complexity() {
      return complexity;
    }

    public int lineNumber() {
      return lineNumber;
    }

    public String filePath() {
      return filePath;
    }
  }
}
