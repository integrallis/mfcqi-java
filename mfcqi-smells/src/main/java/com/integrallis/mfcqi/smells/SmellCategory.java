package com.integrallis.mfcqi.smells;

/**
 * Categories of code smells. Direct port of {@code mfcqi/smell_detection/models.py:SmellCategory}.
 * Drives the per-category weighting performed by the {@code CodeSmellDensity} metric.
 */
public enum SmellCategory {
  /** High-level system structure issues (cyclic dependencies, hubs, god objects). */
  ARCHITECTURAL,
  /** Class-level design problems (god classes, deep inheritance, high coupling). */
  DESIGN,
  /** Code-level implementation issues (long methods, long parameter lists, dead code). */
  IMPLEMENTATION,
  /** Test-specific smells (assertion roulette, empty tests, sleepy tests). */
  TEST
}
