package com.integrallis.mfcqi.core;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Abstract base for all quality metrics. Follows the Template Method pattern: {@link
 * #calculate(Path)} defines the calculation skeleton; subclasses implement the four primitive
 * operations {@link #extract(Path)}, {@link #normalize(Object)}, {@link #getWeight()}, and {@link
 * #getName()}.
 *
 * <p>The type parameter {@code T} is the raw extraction type — typically {@link Double} for simple
 * metrics or a structured POJO for metrics with detailed breakdowns. {@code normalize} maps the raw
 * value to {@code [0.0, 1.0]}.
 *
 * @param <T> raw extraction type
 */
public abstract class Metric<T> {

  /**
   * Template method orchestrating metric calculation:
   *
   * <ol>
   *   <li>validate codebase
   *   <li>pre-process hook
   *   <li>extract raw value
   *   <li>post-process raw hook
   *   <li>normalize to {@code [0,1]}
   *   <li>apply weight
   *   <li>build result
   *   <li>post-calculate hook
   * </ol>
   */
  public final MetricResult calculate(Path codebase) {
    if (!validateCodebase(codebase)) {
      return invalidCodebaseResult();
    }

    preProcess(codebase);
    T raw = extract(codebase);
    T processed = postProcessRaw(raw);
    double normalized = normalize(processed);
    double weighted = normalized * getWeight();

    MetricResult result =
        new MetricResult(getName(), raw, processed, normalized, weighted, getWeight(), null);

    postCalculate(result);
    return result;
  }

  /** True if the codebase exists and is a directory. Subclasses may override. */
  protected boolean validateCodebase(Path codebase) {
    return codebase != null && Files.isDirectory(codebase);
  }

  /** Result returned when the codebase is invalid. */
  protected MetricResult invalidCodebaseResult() {
    return new MetricResult(getName(), null, null, 0.0, 0.0, getWeight(), "Invalid codebase path");
  }

  /** Hook for pre-extraction work (warming caches, etc.). Default: no-op. */
  protected void preProcess(Path codebase) {}

  /** Hook to transform the raw value before normalization. Default: identity. */
  protected T postProcessRaw(T rawValue) {
    return rawValue;
  }

  /** Hook called after the result is built. Default: no-op. */
  protected void postCalculate(MetricResult result) {}

  /** Extract the raw metric value from the codebase. Primitive operation in the template method. */
  public abstract T extract(Path codebase);

  /** Map the raw value to {@code [0.0, 1.0]}. Primitive operation. */
  public abstract double normalize(T value);

  /** Evidence-based weight for this metric. Primitive operation. */
  public abstract double getWeight();

  /** Display/JSON name for this metric. Primitive operation. */
  public abstract String getName();
}
