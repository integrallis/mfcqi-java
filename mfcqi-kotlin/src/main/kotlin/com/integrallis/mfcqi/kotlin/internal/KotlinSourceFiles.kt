package com.integrallis.mfcqi.kotlin.internal

import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors

/**
 * Discovers `.kt` source files under a codebase, excluding build/IDE/VCS directories. Kotlin script
 * files are intentionally excluded: PMD's Kotlin frontend parses Kotlin source grammar, not Gradle
 * or general `.kts` script grammar.
 */
internal object KotlinSourceFiles {

  private val EXCLUDED =
    setOf(
      ".git",
      ".hg",
      ".svn",
      "node_modules",
      "build",
      "dist",
      "out",
      "bin",
      "target",
      ".gradle",
      ".idea",
      ".mvn",
      ".vscode",
      ".venv",
      "venv"
    )

  /** All Kotlin files under [codebase] (a directory or a single file). */
  fun find(codebase: Path): List<Path> {
    if (!Files.exists(codebase)) return emptyList()
    if (Files.isRegularFile(codebase)) {
      return if (isKotlin(codebase) && !isExcluded(codebase)) listOf(codebase) else emptyList()
    }
    if (!Files.isDirectory(codebase)) return emptyList()
    Files.walk(codebase).use { stream ->
      return stream
        .filter { Files.isRegularFile(it) }
        .filter { isKotlin(it) }
        .filter { !isExcluded(it) }
        .sorted()
        .collect(Collectors.toList())
    }
  }

  private fun isKotlin(path: Path): Boolean {
    val name = path.fileName?.toString() ?: return false
    return name.endsWith(".kt")
  }

  /** Excluded if any path element is a build/IDE/VCS dir or a dotfile (ignoring `.`/`..`). */
  private fun isExcluded(path: Path): Boolean =
    path.any { element ->
      val name = element.toString()
      name != "." && name != ".." && (name in EXCLUDED || name.startsWith("."))
    }
}
