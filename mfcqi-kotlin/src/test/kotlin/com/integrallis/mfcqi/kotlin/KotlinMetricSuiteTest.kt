package com.integrallis.mfcqi.kotlin

import java.nio.file.Files
import java.nio.file.Path
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class KotlinMetricSuiteTest {

  @Test
  fun extractsAllMetricCategoriesFromIdiomaticKotlin(@TempDir dir: Path) {
    Files.writeString(
      dir.resolve("Service.kt"),
      """
      package demo

      /** Coordinates repository work. */
      class Service(private val repository: Repository) : BaseService() {
        private val cache = mutableMapOf<String, String>()

        /** Loads and caches a value. */
        fun load(key: String, fallback: String, enabled: Boolean): String {
          if (enabled && key.isNotBlank()) {
            return cache.getOrPut(key) { repository.find(key) ?: fallback }
          }
          return fallback
        }

        private fun clear() {
          cache.clear()
        }
      }

      open class BaseService
      interface Repository {
        fun find(key: String): String?
      }
      """
        .trimIndent()
    )

    val rawValues = KotlinMetrics.all().associate { it.name to it.calculate(dir).rawValue() }

    assertThat(rawValues.keys).hasSize(13)
    assertThat(rawValues["Cognitive Complexity"] as Double).isGreaterThan(0.0)
    assertThat(rawValues["Halstead Volume"] as Double).isGreaterThan(0.0)
    assertThat(rawValues["Maintainability Index"] as Double).isBetween(0.0, 100.0)
    assertThat(rawValues["Documentation Coverage"] as Double).isGreaterThan(0.0)
    assertThat(rawValues["rfc"] as Double).isGreaterThan(0.0)
    assertThat(rawValues["dit"] as Double).isEqualTo(1.0)
    assertThat(rawValues["mhf"] as Double).isEqualTo(1.0 / 3.0)
    assertThat(rawValues["Coupling Between Objects"] as Double).isGreaterThan(0.0)
    assertThat(rawValues["Lack of Cohesion of Methods"] as Double).isGreaterThan(0.0)

    val normalized =
      KotlinMetrics.all().associate { it.name to it.calculate(dir).normalizedValue() }
    assertThat(normalized["Cognitive Complexity"]).isBetween(0.0, 1.0)
    assertThat(normalized["Code Smell Density"]).isGreaterThan(0.0)
  }

  @Test
  fun securityMetricDetectsKotlinCommandExecution(@TempDir dir: Path) {
    Files.writeString(
      dir.resolve("Danger.kt"),
      """
      fun run(command: String) {
        Runtime.getRuntime().exec(command)
      }
      """
        .trimIndent()
    )

    val security = KotlinMetrics.all().single { it.name == "security" }

    assertThat(security.calculate(dir).normalizedValue()).isLessThan(1.0)
  }

  @Test
  fun duplicationMetricPenalizesRepeatedKotlinBlocks(@TempDir dir: Path) {
    val repeated =
      """
      fun first(input: Int): Int {
        val adjusted = input + 1
        val doubled = adjusted * 2
        return doubled
      }
      """
        .trimIndent()
    Files.writeString(dir.resolve("First.kt"), repeated)
    Files.writeString(dir.resolve("Second.kt"), repeated.replace("first", "second"))

    val duplication = KotlinMetrics.all().single { it.name == "Code Duplication" }

    assertThat(duplication.calculate(dir).normalizedValue()).isLessThan(1.0)
  }
}
