package com.integrallis.mfcqi.kotlin

import com.integrallis.mfcqi.core.Metric
import com.integrallis.mfcqi.kotlin.internal.KotlinComplexity
import com.integrallis.mfcqi.kotlin.internal.KotlinParser
import com.integrallis.mfcqi.kotlin.internal.KotlinSourceFiles
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.exp

/**
 * Cyclomatic Complexity for Kotlin source: the average per-function complexity (1 + decision
 * points) across all `.kt`/`.kts` files. Aggregation and the normalization curve mirror the Java
 * [com.integrallis.mfcqi.core.Metric] of the same name so Kotlin and Java scores are comparable.
 */
class KotlinCyclomaticComplexity : Metric<Double>() {

  override fun validateCodebase(codebase: Path): Boolean =
    Files.isDirectory(codebase) || Files.isRegularFile(codebase)

  override fun extract(codebase: Path): Double {
    val complexities =
      KotlinSourceFiles.find(codebase).flatMap { file ->
        val code = runCatching { Files.readString(file) }.getOrNull() ?: return@flatMap emptyList()
        val ast =
          runCatching { KotlinParser.parse(code, file.toString()) }.getOrNull()
            ?: return@flatMap emptyList()
        KotlinComplexity.perFunction(ast)
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
