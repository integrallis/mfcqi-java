package com.integrallis.mfcqi.smells;

import java.nio.file.Path;
import java.util.List;

/**
 * Strategy interface for individual code-smell detectors. Direct port of {@code
 * mfcqi/smell_detection/detector_base.py:SmellDetector} — each implementation provides its own tool
 * {@link #name()} and walks the codebase to emit {@link Smell} records.
 */
public interface SmellDetector {

  /** Tool identifier carried in every {@link Smell#tool()} this detector emits. */
  String name();

  /** Run detection over {@code codebase} and return the list of detected smells. */
  List<Smell> detect(Path codebase);
}
