package com.integrallis.mfcqi.kotlin.internal

import java.nio.file.Files
import java.nio.file.Path
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class KotlinSourceFilesTest {

  @Test
  fun includesKotlinSourcesButNotKotlinBuildScripts(@TempDir dir: Path) {
    val source = Files.writeString(dir.resolve("Sample.kt"), "class Sample")
    Files.writeString(dir.resolve("build.gradle.kts"), "plugins { kotlin(\"jvm\") }")
    Files.writeString(dir.resolve("script.kts"), "println(\"script\")")

    assertThat(KotlinSourceFiles.find(dir)).containsExactly(source)
  }
}
