package com.integrallis.mfcqi.core;

import java.nio.file.Path;

/**
 * Classifies a Java codebase's programming paradigm so the calculator can include only the
 * applicable OO metrics. Implementations live in {@code mfcqi-metrics} (they require AST access);
 * {@code mfcqi-core} only defines the contract so that the calculator can depend on it without
 * pulling in JavaParser.
 */
public interface ParadigmDetector {

  /** Detect the paradigm of the codebase rooted at {@code codebase}. */
  ParadigmDetection detect(Path codebase);
}
