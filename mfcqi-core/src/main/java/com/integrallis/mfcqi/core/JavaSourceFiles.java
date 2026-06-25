package com.integrallis.mfcqi.core;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Java equivalent of {@code mfcqi.core.file_utils.get_python_files}. Walks a codebase and returns
 * the {@code .java} files that should be analyzed, excluding common build/IDE directories and
 * dotfile-prefixed paths.
 *
 * <p>Direct port of {@code mfcqi/core/file_utils.py} (Python: {@code .py} → Java: {@code .java}).
 * The exclusion set keeps every entry from the Python {@code EXCLUDED_DIRS} verbatim — most are
 * harmless when scanning Java but the language-agnostic ones ({@code .git}, {@code build}, {@code
 * dist}, {@code node_modules}, …) still apply — and adds the canonical Java/JVM build conventions
 * that have no Python equivalent ({@code target}, {@code .gradle}, {@code .idea}, {@code .mvn},
 * {@code .vscode}, {@code out}, {@code bin}). Without those, Maven's {@code
 * target/generated-sources/} and IntelliJ's {@code .idea/} would be picked up.
 *
 * <p>The "any path part starting with {@code .}" rule and the test-file substring heuristic ({@code
 * "test" in stem.lower() or "tests" in str(parent)}) are translated literally.
 */
public final class JavaSourceFiles {

  /**
   * Exclusion set translated from {@code mfcqi/core/file_utils.py:EXCLUDED_DIRS}, augmented with
   * Java build-tool conventions (see class javadoc).
   */
  private static final Set<String> EXCLUDED_DIRS;

  static {
    Set<String> dirs = new HashSet<>();
    // Verbatim from Python EXCLUDED_DIRS:
    Collections.addAll(
        dirs,
        ".venv",
        "venv",
        "env",
        ".env",
        "__pycache__",
        ".pytest_cache",
        ".tox",
        ".git",
        ".hg",
        ".svn",
        "node_modules",
        "build",
        "dist",
        ".eggs",
        "*.egg-info",
        ".mypy_cache",
        ".ruff_cache",
        ".coverage",
        "htmlcov",
        "site-packages",
        ".hypothesis",
        ".nox");
    // Java/JVM build-tool conventions (no Python equivalent):
    Collections.addAll(dirs, "target", ".gradle", ".idea", ".mvn", ".vscode", "out", "bin");
    EXCLUDED_DIRS = Collections.unmodifiableSet(dirs);
  }

  private JavaSourceFiles() {}

  /** Convenience overload — equivalent to {@code find(codebase, false)}. */
  public static List<Path> findAll(Path codebase) {
    return find(codebase, false);
  }

  /**
   * Get Java files to analyze. Accepts either a directory or a single {@code .java} file. Mirrors
   * the signature of {@code get_python_files(codebase, exclude_tests=False)}.
   */
  public static List<Path> find(Path codebase, boolean excludeTests) {
    if (codebase == null) {
      return Collections.emptyList();
    }

    // Single-file support — port of `if codebase.is_file(): ...` branch.
    if (Files.isRegularFile(codebase)) {
      String fileName = fileName(codebase);
      if (fileName.isEmpty()) {
        return Collections.emptyList();
      }
      String stemLower = stem(fileName).toLowerCase(Locale.ROOT);
      Path parent = codebase.getParent();
      String parentString = parent == null ? "" : parent.toString();
      boolean isTest = stemLower.contains("test") || parentString.contains("tests");
      boolean isJava = fileName.endsWith(".java");
      if (isJava && shouldAnalyzeFile(codebase) && !(excludeTests && isTest)) {
        return Collections.singletonList(codebase);
      }
      return Collections.emptyList();
    }

    if (!Files.isDirectory(codebase)) {
      return Collections.emptyList();
    }

    // Directory traversal — port of `for py_file in codebase.rglob("*.py"): ...`.
    List<Path> javaFiles = new ArrayList<>();
    try (Stream<Path> stream = Files.walk(codebase)) {
      stream
          .filter(Files::isRegularFile)
          .filter(p -> fileName(p).endsWith(".java"))
          .forEach(
              javaFile -> {
                if (isExcluded(javaFile)) {
                  return;
                }
                if (excludeTests && isTestFile(javaFile)) {
                  return;
                }
                javaFiles.add(javaFile);
              });
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to walk codebase: " + codebase, e);
    }

    // Sort for deterministic ordering — matches Python's `sorted(py_files)`.
    Collections.sort(javaFiles);
    return javaFiles;
  }

  /** Port of {@code should_analyze_file} — true if no path part is excluded or dotfile-prefixed. */
  public static boolean shouldAnalyzeFile(Path file) {
    return !isExcluded(file);
  }

  private static boolean isExcluded(Path file) {
    for (Path part : file) {
      String name = part.toString();
      // The current-dir/parent-dir tokens are not hidden directories: they appear in any
      // relative path (e.g. walking "." for `mfcqi analyze .`) and must not trigger the
      // dotfile-exclusion rule below — otherwise every file under a relative root is dropped.
      if (name.equals(".") || name.equals("..")) {
        continue;
      }
      if (EXCLUDED_DIRS.contains(name) || name.startsWith(".")) {
        return true;
      }
    }
    return false;
  }

  private static boolean isTestFile(Path file) {
    String stemLower = stem(fileName(file)).toLowerCase(Locale.ROOT);
    Path parent = file.getParent();
    String parentString = parent == null ? "" : parent.toString();
    return stemLower.contains("test") || parentString.contains("tests");
  }

  /** Null-safe wrapper around {@link Path#getFileName()} — returns "" for paths without a name. */
  private static String fileName(Path file) {
    Path name = file.getFileName();
    return name == null ? "" : name.toString();
  }

  private static String stem(String fileName) {
    int dot = fileName.lastIndexOf('.');
    return dot < 0 ? fileName : fileName.substring(0, dot);
  }
}
