package com.integrallis.mfcqi.kotlin

import com.integrallis.mfcqi.core.Metric
import com.integrallis.mfcqi.kotlin.internal.KotlinSourceFiles
import java.nio.file.Path

/**
 * Entry point for Kotlin analysis: the metric set and the source detector the calculator uses to
 * recognize a Kotlin codebase. Java callers (the CLI) consume these as static methods.
 */
object KotlinMetrics {

  /** Kotlin-aware implementations of every Java source metric category. */
  @JvmStatic
  fun all(): List<Metric<*>> =
    listOf(
      KotlinCyclomaticComplexity(),
      KotlinCognitiveComplexity(),
      KotlinHalsteadVolume(),
      KotlinMaintainabilityIndex(),
      KotlinCodeDuplication(),
      KotlinDocumentationCoverage(),
      KotlinSecurityMetric(),
      KotlinCodeSmellDensity(),
      KotlinRFCMetric(),
      KotlinDITMetric(),
      KotlinMHFMetric(),
      KotlinCouplingBetweenObjects(),
      KotlinLackOfCohesionOfMethods(),
    )

  /**
   * True if [codebase] contains any Kotlin source — the calculator's analyzable-source detector.
   */
  @JvmStatic fun hasSource(codebase: Path): Boolean = KotlinSourceFiles.find(codebase).isNotEmpty()
}
