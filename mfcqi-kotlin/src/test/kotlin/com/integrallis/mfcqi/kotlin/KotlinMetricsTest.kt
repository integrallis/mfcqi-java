package com.integrallis.mfcqi.kotlin

import java.nio.file.Files
import java.nio.file.Path
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class KotlinMetricsTest {

  @Test
  fun exposesTheKotlinMetricSet() {
    assertThat(KotlinMetrics.all()).extracting<String> { it.name }.contains("Cyclomatic Complexity")
  }

  @Test
  fun detectsKotlinSource(@TempDir dir: Path) {
    assertThat(KotlinMetrics.hasSource(dir)).isFalse()
    Files.writeString(dir.resolve("A.kt"), "fun a() = 1")
    assertThat(KotlinMetrics.hasSource(dir)).isTrue()
  }
}
