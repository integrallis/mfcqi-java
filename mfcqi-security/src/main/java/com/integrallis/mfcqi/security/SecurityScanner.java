package com.integrallis.mfcqi.security;

import java.nio.file.Path;
import java.util.List;

/**
 * Produces {@link SecurityFinding}s for a codebase. Implementations may work from source
 * (JavaParser AST) or from compiled bytecode (SpotBugs + FindSecBugs). This lets {@link
 * SecurityMetric} reuse its CVSS-density scoring across scanning strategies.
 */
public interface SecurityScanner {

  /** Scan the given codebase root and return the security findings. */
  List<SecurityFinding> scan(Path codebase);
}
