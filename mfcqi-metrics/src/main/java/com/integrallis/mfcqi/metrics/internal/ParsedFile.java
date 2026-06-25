package com.integrallis.mfcqi.metrics.internal;

import com.github.javaparser.ast.CompilationUnit;
import java.nio.file.Path;
import java.util.Objects;

/**
 * A successfully parsed Java source file: the original text (for token/line counts), the parsed
 * {@link CompilationUnit}, and the file path (for diagnostics).
 */
public final class ParsedFile {

  private final Path path;
  private final String source;
  private final CompilationUnit compilationUnit;

  /**
   * Creates a parsed-file record.
   *
   * @param path the file's path (must not be {@code null})
   * @param source the original source text (must not be {@code null})
   * @param compilationUnit the parsed AST (must not be {@code null})
   * @throws NullPointerException if any argument is {@code null}
   */
  public ParsedFile(Path path, String source, CompilationUnit compilationUnit) {
    this.path = Objects.requireNonNull(path, "path");
    this.source = Objects.requireNonNull(source, "source");
    this.compilationUnit = Objects.requireNonNull(compilationUnit, "compilationUnit");
  }

  /**
   * Returns the path of the parsed file.
   *
   * @return the file path, never {@code null}
   */
  public Path path() {
    return path;
  }

  /**
   * Returns the original source text, used for token and line counts.
   *
   * @return the source text, never {@code null}
   */
  public String source() {
    return source;
  }

  /**
   * Returns the parsed JavaParser AST for this file.
   *
   * @return the compilation unit, never {@code null}
   */
  public CompilationUnit compilationUnit() {
    return compilationUnit;
  }
}
