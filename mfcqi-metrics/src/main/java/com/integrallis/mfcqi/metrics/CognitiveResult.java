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

  /**
   * Creates a result. The supplied lists are wrapped as unmodifiable.
   *
   * @param average the mean cognitive complexity across all analyzed functions
   * @param functions every analyzed function's entry (must not be {@code null})
   * @param hotspots the subset of functions at or above the configured threshold (must not be
   *     {@code null})
   * @throws NullPointerException if {@code functions} or {@code hotspots} is {@code null}
   */
  public CognitiveResult(
      double average, List<FunctionEntry> functions, List<FunctionEntry> hotspots) {
    this.average = average;
    this.functions = Collections.unmodifiableList(Objects.requireNonNull(functions, "functions"));
    this.hotspots = Collections.unmodifiableList(Objects.requireNonNull(hotspots, "hotspots"));
  }

  /**
   * Returns the mean cognitive complexity across all analyzed functions.
   *
   * @return the average complexity, or {@code 0.0} if no functions were analyzed
   */
  public double average() {
    return average;
  }

  /**
   * Returns every analyzed function's complexity entry.
   *
   * @return an unmodifiable list of function entries
   */
  public List<FunctionEntry> functions() {
    return functions;
  }

  /**
   * Returns the functions whose complexity reached the hotspot threshold, sorted descending by
   * complexity.
   *
   * @return an unmodifiable list of hotspot entries
   */
  public List<FunctionEntry> hotspots() {
    return hotspots;
  }

  /** A single function/method entry — name, complexity, source location. */
  public static final class FunctionEntry {
    private final String functionName;
    private final int complexity;
    private final int lineNumber;
    private final String filePath;

    /**
     * Creates a function entry.
     *
     * @param functionName the method, constructor, or initializer name (must not be {@code null})
     * @param complexity the cognitive complexity of the function
     * @param lineNumber the 1-based line where the function begins
     * @param filePath the source file containing the function (must not be {@code null})
     * @throws NullPointerException if {@code functionName} or {@code filePath} is {@code null}
     */
    public FunctionEntry(String functionName, int complexity, int lineNumber, String filePath) {
      this.functionName = Objects.requireNonNull(functionName, "functionName");
      this.complexity = complexity;
      this.lineNumber = lineNumber;
      this.filePath = Objects.requireNonNull(filePath, "filePath");
    }

    /**
     * Returns the name of the function this entry describes.
     *
     * @return the function name, never {@code null}
     */
    public String functionName() {
      return functionName;
    }

    /**
     * Returns the cognitive complexity computed for this function.
     *
     * @return the complexity value
     */
    public int complexity() {
      return complexity;
    }

    /**
     * Returns the 1-based source line where this function begins.
     *
     * @return the line number, or {@code 0} if unknown
     */
    public int lineNumber() {
      return lineNumber;
    }

    /**
     * Returns the path of the source file containing this function.
     *
     * @return the file path, never {@code null}
     */
    public String filePath() {
      return filePath;
    }
  }
}
