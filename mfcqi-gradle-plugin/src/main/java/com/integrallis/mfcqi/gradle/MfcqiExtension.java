package com.integrallis.mfcqi.gradle;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;

/**
 * Configuration for the MFCQI Gradle plugin.
 *
 * <pre>
 * mfcqi {
 *     source = layout.projectDirectory        // what to analyze (default: project dir)
 *     parallelism = 4                          // metric workers (default: CPU count)
 *     jsonReport = layout.buildDirectory.file("reports/mfcqi/mfcqi.json")
 *     badgeFile = layout.projectDirectory.file(".github/badges/mfcqi.json")
 *     failOnGate = true                        // fail the build when a quality gate fails
 * }
 * </pre>
 */
public abstract class MfcqiExtension {

  /** Source root to analyze. Defaults to the project directory. */
  public abstract DirectoryProperty getSource();

  /** Number of metric workers. Defaults to the available processor count. */
  public abstract Property<Integer> getParallelism();

  /** Optional JSON report destination. */
  public abstract RegularFileProperty getJsonReport();

  /** Badge JSON destination. Defaults to {@code .github/badges/mfcqi.json}. */
  public abstract RegularFileProperty getBadgeFile();

  /** Optional explicit quality-gate config; otherwise {@code .mfcqi.yaml} is auto-discovered. */
  public abstract RegularFileProperty getGateFile();

  /** Whether {@code mfcqiGate} fails the build on a gate failure. Defaults to {@code true}. */
  public abstract Property<Boolean> getFailOnGate();
}
