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

  public String id() {
    return id;
  }

  public String name() {
    return name;
  }

  public SmellCategory category() {
    return category;
  }

  public SmellSeverity severity() {
    return severity;
  }

  public String location() {
    return location;
  }

  public String tool() {
    return tool;
  }

  public String description() {
    return description;
  }

  public double severityWeight() {
    return severityWeight;
  }

  @Override
  public String toString() {
    return id + "[" + severity + "] " + location + " (" + tool + ")";
  }
}
