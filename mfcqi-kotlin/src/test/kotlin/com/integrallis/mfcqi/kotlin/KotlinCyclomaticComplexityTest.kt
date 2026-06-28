package com.integrallis.mfcqi.kotlin

import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.exp
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class KotlinCyclomaticComplexityTest {

  private val metric = KotlinCyclomaticComplexity()

  @Test
  fun weightAndNameMatchTheJavaMetric() {
    assertThat(metric.getWeight()).isEqualTo(0.85)
    assertThat(metric.getName()).isEqualTo("Cyclomatic Complexity")
  }

  @Test
  fun normalizeFollowsTheExponentialCurve() {
    assertThat(metric.normalize(1.0)).isEqualTo(1.0)
    assertThat(metric.normalize(0.5)).isEqualTo(1.0)
    assertThat(metric.normalize(25.0)).isEqualTo(0.0)
    assertThat(metric.normalize(40.0)).isEqualTo(0.0)
    assertThat(metric.normalize(11.0)).isCloseTo(exp(-1.0), within(1e-9))
    assertThat(metric.normalize(null)).isEqualTo(0.0)
  }

  @Test
  fun averagesPerFunctionComplexityAcrossFiles(@TempDir dir: Path) {
    // fun a(): complexity 1 (no decisions)
    // fun b(): base 1 + if 1 + && 1 = 3
    Files.writeString(
      dir.resolve("Sample.kt"),
      """
        package demo
        fun a(): Int = 1
        fun b(x: Int): Int = if (x > 0 && x < 10) 1 else 2
        """
        .trimIndent()
    )
    // average of {1, 3} = 2.0
    assertThat(metric.extract(dir)).isCloseTo(2.0, within(1e-9))
  }

  @Test
  fun countsLoopsWhenAndCatchAsDecisions(@TempDir dir: Path) {
    Files.writeString(
      dir.resolve("Loops.kt"),
      """
        fun f(xs: List<Int>): Int {
            var t = 0
            for (x in xs) {              // +1
                when {                    // when entries below
                    x > 0 -> t += 1       // +1
                    else -> t -= 1        // +1 (whenEntry)
                }
                while (t > 100) { t -= 1 }   // +1
            }
            try { t += 1 } catch (e: Exception) { }  // +1 catchBlock
            return t
        }
        """
        .trimIndent()
    )
    // single function: base 1 + for 1 + 2 whenEntries + while 1 + catch 1 = 6
    assertThat(metric.extract(dir)).isCloseTo(6.0, within(1e-9))
  }

  @Test
  fun emptyCodebaseDefaultsToOne(@TempDir dir: Path) {
    assertThat(metric.extract(dir)).isEqualTo(1.0)
  }

  @Test
  fun acceptsASingleFilePath(@TempDir dir: Path) {
    val file = Files.writeString(dir.resolve("One.kt"), "fun a(): Int = if (true) 1 else 2")
    // single function: base 1 + if 1 = 2
    assertThat(metric.extract(file)).isCloseTo(2.0, within(1e-9))
  }

  @Test
  fun ignoresNonKotlinAndMissingPaths(@TempDir dir: Path) {
    Files.writeString(dir.resolve("notes.txt"), "fun a() = if (x) 1 else 2")
    assertThat(metric.extract(dir)).isEqualTo(1.0) // no .kt files -> default 1.0
    assertThat(metric.extract(dir.resolve("missing"))).isEqualTo(1.0)
  }
}
