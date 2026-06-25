package com.integrallis.mfcqi.smells;

import java.util.Objects;

/**
 * A single detected code smell. Direct port of the Python {@code Smell} dataclass in {@code
 * mfcqi/smell_detection/models.py}.
 *
 * <p>De-duplication contract: two {@code Smell}s are considered duplicates if they share the same
 * {@code id} and {@code location}; when duplicates are found, the one with the higher severity wins
 * (see {@link SmellAggregator}).
 */
public final class Smell {

  private final String id;
  private final String name;
  private final SmellCategory category;
  private final SmellSeverity severity;
  private final String location;
  private final String tool;
  private final String description;
  private final double severityWeight;

  public Smell(
      String id,
      String name,
      SmellCategory category,
      SmellSeverity severity,
      String location,
      String tool,
      String description) {
    this(id, name, category, severity, location, tool, description, severity.defaultWeight());
  }

  public Smell(
      String id,
      String name,
      SmellCategory category,
      SmellSeverity severity,
      String location,
      String tool,
      String description,
      double severityWeight) {
    this.id = Objects.requireNonNull(id, "id");
    this.name = Objects.requireNonNull(name, "name");
    this.category = Objects.requireNonNull(category, "category");
    this.severity = Objects.requireNonNull(severity, "severity");
    this.location = Objects.requireNonNull(location, "location");
    this.tool = Objects.requireNonNull(tool, "tool");
    this.description = Objects.requireNonNull(description, "description");
    this.severityWeight = severityWeight;
  }

  /**
   * Returns the smell type identifier (e.g. {@code "LONG_METHOD"}). Together with {@link
   * #location()} it forms the de-duplication key.
   *
   * @return the smell type id
   */
  public String id() {
    return id;
  }

  /**
   * Returns the human-readable smell name (e.g. {@code "Long Method"}).
   *
   * @return the display name
   */
  public String name() {
    return name;
  }

  /**
   * Returns the category this smell belongs to, which drives per-category weighting.
   *
   * @return the smell category
   */
  public SmellCategory category() {
    return category;
  }

  /**
   * Returns the severity bucket of this smell.
   *
   * @return the severity
   */
  public SmellSeverity severity() {
    return severity;
  }

  /**
   * Returns the source location of the smell, typically {@code path:line}. Together with {@link
   * #id()} it forms the de-duplication key.
   *
   * @return the source location
   */
  public String location() {
    return location;
  }

  /**
   * Returns the identifier of the detector that emitted this smell.
   *
   * @return the emitting tool name
   */
  public String tool() {
    return tool;
  }

  /**
   * Returns the descriptive message explaining the smell and the threshold it exceeded.
   *
   * @return the description
   */
  public String description() {
    return description;
  }

  /**
   * Returns the weight contributed by this smell when aggregating severity-weighted counts.
   * Defaults to the {@link #severity()}'s default weight unless explicitly overridden at
   * construction.
   *
   * @return the severity weight
   */
  public double severityWeight() {
    return severityWeight;
  }

  @Override
  public String toString() {
    return id + "[" + severity + "] " + location + " (" + tool + ")";
  }
}
