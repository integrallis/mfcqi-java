package com.integrallis.mfcqi.kotlin

import com.integrallis.mfcqi.core.Metric
import com.integrallis.mfcqi.kotlin.internal.PmdKotlinAnalysis
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.exp

/**
 * Cyclomatic Complexity for Kotlin source: the average per-function complexity (1 + decision
 * points) across all `.kt` files. Aggregation and the normalization curve mirror the Java
 * [com.integrallis.mfcqi.core.Metric] of the same name so Kotlin and Java scores are comparable.
 */
class KotlinCyclomaticComplexity : Metric<Double>() {

  override fun validateCodebase(codebase: Path): Boolean =
    Files.isDirectory(codebase) || Files.isRegularFile(codebase)

  override fun extract(codebase: Path): Double {
    val complexities =
      PmdKotlinAnalysis.analyze(codebase).files.flatMap { file ->
        file.nodes("FunctionDeclaration").map { kotlinFunctionComplexity(file, it) }
      }
    if (complexities.isEmpty()) return 1.0
    return complexities.average()
  }

  override fun normalize(value: Double?): Double {
    val v = value ?: return 0.0
    if (v <= 1.0) return 1.0
    if (v >= 25.0) return 0.0
    return exp(-0.1 * (v - 1.0))
  }

  override fun getWeight(): Double = 0.85

  override fun getName(): String = "Cyclomatic Complexity"
}
