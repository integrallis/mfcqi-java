package com.integrallis.mfcqi.core;

/**
 * Programming paradigm classification produced by {@link ParadigmDetector}. Drives which OO metrics
 * (RFC, DIT, MHF, CBO, LCOM) the calculator includes.
 *
 * <p>Mirrors the four-level classification used by the Python reference implementation.
 */
public enum Paradigm {
  /** Class-heavy code with significant inheritance. All five OO metrics apply. */
  STRONG_OO,
  /** Mix of classes and free functions. All five OO metrics apply. */
  MIXED_OO,
  /** A few classes in mostly procedural code. Only RFC applies. */
  WEAK_OO,
  /** Almost no classes. No OO metrics apply. */
  PROCEDURAL
}
